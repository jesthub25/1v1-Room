package org.jestron.arena1v1.hook;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jestron.arena1v1.model.Cuboid;

/**
 * WorldEdit / FastAsyncWorldEdit secimini okur. Derleme zamani bagimliligi yoktur (reflection),
 * bu yuzden WorldEdit yuklu degilse, guncellenirse veya FAWE kullanilsa bile plugin hata vermez.
 */
public final class WorldEditHook {

    private static boolean warned;

    private WorldEditHook() {
    }

    public static boolean isAvailable() {
        return findPlugin() != null;
    }

    private static Plugin findPlugin() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (plugin == null || !plugin.isEnabled()) {
            plugin = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
        }
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }

    /**
     * Oyuncunun bulundugu dunyadaki tam WorldEdit secimi. Secim yoksa/eksikse veya WorldEdit yoksa null.
     */
    public static Cuboid getSelection(Player player, Logger logger) {
        Plugin worldEdit = findPlugin();
        if (worldEdit == null) {
            return null;
        }
        try {
            ClassLoader cl = worldEdit.getClass().getClassLoader();
            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, cl);
            Class<?> worldEditClass = Class.forName("com.sk89q.worldedit.WorldEdit", true, cl);
            Class<?> sessionManagerClass = Class.forName("com.sk89q.worldedit.session.SessionManager", true, cl);
            Class<?> sessionOwnerClass = Class.forName("com.sk89q.worldedit.session.SessionOwner", true, cl);
            Class<?> localSessionClass = Class.forName("com.sk89q.worldedit.LocalSession", true, cl);
            Class<?> weWorldClass = Class.forName("com.sk89q.worldedit.world.World", true, cl);
            Class<?> regionClass = Class.forName("com.sk89q.worldedit.regions.Region", true, cl);
            Class<?> vectorClass = Class.forName("com.sk89q.worldedit.math.BlockVector3", true, cl);

            Object wePlayer = adapterClass.getMethod("adapt", Player.class).invoke(null, player);
            Object weWorld = adapterClass.getMethod("adapt", World.class).invoke(null, player.getWorld());
            Object instance = worldEditClass.getMethod("getInstance").invoke(null);
            Object sessionManager = worldEditClass.getMethod("getSessionManager").invoke(instance);
            Object session = sessionManagerClass.getMethod("get", sessionOwnerClass).invoke(sessionManager, wePlayer);
            if (session == null) {
                return null;
            }
            Object region = localSessionClass.getMethod("getSelection", weWorldClass).invoke(session, weWorld);
            if (region == null) {
                return null;
            }
            Object min = regionClass.getMethod("getMinimumPoint").invoke(region);
            Object max = regionClass.getMethod("getMaximumPoint").invoke(region);
            int[] a = coords(vectorClass, min);
            int[] b = coords(vectorClass, max);
            return new Cuboid(player.getWorld().getName(), a[0], a[1], a[2], b[0], b[1], b[2]);
        } catch (InvocationTargetException e) {
            // IncompleteRegionException vb.: secim yok veya eksik
            return null;
        } catch (Throwable t) {
            if (!warned && logger != null) {
                warned = true;
                logger.warning("WorldEdit secimi okunamadi (" + t + "). /1v1 pos1 ve /1v1 pos2 kullanabilirsin.");
            }
            return null;
        }
    }

    private static int[] coords(Class<?> vectorClass, Object vector) throws ReflectiveOperationException {
        return new int[]{
                component(vectorClass, vector, "x", "getBlockX", "getX"),
                component(vectorClass, vector, "y", "getBlockY", "getY"),
                component(vectorClass, vector, "z", "getBlockZ", "getZ")
        };
    }

    private static int component(Class<?> vectorClass, Object vector, String... names) throws ReflectiveOperationException {
        ReflectiveOperationException last = null;
        for (String name : names) {
            try {
                Method m = vectorClass.getMethod(name);
                Object value = m.invoke(vector);
                if (value instanceof Number) {
                    return (int) Math.floor(((Number) value).doubleValue());
                }
            } catch (NoSuchMethodException e) {
                last = e;
            }
        }
        throw last != null ? last : new NoSuchMethodException(String.join("/", names));
    }
}
