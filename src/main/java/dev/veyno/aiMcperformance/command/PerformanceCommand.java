package dev.veyno.aiMcperformance.command;

import dev.veyno.aiMcperformance.config.PerformanceConfig;
import dev.veyno.aiMcperformance.metrics.MetricType;
import dev.veyno.aiMcperformance.metrics.PerformanceAnalyzer;
import dev.veyno.aiMcperformance.metrics.PerformanceSample;
import dev.veyno.aiMcperformance.metrics.PerformanceTracker;
import dev.veyno.aiMcperformance.metrics.StatsWindow;
import dev.veyno.aiMcperformance.monitor.BossBarMonitor;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PerformanceCommand implements CommandExecutor {
    private static final DecimalFormat ONE_DECIMAL = new DecimalFormat("0.0");
    private static final DecimalFormat TWO_DECIMAL = new DecimalFormat("0.00");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
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
        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.AnalysisResult result = analyzer.analyze(
                samples,
                windowSeconds,
                config.getReportSpikeMsptThreshold(),
                config.getReportCorrelationThreshold(),
                config.getReportPeakWindowSeconds(),
                config.getReportPeakWindowCount(),
                config.getActionCpuThreshold(),
                config.getActionEntityThreshold()
        );
        StatsWindow msptStats = result.msptStats();
        PerformanceAnalyzer.Correlations correlations = result.correlations();
        sender.sendMessage("Performance-Report (" + windowSeconds + "s):");
        sender.sendMessage("MSPT Ø " + ONE_DECIMAL.format(msptStats.average()) + "ms, P95 "
                + ONE_DECIMAL.format(msptStats.p95()) + "ms, Spikes " + result.spikeCount());
        sender.sendMessage("Korrelation MSPT: Entities r=" + TWO_DECIMAL.format(correlations.entities())
                + ", Chunks r=" + TWO_DECIMAL.format(correlations.chunks())
                + ", Spieler r=" + TWO_DECIMAL.format(correlations.players()));
        if (result.bottlenecks().isEmpty()) {
            sender.sendMessage("Engpässe: keine auffälligen Indikatoren.");
        } else {
            sender.sendMessage("Engpässe:");
            result.bottlenecks().forEach(hint -> sender.sendMessage(" - " + hint));
        }
        if (!result.peakWindows().isEmpty()) {
            sender.sendMessage("Top-" + result.peakWindows().size() + " Peak-Windows ("
                    + config.getReportPeakWindowSeconds() + "s):");
            int index = 1;
            for (PerformanceAnalyzer.PeakWindow window : result.peakWindows()) {
                sender.sendMessage(" " + index + ") "
                        + TIME_FORMAT.format(window.start()) + " - " + TIME_FORMAT.format(window.end())
                        + ": P95 " + ONE_DECIMAL.format(window.msptStats().p95()) + "ms, Ø "
                        + ONE_DECIMAL.format(window.msptStats().average()) + "ms, Ø Entities "
                        + ONE_DECIMAL.format(window.averageEntities()) + ", Ø Chunks "
                        + ONE_DECIMAL.format(window.averageChunks()) + ", Ø Spieler "
                        + ONE_DECIMAL.format(window.averagePlayers()));
                index++;
            }
        }
    }
}
