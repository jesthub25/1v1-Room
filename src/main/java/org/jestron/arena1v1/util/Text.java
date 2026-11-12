package org.jestron.arena1v1.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;

public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final char SECTION = '§';

    private Text() {
    }

    /** '&' renk kodlarini ve '&#RRGGBB' hex renklerini cevirir. */
    public static String colorize(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        Matcher matcher = HEX.matcher(message);
        StringBuilder sb = new StringBuilder(message.length() + 32);
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder(14).append(SECTION).append('x');
            for (char c : hex.toCharArray()) {
                replacement.append(SECTION).append(c);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    /** {anahtar}, deger ciftleriyle yer tutuculari doldurur. */
    public static String replace(String message, String... placeholders) {
        if (message == null) {
            return "";
        }
        String result = message;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String value = placeholders[i + 1] == null ? "" : placeholders[i + 1];
            result = result.replace("{" + placeholders[i] + "}", value);
        }
        return result;
    }
}
