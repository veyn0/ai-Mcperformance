package dev.veyno.aiMcperformance.command;

import dev.veyno.aiMcperformance.metrics.MetricType;
import dev.veyno.aiMcperformance.monitor.BossBarMonitor;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PerformanceCommand implements CommandExecutor {
    private final BossBarMonitor monitor;

    public PerformanceCommand(BossBarMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl kann nur im Spiel genutzt werden.");
            return true;
        }
        if (args.length < 2 || !"monitor".equalsIgnoreCase(args[0])) {
            sender.sendMessage("Verwendung: /performance monitor <mspt|tps|entities|ram|cpu|chunks|viewdistance> <on|off|toggle>");
            return true;
        }
        MetricType type = parseType(args[1]);
        if (type == null) {
            sender.sendMessage("Unbekanntes Metric: " + args[1]);
            return true;
        }
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "toggle";
        boolean enable;
        switch (action) {
            case "on", "enable" -> enable = true;
            case "off", "disable" -> enable = false;
            case "toggle" -> enable = !monitor.isEnabled(player, type);
            default -> {
                sender.sendMessage("Verwendung: /performance monitor <mspt|tps|entities|ram|cpu|chunks|viewdistance> <on|off|toggle>");
                return true;
            }
        }
        monitor.toggle(player, type, enable);
        sender.sendMessage("BossBar für " + type.getDisplayName() + " " + (enable ? "aktiviert." : "deaktiviert."));
        return true;
    }

    private MetricType parseType(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "mspt" -> MetricType.MSPT;
            case "tps" -> MetricType.TPS;
            case "entities", "entity" -> MetricType.ENTITIES;
            case "ram", "memory" -> MetricType.RAM;
            case "cpu" -> MetricType.CPU;
            case "chunks", "chunk" -> MetricType.CHUNKS;
            case "viewdistance", "view", "vd" -> MetricType.VIEW_DISTANCE;
            default -> null;
        };
    }
}
