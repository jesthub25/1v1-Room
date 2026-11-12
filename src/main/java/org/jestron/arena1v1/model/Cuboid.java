package org.jestron.arena1v1.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * Blok koordinatlariyla tanimli, iki kosesi dahil dikdortgenler prizmasi alan.
 */
public final class Cuboid {

    private final String worldName;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public Cuboid(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        if (worldName == null || worldName.isEmpty()) {
            throw new IllegalArgumentException("Dunya ismi bos olamaz");
        }
        this.worldName = worldName;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public static Cuboid of(String worldName, Vector a, Vector b) {
        return new Cuboid(worldName,
                a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    /** Konum bu alanin icinde mi (blok bazli, max koseler dahil). */
    public boolean contains(Location loc) {
        if (!isSameWorld(loc)) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    /** Sadece yatay (X/Z) sinirlar kontrol edilir; ziplama vb. yukseklik farklarinda takilmayi onler. */
    public boolean containsHorizontally(Location loc) {
        if (!isSameWorld(loc)) {
            return false;
        }
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    private boolean isSameWorld(Location loc) {
        if (loc == null) {
            return false;
        }
        World world = loc.getWorld();
        return world != null && world.getName().equals(worldName);
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (long) (maxY - minY + 1) * (long) (maxZ - minZ + 1);
    }

    public Vector getMinVector() {
        return new Vector(minX, minY, minZ);
    }

    public Vector getMaxVector() {
        return new Vector(maxX, maxY, maxZ);
    }

    public String getWorldName() {
        return worldName;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    @Override
    public String toString() {
        return worldName + " [" + minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ + "]";
    }
}
