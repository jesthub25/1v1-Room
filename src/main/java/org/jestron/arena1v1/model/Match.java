package org.jestron.arena1v1.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;

/**
 * Bir arenadaki tek bir 1v1 oturumu.
 */
public final class Match {

    public static final int MAX_PLAYERS = 2;

    private final Arena arena;
    private final Set<UUID> players = new LinkedHashSet<>();
    private MatchState state = MatchState.WAITING;
    private int remainingSeconds;
    private BukkitTask timerTask;
    private BukkitTask endTask;
    /** Mac basinda gercekten yerlestirilen kapi (reload/duzenleme sonrasi dogru alanin temizlenmesi icin). */
    private Cuboid placedDoor;
    private Material placedDoorMaterial;

    public Match(Arena arena) {
        this.arena = arena;
    }

    public Arena getArena() {
        return arena;
    }

    public String getArenaKey() {
        return arena.getKey();
    }

    public boolean addPlayer(UUID uuid) {
        if (players.size() >= MAX_PLAYERS) {
            return false;
        }
        return players.add(uuid);
    }

    public boolean removePlayer(UUID uuid) {
        return players.remove(uuid);
    }

    public boolean hasPlayer(UUID uuid) {
        return players.contains(uuid);
    }

    public List<UUID> getPlayers() {
        return new ArrayList<>(players);
    }

    public int size() {
        return players.size();
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public boolean isFull() {
        return players.size() >= MAX_PLAYERS;
    }

    public MatchState getState() {
        return state;
    }

    public void setState(MatchState state) {
        this.state = state;
    }

    public boolean isBusy() {
        return state != MatchState.WAITING;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public BukkitTask getTimerTask() {
        return timerTask;
    }

    public void setTimerTask(BukkitTask timerTask) {
        this.timerTask = timerTask;
    }

    public BukkitTask getEndTask() {
        return endTask;
    }

    public void setEndTask(BukkitTask endTask) {
        this.endTask = endTask;
    }

    public Cuboid getPlacedDoor() {
        return placedDoor;
    }

    public Material getPlacedDoorMaterial() {
        return placedDoorMaterial;
    }

    public void setPlacedDoor(Cuboid placedDoor, Material material) {
        this.placedDoor = placedDoor;
        this.placedDoorMaterial = material;
    }

    public void cancelTimer() {
        if (timerTask != null) {
            try {
                timerTask.cancel();
            } catch (IllegalStateException ignored) {
                // zaten iptal edilmis
            }
            timerTask = null;
        }
    }

    public void cancelEndTask() {
        if (endTask != null) {
            try {
                endTask.cancel();
            } catch (IllegalStateException ignored) {
                // zaten iptal edilmis
            }
            endTask = null;
        }
    }

    public void cancelAllTasks() {
        cancelTimer();
        cancelEndTask();
    }

    public String getFormattedTime() {
        int s = Math.max(0, remainingSeconds);
        return String.format("%02d:%02d", s / 60, s % 60);
    }
}
