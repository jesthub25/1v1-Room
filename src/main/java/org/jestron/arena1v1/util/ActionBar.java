package org.jestron.arena1v1.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

/**
 * Action bar gonderimi ayri sinifta: bungee-chat API'si ileride kaldirilsa bile
 * hata sadece burada yakalanir, mac akisini etkilemez.
 */
public final class ActionBar {

    private static boolean disabled;

    private ActionBar() {
    }

    /** @return gonderildiyse true */
    public static boolean send(Player player, String legacyText) {
        if (disabled) {
            return false;
        }
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(legacyText));
            return true;
        } catch (LinkageError e) {
            disabled = true;
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
