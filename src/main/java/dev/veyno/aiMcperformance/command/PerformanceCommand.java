package dev.veyno.aiMcperformance.command;

import dev.veyno.aiMcperformance.config.PerformanceConfig;
import dev.veyno.aiMcperformance.metrics.MetricType;
import dev.veyno.aiMcperformance.metrics.PerformanceSample;
import dev.veyno.aiMcperformance.metrics.PerformanceTracker;
import dev.veyno.aiMcperformance.metrics.StatsWindow;
import dev.veyno.aiMcperformance.monitor.BossBarMonitor;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PerformanceCommand implements CommandExecutor {
    private static final DecimalFormat ONE_DECIMAL = new DecimalFormat("0.0");
    private static final DecimalFormat TWO_DECIMAL = new DecimalFormat("0.00");
    private final BossBarMonitor monitor;
    private final PerformanceTracker tracker;
    private final PerformanceConfig config;

    public PerformanceCommand(BossBarMonitor monitor, PerformanceTracker tracker, PerformanceConfig config) {
        this.monitor = monitor;
        this.tracker = tracker;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && "report".equalsIgnoreCase(args[0])) {
            sendReport(sender);
            return true;
        }
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

    private void sendReport(CommandSender sender) {
        int windowSeconds = Math.max(10, config.getReportWindowSeconds());
        List<PerformanceSample> samples = tracker.getSamplesSinceSeconds(windowSeconds);
        if (samples.isEmpty()) {
            sender.sendMessage("Keine Daten für den Report verfügbar.");
            return;
        }
        StatsWindow msptStats = tracker.statsWindow(windowSeconds, PerformanceSample::mspt);
        double p95 = msptStats.p95();
        long spikes = samples.stream()
                .filter(sample -> sample.mspt() >= config.getReportSpikeMsptThreshold())
                .count();
        double entitiesCorr = StatsWindow.correlation(samples, PerformanceSample::mspt, sample -> sample.entities());
        double chunksCorr = StatsWindow.correlation(samples, PerformanceSample::mspt, sample -> sample.chunks());
        String correlationHint = correlationHint(entitiesCorr, chunksCorr);
        sender.sendMessage("Performance-Report (" + windowSeconds + "s): P95 MSPT "
                + ONE_DECIMAL.format(p95) + "ms, Spikes " + spikes + ", " + correlationHint);
    }

    private String correlationHint(double entitiesCorr, double chunksCorr) {
        double threshold = config.getReportCorrelationThreshold();
        double entitiesAbs = Math.abs(entitiesCorr);
        double chunksAbs = Math.abs(chunksCorr);
        if (entitiesAbs < threshold && chunksAbs < threshold) {
            return "Hinweis: Keine klare Korrelation zu Entities/Chunks (rE="
                    + TWO_DECIMAL.format(entitiesCorr) + ", rC=" + TWO_DECIMAL.format(chunksCorr) + ")";
        }
        if (entitiesAbs >= chunksAbs) {
            return "Hinweis: MSPT korreliert stärker mit Entities (rE="
                    + TWO_DECIMAL.format(entitiesCorr) + ", rC=" + TWO_DECIMAL.format(chunksCorr) + ")";
        }
        return "Hinweis: MSPT korreliert stärker mit Chunks (rE="
                + TWO_DECIMAL.format(entitiesCorr) + ", rC=" + TWO_DECIMAL.format(chunksCorr) + ")";
    }
}
