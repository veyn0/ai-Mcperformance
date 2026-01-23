package dev.veyno.aiMcperformance.command;

import dev.veyno.aiMcperformance.AiMcperformance;
import dev.veyno.aiMcperformance.config.PerformanceConfig;
import dev.veyno.aiMcperformance.metrics.MetricType;
import dev.veyno.aiMcperformance.metrics.PerformanceAnalyzer;
import dev.veyno.aiMcperformance.metrics.PerformanceSample;
import dev.veyno.aiMcperformance.metrics.PerformanceTracker;
import dev.veyno.aiMcperformance.metrics.StatsWindow;
import dev.veyno.aiMcperformance.message.MessageFormatter;
import dev.veyno.aiMcperformance.monitor.BossBarMonitor;
import dev.veyno.aiMcperformance.testing.LoadTestManager;
import dev.veyno.aiMcperformance.testing.LoadTestManager.LoadTestStatus;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final LoadTestManager loadTestManager;
    private final MessageFormatter messageFormatter = new MessageFormatter();

    public PerformanceCommand(
            AiMcperformance plugin,
            BossBarMonitor monitor,
            PerformanceTracker tracker,
            PerformanceConfig config,
            LoadTestManager loadTestManager
    ) {
        this.plugin = plugin;
        this.monitor = monitor;
        this.tracker = tracker;
        this.config = config;
        this.loadTestManager = loadTestManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("reload".equalsIgnoreCase(command.getName())) {
            plugin.reloadPluginState();
            sendFormatted(sender, config.getMessageReload(), Map.of());
            return true;
        }
        if (args.length >= 1 && "reload".equalsIgnoreCase(args[0])) {
            plugin.reloadPluginState();
            sendFormatted(sender, config.getMessageReload(), Map.of());
            return true;
        }
        if (args.length >= 1 && "feature".equalsIgnoreCase(args[0])) {
            return handleFeatureToggle(sender, args);
        }
        if (args.length >= 1 && "test".equalsIgnoreCase(args[0])) {
            return handleTestMode(sender, args);
        }
        if (args.length >= 1 && "report".equalsIgnoreCase(args[0])) {
            if (!config.isReportEnabled()) {
                sendFormatted(sender, config.getMessageReportDisabled(), Map.of());
                return true;
            }
            sendReport(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendFormatted(sender, config.getMessageOnlyPlayer(), Map.of());
            return true;
        }
        if (args.length < 2 || !"monitor".equalsIgnoreCase(args[0])) {
            sendFormatted(sender, config.getMessageUsageMonitor(), Map.of());
            sendFormatted(sender, config.getMessageUsageFeature(), Map.of());
            sendFormatted(sender, config.getMessageUsageTestMode(), Map.of());
            sendFormatted(sender, config.getMessageUsageReload(), Map.of());
            return true;
        }
        if (!config.isBossBarEnabled()) {
            sendFormatted(sender, config.getMessageBossBarDisabled(), Map.of());
            return true;
        }
        MetricType type = parseType(args[1]);
        if (type == null) {
            sendFormatted(sender, config.getMessageBossBarUnknownMetric(), Map.of("metric", args[1]));
            return true;
        }
        String action = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "toggle";
        boolean enable;
        switch (action) {
            case "on", "enable" -> enable = true;
            case "off", "disable" -> enable = false;
            case "toggle" -> enable = !monitor.isEnabled(player, type);
            default -> {
                sendFormatted(sender, config.getMessageUsageMonitor(), Map.of());
                return true;
            }
        }
        monitor.toggle(player, type, enable);
        String state = enable ? "aktiviert" : "deaktiviert";
        sendFormatted(sender, config.getMessageBossBarToggle(), Map.of(
                "metric", type.getDisplayName(),
                "state", state
        ));
        return true;
    }

    private boolean handleFeatureToggle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendFormatted(sender, config.getMessageUsageFeature(), Map.of());
            return true;
        }
        FeatureToggle feature = FeatureToggle.fromInput(args[1]);
        if (feature == null) {
            sendFormatted(sender, config.getMessageFeatureUnknown(), Map.of("feature", args[1]));
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
                sendFormatted(sender, config.getMessageUsageFeature(), Map.of());
                return true;
            }
        }
        plugin.updateFeatureToggle(feature.configPath(), enable);
        String state = enable ? "aktiviert" : "deaktiviert";
        sendFormatted(sender, config.getMessageFeatureToggle(), Map.of(
                "feature", feature.displayName(),
                "state", state
        ));
        return true;
    }

    private boolean handleTestMode(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendFormatted(sender, config.getMessageUsageTestMode(), Map.of());
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "start" -> {
                String requestedAction = args.length >= 3 ? args[2] : null;
                boolean started = loadTestManager.start(requestedAction);
                if (started) {
                    sendFormatted(sender, config.getMessageTestModeStarted(), Map.of());
                    return true;
                }
                String error = loadTestManager.getLastError();
                if ("disabled".equals(error)) {
                    sendFormatted(sender, config.getMessageTestModeDisabled(), Map.of());
                } else if ("invalid-action".equals(error)) {
                    sendFormatted(sender, config.getMessageTestModeInvalidAction(), Map.of(
                            "action", requestedAction != null ? requestedAction : config.getTestModeAction()
                    ));
                } else {
                    sendFormatted(sender, config.getMessageTestModeAlreadyRunning(), Map.of());
                }
                return true;
            }
            case "stop" -> {
                boolean stopped = loadTestManager.stop();
                if (stopped) {
                    sendFormatted(sender, config.getMessageTestModeStopped(), Map.of());
                } else {
                    sendFormatted(sender, config.getMessageTestModeNotRunning(), Map.of());
                }
                return true;
            }
            case "status" -> {
                LoadTestStatus status = loadTestManager.getStatus();
                if (!status.running()) {
                    sendFormatted(sender, config.getMessageTestModeNotRunning(), Map.of());
                    return true;
                }
                sendFormatted(sender, config.getMessageTestModeStatus(), Map.of(
                        "action", status.action() == null ? "-" : status.action(),
                        "step", Integer.toString(status.currentStep()),
                        "total_steps", Integer.toString(status.totalSteps()),
                        "value", Integer.toString(status.currentValue()),
                        "started", status.startedAt() == null ? "-" : TIME_FORMAT.format(status.startedAt()),
                        "ends", status.endsAt() == null ? "-" : TIME_FORMAT.format(status.endsAt())
                ));
                return true;
            }
            default -> {
                sendFormatted(sender, config.getMessageUsageTestMode(), Map.of());
                return true;
            }
        }
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
            sendFormatted(sender, config.getMessageReportEmpty(), Map.of());
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
        sendFormatted(sender, config.getMessageReportHeader(), Map.of(
                "window", Integer.toString(windowSeconds)
        ));
        sendFormatted(sender, config.getMessageReportMspt(), Map.of(
                "mspt_avg", ONE_DECIMAL.format(msptStats.average()),
                "mspt_p95", ONE_DECIMAL.format(msptStats.p95()),
                "spike_count", Long.toString(result.spikeCount())
        ));
        sendFormatted(sender, config.getMessageReportCorrelation(), Map.of(
                "corr_entities", TWO_DECIMAL.format(correlations.entities()),
                "corr_chunks", TWO_DECIMAL.format(correlations.chunks()),
                "corr_players", TWO_DECIMAL.format(correlations.players())
        ));
        if (result.bottlenecks().isEmpty()) {
            sendFormatted(sender, config.getMessageReportBottleneckNone(), Map.of());
        } else {
            sendFormatted(sender, config.getMessageReportBottleneckHeader(), Map.of());
            result.bottlenecks().forEach(hint -> sendFormatted(sender, config.getMessageReportBottleneckItem(),
                    Map.of("bottleneck", hint)));
        }
        if (!result.peakWindows().isEmpty()) {
            sendFormatted(sender, config.getMessageReportPeakHeader(), Map.of(
                    "count", Integer.toString(result.peakWindows().size()),
                    "window", Integer.toString(config.getReportPeakWindowSeconds())
            ));
            int index = 1;
            for (PerformanceAnalyzer.PeakWindow window : result.peakWindows()) {
                sendFormatted(sender, config.getMessageReportPeakItem(), Map.of(
                        "index", Integer.toString(index),
                        "start", TIME_FORMAT.format(window.start()),
                        "end", TIME_FORMAT.format(window.end()),
                        "mspt_p95", ONE_DECIMAL.format(window.msptStats().p95()),
                        "mspt_avg", ONE_DECIMAL.format(window.msptStats().average()),
                        "entities", ONE_DECIMAL.format(window.averageEntities()),
                        "chunks", ONE_DECIMAL.format(window.averageChunks()),
                        "players", ONE_DECIMAL.format(window.averagePlayers())
                ));
                index++;
            }
        }
    }

    private void sendFormatted(CommandSender sender, String message, Map<String, String> placeholders) {
        sender.sendMessage(messageFormatter.formatLegacy(sender, message, placeholders));
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
