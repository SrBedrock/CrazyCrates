package com.badbones69.crazycrates.other;

import com.badbones69.crazycrates.CrazyCrates;
import com.badbones69.crazycrates.common.config.types.ConfigKeys;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Matcher.quoteReplacement;

@SuppressWarnings("ALL")
public class MsgUtils {

    private static final CrazyCrates plugin = CrazyCrates.get();
    private static final Pattern HEX_PATTERN = Pattern.compile("(&?)#([a-f\\d]{6})", Pattern.CASE_INSENSITIVE);

    public static @NotNull String color(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder();

        while (matcher.find()) {
            String colorCode = matcher.group();
            colorCode = colorCode.replace("&", "");
            String replacement = net.md_5.bungee.api.ChatColor.of(colorCode).toString();
            matcher.appendReplacement(buffer, replacement);
        }

        return org.bukkit.ChatColor.translateAlternateColorCodes('&', matcher.appendTail(buffer).toString());
    }

    public static void sendMessage(CommandSender commandSender, String message, boolean prefixToggle) {
        if (message == null || message.isEmpty()) return;

        String prefix = getPrefix();

        if (commandSender instanceof Player player) {
            if (!prefix.isEmpty() && prefixToggle)
                player.sendMessage(color(message.replaceAll("%prefix%", quoteReplacement(prefix))).replaceAll("%Prefix%", quoteReplacement(prefix)));
            else player.sendMessage(color(message));

            return;
        }

        if (!prefix.isEmpty() && prefixToggle)
            commandSender.sendMessage(color(message.replaceAll("%prefix%", quoteReplacement(prefix))).replaceAll("%Prefix%", quoteReplacement(prefix)));
        else commandSender.sendMessage(color(message));
    }

    public static @NotNull String getPrefix() {
        return color(plugin.getCrazyHandler().getConfigManager().getConfig().getProperty(ConfigKeys.command_prefix));
    }

    public static @NotNull String getPrefix(String msg) {
        return color(getPrefix() + msg);
    }

    public static @NotNull String sanitizeColor(String msg) {
        return sanitizeFormat(color(msg));
    }

    public static @NotNull String sanitizeFormat(String string) {
        return TextComponent.toLegacyText(TextComponent.fromLegacyText(string));
    }

    @Contract("!null -> !null; null -> null")
    public static String removeColor(String msg) {
        return ChatColor.stripColor(msg);
    }
}