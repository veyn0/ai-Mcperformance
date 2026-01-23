package dev.veyno.aiMcperformance.testing;

import dev.veyno.aiMcperformance.AiMcperformance;
import dev.veyno.aiMcperformance.config.PerformanceConfig;
import dev.veyno.aiMcperformance.metrics.PerformanceSample;
import dev.veyno.aiMcperformance.metrics.PerformanceTracker;
import dev.veyno.aiMcperformance.metrics.StatsWindow;
import dev.veyno.aiMcperformance.optimization.ViewDistanceOptimizer;
import dev.veyno.aiMcperformance.optimization.actions.ActionEngine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.scheduler.BukkitTask;

public class LoadTestManager {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.systemDefault());
    private final AiMcperformance plugin;
    private final PerformanceConfig config;
    private final PerformanceTracker tracker;
    private final ViewDistanceOptimizer viewDistanceOptimizer;
    private final ActionEngine actionEngine;
    private final List<StepRecord> stepRecords = new ArrayList<>();
    private BukkitTask stepTask;
    private BukkitTask endTask;
    private boolean running;
    private Instant testStartedAt;
    private Instant testEndsAt;
    private Instant currentStepStart;
    private boolean currentStepClosed;
    private int stepIndex;
    private int totalSteps;
    private LoadTestAction action;
    private boolean viewDistanceWasEnabled;
    private boolean actionsWereEnabled;
    private String lastError;
    private String actionOverride;

    public LoadTestManager(
            AiMcperformance plugin,
            PerformanceConfig config,
            PerformanceTracker tracker,
            ViewDistanceOptimizer viewDistanceOptimizer,
            ActionEngine actionEngine
    ) {
        this.plugin = plugin;
        this.config = config;
        this.tracker = tracker;
        this.viewDistanceOptimizer = viewDistanceOptimizer;
        this.actionEngine = actionEngine;
    }

    public boolean isRunning() {
        return running;
    }

    public String getLastError() {
        return lastError;
    }

    public LoadTestStatus getStatus() {
        if (!running) {
            return new LoadTestStatus(false, null, 0, 0, 0, null, null);
        }
        return new LoadTestStatus(
                true,
                action != null ? action.name() : null,
                stepIndex + 1,
                totalSteps,
                action != null ? action.currentValue() : 0,
                testStartedAt,
                testEndsAt
        );
    }

    public boolean start() {
        return start(null);
    }

    public boolean start(String requestedAction) {
        lastError = null;
        actionOverride = requestedAction;
        if (running) {
            lastError = "running";
            return false;
        }
        if (!config.isTestModeEnabled()) {
            lastError = "disabled";
            return false;
        }
        int durationMinutes = Math.max(1, config.getTestModeDurationMinutes());
        int stepIntervalMinutes = Math.max(1, config.getTestModeStepIntervalMinutes());
        totalSteps = Math.max(1, (int) Math.ceil((double) durationMinutes / stepIntervalMinutes));
        stepIndex = 0;
        stepRecords.clear();
        testStartedAt = Instant.now();
        testEndsAt = testStartedAt.plus(Duration.ofMinutes(durationMinutes));
        currentStepStart = testStartedAt;
        currentStepClosed = false;

        action = createActionHandler();
        if (action == null) {
            plugin.getLogger().warning("Load test not started: unsupported action type.");
            lastError = "invalid-action";
            actionOverride = null;
            return false;
        }

        pauseOptimizations();
        distributePlayersIfConfigured();

        action.captureBaseline();
        action.apply(stepIndex);

        long stepTicks = Duration.ofMinutes(stepIntervalMinutes).getSeconds() * 20L;
        stepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::advanceStep, stepTicks, stepTicks);
        long totalTicks = Duration.ofMinutes(durationMinutes).getSeconds() * 20L;
        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::stop, totalTicks);
        running = true;
        plugin.getLogger().info("Load test started with " + action.name() + " for " + durationMinutes + " minutes.");
        return true;
    }

    public boolean stop() {
        if (!running) {
            return false;
        }
        if (!currentStepClosed) {
            closeCurrentStep();
        }
        if (stepTask != null) {
            stepTask.cancel();
            stepTask = null;
        }
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        if (action != null) {
            action.restoreBaseline();
        }
        actionOverride = null;
        writeOutputFiles();
        resumeOptimizations();
        running = false;
        plugin.getLogger().info("Load test finished and results written.");
        return true;
    }

    private void advanceStep() {
        if (!running) {
            return;
        }
        closeCurrentStep();
        stepIndex++;
        if (stepIndex >= totalSteps) {
            stop();
            return;
        }
        currentStepStart = Instant.now();
        currentStepClosed = false;
        if (action != null) {
            action.apply(stepIndex);
        }
    }

    private void closeCurrentStep() {
        Instant now = Instant.now();
        List<PerformanceSample> samples = tracker.getSamplesBetween(currentStepStart, now);
        StepRecord record = new StepRecord(stepIndex, action.currentValue(), currentStepStart, now, samples);
        stepRecords.add(record);
        currentStepClosed = true;
    }

    private LoadTestAction createActionHandler() {
        String selected = actionOverride != null ? actionOverride : config.getTestModeAction();
        if (selected == null) {
            return null;
        }
        return switch (selected.toLowerCase(Locale.ROOT)) {
            case "view-distance" -> new ViewDistanceActionHandler();
            case "simulation-distance" -> new SimulationDistanceActionHandler();
            case "mob-caps", "entity-cap", "entity-caps", "entitycap", "entitycaps" -> new MobCapsActionHandler();
            default -> null;
        };
    }

    private void pauseOptimizations() {
        viewDistanceWasEnabled = config.isDynamicViewDistanceEnabled();
        actionsWereEnabled = config.isActionEngineEnabled();
        if (viewDistanceOptimizer != null) {
            viewDistanceOptimizer.stop();
        }
        if (actionEngine != null) {
            actionEngine.stop();
        }
    }

    private void resumeOptimizations() {
        if (viewDistanceOptimizer != null && viewDistanceWasEnabled) {
            viewDistanceOptimizer.schedule();
        }
        if (actionEngine != null && actionsWereEnabled) {
            actionEngine.schedule();
        }
    }

    private void distributePlayersIfConfigured() {
        if (!config.isTestModePlayerDistributionEnabled()) {
            return;
        }
        World world = resolveDistributionWorld();
        if (world == null) {
            return;
        }
        String mode = config.getTestModePlayerDistributionMode();
        if ("cluster".equalsIgnoreCase(mode)) {
            clusterPlayers(world, config.getTestModePlayerClusterRadius());
            return;
        }
        spreadPlayers(world, config.getTestModePlayerSpreadRadius());
    }

    private World resolveDistributionWorld() {
        String worldName = config.getTestModePlayerDistributionWorld();
        if (worldName != null && !worldName.isBlank()) {
            return Bukkit.getWorld(worldName);
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    private void spreadPlayers(World world, int radius) {
        if (radius <= 0) {
            return;
        }
        Location spawn = world.getSpawnLocation();
        int index = 0;
        int totalPlayers = Bukkit.getOnlinePlayers().size();
        for (Player player : Bukkit.getOnlinePlayers()) {
            double angle = (2 * Math.PI) * (index / Math.max(1.0, totalPlayers));
            double distance = radius * 0.5 + (radius * 0.5 * Math.random());
            double x = spawn.getX() + Math.cos(angle) * distance;
            double z = spawn.getZ() + Math.sin(angle) * distance;
            Location target = new Location(world, x, spawn.getY(), z);
            Location safe = world.getHighestBlockAt(target).getLocation().add(0.5, 1.0, 0.5);
            player.teleport(safe);
            index++;
        }
    }

    private void clusterPlayers(World world, int radius) {
        if (radius <= 0) {
            return;
        }
        Location spawn = world.getSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            double dx = (Math.random() * 2 - 1) * radius;
            double dz = (Math.random() * 2 - 1) * radius;
            Location target = new Location(world, spawn.getX() + dx, spawn.getY(), spawn.getZ() + dz);
            Location safe = world.getHighestBlockAt(target).getLocation().add(0.5, 1.0, 0.5);
            player.teleport(safe);
        }
    }

    private void writeOutputFiles() {
        Path summaryPath = resolveOutputPath(config.getTestModeSummaryPath());
        try {
            Files.createDirectories(summaryPath.getParent());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create load test output directory: " + e.getMessage());
        }
        writeSummary(summaryPath);
        if (config.isTestModeWriteRawSamples()) {
            Path samplePath = resolveOutputPath(config.getTestModeSamplePath());
            try {
                Files.createDirectories(samplePath.getParent());
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create load test sample output directory: " + e.getMessage());
            }
            writeSamples(samplePath);
        }
    }

    private Path resolveOutputPath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return plugin.getDataFolder().toPath().resolve("tests/load-test-summary.csv");
        }
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path;
        }
        return plugin.getDataFolder().toPath().resolve(path);
    }

    private void writeSummary(Path path) {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",",
                "step",
                "action",
                "action_value",
                "start",
                "end",
                "samples",
                "mspt_avg",
                "mspt_p95",
                "tps_avg",
                "cpu_avg",
                "ram_mb_avg",
                "entities_avg",
                "chunks_avg",
                "players_avg",
                "view_distance_avg"
        ));
        for (StepRecord record : stepRecords) {
            StatsWindow msptStats = StatsWindow.fromSamples(record.samples(), PerformanceSample::mspt);
            double tpsAvg = average(record.samples(), PerformanceSample::tps);
            double cpuAvg = average(record.samples(), PerformanceSample::cpuUsagePercent);
            double ramAvg = average(record.samples(), sample -> sample.usedRamBytes() / 1024.0 / 1024.0);
            double entitiesAvg = average(record.samples(), PerformanceSample::entities);
            double chunksAvg = average(record.samples(), PerformanceSample::chunks);
            double playersAvg = average(record.samples(), PerformanceSample::players);
            double viewAvg = average(record.samples(), PerformanceSample::viewDistance);
            lines.add(String.join(",",
                    Integer.toString(record.step()),
                    action != null ? action.name() : "unknown",
                    Integer.toString(record.value()),
                    TIME_FORMAT.format(record.start()),
                    TIME_FORMAT.format(record.end()),
                    Integer.toString(record.samples().size()),
                    format(msptStats.average()),
                    format(msptStats.p95()),
                    format(tpsAvg),
                    format(cpuAvg),
                    format(ramAvg),
                    format(entitiesAvg),
                    format(chunksAvg),
                    format(playersAvg),
                    format(viewAvg)
            ));
        }
        writeLines(path, lines);
    }

    private void writeSamples(Path path) {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",",
                "step",
                "action",
                "action_value",
                "timestamp",
                "mspt",
                "tps",
                "cpu",
                "ram_bytes",
                "entities",
                "chunks",
                "view_distance",
                "players"
        ));
        for (StepRecord record : stepRecords) {
            for (PerformanceSample sample : record.samples()) {
                lines.add(String.join(",",
                        Integer.toString(record.step()),
                        action != null ? action.name() : "unknown",
                        Integer.toString(record.value()),
                        TIME_FORMAT.format(sample.timestamp()),
                        format(sample.mspt()),
                        format(sample.tps()),
                        format(sample.cpuUsagePercent()),
                        Long.toString(sample.usedRamBytes()),
                        Integer.toString(sample.entities()),
                        Integer.toString(sample.chunks()),
                        Integer.toString(sample.viewDistance()),
                        Integer.toString(sample.players())
                ));
            }
        }
        writeLines(path, lines);
    }

    private void writeLines(Path path, List<String> lines) {
        try {
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write load test file: " + e.getMessage());
        }
    }

    private double average(List<PerformanceSample> samples, java.util.function.ToDoubleFunction<PerformanceSample> extractor) {
        if (samples.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (PerformanceSample sample : samples) {
            sum += extractor.applyAsDouble(sample);
        }
        return sum / samples.size();
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private interface LoadTestAction {
        void captureBaseline();

        void apply(int step);

        void restoreBaseline();

        String name();

        int currentValue();
    }

    private final class ViewDistanceActionHandler implements LoadTestAction {
        private final Map<UUID, Integer> baseline = new java.util.HashMap<>();
        private int current;

        @Override
        public void captureBaseline() {
            baseline.clear();
            for (World world : Bukkit.getWorlds()) {
                baseline.put(world.getUID(), world.getViewDistance());
            }
        }

        @Override
        public void apply(int step) {
            int value = nextValue(step, config.getTestModeValueStart(), config.getTestModeValueStep(), config.getTestModeValueMax());
            current = value;
            for (World world : Bukkit.getWorlds()) {
                world.setViewDistance(value);
            }
        }

        @Override
        public void restoreBaseline() {
            for (World world : Bukkit.getWorlds()) {
                Integer value = baseline.get(world.getUID());
                if (value != null) {
                    world.setViewDistance(value);
                }
            }
        }

        @Override
        public String name() {
            return "view-distance";
        }

        @Override
        public int currentValue() {
            return current;
        }
    }

    private final class SimulationDistanceActionHandler implements LoadTestAction {
        private final Map<UUID, Integer> baseline = new java.util.HashMap<>();
        private int current;

        @Override
        public void captureBaseline() {
            baseline.clear();
            for (World world : Bukkit.getWorlds()) {
                baseline.put(world.getUID(), world.getSimulationDistance());
            }
        }

        @Override
        public void apply(int step) {
            int value = nextValue(step, config.getTestModeValueStart(), config.getTestModeValueStep(), config.getTestModeValueMax());
            current = value;
            for (World world : Bukkit.getWorlds()) {
                world.setSimulationDistance(value);
            }
        }

        @Override
        public void restoreBaseline() {
            for (World world : Bukkit.getWorlds()) {
                Integer value = baseline.get(world.getUID());
                if (value != null) {
                    world.setSimulationDistance(value);
                }
            }
        }

        @Override
        public String name() {
            return "simulation-distance";
        }

        @Override
        public int currentValue() {
            return current;
        }
    }

    private final class MobCapsActionHandler implements LoadTestAction {
        private final Map<UUID, Map<SpawnCategory, Integer>> baseline = new java.util.HashMap<>();
        private int current;

        @Override
        public void captureBaseline() {
            baseline.clear();
            for (World world : Bukkit.getWorlds()) {
                Map<SpawnCategory, Integer> worldCaps = new EnumMap<>(SpawnCategory.class);
                for (SpawnCategory category : SpawnCategory.values()) {
                    worldCaps.put(category, world.getSpawnLimit(category));
                }
                baseline.put(world.getUID(), worldCaps);
            }
        }

        @Override
        public void apply(int step) {
            int stepValue = nextValue(step, config.getTestModeMobCapsStart(), config.getTestModeMobCapsStep(),
                    config.getTestModeMobCapsMax());
            current = stepValue;
            Map<SpawnCategory, Integer> baseLimits = config.getTestModeMobCapsBaseLimits();
            Map<SpawnCategory, Integer> maxLimits = config.getTestModeMobCapsMaxLimits();
            for (World world : Bukkit.getWorlds()) {
                for (SpawnCategory category : SpawnCategory.values()) {
                    int base = baseLimits.getOrDefault(category, world.getSpawnLimit(category));
                    int max = maxLimits.getOrDefault(category, Integer.MAX_VALUE);
                    int target = Math.min(max, base + stepValue);
                    world.setSpawnLimit(category, target);
                }
            }
        }

        @Override
        public void restoreBaseline() {
            for (World world : Bukkit.getWorlds()) {
                Map<SpawnCategory, Integer> worldCaps = baseline.get(world.getUID());
                if (worldCaps == null) {
                    continue;
                }
                for (Map.Entry<SpawnCategory, Integer> entry : worldCaps.entrySet()) {
                    world.setSpawnLimit(entry.getKey(), entry.getValue());
                }
            }
        }

        @Override
        public String name() {
            return "mob-caps";
        }

        @Override
        public int currentValue() {
            return current;
        }
    }

    private int nextValue(int step, int start, int stepSize, int max) {
        int value = start + (step * stepSize);
        if (max > 0) {
            return Math.min(max, value);
        }
        return value;
    }

    private record StepRecord(int step, int value, Instant start, Instant end, List<PerformanceSample> samples) {
    }

    public record LoadTestStatus(
            boolean running,
            String action,
            int currentStep,
            int totalSteps,
            int currentValue,
            Instant startedAt,
            Instant endsAt
    ) {
    }
}
