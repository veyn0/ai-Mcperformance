package dev.veyno.aiMcperformance.optimization;

import dev.veyno.aiMcperformance.config.PerformanceConfig;
import dev.veyno.aiMcperformance.metrics.PerformanceTracker;
import java.time.Instant;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

public class ViewDistanceOptimizer implements Runnable {
    private final Plugin plugin;
    private final PerformanceConfig config;
    private final PerformanceTracker tracker;
    private Instant lastChange = Instant.EPOCH;

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
        if (!lastChange.plusSeconds(config.getViewDistanceCooldownSeconds()).isBefore(Instant.now())) {
            return;
        }
        int sampleWindow = Math.max(5, config.getViewDistanceSampleWindowSeconds());
        double avgMspt = tracker.averageMspt(sampleWindow);
        int min = config.getViewDistanceMin();
        int max = config.getViewDistanceMax();
        int step = Math.max(1, config.getViewDistanceStep());
        if (avgMspt >= config.getViewDistanceHighMspt()) {
            adjustViewDistance(-step, min, max);
        } else if (avgMspt > 0 && avgMspt <= config.getViewDistanceLowMspt()) {
            adjustViewDistance(step, min, max);
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
}
