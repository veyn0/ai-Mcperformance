package dev.veyno.aiMcperformance.monitor;

import dev.veyno.aiMcperformance.config.PerformanceConfig;
import dev.veyno.aiMcperformance.metrics.MetricType;
import dev.veyno.aiMcperformance.metrics.PerformanceSample;
import dev.veyno.aiMcperformance.metrics.PerformanceTracker;
import dev.veyno.aiMcperformance.optimization.ViewDistanceStatus;
import java.text.DecimalFormat;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class BossBarMonitor {
    private static final DecimalFormat ONE_DECIMAL = new DecimalFormat("0.0");
    private static final DecimalFormat ZERO_DECIMAL = new DecimalFormat("0");
    private final Plugin plugin;
    private final PerformanceConfig config;
    private final PerformanceTracker tracker;
    private final Supplier<ViewDistanceStatus> viewDistanceStatusSupplier;
    private final Map<UUID, EnumMap<MetricType, BossBar>> bars = new java.util.HashMap<>();

    public BossBarMonitor(
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

    public void toggle(Player player, MetricType type, boolean enabled) {
        if (enabled) {
            enable(player, type);
        } else {
            disable(player, type);
        }
    }

    public void enable(Player player, MetricType type) {
        bars.computeIfAbsent(player.getUniqueId(), key -> new EnumMap<>(MetricType.class));
        Map<MetricType, BossBar> playerBars = bars.get(player.getUniqueId());
        if (playerBars.containsKey(type)) {
            return;
        }
        BossBar bar = Bukkit.createBossBar(formatTitle(type), BarColor.BLUE, BarStyle.SOLID);
        bar.addPlayer(player);
        bar.setProgress(1.0);
        playerBars.put(type, bar);
    }

    public void disable(Player player, MetricType type) {
        Map<MetricType, BossBar> playerBars = bars.get(player.getUniqueId());
        if (playerBars == null) {
            return;
        }
        BossBar bar = playerBars.remove(type);
        if (bar != null) {
            bar.removeAll();
        }
        if (playerBars.isEmpty()) {
            bars.remove(player.getUniqueId());
        }
    }

    public void disableAll(Player player) {
        Map<MetricType, BossBar> playerBars = bars.remove(player.getUniqueId());
        if (playerBars == null) {
            return;
        }
        playerBars.values().forEach(BossBar::removeAll);
    }

    public boolean isEnabled(Player player, MetricType type) {
        Map<MetricType, BossBar> playerBars = bars.get(player.getUniqueId());
        return playerBars != null && playerBars.containsKey(type);
    }

    public void updateAll() {
        for (Map.Entry<UUID, EnumMap<MetricType, BossBar>> entry : bars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            for (Map.Entry<MetricType, BossBar> barEntry : entry.getValue().entrySet()) {
                BossBar bar = barEntry.getValue();
                bar.setTitle(formatTitle(barEntry.getKey()));
                bar.setProgress(1.0);
            }
        }
    }

    private String formatTitle(MetricType type) {
        String values = switch (type) {
            case MSPT -> formatWindowValues("ms", tracker::averageMspt);
            case TPS -> formatWindowValues("", tracker::averageTps);
            case ENTITIES -> formatWindowValues("", tracker::averageEntities);
            case RAM -> formatWindowValues("MB", seconds -> tracker.averageRamBytes(seconds) / (1024.0 * 1024.0));
            case CPU -> formatWindowValues("%", tracker::averageCpu);
            case CHUNKS -> formatWindowValues("", tracker::averageChunks);
            case VIEW_DISTANCE -> formatViewDistanceStatus();
        };
        return config.getBossBarTitleFormat()
                .replace("{metric}", type.getDisplayName())
                .replace("{values}", values);
    }

    private String formatWindowValues(String unit, java.util.function.IntToDoubleFunction function) {
        String ten = format(function.applyAsDouble(10), unit);
        String thirty = format(function.applyAsDouble(30), unit);
        String minute = format(function.applyAsDouble(60), unit);
        String five = format(function.applyAsDouble(300), unit);
        return "10s " + ten + " | 30s " + thirty + " | 1m " + minute + " | 5m " + five;
    }

    private String format(double value, String unit) {
        String formatted = unit.isEmpty() ? ZERO_DECIMAL.format(value) : ONE_DECIMAL.format(value);
        return formatted + unit;
    }

    private String formatViewDistanceStatus() {
        ViewDistanceStatus status = viewDistanceStatusSupplier != null ? viewDistanceStatusSupplier.get() : null;
        int current = status != null ? status.currentViewDistance() : latestViewDistance();
        String up = formatDirection("Hoch", status != null && status.increaseRecommended(),
                status != null ? status.cooldownRemainingSeconds() : 0);
        String down = formatDirection("Runter", status != null && status.decreaseRecommended(),
                status != null ? status.cooldownRemainingSeconds() : 0);
        String prediction = status != null && status.predictedChunkIncrease() > 0
                ? " | +" + status.predictedChunkIncrease() + " Chunks"
                : "";
        return "Aktuell " + current + " | " + up + " | " + down + prediction;
    }

    private int latestViewDistance() {
        PerformanceSample sample = tracker.latestSample();
        return sample != null ? sample.viewDistance() : 0;
    }

    private String formatDirection(String label, boolean recommended, long cooldownRemainingSeconds) {
        if (!recommended) {
            return label + ": blockiert";
        }
        if (cooldownRemainingSeconds > 0) {
            return label + " in " + cooldownRemainingSeconds + "s";
        }
        return label + ": jetzt";
    }
}
