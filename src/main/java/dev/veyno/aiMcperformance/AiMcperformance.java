package dev.veyno.aiMcperformance;

import dev.veyno.aiMcperformance.command.PerformanceCommand;
import dev.veyno.aiMcperformance.config.PerformanceConfig;
import dev.veyno.aiMcperformance.metrics.PerformanceSample;
import dev.veyno.aiMcperformance.metrics.PerformanceTracker;
import dev.veyno.aiMcperformance.metrics.PterodactylMetricsService;
import dev.veyno.aiMcperformance.monitor.BossBarMonitor;
import dev.veyno.aiMcperformance.monitor.MonitorListener;
import dev.veyno.aiMcperformance.optimization.ViewDistanceOptimizer;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public final class AiMcperformance extends JavaPlugin {
    private PerformanceTracker tracker;
    private BossBarMonitor bossBarMonitor;
    private PerformanceConfig performanceConfig;
    private PterodactylMetricsService pterodactylMetricsService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        performanceConfig = new PerformanceConfig(getConfig());
        tracker = new PerformanceTracker(performanceConfig.getMaxSampleWindowSeconds());
        pterodactylMetricsService = new PterodactylMetricsService(this, performanceConfig);
        pterodactylMetricsService.start();
        pterodactylMetricsService.schedule();
        bossBarMonitor = new BossBarMonitor(this, performanceConfig, tracker);
        getCommand("performance").setExecutor(new PerformanceCommand(bossBarMonitor));
        getServer().getPluginManager().registerEvents(new MonitorListener(bossBarMonitor), this);
        scheduleSampling();
        new ViewDistanceOptimizer(this, performanceConfig, tracker).schedule();

    }

    @Override
    public void onDisable() {
        if (bossBarMonitor != null) {
            Bukkit.getOnlinePlayers().forEach(bossBarMonitor::disableAll);
        }
    }

    private void scheduleSampling() {
        int intervalSeconds = Math.max(1, performanceConfig.getSampleIntervalSeconds());
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            double mspt = Bukkit.getServer().getAverageTickTime();
            double tps = Bukkit.getServer().getTPS()[0];
            int entities = Bukkit.getWorlds().stream()
                    .mapToInt(world -> world.getEntities().size())
                    .sum();
            int chunks = Bukkit.getWorlds().stream()
                    .mapToInt(world -> world.getLoadedChunks().length)
                    .sum();
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            long usedRam = heap.getUsed();
            double cpuUsage = pterodactylMetricsService.getCpuUsage();
            tracker.addSample(new PerformanceSample(mspt, tps, entities, chunks, usedRam, cpuUsage));
            bossBarMonitor.updateAll();
        }, intervalSeconds * 20L, intervalSeconds * 20L);
    }
}
