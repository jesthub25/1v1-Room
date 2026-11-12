package org.jestron.arena1v1.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jestron.arena1v1.Arena1v1Plugin;
import org.jestron.arena1v1.config.Settings;
import org.jestron.arena1v1.model.Arena;
import org.jestron.arena1v1.model.Cuboid;
import org.jestron.arena1v1.model.Match;
import org.jestron.arena1v1.model.MatchState;
import org.jestron.arena1v1.util.ActionBar;
import org.jestron.arena1v1.util.Effects;
import org.jestron.arena1v1.util.Text;

/**
 * Tum 1v1 mac mantigi. Tum metotlar ana thread'den cagrilir.
 */
public final class MatchManager {

    public static final long MAX_DOOR_VOLUME = 10_000L;
    private static final long LOBBY_MESSAGE_COOLDOWN_MS = 3000L;

    private final Arena1v1Plugin plugin;
    private final Map<String, Match> matches = new HashMap<>();
    private final Map<UUID, Match> playerMatches = new HashMap<>();
    /** Mac sirasinda olen/oldurulen oyuncular: dogunca spawn'a gonderilir. */
    private final Set<UUID> pendingSpawn = new HashSet<>();
    /** Pluginin kendi isinlamalari (teleport engeline takilmasin). */
    private final Set<UUID> internalTeleport = new HashSet<>();
    private final Map<UUID, Long> joinMessageCooldown = new HashMap<>();
    private final Map<UUID, Long> leaveMessageCooldown = new HashMap<>();
    /** Mac sirasinda oyundan cikarken olduruluyor (olum olayi 'cikti' olarak islenir). */
    private final Set<UUID> quitting = new HashSet<>();

    public MatchManager(Arena1v1Plugin plugin) {
        this.plugin = plugin;
        Effects.init(plugin.getLogger());
    }

    private Settings settings() {
        return plugin.getSettings();
    }

    // ================================================================== sorgular

    public Match getMatch(Player player) {
        return playerMatches.get(player.getUniqueId());
    }

    public Match getMatch(Arena arena) {
        return matches.get(arena.getKey());
    }

    public boolean isBusy(Arena arena) {
        Match match = matches.get(arena.getKey());
        return match != null && match.isBusy();
    }

    public int getPlayerCount(Arena arena) {
        Match match = matches.get(arena.getKey());
        return match == null ? 0 : match.size();
    }

    /** Oyuncu dovuste veya kazanan olarak item toplama asamasinda mi (komutlar engellenir). */
    public boolean isInActiveMatch(Player player) {
        Match match = playerMatches.get(player.getUniqueId());
        return match != null && match.isBusy();
    }

    public boolean isInternalTeleport(Player player) {
        return internalTeleport.contains(player.getUniqueId());
    }

    public boolean isEligibleToJoin(Player player) {
        GameMode mode = player.getGameMode();
        return (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && !player.isDead()
                && player.hasPermission("arena.use");
    }

    // ================================================================== lobi

    /**
     * Oyuncuyu arenanin bekleme lobisine ekler; ikinci oyuncu geldiginde mac baslar.
     *
     * @return oyuncu bu arenaya katildiysa true
     */
    public boolean tryJoin(Player player, Arena arena) {
        if (!arena.hasDoor() || Bukkit.getWorld(arena.getWorldName()) == null || !isEligibleToJoin(player)) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        Match current = playerMatches.get(uuid);
        if (current != null) {
            if (current.getArenaKey().equals(arena.getKey()) || current.isBusy()) {
                return false;
            }
            leaveLobby(player, current, false);
        }

        Match match = matches.get(arena.getKey());
        if (match != null && (match.isBusy() || match.isFull())) {
            return false;
        }
        if (match == null) {
            match = new Match(arena);
            matches.put(arena.getKey(), match);
        }
        if (!match.addPlayer(uuid)) {
            return false;
        }
        playerMatches.put(uuid, match);

        if (consumeCooldown(joinMessageCooldown, player)) {
            settings().send(player, "arenaya-katildin", "arena", arena.getName());
        }

        if (match.isFull()) {
            tryStart(match);
        } else {
            settings().send(player, "rakip-bekleniyor", "sayi", String.valueOf(match.size()));
        }
        return true;
    }

    /** Bekleme asamasindaki oyuncuyu lobiden cikarir. */
    public void leaveLobby(Player player, Match match, boolean notifySelf) {
        if (match.isBusy() || !match.hasPlayer(player.getUniqueId())) {
            return;
        }
        detach(match, player.getUniqueId());
        if (notifySelf && consumeCooldown(leaveMessageCooldown, player)) {
            settings().send(player, "arenadan-ayrildin");
        }
        for (Player other : onlinePlayers(match)) {
            settings().send(other, "oyuncu-ayrildi", "oyuncu", player.getName());
            settings().send(other, "rakip-bekleniyor", "sayi", String.valueOf(match.size()));
        }
        if (match.isEmpty()) {
            matches.remove(match.getArenaKey(), match);
        }
    }

    private static boolean consumeCooldown(Map<UUID, Long> cooldowns, Player player) {
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < LOBBY_MESSAGE_COOLDOWN_MS) {
            return false;
        }
        cooldowns.put(player.getUniqueId(), now);
        return true;
    }

    // ================================================================== mac baslangici

    private void tryStart(Match match) {
        Arena arena = match.getArena();
        // Baslamadan once iki oyuncunun da gercekten arenada ve uygun durumda oldugunu dogrula
        for (UUID uuid : match.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline() || !isEligibleToJoin(p) || !arena.getArea().contains(p.getLocation())) {
                detach(match, uuid);
            }
        }
        if (!match.isFull()) {
            if (match.isEmpty()) {
                matches.remove(match.getArenaKey(), match);
            } else {
                for (Player p : onlinePlayers(match)) {
                    settings().send(p, "rakip-bekleniyor", "sayi", String.valueOf(match.size()));
                }
            }
            return;
        }
        startFight(match);
    }

    private void startFight(Match match) {
        match.setState(MatchState.FIGHTING);
        match.setRemainingSeconds(settings().getMatchDuration());
        placeDoor(match);

        String time = match.getFormattedTime();
        for (Player p : onlinePlayers(match)) {
            sendTitle(p, settings().raw("baslik-basladi"), settings().raw("altbaslik-basladi", "sure", time), 10, 70, 20);
            settings().send(p, "arena-basladi");
            Effects.playSound(p, settings().getSound("kapi-kapanma"));
            Effects.playSound(p, settings().getSound("arena-baslama"));
        }
        match.setTimerTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(match), 20L, 20L));
    }

    private void tick(Match match) {
        if (matches.get(match.getArenaKey()) != match || match.getState() != MatchState.FIGHTING) {
            match.cancelTimer();
            return;
        }
        match.setRemainingSeconds(match.getRemainingSeconds() - 1);
        int left = match.getRemainingSeconds();
        String time = match.getFormattedTime();
        String color = left <= 60 ? "&c" : (left <= 300 ? "&e" : "&a");
        String actionBar = Text.colorize(color + "&l" + time);
        for (Player p : onlinePlayers(match)) {
            if (!ActionBar.send(p, actionBar) && left > 0 && left % 60 == 0) {
                settings().send(p, "kalan-sure", "sure", time);
            }
            if (left > 0 && left <= 10) {
                sendTitle(p, Text.colorize("&c&l" + left), settings().raw("altbaslik-son-saniyeler"), 0, 20, 0);
            }
        }
        if (left <= 0) {
            timeUp(match);
        }
    }

    private void timeUp(Match match) {
        match.cancelTimer();
        match.setState(MatchState.ENDING);
        int delay = settings().getDrawDoorDelay();
        for (Player p : onlinePlayers(match)) {
            sendTitle(p, settings().raw("baslik-sure-doldu"), settings().raw("altbaslik-sure-doldu"), 10, 70, 20);
            settings().send(p, "sure-doldu");
            if (delay > 0) {
                settings().send(p, "arena-cooldown", "saniye", String.valueOf(delay));
            }
            Effects.playSound(p, settings().getSound("arena-bitis"));
        }
        scheduleFinish(match, delay);
    }

    // ================================================================== mac sonu

    /** Bir oyuncu dovus sirasinda elendi (oldu / cikti). */
    private void eliminate(Match match, Player loser, boolean quit) {
        UUID loserId = loser.getUniqueId();
        detach(match, loserId);

        if (quit) {
            for (Player p : onlinePlayers(match)) {
                settings().send(p, "oyuncu-cikti", "oyuncu", loser.getName());
            }
        } else {
            settings().send(loser, "oyuncu-oldu", "oyuncu", loser.getName());
            for (Player p : onlinePlayers(match)) {
                settings().send(p, "oyuncu-oldu", "oyuncu", loser.getName());
            }
            settings().send(loser, "kaybettin");
            sendTitle(loser, settings().raw("baslik-kaybettin"), settings().raw("altbaslik-kaybettin"), 10, 60, 20);
            Effects.playSound(loser, settings().getSound("oyuncu-oldu"));
        }

        match.cancelTimer();
        match.setState(MatchState.ENDING);

        Player winner = null;
        for (Player p : onlinePlayers(match)) {
            if (!p.isDead()) {
                winner = p;
                break;
            }
        }
        if (winner == null) {
            finish(match);
            return;
        }

        int wait = settings().getWinnerWait();
        String waitText = String.valueOf(wait);
        if (quit) {
            settings().send(winner, "rakip-cikti");
        }
        settings().send(winner, "arena-bitti", "kazanan", winner.getName());
        settings().send(loser, "arena-bitti", "kazanan", winner.getName());
        settings().send(winner, "kazandin", "saniye", waitText);
        if (wait > 0) {
            settings().send(winner, "arena-cooldown", "saniye", waitText);
        }
        sendTitle(winner, settings().raw("baslik-kazandin"), settings().raw("altbaslik-kazandin", "saniye", waitText), 10, 70, 20);
        Effects.playSound(winner, settings().getSound("kazanma"));
        Effects.playSound(winner, settings().getSound("arena-bitis"));

        scheduleFinish(match, wait);
    }

    private void scheduleFinish(Match match, int seconds) {
        match.cancelEndTask();
        if (seconds <= 0) {
            finish(match);
            return;
        }
        match.setEndTask(Bukkit.getScheduler().runTaskLater(plugin, () -> finish(match), seconds * 20L));
    }

    /** Kapilari acar, kalan oyunculari spawn'a gonderir ve maci kapatir. */
    private void finish(Match match) {
        if (matches.get(match.getArenaKey()) != match) {
            match.cancelAllTasks();
            return;
        }
        match.cancelAllTasks();
        matches.remove(match.getArenaKey());
        removeDoor(match);
        for (UUID uuid : match.getPlayers()) {
            detach(match, uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                if (match.isBusy()) {
                    settings().send(p, "kapilar-acildi");
                }
                teleportToSpawn(p);
            }
        }
    }

    /** Yetkili tarafindan veya arena silinirken mac zorla bitirilir (berabere). */
    public void forceEnd(Match match, boolean notify) {
        if (notify) {
            for (Player p : onlinePlayers(match)) {
                settings().send(p, "mac-bitirildi", "arena", match.getArena().getName());
            }
        }
        if (!match.isBusy()) {
            // bekleme lobisi: sadece dagit, isinlama yok
            match.cancelAllTasks();
            matches.remove(match.getArenaKey(), match);
            for (UUID uuid : match.getPlayers()) {
                detach(match, uuid);
            }
            return;
        }
        finish(match);
    }

    // ================================================================== olaylar

    public void handleDeath(Player player) {
        Match match = playerMatches.get(player.getUniqueId());
        if (match == null) {
            return;
        }
        if (!match.isBusy()) {
            leaveLobby(player, match, false);
            return;
        }
        UUID uuid = player.getUniqueId();
        pendingSpawn.add(uuid);
        if (match.getState() == MatchState.FIGHTING) {
            eliminate(match, player, quitting.contains(uuid));
            return;
        }
        // Bitis asamasinda (kazanan item toplarken / berabere beklerken) olen oyuncu sadece cikarilir
        detach(match, uuid);
        if (match.isEmpty()) {
            finish(match);
        }
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        joinMessageCooldown.remove(uuid);
        leaveMessageCooldown.remove(uuid);
        Match match = playerMatches.get(uuid);
        if (match == null) {
            return;
        }
        switch (match.getState()) {
            case WAITING:
                leaveLobby(player, match, false);
                break;
            case FIGHTING:
                if (settings().isKillOnQuit() && !player.isDead()) {
                    // Itemler arenaya dussun: olum olayi uzerinden kayip akisi calisir
                    quitting.add(uuid);
                    try {
                        player.setHealth(0.0D);
                    } catch (Throwable ignored) {
                        // oldurulemediyse asagida normal cikis olarak islenir
                    } finally {
                        quitting.remove(uuid);
                    }
                }
                if (playerMatches.get(uuid) == match) {
                    // Olum gerceklesmedi (iptal edildi / ayar kapali): hukmen maglubiyet
                    if (!player.isDead()) {
                        teleportToSpawn(player);
                    } else {
                        pendingSpawn.add(uuid);
                    }
                    eliminate(match, player, true);
                }
                break;
            case ENDING:
            default:
                detach(match, uuid);
                if (!player.isDead()) {
                    teleportToSpawn(player);
                } else {
                    pendingSpawn.add(uuid);
                }
                if (match.isEmpty()) {
                    finish(match);
                }
                break;
        }
    }

    /** Oyuncu dogarken spawn'a gonderilmesi gerekiyorsa hedef konumu dondurur, yoksa null. */
    public Location consumeRespawnLocation(Player player) {
        if (!pendingSpawn.remove(player.getUniqueId())) {
            return null;
        }
        return settings().getSpawnOrDefault();
    }

    /** Sunucuya girdiginde: bekleyen spawn isinlamasi varsa uygula. */
    public void handleJoin(Player player) {
        UUID uuid = player.getUniqueId();
        if (pendingSpawn.contains(uuid) && !player.isDead()) {
            pendingSpawn.remove(uuid);
            teleportToSpawn(player);
            return;
        }
        if (player.hasPermission("arena.bypass") || !isEligibleToJoin(player)) {
            return;
        }
        Arena busy = findBusyArenaAt(player.getLocation());
        if (busy != null) {
            settings().send(player, "arena-mesgul");
            teleportToSpawn(player);
        }
    }

    public Arena findBusyArenaAt(Location location) {
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            if (isBusy(arena) && arena.getArea().contains(location)) {
                return arena;
            }
        }
        return null;
    }

    // ================================================================== yonetim

    /** Arena silinmeden/yeniden yuklenmeden once ilgili maci kapatir. */
    public void endMatchFor(Arena arena) {
        Match match = matches.get(arena.getKey());
        if (match != null) {
            forceEnd(match, true);
        }
    }

    /** Reload sonrasi artik var olmayan arenalardaki maclari kapatir. */
    public void validateAfterReload() {
        for (Match match : new ArrayList<>(matches.values())) {
            Arena current = plugin.getArenaManager().get(match.getArena().getName());
            if (current == null || (!match.isBusy() && current != match.getArena())) {
                forceEnd(match, match.isBusy());
            }
        }
    }

    /** Plugin acilisinda bos arenalarin kapilarini acar (cokme sonrasi kalan bloklar). */
    public void openAllIdleDoors() {
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            if (arena.hasDoor() && !isBusy(arena)) {
                clearDoor(arena.getDoor());
            }
        }
    }

    public void shutdown() {
        for (Match match : new ArrayList<>(matches.values())) {
            try {
                if (match.isBusy()) {
                    finish(match);
                } else {
                    forceEnd(match, false);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Mac kapatilirken hata: " + match.getArena().getName() + " (" + t + ")");
            }
        }
        matches.clear();
        playerMatches.clear();
        internalTeleport.clear();
        joinMessageCooldown.clear();
        leaveMessageCooldown.clear();
        quitting.clear();
    }

    // ================================================================== yardimcilar

    private void detach(Match match, UUID uuid) {
        match.removePlayer(uuid);
        playerMatches.remove(uuid, match);
    }

    private List<Player> onlinePlayers(Match match) {
        List<Player> list = new ArrayList<>(2);
        for (UUID uuid : match.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                list.add(p);
            }
        }
        return list;
    }

    public void teleportToSpawn(Player player) {
        if (player.isDead()) {
            pendingSpawn.add(player.getUniqueId());
            return;
        }
        Location target = settings().getSpawnOrDefault();
        if (target == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        internalTeleport.add(uuid);
        try {
            if (player.isInsideVehicle()) {
                player.leaveVehicle();
            }
            player.teleport(target);
        } catch (Throwable t) {
            plugin.getLogger().warning(player.getName() + " spawn'a isinlanamadi: " + t);
        } finally {
            internalTeleport.remove(uuid);
        }
        if (settings().isTeleportEffect()) {
            Effects.playEffect(player.getLocation(), settings().getTeleportEffectName());
        }
    }

    private void placeDoor(Match match) {
        Arena arena = match.getArena();
        if (!arena.hasDoor()) {
            return;
        }
        Material material = parseDoorMaterial(arena.getDoorMaterial());
        Cuboid door = arena.getDoor();
        match.setPlacedDoor(door, material);
        fill(door, material);
    }

    private void removeDoor(Match match) {
        Cuboid door = match.getPlacedDoor();
        if (door != null) {
            clearDoor(door);
        }
    }

    private void clearDoor(Cuboid door) {
        fill(door, Material.AIR);
    }

    private void fill(Cuboid region, Material material) {
        World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) {
            return;
        }
        for (int x = region.getMinX(); x <= region.getMaxX(); x++) {
            for (int y = region.getMinY(); y <= region.getMaxY(); y++) {
                for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != material) {
                        block.setType(material, false);
                    }
                }
            }
        }
    }

    /** Kapi icin uygun blok mu: gercek, kati, yercekimsiz blok. */
    public static Material parseValidDoorMaterial(String name) {
        if (name == null) {
            return null;
        }
        String clean = name.trim().toUpperCase(Locale.ROOT);
        if (clean.startsWith("MINECRAFT:")) {
            clean = clean.substring("MINECRAFT:".length());
        }
        if (clean.isEmpty() || clean.startsWith("LEGACY_")) {
            return null;
        }
        Material material = Material.matchMaterial(clean);
        if (material == null) {
            return null;
        }
        return isValidDoorMaterial(material) ? material : null;
    }

    public static boolean isValidDoorMaterial(Material material) {
        if (material == null || material.name().startsWith("LEGACY_")) {
            return false;
        }
        return material.isBlock() && !material.isAir() && material.isSolid() && !material.hasGravity();
    }

    private static Material parseDoorMaterial(String name) {
        Material material = parseValidDoorMaterial(name);
        return material != null ? material : Material.IRON_BARS;
    }

    private void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        } catch (Throwable ignored) {
            // baslik gonderilemezse oyunu etkilemesin
        }
    }
}
