package dev.veyno.aiMcperformance.command;

import dev.veyno.aiMcperformance.AiMcperformance;
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
    private final AiMcperformance plugin;
    private final BossBarMonitor monitor;
    private final PerformanceTracker tracker;
    private final PerformanceConfig config;

    public PerformanceCommand(
            AiMcperformance plugin,
            BossBarMonitor monitor,
            PerformanceTracker tracker,
            PerformanceConfig config
    ) {
        this.plugin = plugin;
        this.monitor = monitor;
        this.tracker = tracker;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("reload".equalsIgnoreCase(command.getName())) {
            plugin.reloadPluginState();
            sender.sendMessage("AI-McPerformance-Konfiguration neu geladen.");
            return true;
        }
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            plugin.reloadPluginState();
            sender.sendMessage("AI-McPerformance-Konfiguration neu geladen.");
            return true;
        }
        if (args.length >= 1 && "feature".equalsIgnoreCase(args[0])) {
            return handleFeatureToggle(sender, args);
        }
        if (args.length >= 1 && "report".equalsIgnoreCase(args[0])) {
            if (!config.isReportEnabled()) {
                sender.sendMessage("Performance-Reports sind derzeit deaktiviert.");
                return true;
            }
            sendReport(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl kann nur im Spiel genutzt werden.");
            return true;
        }
        if (args.length < 2 || !"monitor".equalsIgnoreCase(args[0])) {
            sender.sendMessage("Verwendung: /performance monitor <mspt|tps|entities|ram|cpu|chunks|viewdistance> <on|off|toggle>");
            sender.sendMessage("Oder: /performance feature <sampling|storage|bossbar|report|pterodactyl|viewdistance|actions> <on|off|toggle>");
            sender.sendMessage("Oder: /performance reload");
            return true;
        }
        if (!config.isBossBarEnabled()) {
            sender.sendMessage("BossBar-Monitoring ist derzeit deaktiviert.");
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

    private boolean handleFeatureToggle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Verwendung: /performance feature <sampling|storage|bossbar|report|pterodactyl|viewdistance|actions> <on|off|toggle>");
            return true;
        }
        FeatureToggle feature = FeatureToggle.fromInput(args[1]);
        if (feature == null) {
            sender.sendMessage("Unbekanntes Feature: " + args[1]);
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        boolean current = feature.isEnabled(config);
        boolean enable;
        switch (action) {
            case "on", "enable" -> enable = true;
            case "off", "disable" -> enable = false;
            case "toggle" -> enable = !current;
            default -> {
                sender.sendMessage("Verwendung: /performance feature <sampling|storage|bossbar|report|pterodactyl|viewdistance|actions> <on|off|toggle>");
                return true;
            }
        }
        plugin.updateFeatureToggle(feature.configPath(), enable);
        sender.sendMessage("Feature " + feature.displayName() + " " + (enable ? "aktiviert." : "deaktiviert."));
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

    private enum FeatureToggle {
        SAMPLING("Sampling", "sampling.enabled", List.of("sampling", "samples")),
        STORAGE("Storage", "storage.enabled", List.of("storage", "persist", "persistence")),
        BOSSBAR("BossBar", "bossbar.enabled", List.of("bossbar", "bar")),
        REPORT("Report", "report.enabled", List.of("report", "reports")),
        PTERODACTYL("Pterodactyl", "pterodactyl.enabled", List.of("pterodactyl", "ptero", "panel")),
        VIEW_DISTANCE("View-Distance", "view-distance.enabled", List.of("viewdistance", "view-distance", "view")),
        ACTIONS("Actions", "actions.enabled", List.of("actions", "action", "auto-actions"));

        private final String displayName;
        private final String configPath;
        private final List<String> aliases;

        FeatureToggle(String displayName, String configPath, List<String> aliases) {
            this.displayName = displayName;
            this.configPath = configPath;
            this.aliases = aliases;
        }

        public String displayName() {
            return displayName;
        }

        public String configPath() {
            return configPath;
        }

        public boolean isEnabled(PerformanceConfig config) {
            return switch (this) {
                case SAMPLING -> config.isSamplingEnabled();
                case STORAGE -> config.isStorageEnabled();
                case BOSSBAR -> config.isBossBarEnabled();
                case REPORT -> config.isReportEnabled();
                case PTERODACTYL -> config.isPterodactylEnabled();
                case VIEW_DISTANCE -> config.isDynamicViewDistanceEnabled();
                case ACTIONS -> config.isActionEngineEnabled();
            };
        }

        public static FeatureToggle fromInput(String input) {
            if (input == null) {
                return null;
            }
            String normalized = input.toLowerCase(Locale.ROOT);
            for (FeatureToggle feature : values()) {
                if (feature.aliases.contains(normalized)) {
                    return feature;
                }
            }
            return null;
        }
    }
}
