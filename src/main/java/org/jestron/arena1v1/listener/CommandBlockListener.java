package org.jestron.arena1v1.listener;

import java.util.Locale;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jestron.arena1v1.Arena1v1Plugin;

/**
 * Mac sirasinda (ve kazanan item toplarken) izin verilmeyen komutlari engeller.
 */
public final class CommandBlockListener implements Listener {

    private final Arena1v1Plugin plugin;

    public CommandBlockListener(Arena1v1Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getMatchManager().isInActiveMatch(player) || player.hasPermission("arena.bypass")) {
            return;
        }
        String message = event.getMessage();
        String body = message.startsWith("/") ? message.substring(1) : message;
        body = body.trim();
        int space = body.indexOf(' ');
        String label = (space >= 0 ? body.substring(0, space) : body).toLowerCase(Locale.ROOT);

        Set<String> allowed = plugin.getSettings().getAllowedCommands();
        if (!label.isEmpty()) {
            if (allowed.contains(label)) {
                return;
            }
            int colon = label.indexOf(':');
            if (colon >= 0) {
                String namespace = label.substring(0, colon);
                String plain = label.substring(colon + 1);
                if (namespace.equals(plugin.getName().toLowerCase(Locale.ROOT)) && allowed.contains(plain)) {
                    return;
                }
            }
        }
        event.setCancelled(true);
        plugin.getSettings().send(player, "komut-yasak");
    }
}
