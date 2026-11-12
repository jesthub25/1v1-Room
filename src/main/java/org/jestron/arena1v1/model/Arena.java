package org.jestron.arena1v1.model;

import java.util.Locale;

/**
 * Arena tanimi (sadece geometri ve ayarlar). Mac durumu {@link Match} icinde tutulur.
 */
public final class Arena {

    private final String name;
    private final Cuboid area;
    private Cuboid door;
    private String doorMaterial;

    public Arena(String name, Cuboid area) {
        this.name = name;
        this.area = area;
    }

    public static String keyOf(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public String getName() {
        return name;
    }

    public String getKey() {
        return keyOf(name);
    }

    public String getWorldName() {
        return area.getWorldName();
    }

    public Cuboid getArea() {
        return area;
    }

    public Cuboid getDoor() {
        return door;
    }

    public String getDoorMaterial() {
        return doorMaterial;
    }

    public boolean hasDoor() {
        return door != null && doorMaterial != null && !doorMaterial.isEmpty();
    }

    public void setDoor(Cuboid door, String doorMaterial) {
        this.door = door;
        this.doorMaterial = doorMaterial;
    }
}
