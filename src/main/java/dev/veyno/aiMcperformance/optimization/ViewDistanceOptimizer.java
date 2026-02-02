package dev.veyno.aiMcperformance.optimization;

import dev.veyno.aiMcperformance.config.PerformanceConfig;
import dev.veyno.aiMcperformance.metrics.PerformanceSample;
import dev.veyno.aiMcperformance.metrics.PerformanceTracker;
import dev.veyno.aiMcperformance.scheduler.SchedulerUtil;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

public class ViewDistanceOptimizer implements Runnable {
    private static final double KP = 0.22;
    private static final double KI = 0.015;
    private static final double KD = 1.5;
    private static final double STABLE_TREND_THRESHOLD = 0.2;
    private final Plugin plugin;
    private final PerformanceConfig config;
    private final PerformanceTracker tracker;
    private ScheduledTask task;
    private Instant lastChange = Instant.EPOCH;
    private Instant belowTargetSince = null;
    private Instant lastRun = Instant.EPOCH;
    private double integratedError = 0.0;

    public ViewDistanceOptimizer(Plugin plugin, PerformanceConfig config, PerformanceTracker tracker) {
        this.plugin = plugin;
        this.config = config;
        this.tracker = tracker;
    }

    public void schedule() {
        stop();
        int intervalSeconds = Math.max(5, config.getViewDistanceCheckIntervalSeconds());
        task = SchedulerUtil.runAtFixedRate(plugin, this, intervalSeconds * 20L, intervalSeconds * 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public void run() {
        if (!config.isDynamicViewDistanceEnabled()) {
            return;
        }
        Instant now = Instant.now();
        int sampleWindow = Math.max(5, config.getViewDistanceSampleWindowSeconds());
        double targetMspt = config.getViewDistanceTargetMspt();
        PerformanceTracker.MsptEwmaTrend ewmaTrend = tracker.msptEwmaTrend(sampleWindow, config.getViewDistanceEwmaAlpha());
        double ewmaMspt = ewmaTrend.ewmaMspt();
        double trendPerSecond = ewmaTrend.trendPerSecond();
        boolean cooldownElapsed = lastChange.plusSeconds(config.getViewDistanceCooldownSeconds()).isBefore(now);
        double error = ewmaMspt - targetMspt;
        if (lastRun.equals(Instant.EPOCH)) {
            lastRun = now;
        }
        double elapsedSeconds = Math.max(1.0, ChronoUnit.MILLIS.between(lastRun, now) / 1000.0);
        integratedError += error * elapsedSeconds;
        integratedError = Math.max(-200.0, Math.min(200.0, integratedError));
        lastRun = now;

        boolean belowTarget = ewmaMspt > 0 && ewmaMspt < targetMspt && Math.abs(trendPerSecond) <= STABLE_TREND_THRESHOLD;
        if (belowTarget) {
            if (belowTargetSince == null) {
                belowTargetSince = now;
            }
        } else {
            belowTargetSince = null;
        }

        if (!cooldownElapsed) {
            return;
        }

        double adjustment = -(KP * error + KI * integratedError + KD * trendPerSecond);
        adjustment = applyAggressiveLowering(error, adjustment, sampleWindow);

        int maxAdjust = Math.max(1, (int) Math.ceil(config.getViewDistanceMaxAdjustPerMinute() * (elapsedSeconds / 60.0)));
        int delta = (int) Math.round(adjustment);
        delta = Math.max(-maxAdjust, Math.min(maxAdjust, delta));

        if (delta == 0) {
            return;
        }

        if (delta > 0 && !isIncreaseStable(now)) {
            return;
        }

        adjustViewDistance(delta, config.getViewDistanceMin(), config.getViewDistanceMax());
    }

    private double applyAggressiveLowering(double error, double adjustment, int sampleWindow) {
        if (error <= 0 || adjustment >= 0) {
            return adjustment;
        }
        double playersFactor = loadFactor(
                tracker.averagePlayers(sampleWindow),
                tracker.averagePlayers(Math.max(300, sampleWindow * 4))
        );
        double chunksFactor = loadFactor(
                tracker.averageChunks(sampleWindow),
                tracker.averageChunks(Math.max(300, sampleWindow * 4))
        );
        double entitiesFactor = loadFactor(
                tracker.averageEntities(sampleWindow),
                tracker.averageEntities(Math.max(300, sampleWindow * 4))
        );
        double boost = 1.0 + Math.min(1.0, playersFactor + chunksFactor + entitiesFactor);
        return adjustment * boost;
    }

    private double loadFactor(double current, double baseline) {
        if (current <= 0 || baseline <= 0) {
            return 0.0;
        }
        double ratio = current / baseline;
        if (ratio <= 1.1) {
            return 0.0;
        }
        return Math.min(0.5, (ratio - 1.1) * 0.5);
    }

    private boolean isIncreaseStable(Instant now) {
        if (belowTargetSince == null) {
            return false;
        }
        long stableSeconds = ChronoUnit.SECONDS.between(belowTargetSince, now);
        return stableSeconds >= config.getViewDistanceIncreaseStableSeconds();
    }

    private void adjustViewDistance(int delta, int min, int max) {
        boolean changed = false;
        for (World world : Bukkit.getWorlds()) {
            int current = world.getViewDistance();
            int next = Math.max(min, Math.min(max, current + delta));
            if (next != current) {
                world.setViewDistance(next);
                changed = true;
            }
        }
        if (changed) {
            lastChange = Instant.now();
            plugin.getLogger().info("Adjusted view distance by " + delta + " based on MSPT control.");
        }
    }

    public ViewDistanceStatus getStatusSnapshot() {
        int sampleWindow = Math.max(5, config.getViewDistanceSampleWindowSeconds());
        PerformanceTracker.MsptEwmaTrend ewmaTrend = tracker.msptEwmaTrend(sampleWindow, config.getViewDistanceEwmaAlpha());
        double targetMspt = config.getViewDistanceTargetMspt();
        double error = ewmaTrend.ewmaMspt() - targetMspt;
        boolean increaseRecommended = error < 0 && isIncreaseStable(Instant.now());
        boolean decreaseRecommended = error > 0 || ewmaTrend.trendPerSecond() > STABLE_TREND_THRESHOLD;
        long cooldownRemaining = cooldownRemainingSeconds();
        int currentViewDistance = currentViewDistance();
        int predictedIncrease = increaseRecommended ? predictViewDistanceIncrease(currentViewDistance, targetMspt) : 0;
        int predictedChunks = increaseRecommended
                ? predictedAdditionalChunks(currentViewDistance, predictedIncrease)
                : 0;
        return new ViewDistanceStatus(
                currentViewDistance,
                increaseRecommended,
                decreaseRecommended,
                cooldownRemaining,
                predictedIncrease,
                predictedChunks
        );
    }

    private int predictViewDistanceIncrease(int currentViewDistance, double targetMspt) {
        int historySeconds = config.getLongTermSampleWindowSeconds();
        List<PerformanceSample> history = tracker.getSamplesSinceSeconds(historySeconds);
        if (history.isEmpty()) {
            return 0;
        }
        Map<Integer, Double> avgMsptByViewDistance = history.stream()
                .collect(Collectors.groupingBy(
                        PerformanceSample::viewDistance,
                        Collectors.averagingDouble(PerformanceSample::mspt)
                ));
        int maxAllowed = avgMsptByViewDistance.entrySet().stream()
                .filter(entry -> entry.getValue() > 0 && entry.getValue() <= targetMspt)
                .map(Map.Entry::getKey)
                .max(Integer::compareTo)
                .orElse(currentViewDistance);
        maxAllowed = Math.min(config.getViewDistanceMax(), maxAllowed);
        if (maxAllowed <= currentViewDistance) {
            return 0;
        }
        return maxAllowed - currentViewDistance;
    }

    private int predictedAdditionalChunks(int currentViewDistance, int deltaViewDistance) {
        if (deltaViewDistance <= 0) {
            return 0;
        }
        int players = (int) Math.max(1, Math.round(tracker.averagePlayers(300)));
        int currentChunksPerPlayer = chunksForViewDistance(currentViewDistance);
        int nextChunksPerPlayer = chunksForViewDistance(currentViewDistance + deltaViewDistance);
        int additionalPerPlayer = Math.max(0, nextChunksPerPlayer - currentChunksPerPlayer);
        return additionalPerPlayer * players;
    }

    private int chunksForViewDistance(int viewDistance) {
        int radius = Math.max(0, viewDistance);
        int diameter = radius * 2 + 1;
        return diameter * diameter;
    }

    private int currentViewDistance() {
        return (int) Math.round(Bukkit.getWorlds().stream()
                .mapToInt(World::getViewDistance)
                .average()
                .orElse(0.0));
    }

    private long cooldownRemainingSeconds() {
        long remaining = ChronoUnit.SECONDS.between(Instant.now(), lastChange.plusSeconds(config.getViewDistanceCooldownSeconds()));
        return Math.max(0, remaining);
    }
}
