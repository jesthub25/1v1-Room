package org.jestron.arena1v1.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.jestron.arena1v1.Arena1v1Plugin;
import org.jestron.arena1v1.util.Text;

/**
 * config.yml erisimi. Degerler yuklenirken guvenli araliga cekilir.
 */
public final class Settings {

    private static final String DEFAULT_PREFIX = "&8[&c1v1Arena&8] &r";

    private final Arena1v1Plugin plugin;

    private int matchDuration;
    private int winnerWait;
    private int drawDoorDelay;
    private boolean killOnQuit;
    private boolean teleportEffect;
    private String teleportEffectName;
    private Set<String> allowedCommands = Collections.emptySet();

    public Settings(Arena1v1Plugin plugin) {
        this.plugin = plugin;
    }

    /** config.yml'yi olusturur/yukler ve eksik yeni ayarlari mevcut dosyaya ekler. */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        config.options().copyDefaults(true);
        plugin.saveConfig();

        matchDuration = clamp(config.getInt("arena.mac-suresi", 600), 10, 86400);
        winnerWait = clamp(config.getInt("arena.bitis-bekleme-suresi", 30), 0, 3600);
        drawDoorDelay = clamp(config.getInt("arena.kapi-silme-suresi", 2), 0, 3600);
        killOnQuit = config.getBoolean("arena.cikista-oldur", true);
        teleportEffect = config.getBoolean("arena.efekt-ver", true);
        teleportEffectName = config.getString("arena.teleport-efekti", "ENDER_SIGNAL");

        Set<String> allowed = new HashSet<>();
        List<String> list = config.getStringList("arena.izinli-komutlar");
        for (String s : list) {
            if (s == null) {
                continue;
            }
            String c = s.trim().toLowerCase(Locale.ROOT);
            if (c.startsWith("/")) {
                c = c.substring(1);
            }
            if (!c.isEmpty()) {
                allowed.add(c);
            }
        }
        allowedCommands = Collections.unmodifiableSet(allowed);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public int getMatchDuration() {
        return matchDuration;
    }

    public int getWinnerWait() {
        return winnerWait;
    }

    public int getDrawDoorDelay() {
        return drawDoorDelay;
    }

    public boolean isKillOnQuit() {
        return killOnQuit;
    }

    public boolean isTeleportEffect() {
        return teleportEffect;
    }

    public String getTeleportEffectName() {
        return teleportEffectName;
    }

    public Set<String> getAllowedCommands() {
        return allowedCommands;
    }

    // ------------------------------------------------------------------ mesajlar

    /** Renklendirilmis, prefix'siz mesaj. Bulunamazsa uyari metni doner. */
    public String raw(String key, String... placeholders) {
        String value = plugin.getConfig().getString("mesajlar." + key);
        if (value == null) {
            value = "&cMesaj bulunamadi: " + key;
        }
        return Text.colorize(Text.replace(value, placeholders));
    }

    /** Prefix'li, renklendirilmis mesaj. */
    public String message(String key, String... placeholders) {
        String prefix = plugin.getConfig().getString("mesajlar.prefix", DEFAULT_PREFIX);
        return Text.colorize(prefix) + raw(key, placeholders);
    }

    /** Mesaj bos ise gondermez. */
    public void send(CommandSender sender, String key, String... placeholders) {
        if (sender == null) {
            return;
        }
        String rawValue = plugin.getConfig().getString("mesajlar." + key);
        if (rawValue != null && rawValue.trim().isEmpty()) {
            return;
        }
        sender.sendMessage(message(key, placeholders));
    }

    public String getSound(String key) {
        return plugin.getConfig().getString("sesler." + key, "");
    }

    // ------------------------------------------------------------------ spawn

    /**
     * Ayarlanmis spawn noktasi; ayarlanmamissa veya dunyasi yuklu degilse ana dunyanin spawn noktasi.
     * Hic dunya yoksa null.
     */
    public Location getSpawnOrDefault() {
        Location spawn = getConfiguredSpawn();
        if (spawn != null) {
            return spawn;
        }
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            return null;
        }
        return worlds.get(0).getSpawnLocation();
    }

    /** Sadece config'de ayarlanmis ve dunyasi yuklu spawn; yoksa null. */
    public Location getConfiguredSpawn() {
        FileConfiguration config = plugin.getConfig();
        String worldName = config.getString("spawn.world", "");
        if (worldName == null || worldName.isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world,
                config.getDouble("spawn.x"),
                config.getDouble("spawn.y"),
                config.getDouble("spawn.z"),
                (float) config.getDouble("spawn.yaw"),
                (float) config.getDouble("spawn.pitch"));
    }

    public void setSpawn(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        FileConfiguration config = plugin.getConfig();
        config.set("spawn.world", world.getName());
        config.set("spawn.x", loc.getX());
        config.set("spawn.y", loc.getY());
        config.set("spawn.z", loc.getZ());
        config.set("spawn.yaw", (double) loc.getYaw());
        config.set("spawn.pitch", (double) loc.getPitch());
        plugin.saveConfig();
    }
}
