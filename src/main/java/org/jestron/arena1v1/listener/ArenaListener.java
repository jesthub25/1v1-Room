package org.jestron.arena1v1.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jestron.arena1v1.Arena1v1Plugin;
import org.jestron.arena1v1.manager.MatchManager;
import org.jestron.arena1v1.model.Arena;
import org.jestron.arena1v1.model.Match;
import org.jestron.arena1v1.model.MatchState;

public final class ArenaListener implements Listener {

    private static final long WARN_COOLDOWN_MS = 2000L;

    private final Arena1v1Plugin plugin;
    private final Map<UUID, Long> warnCooldown = new HashMap<>();

    public ArenaListener(Arena1v1Plugin plugin) {
        this.plugin = plugin;
    }

    private MatchManager matches() {
        return plugin.getMatchManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && sameWorld(from, to)) {
            return;
        }
        handleLocationChange(event.getPlayer(), from, to, event, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (matches().isInternalTeleport(player)) {
            return;
        }
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        handleLocationChange(player, event.getFrom(), to, event, true);
    }

    /**
     * Hareket ve isinlanmalarin ortak kontrolu.
     *
     * @param cancellable olay; iptal etmek icin kullanilir
     */
    private void handleLocationChange(Player player, Location from, Location to,
                                      org.bukkit.event.Cancellable cancellable, boolean teleport) {
        MatchManager mm = matches();
        Match match = mm.getMatch(player);

        if (match != null) {
            Arena arena = match.getArena();
            if (match.getState() == MatchState.WAITING) {
                if (!arena.getArea().contains(to)) {
                    mm.leaveLobby(player, match, true);
                }
                return;
            }
            // Dovus / bitis asamasi: arenadan kacis yok
            if (!arena.getArea().containsHorizontally(to)) {
                // Zaten disaridaysa (hatali alan vb.) oyuncuyu kilitleme
                if (!teleport && !arena.getArea().containsHorizontally(from)) {
                    return;
                }
                cancellable.setCancelled(true);
                warn(player, "kacis-yok");
            }
            return;
        }

        if (player.hasPermission("arena.bypass") || !mm.isEligibleToJoin(player)) {
            return;
        }

        for (Arena arena : plugin.getArenaManager().getArenas()) {
            if (!arena.hasDoor() || !arena.getArea().contains(to)) {
                continue;
            }
            if (mm.isBusy(arena)) {
                // Mac devam eden arenaya giris yok
                if (!teleport && arena.getArea().contains(from)) {
                    // Zaten iceride kalmis (ornegin giris aninda): disari gonder
                    warn(player, "arena-mesgul");
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (player.isOnline() && mm.getMatch(player) == null && arena.getArea().contains(player.getLocation())) {
                            mm.teleportToSpawn(player);
                        }
                    });
                    return;
                }
                cancellable.setCancelled(true);
                warn(player, "arena-mesgul");
                return;
            }
            if (teleport) {
                // Isinlanma tamamlandiktan sonra konum dogrulanarak katilim yapilir
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && mm.getMatch(player) == null && arena.getArea().contains(player.getLocation())) {
                        mm.tryJoin(player, arena);
                    }
                });
            } else {
                mm.tryJoin(player, arena);
            }
            return;
        }
    }

    private void warn(Player player, String key) {
        long now = System.currentTimeMillis();
        Long last = warnCooldown.get(player.getUniqueId());
        if (last != null && now - last < WARN_COOLDOWN_MS) {
            return;
        }
        warnCooldown.put(player.getUniqueId(), now);
        plugin.getSettings().send(player, key);
    }

    private static boolean sameWorld(Location a, Location b) {
        return a.getWorld() != null && b.getWorld() != null && a.getWorld().getName().equals(b.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        matches().handleDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Location spawn = matches().consumeRespawnLocation(event.getPlayer());
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Bir tick sonra: diger pluginlerin giris isinlamalari tamamlansin
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                matches().handleJoin(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        warnCooldown.remove(player.getUniqueId());
        plugin.getSelectionManager().clear(player.getUniqueId());
        matches().handleQuit(player);
    }
}
