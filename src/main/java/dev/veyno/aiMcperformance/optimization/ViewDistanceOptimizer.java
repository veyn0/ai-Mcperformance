package dev.veyno.aiMcperformance.optimization;

import dev.veyno.aiMcperformance.config.PerformanceConfig;
import dev.veyno.aiMcperformance.metrics.PerformanceSample;
import dev.veyno.aiMcperformance.metrics.PerformanceTracker;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

public class ViewDistanceOptimizer implements Runnable {
    private final Plugin plugin;
    private final PerformanceConfig config;
    private final PerformanceTracker tracker;
    private Instant lastChange = Instant.EPOCH;
    private Instant lastRapidResponse = Instant.EPOCH;

    public ViewDistanceOptimizer(Plugin plugin, PerformanceConfig config, PerformanceTracker tracker) {
        this.plugin = plugin;
        this.config = config;
        this.tracker = tracker;
    }

    public void schedule() {
        if (!config.isDynamicViewDistanceEnabled()) {
            return;
        }
        int intervalSeconds = Math.max(5, config.getViewDistanceCheckIntervalSeconds());
        Bukkit.getScheduler().runTaskTimer(plugin, this, intervalSeconds * 20L, intervalSeconds * 20L);
    }

    @Override
    public void run() {
        if (!config.isDynamicViewDistanceEnabled()) {
            return;
        }
        Instant now = Instant.now();
        int sampleWindow = Math.max(5, config.getViewDistanceSampleWindowSeconds());
        double avgMspt = tracker.averageMspt(sampleWindow);
        int min = config.getViewDistanceMin();
        int max = config.getViewDistanceMax();
        int step = Math.max(1, config.getViewDistanceStep());
        boolean rapidIncrease = isRapidMsptIncrease();
        boolean cooldownElapsed = lastChange.plusSeconds(config.getViewDistanceCooldownSeconds()).isBefore(now);
        boolean rapidCooldownElapsed = lastRapidResponse
                .plusSeconds(config.getViewDistanceRapidCooldownSeconds())
                .isBefore(now);

        if (rapidIncrease && rapidCooldownElapsed) {
            int multiplier = computeDecreaseMultiplier(avgMspt, true);
            adjustViewDistance(-step * multiplier, min, max);
            lastRapidResponse = now;
            return;
        }

        if (!cooldownElapsed) {
            return;
        }

        if (avgMspt >= config.getViewDistanceHighMspt()) {
            int multiplier = computeDecreaseMultiplier(avgMspt, false);
            adjustViewDistance(-step * multiplier, min, max);
        } else if (avgMspt > 0 && avgMspt <= config.getViewDistanceLowMspt()) {
            int delta = computeIncreaseDelta(step);
            adjustViewDistance(delta, min, max);
        }
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
            plugin.getLogger().info("Adjusted view distance by " + delta + " due to MSPT trend.");
        }
    }

    public ViewDistanceStatus getStatusSnapshot() {
        int sampleWindow = Math.max(5, config.getViewDistanceSampleWindowSeconds());
        double avgMspt = tracker.averageMspt(sampleWindow);
        boolean rapidIncrease = isRapidMsptIncrease();
        boolean increaseRecommended = avgMspt > 0 && avgMspt <= config.getViewDistanceLowMspt();
        boolean decreaseRecommended = avgMspt >= config.getViewDistanceHighMspt() || rapidIncrease;
        long cooldownRemaining = cooldownRemainingSeconds();
        int currentViewDistance = currentViewDistance();
        int predictedIncrease = increaseRecommended ? predictViewDistanceIncrease(currentViewDistance) : 0;
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

    private int computeIncreaseDelta(int step) {
        int maxMultiplier = Math.max(1, config.getViewDistanceMaxStepMultiplier());
        int currentViewDistance = currentViewDistance();
        int predictedIncrease = predictViewDistanceIncrease(currentViewDistance);
        int desired = predictedIncrease > 0 ? predictedIncrease : step;
        int desiredSteps = Math.max(1, (int) Math.ceil((double) desired / step));
        int steps = Math.min(maxMultiplier, desiredSteps);
        int delta = step * steps;
        if (predictedIncrease > 0) {
            delta = Math.min(delta, predictedIncrease);
        }
        return Math.max(step, delta);
    }

    private int computeDecreaseMultiplier(double avgMspt, boolean rapidIncrease) {
        int maxMultiplier = Math.max(1, config.getViewDistanceMaxStepMultiplier());
        int multiplier = rapidIncrease ? 2 : 1;
        if (avgMspt > config.getViewDistanceHighMspt()) {
            double over = avgMspt - config.getViewDistanceHighMspt();
            int extra = (int) Math.floor(over / Math.max(1.0, config.getViewDistanceOverageMsptPerStep()));
            multiplier = Math.max(multiplier, 1 + extra);
        }
        return Math.min(maxMultiplier, multiplier);
    }

    private boolean isRapidMsptIncrease() {
        int rapidWindow = Math.max(3, config.getViewDistanceRapidSampleWindowSeconds());
        int baselineWindow = Math.max(rapidWindow + 1, config.getViewDistanceRapidBaselineWindowSeconds());
        double rapidAvg = tracker.averageMspt(rapidWindow);
        double baselineAvg = tracker.averageMspt(baselineWindow);
        if (rapidAvg <= 0 || baselineAvg <= 0) {
            return false;
        }
        return (rapidAvg - baselineAvg) >= config.getViewDistanceRapidMsptIncrease();
    }

    private int predictViewDistanceIncrease(int currentViewDistance) {
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
                .filter(entry -> entry.getValue() > 0 && entry.getValue() <= config.getViewDistanceLowMspt())
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
