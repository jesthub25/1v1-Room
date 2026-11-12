package org.jestron.arena1v1.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Effect;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Ses ve efekt oynatma. 1.21.3+ ile Sound bir enum olmaktan cikti; bu sinif Sound tipine
 * dogrudan baglanmadan, anahtar (string) uzerinden calistigi icin tum 1.20.1+ surumlerde guvenlidir.
 */
public final class Effects {

    private static Map<String, String> soundsByLegacyName;
    private static Logger logger;

    private Effects() {
    }

    public static void init(Logger pluginLogger) {
        logger = pluginLogger;
        soundsByLegacyName = null;
    }

    /** Config'deki ses ismini oyuncuya calar. Bos/gecersiz ise sessizce atlar. */
    public static void playSound(Player player, String configured) {
        if (player == null || configured == null) {
            return;
        }
        String key = resolveSound(configured);
        if (key == null) {
            return;
        }
        try {
            player.playSound(player.getLocation(), key, 1.0f, 1.0f);
        } catch (Throwable ignored) {
            // ses calinamazsa oyunu etkilememeli
        }
    }

    static String resolveSound(String configured) {
        String value = configured.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.indexOf('.') >= 0 || value.indexOf(':') >= 0) {
            return value.toLowerCase(Locale.ROOT);
        }
        Map<String, String> map = legacyMap();
        String key = map.get(value.toUpperCase(Locale.ROOT));
        if (key == null && logger != null) {
            logger.warning("Gecersiz ses ismi config'de: " + configured);
            map.put(value.toUpperCase(Locale.ROOT), "");
        }
        return key == null || key.isEmpty() ? null : key;
    }

    private static synchronized Map<String, String> legacyMap() {
        if (soundsByLegacyName != null) {
            return soundsByLegacyName;
        }
        Map<String, String> map = new HashMap<>();
        try {
            for (Object entry : soundRegistry()) {
                if (!(entry instanceof Keyed)) {
                    continue;
                }
                NamespacedKey key = ((Keyed) entry).getKey();
                String legacyName = key.getKey().replace('.', '_').toUpperCase(Locale.ROOT);
                map.put(legacyName, key.toString());
            }
        } catch (Throwable t) {
            if (logger != null) {
                logger.warning("Ses listesi okunamadi, eski isimli sesler calmayacak: " + t);
            }
        }
        soundsByLegacyName = map;
        return map;
    }

    /** Surume gore SOUND_EVENT (yeni) veya SOUNDS (eski) registry'si; dogrudan alan baglantisi yok. */
    private static Iterable<?> soundRegistry() throws ReflectiveOperationException {
        Class<?> registryClass = Class.forName("org.bukkit.Registry");
        ReflectiveOperationException last = null;
        for (String field : new String[]{"SOUND_EVENT", "SOUNDS"}) {
            try {
                Object value = registryClass.getField(field).get(null);
                if (value instanceof Iterable) {
                    return (Iterable<?>) value;
                }
            } catch (NoSuchFieldException e) {
                last = e;
            }
        }
        throw last != null ? last : new NoSuchFieldException("SOUNDS");
    }

    /** Spawn isinlanma efekti. */
    public static void playEffect(Location location, String effectName) {
        if (location == null || effectName == null || effectName.trim().isEmpty()) {
            return;
        }
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        try {
            Effect effect = Effect.valueOf(effectName.trim().toUpperCase(Locale.ROOT));
            world.playEffect(location, effect, 0);
        } catch (Throwable ignored) {
            // gecersiz efekt veya veri gerektiren efekt: sessizce atla
        }
    }
}
