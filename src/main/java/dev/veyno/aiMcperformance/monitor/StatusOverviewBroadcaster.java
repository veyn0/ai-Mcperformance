package dev.veyno.aiMcperformance.monitor;

import dev.veyno.aiMcperformance.config.PerformanceConfig;
import dev.veyno.aiMcperformance.message.MessageFormatter;
import dev.veyno.aiMcperformance.metrics.PerformanceSample;
import dev.veyno.aiMcperformance.metrics.PerformanceTracker;
import dev.veyno.aiMcperformance.optimization.ViewDistanceStatus;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class StatusOverviewBroadcaster {
    private static final DecimalFormat ONE_DECIMAL = new DecimalFormat("0.0");
    private static final DecimalFormat ZERO_DECIMAL = new DecimalFormat("0");
    private final Plugin plugin;
    private final PerformanceConfig config;
    private final PerformanceTracker tracker;
    private final Supplier<ViewDistanceStatus> viewDistanceStatusSupplier;
    private final MessageFormatter messageFormatter = new MessageFormatter();
    private BukkitTask task;

    public StatusOverviewBroadcaster(
            Plugin plugin,
            PerformanceConfig config,
            PerformanceTracker tracker,
            Supplier<ViewDistanceStatus> viewDistanceStatusSupplier
    ) {
        this.plugin = plugin;
        this.config = config;
        this.tracker = tracker;
        this.viewDistanceStatusSupplier = viewDistanceStatusSupplier;
    }

    public void schedule() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (!config.isStatusOverviewEnabled()) {
            return;
        }
        int intervalMinutes = Math.max(1, config.getStatusOverviewIntervalMinutes());
        task = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::broadcast,
                intervalMinutes * 60L * 20L,
                intervalMinutes * 60L * 20L
        );
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void broadcast() {
        if (!config.isStatusOverviewEnabled()) {
            return;
        }
        List<String> lines = config.getStatusOverviewLines();
        if (lines.isEmpty()) {
            return;
        }
        Map<String, String> placeholders = buildPlaceholders();
        if (config.isStatusOverviewChatEnabled()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                sendLines(player, lines, placeholders);
            }
        }
        if (config.isStatusOverviewConsoleEnabled()) {
            CommandSender console = Bukkit.getConsoleSender();
            sendLines(console, lines, placeholders);
        }
    }

    private void sendLines(CommandSender sender, List<String> lines, Map<String, String> placeholders) {
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String formatted = messageFormatter.formatLegacy(sender, line, placeholders);
            sender.sendMessage(formatted);
        }
    }

    private Map<String, String> buildPlaceholders() {
        int windowSeconds = Math.max(10, config.getStatusOverviewSampleWindowSeconds());
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("window_seconds", Integer.toString(windowSeconds));
        placeholders.put("mspt", ONE_DECIMAL.format(tracker.averageMspt(windowSeconds)));
        placeholders.put("tps", ONE_DECIMAL.format(tracker.averageTps(windowSeconds)));
        placeholders.put("entities", ZERO_DECIMAL.format(tracker.averageEntities(windowSeconds)));
        placeholders.put("chunks", ZERO_DECIMAL.format(tracker.averageChunks(windowSeconds)));
        placeholders.put("ram_mb", ONE_DECIMAL.format(tracker.averageRamBytes(windowSeconds) / (1024.0 * 1024.0)));
        placeholders.put("cpu_percent", ONE_DECIMAL.format(tracker.averageCpu(windowSeconds)));
        placeholders.put("players", ZERO_DECIMAL.format(tracker.averagePlayers(windowSeconds)));
        placeholders.put("view_distance", formatViewDistance());
        PerformanceSample sample = tracker.latestSample();
        placeholders.put("latest_mspt", ONE_DECIMAL.format(sample != null ? sample.mspt() : 0.0));
        placeholders.put("latest_tps", ONE_DECIMAL.format(sample != null ? sample.tps() : 0.0));
        return placeholders;
    }

    private String formatViewDistance() {
        ViewDistanceStatus status = viewDistanceStatusSupplier != null ? viewDistanceStatusSupplier.get() : null;
        if (status != null) {
            return Integer.toString(status.currentViewDistance());
        }
        PerformanceSample sample = tracker.latestSample();
        return sample != null ? Integer.toString(sample.viewDistance()) : "0";
    }
}
