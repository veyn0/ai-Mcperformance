package dev.veyno.aiMcperformance.message;

import java.lang.reflect.Method;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MessageFormatter {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();

    public String formatLegacy(CommandSender sender, String message, Map<String, String> placeholders) {
        if (message == null) {
            return "";
        }
        String replaced = applyCustomPlaceholders(message, placeholders);
        if (sender instanceof Player player) {
            replaced = applyPlaceholderApi(player, replaced);
        }
        try {
            Component component = miniMessage.deserialize(replaced);
            return legacySerializer.serialize(component);
        } catch (Exception ignored) {
            return replaced;
        }
    }

    private String applyCustomPlaceholders(String message, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return message;
        }
        String replaced = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String key = "{" + entry.getKey() + "}";
            replaced = replaced.replace(key, entry.getValue());
        }
        return replaced;
    }

    private String applyPlaceholderApi(Player player, String message) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return message;
        }
        try {
            Class<?> placeholderApi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method method = placeholderApi.getMethod("setPlaceholders", Player.class, String.class);
            Object result = method.invoke(null, player, message);
            return result instanceof String formatted ? formatted : message;
        } catch (Exception ignored) {
            return message;
        }
    }
}
