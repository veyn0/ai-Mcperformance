package dev.veyno.aiMcperformance;

import dev.veyno.aiMcperformance.command.PerformanceCommand;
import dev.veyno.aiMcperformance.command.PerformanceTabCompleter;
import dev.veyno.aiMcperformance.config.PerformanceConfig;
import dev.veyno.aiMcperformance.metrics.PerformanceSample;
import dev.veyno.aiMcperformance.metrics.PerformanceTracker;
import dev.veyno.aiMcperformance.metrics.PterodactylMetricsService;
import dev.veyno.aiMcperformance.metrics.storage.CsvPerformanceSampleStore;
import dev.veyno.aiMcperformance.metrics.storage.NoopPerformanceSampleStore;
import dev.veyno.aiMcperformance.metrics.storage.PerformanceSampleStore;
import dev.veyno.aiMcperformance.monitor.BossBarMonitor;
import dev.veyno.aiMcperformance.monitor.MonitorListener;
import dev.veyno.aiMcperformance.monitor.StatusOverviewBroadcaster;
import dev.veyno.aiMcperformance.optimization.ViewDistanceOptimizer;
import dev.veyno.aiMcperformance.optimization.actions.ActionEngine;
import dev.veyno.aiMcperformance.optimization.actions.EntityActivationRangeAction;
import dev.veyno.aiMcperformance.optimization.actions.MobCapsAction;
import dev.veyno.aiMcperformance.optimization.actions.SimulationDistanceAction;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class AiMcperformance extends JavaPlugin {
    private PerformanceTracker tracker;
    private BossBarMonitor bossBarMonitor;
    private PerformanceConfig performanceConfig;
    private PterodactylMetricsService pterodactylMetricsService;
    private ViewDistanceOptimizer viewDistanceOptimizer;
    private ActionEngine actionEngine;
    private PerformanceSampleStore sampleStore;
    private BukkitTask samplingTask;
    private StatusOverviewBroadcaster statusOverviewBroadcaster;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        performanceConfig = new PerformanceConfig(getConfig());
        tracker = new PerformanceTracker(performanceConfig.getLongTermSampleWindowSeconds());
        pterodactylMetricsService = new PterodactylMetricsService(this, performanceConfig);
        viewDistanceOptimizer = new ViewDistanceOptimizer(this, performanceConfig, tracker);
        actionEngine = new ActionEngine(this, performanceConfig, tracker, List.of(
                new SimulationDistanceAction(performanceConfig),
                new EntityActivationRangeAction(performanceConfig),
                new MobCapsAction(performanceConfig)
        ));
        bossBarMonitor = new BossBarMonitor(this, performanceConfig, tracker, viewDistanceOptimizer::getStatusSnapshot);
        statusOverviewBroadcaster = new StatusOverviewBroadcaster(
                this,
                performanceConfig,
                tracker,
                viewDistanceOptimizer::getStatusSnapshot
        );
        PluginCommand performanceCommand = getCommand("performance");
        if (performanceCommand != null) {
            performanceCommand.setExecutor(new PerformanceCommand(this, bossBarMonitor, tracker, performanceConfig));
            performanceCommand.setTabCompleter(new PerformanceTabCompleter());
        } else {
            getLogger().warning("Command 'performance' not found in plugin.yml.");
        }
        PluginCommand reloadCommand = getCommand("reload");
        if (reloadCommand != null) {
            reloadCommand.setExecutor(new PerformanceCommand(this, bossBarMonitor, tracker, performanceConfig));
        }
        getServer().getPluginManager().registerEvents(new MonitorListener(bossBarMonitor), this);
        applyConfiguration(true);

    }

    @Override
    public void onDisable() {
        if (bossBarMonitor != null) {
            Bukkit.getOnlinePlayers().forEach(bossBarMonitor::disableAll);
        }
        if (samplingTask != null) {
            samplingTask.cancel();
        }
        if (sampleStore != null) {
            sampleStore.flushNowAsync();
        }
        if (pterodactylMetricsService != null) {
            pterodactylMetricsService.stop();
        }
        if (actionEngine != null) {
            actionEngine.shutdown();
        }
        if (viewDistanceOptimizer != null) {
            viewDistanceOptimizer.stop();
        }
        if (statusOverviewBroadcaster != null) {
            statusOverviewBroadcaster.stop();
        }
    }

    private void scheduleSampling() {
        int intervalSeconds = Math.max(1, performanceConfig.getSampleIntervalSeconds());
        if (samplingTask != null) {
            samplingTask.cancel();
        }
        samplingTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!performanceConfig.isSamplingEnabled()) {
                return;
            }
            double mspt = Bukkit.getServer().getAverageTickTime();
            double tps = Bukkit.getServer().getTPS()[0];
            int entities = Bukkit.getWorlds().stream()
                    .mapToInt(world -> world.getEntities().size())
                    .sum();
            int chunks = Bukkit.getWorlds().stream()
                    .mapToInt(world -> world.getLoadedChunks().length)
                    .sum();
            int viewDistance = (int) Math.round(Bukkit.getWorlds().stream()
                    .mapToInt(World::getViewDistance)
                    .average()
                    .orElse(0.0));
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            long usedRam = heap.getUsed();
            double cpuUsage = pterodactylMetricsService.getCpuUsage();
            int players = Bukkit.getOnlinePlayers().size();
            PerformanceSample sample = new PerformanceSample(
                    Instant.now(),
                    mspt,
                    tps,
                    entities,
                    chunks,
                    usedRam,
                    cpuUsage,
                    viewDistance,
                    players
            );
            tracker.addSample(sample);
            sampleStore.addSample(sample);
            sampleStore.requestFlush();
            bossBarMonitor.updateAll();
        }, intervalSeconds * 20L, intervalSeconds * 20L);
    }

    private PerformanceSampleStore createSampleStore() {
        if (!performanceConfig.isStorageEnabled()) {
            return new NoopPerformanceSampleStore();
        }
        String type = performanceConfig.getStorageType();
        if (type == null || type.equalsIgnoreCase("none")) {
            return new NoopPerformanceSampleStore();
        }
        if (type.equalsIgnoreCase("csv")) {
            Path path = resolveStoragePath(performanceConfig.getStoragePath());
            return new CsvPerformanceSampleStore(this, path, performanceConfig.getStorageFlushIntervalSeconds());
        }
        getLogger().warning("Unknown storage type '" + type + "', disabling persistence.");
        return new NoopPerformanceSampleStore();
    }

    private Path resolveStoragePath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return getDataFolder().toPath().resolve("samples/metrics.csv");
        }
        Path path = Paths.get(configuredPath);
        if (path.isAbsolute()) {
            return path;
        }
        return getDataFolder().toPath().resolve(path);
    }

    private void restoreSamplesIfConfigured() {
        if (!performanceConfig.isStorageEnabled()) {
            return;
        }
        if (!performanceConfig.isStorageRestoreOnStartEnabled()) {
            return;
        }
        List<PerformanceSample> restored = sampleStore.loadSamples();
        if (!restored.isEmpty()) {
            tracker.restoreSamples(restored);
            getLogger().info("Restored " + restored.size() + " performance samples from storage.");
        }
    }

    public void applyConfiguration(boolean restoreSamples) {
        performanceConfig.reload(getConfig());
        if (sampleStore != null) {
            sampleStore.flushNowAsync();
        }
        sampleStore = createSampleStore();
        if (restoreSamples) {
            restoreSamplesIfConfigured();
        }
        if (pterodactylMetricsService != null) {
            pterodactylMetricsService.stop();
            pterodactylMetricsService.start();
            pterodactylMetricsService.schedule();
        }
        if (viewDistanceOptimizer != null) {
            viewDistanceOptimizer.schedule();
        }
        if (actionEngine != null && !performanceConfig.isActionEngineEnabled()) {
            actionEngine.shutdown();
        } else if (actionEngine != null) {
            actionEngine.schedule();
        }
        scheduleSampling();
        if (!performanceConfig.isBossBarEnabled() && bossBarMonitor != null) {
            bossBarMonitor.disableAllPlayers();
        }
        if (statusOverviewBroadcaster != null) {
            statusOverviewBroadcaster.schedule();
        }
    }

    public void reloadPluginState() {
        reloadConfig();
        applyConfiguration(false);
    }

    public void updateFeatureToggle(String configPath, boolean enabled) {
        getConfig().set(configPath, enabled);
        saveConfig();
        applyConfiguration(false);
    }
}
