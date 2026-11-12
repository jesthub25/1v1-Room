package org.jestron.arena1v1.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jestron.arena1v1.Arena1v1Plugin;
import org.jestron.arena1v1.config.Settings;
import org.jestron.arena1v1.manager.ArenaManager;
import org.jestron.arena1v1.manager.MatchManager;
import org.jestron.arena1v1.model.Arena;
import org.jestron.arena1v1.model.Cuboid;
import org.jestron.arena1v1.model.Match;
import org.jestron.arena1v1.model.MatchState;
import org.jestron.arena1v1.util.Text;

public final class ArenaCommand implements TabExecutor {

    private static final List<String> ADMIN_SUBCOMMANDS =
            Arrays.asList("olustur", "arena", "sil", "reload", "setspawn", "pos1", "pos2", "bitir");
    private static final List<String> USER_SUBCOMMANDS = Collections.singletonList("liste");

    private final Arena1v1Plugin plugin;

    public ArenaCommand(Arena1v1Plugin plugin) {
        this.plugin = plugin;
    }

    private Settings settings() {
        return plugin.getSettings();
    }

    private ArenaManager arenas() {
        return plugin.getArenaManager();
    }

    private MatchManager matches() {
        return plugin.getMatchManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "olustur":
            case "oluştur":
                handleCreate(sender, args);
                break;
            case "arena":
            case "kapi":
                handleDoor(sender, args);
                break;
            case "sil":
                handleDelete(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "liste":
                handleList(sender);
                break;
            case "setspawn":
                handleSetSpawn(sender);
                break;
            case "pos1":
                handlePos(sender, 0);
                break;
            case "pos2":
                handlePos(sender, 1);
                break;
            case "bitir":
                handleStop(sender, args);
                break;
            default:
                sendHelp(sender, label);
                break;
        }
        return true;
    }

    // ------------------------------------------------------------------ alt komutlar

    private boolean checkAdmin(CommandSender sender) {
        if (!sender.hasPermission("arena.admin")) {
            settings().send(sender, "yetki-yok");
            return false;
        }
        return true;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        settings().send(sender, "oyuncu-degil");
        return null;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) {
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.colorize("&cKullanim: /1v1 olustur <isim>"));
            return;
        }
        String name = args[1];
        if (!ArenaManager.isValidName(name)) {
            settings().send(player, "gecersiz-isim");
            return;
        }
        if (arenas().get(name) != null) {
            settings().send(player, "arena-zaten-var", "arena", name);
            return;
        }
        Cuboid selection = plugin.getSelectionManager().getSelection(player, plugin.getLogger());
        if (selection == null) {
            settings().send(player, "secim-yok");
            return;
        }
        arenas().create(name, selection);
        plugin.getSelectionManager().clear(player.getUniqueId());
        settings().send(player, "arena-olusturuldu", "arena", name);
    }

    private void handleDoor(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) {
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.colorize("&cKullanim: /1v1 arena <isim> <blok>"));
            sender.sendMessage(Text.colorize("&7Ornek: /1v1 arena arena1 IRON_BARS"));
            return;
        }
        Arena arena = arenas().get(args[1]);
        if (arena == null) {
            settings().send(player, "arena-bulunamadi", "arena", args[1]);
            return;
        }
        String blockName = args[2];
        Material material = Material.matchMaterial(blockName.trim().toUpperCase(Locale.ROOT));
        if (material == null) {
            settings().send(player, "gecersiz-blok", "blok", blockName);
            return;
        }
        if (!MatchManager.isValidDoorMaterial(material)) {
            settings().send(player, "blok-uygun-degil", "blok", material.name());
            return;
        }
        Match match = matches().getMatch(arena);
        if (match != null && match.isBusy()) {
            settings().send(player, "mac-var", "arena", arena.getName());
            return;
        }
        Cuboid selection = plugin.getSelectionManager().getSelection(player, plugin.getLogger());
        if (selection == null) {
            settings().send(player, "secim-yok");
            return;
        }
        if (!selection.getWorldName().equals(arena.getWorldName())) {
            settings().send(player, "secim-farkli-dunya");
            return;
        }
        if (selection.volume() > MatchManager.MAX_DOOR_VOLUME) {
            settings().send(player, "kapi-cok-buyuk", "max", String.valueOf(MatchManager.MAX_DOOR_VOLUME));
            return;
        }
        arenas().setDoor(arena, selection, material);
        plugin.getSelectionManager().clear(player.getUniqueId());
        settings().send(player, "kapi-ayarlandi", "arena", arena.getName(), "blok", material.name());
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.colorize("&cKullanim: /1v1 sil <isim>"));
            return;
        }
        Arena arena = arenas().get(args[1]);
        if (arena == null) {
            settings().send(sender, "arena-bulunamadi", "arena", args[1]);
            return;
        }
        matches().endMatchFor(arena);
        arenas().delete(arena);
        settings().send(sender, "arena-silindi", "arena", arena.getName());
    }

    private void handleReload(CommandSender sender) {
        if (!checkAdmin(sender)) {
            return;
        }
        settings().load();
        arenas().load();
        matches().validateAfterReload();
        settings().send(sender, "reload-tamamlandi");
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("arena.use") && !sender.hasPermission("arena.admin")) {
            settings().send(sender, "yetki-yok");
            return;
        }
        sender.sendMessage(Text.colorize("&8=== &cArena Listesi &8==="));
        if (arenas().getArenas().isEmpty()) {
            sender.sendMessage(Text.colorize("&cHic arena bulunmuyor!"));
            return;
        }
        for (Arena arena : arenas().getArenas()) {
            Match match = matches().getMatch(arena);
            String status;
            if (match == null) {
                status = "&a[BOS]";
            } else if (match.getState() == MatchState.WAITING) {
                status = "&e[BEKLIYOR]";
            } else if (match.getState() == MatchState.FIGHTING) {
                status = "&c[MAC: " + match.getFormattedTime() + "]";
            } else {
                status = "&6[BITIYOR]";
            }
            String doorStatus = arena.hasDoor() ? "&a[K:VAR]" : "&c[K:YOK]";
            sender.sendMessage(Text.colorize("&e" + arena.getName() + " " + status + " " + doorStatus
                    + " &7(" + matches().getPlayerCount(arena) + "/2)"));
        }
    }

    private void handleSetSpawn(CommandSender sender) {
        if (!checkAdmin(sender)) {
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        settings().setSpawn(player.getLocation());
        settings().send(player, "spawn-ayarlandi");
    }

    private void handlePos(CommandSender sender, int index) {
        if (!checkAdmin(sender)) {
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Location loc = player.getLocation();
        plugin.getSelectionManager().setPosition(player, index, loc);
        settings().send(player, "pos-ayarlandi",
                "pos", String.valueOf(index + 1),
                "x", String.valueOf(loc.getBlockX()),
                "y", String.valueOf(loc.getBlockY()),
                "z", String.valueOf(loc.getBlockZ()));
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.colorize("&cKullanim: /1v1 bitir <isim>"));
            return;
        }
        Arena arena = arenas().get(args[1]);
        if (arena == null) {
            settings().send(sender, "arena-bulunamadi", "arena", args[1]);
            return;
        }
        Match match = matches().getMatch(arena);
        if (match == null) {
            settings().send(sender, "mac-yok", "arena", arena.getName());
            return;
        }
        matches().forceEnd(match, true);
        settings().send(sender, "mac-bitirildi", "arena", arena.getName());
    }

    private void sendHelp(CommandSender sender, String label) {
        String l = "/" + label;
        sender.sendMessage(Text.colorize("&8=== &c1v1 Arena Komutlari &8==="));
        if (sender.hasPermission("arena.admin")) {
            sender.sendMessage(Text.colorize("&e" + l + " olustur <isim> &7- Secili alandan arena olustur"));
            sender.sendMessage(Text.colorize("&e" + l + " arena <isim> <blok> &7- Secili alani kapi yap"));
            sender.sendMessage(Text.colorize("&e" + l + " pos1 &7/ &e" + l + " pos2 &7- WorldEdit'siz alan secimi"));
            sender.sendMessage(Text.colorize("&e" + l + " sil <isim> &7- Arena sil"));
            sender.sendMessage(Text.colorize("&e" + l + " bitir <isim> &7- Devam eden maci bitir"));
            sender.sendMessage(Text.colorize("&e" + l + " reload &7- Config yenile"));
            sender.sendMessage(Text.colorize("&e" + l + " setspawn &7- Spawn noktasi ayarla"));
        }
        sender.sendMessage(Text.colorize("&e" + l + " liste &7- Arena listesi"));
        sender.sendMessage(Text.colorize("&7Arenaya girmek icin &earena alanina yuruyun&7!"));
    }

    // ------------------------------------------------------------------ tab

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission("arena.admin");
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (admin) {
                options.addAll(ADMIN_SUBCOMMANDS);
            }
            options.addAll(USER_SUBCOMMANDS);
            return filter(options, args[0]);
        }
        if (!admin) {
            return Collections.emptyList();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "sil":
                case "arena":
                case "kapi":
                case "bitir":
                    return filter(arenas().getArenaNames(), args[1]);
                default:
                    return Collections.emptyList();
            }
        }
        if (args.length == 3 && (sub.equals("arena") || sub.equals("kapi"))) {
            String input = args[2].toUpperCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (Material material : Material.values()) {
                if (result.size() >= 50) {
                    break;
                }
                if (material.name().startsWith(input) && MatchManager.isValidDoorMaterial(material)) {
                    result.add(material.name());
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
