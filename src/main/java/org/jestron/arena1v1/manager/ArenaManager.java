package org.jestron.arena1v1.manager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.jestron.arena1v1.Arena1v1Plugin;
import org.jestron.arena1v1.model.Arena;
import org.jestron.arena1v1.model.Cuboid;

/**
 * Arena tanimlarini arenas.yml'den yukler ve kaydeder. Dosya formati onceki surumle uyumludur.
 */
public final class ArenaManager {

    private static final String ROOT = "arenalar";
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private final Arena1v1Plugin plugin;
    private final File file;
    private YamlConfiguration config;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(Arena1v1Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
    }

    public static boolean isValidName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    public void load() {
        arenas.clear();
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().severe("Plugin klasoru olusturulamadi: " + parent.getAbsolutePath());
            }
            config = new YamlConfiguration();
            config.createSection(ROOT);
            save();
        } else {
            config = YamlConfiguration.loadConfiguration(file);
        }

        ConfigurationSection section = config.getConfigurationSection(ROOT);
        if (section == null) {
            plugin.getLogger().info("0 arena yuklendi.");
            return;
        }
        for (String name : section.getKeys(false)) {
            try {
                Arena arena = read(name);
                if (arena == null) {
                    plugin.getLogger().warning("Arena eksik veri nedeniyle atlandi: " + name);
                    continue;
                }
                if (arenas.containsKey(arena.getKey())) {
                    plugin.getLogger().warning("Ayni isimde (buyuk/kucuk harf farkli) ikinci arena atlandi: " + name);
                    continue;
                }
                arenas.put(arena.getKey(), arena);
            } catch (Exception e) {
                plugin.getLogger().warning("Arena yuklenirken hata: " + name + " (" + e.getMessage() + ")");
            }
        }
        plugin.getLogger().info(arenas.size() + " arena yuklendi.");
    }

    private Arena read(String name) {
        String path = ROOT + "." + name + ".";
        String world = config.getString(path + "dunya");
        Vector min = config.getVector(path + "min");
        Vector max = config.getVector(path + "max");
        if (world == null || world.isEmpty() || min == null || max == null) {
            return null;
        }
        Arena arena = new Arena(name, Cuboid.of(world, min, max));
        Vector doorMin = config.getVector(path + "kapi-min");
        Vector doorMax = config.getVector(path + "kapi-max");
        String doorBlock = config.getString(path + "kapi-blok");
        if (doorMin != null && doorMax != null && doorBlock != null && !doorBlock.isEmpty()) {
            arena.setDoor(Cuboid.of(world, doorMin, doorMax), doorBlock);
        }
        return arena;
    }

    private void write(Arena arena) {
        String base = ROOT + "." + arena.getName();
        config.set(base, null);
        String path = base + ".";
        config.set(path + "dunya", arena.getWorldName());
        config.set(path + "min", arena.getArea().getMinVector());
        config.set(path + "max", arena.getArea().getMaxVector());
        if (arena.hasDoor()) {
            config.set(path + "kapi-min", arena.getDoor().getMinVector());
            config.set(path + "kapi-max", arena.getDoor().getMaxVector());
            config.set(path + "kapi-blok", arena.getDoorMaterial());
        }
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("arenas.yml kaydedilemedi: " + e.getMessage());
        }
    }

    public Arena create(String name, Cuboid area) {
        Arena arena = new Arena(name, area);
        arenas.put(arena.getKey(), arena);
        write(arena);
        save();
        return arena;
    }

    public void setDoor(Arena arena, Cuboid door, Material material) {
        arena.setDoor(door, material.name());
        write(arena);
        save();
    }

    public void delete(Arena arena) {
        arenas.remove(arena.getKey());
        config.set(ROOT + "." + arena.getName(), null);
        save();
    }

    public Arena get(String name) {
        return name == null ? null : arenas.get(Arena.keyOf(name));
    }

    public Collection<Arena> getArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public List<String> getArenaNames() {
        List<String> names = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            names.add(arena.getName());
        }
        return names;
    }
}
