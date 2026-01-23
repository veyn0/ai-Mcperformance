package dev.veyno.aiMcperformance.optimization.actions;

import dev.veyno.aiMcperformance.config.PerformanceConfig;
import dev.veyno.aiMcperformance.metrics.PerformanceTracker;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class ActionEngine implements Runnable {
    private final Plugin plugin;
    private final PerformanceConfig config;
    private final PerformanceTracker tracker;
    private final List<PerformanceAction> actions;
    private final Map<String, ActionState> states = new HashMap<>();
    private final ActionContext context;
    private BukkitTask task;

    public ActionEngine(Plugin plugin, PerformanceConfig config, PerformanceTracker tracker, List<PerformanceAction> actions) {
        this.plugin = plugin;
        this.config = config;
        this.tracker = tracker;
        this.actions = actions.stream()
                .sorted(Comparator.comparingInt(PerformanceAction::getPriority).reversed())
                .toList();
        this.context = new ActionContext(plugin, config);
        actions.forEach(action -> states.put(action.getName(), new ActionState()));
    }

    public void schedule() {
        stop();
        int intervalSeconds = Math.max(5, config.getActionEngineCheckIntervalSeconds());
        task = Bukkit.getScheduler().runTaskTimer(plugin, this, intervalSeconds * 20L, intervalSeconds * 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        stop();
        actions.stream()
                .sorted(Comparator.comparingInt(PerformanceAction::getPriority))
                .forEach(this::tryRevert);
    }

    @Override
    public void run() {
        if (!config.isActionEngineEnabled()) {
            return;
        }
        int sampleWindowSeconds = Math.max(5, config.getActionEngineSampleWindowSeconds());
        double avgMspt = tracker.averageMspt(sampleWindowSeconds);
        double avgCpu = tracker.averageCpu(sampleWindowSeconds);
        double avgEntities = tracker.averageEntities(sampleWindowSeconds);

        boolean underPressure = avgMspt >= config.getActionMsptThreshold()
                || avgCpu >= config.getActionCpuThreshold()
                || avgEntities >= config.getActionEntityThreshold();
        boolean recovered = avgMspt <= config.getActionMsptRecovery()
                && avgCpu <= config.getActionCpuRecovery()
                && avgEntities <= config.getActionEntityRecovery();

        if (underPressure) {
            for (PerformanceAction action : actions) {
                ActionState state = states.get(action.getName());
                if (state != null && !state.active && cooldownElapsed(action, state)) {
                    if (action.apply(context)) {
                        state.active = true;
                        state.lastChange = Instant.now();
                        plugin.getLogger().info("Action applied: " + action.getName());
                        break;
                    }
                }
            }
            return;
        }

        if (recovered) {
            List<PerformanceAction> revertOrder = actions.stream()
                    .sorted(Comparator.comparingInt(PerformanceAction::getPriority))
                    .toList();
            for (PerformanceAction action : revertOrder) {
                ActionState state = states.get(action.getName());
                if (state != null && state.active && cooldownElapsed(action, state)) {
                    if (action.revert(context)) {
                        state.active = false;
                        state.lastChange = Instant.now();
                        plugin.getLogger().info("Action reverted: " + action.getName());
                        break;
                    }
                }
            }
        }
    }

    private boolean cooldownElapsed(PerformanceAction action, ActionState state) {
        if (state.lastChange == null) {
            return true;
        }
        long cooldownSeconds = Math.max(0, action.getCooldownSeconds());
        return Duration.between(state.lastChange, Instant.now()).getSeconds() >= cooldownSeconds;
    }

    private void tryRevert(PerformanceAction action) {
        ActionState state = states.get(action.getName());
        if (state != null && state.active) {
            if (action.revert(context)) {
                state.active = false;
                state.lastChange = Instant.now();
            }
        }
    }

    private static final class ActionState {
        private boolean active;
        private Instant lastChange;
    }
}
