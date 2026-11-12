package org.jestron.arena1v1.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jestron.arena1v1.hook.WorldEditHook;
import org.jestron.arena1v1.model.Cuboid;

/**
 * /1v1 pos1 - /1v1 pos2 ile yapilan secimler; yoksa WorldEdit secimi kullanilir.
 */
public final class SelectionManager {

    private final Map<UUID, Location[]> positions = new HashMap<>();

    public void setPosition(Player player, int index, Location location) {
        Location[] arr = positions.computeIfAbsent(player.getUniqueId(), k -> new Location[2]);
        arr[index] = location.getBlock().getLocation();
    }

    /**
     * Oncelik: tamamlanmis pos1/pos2 secimi (ayni dunyada), sonra WorldEdit secimi. Hicbiri yoksa null.
     */
    public Cuboid getSelection(Player player, Logger logger) {
        Location[] arr = positions.get(player.getUniqueId());
        if (arr != null && arr[0] != null && arr[1] != null) {
            World w1 = arr[0].getWorld();
            World w2 = arr[1].getWorld();
            if (w1 != null && w2 != null && w1.getName().equals(w2.getName())) {
                return new Cuboid(w1.getName(),
                        arr[0].getBlockX(), arr[0].getBlockY(), arr[0].getBlockZ(),
                        arr[1].getBlockX(), arr[1].getBlockY(), arr[1].getBlockZ());
            }
        }
        return WorldEditHook.getSelection(player, logger);
    }

    /** Basarili bir kullanimdan sonra pos1/pos2 temizlenir (bir sonraki secime karismasin). */
    public void clear(UUID uuid) {
        positions.remove(uuid);
    }
}
