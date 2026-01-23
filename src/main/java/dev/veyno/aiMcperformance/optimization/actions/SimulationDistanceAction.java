package dev.veyno.aiMcperformance.optimization.actions;

import dev.veyno.aiMcperformance.config.PerformanceConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class SimulationDistanceAction implements PerformanceAction {
    private final PerformanceConfig config;
    private final Map<UUID, Integer> previousDistances = new HashMap<>();

    public SimulationDistanceAction(PerformanceConfig config) {
        this.config = config;
    }

    @Override
    public String getName() {
        return "simulation-distance";
    }

    @Override
    public int getPriority() {
        return config.getSimulationDistanceActionPriority();
    }

    @Override
    public long getCooldownSeconds() {
        return config.getSimulationDistanceActionCooldownSeconds();
    }

    @Override
    public boolean apply(ActionContext context) {
        int target = config.getSimulationDistanceActionTarget();
        int min = config.getSimulationDistanceActionMin();
        int max = config.getSimulationDistanceActionMax();
        int clampedTarget = Math.max(min, Math.min(max, target));
        boolean changed = false;
        for (World world : Bukkit.getWorlds()) {
            int current = world.getSimulationDistance();
            int next = Math.min(current, clampedTarget);
            if (next != current) {
                previousDistances.put(world.getUID(), current);
                world.setSimulationDistance(next);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean revert(ActionContext context) {
        if (previousDistances.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (World world : Bukkit.getWorlds()) {
            Integer previous = previousDistances.get(world.getUID());
            if (previous != null) {
                world.setSimulationDistance(previous);
                changed = true;
            }
        }
        previousDistances.clear();
        return changed;
    }
}
