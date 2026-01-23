package dev.veyno.aiMcperformance.optimization.actions;

import dev.veyno.aiMcperformance.config.PerformanceConfig;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.SpawnCategory;

public class MobCapsAction implements PerformanceAction {
    private final PerformanceConfig config;
    private final Map<UUID, Map<SpawnCategory, Integer>> previousCaps = new HashMap<>();

    public MobCapsAction(PerformanceConfig config) {
        this.config = config;
    }

    @Override
    public String getName() {
        return "mob-caps";
    }

    @Override
    public int getPriority() {
        return config.getMobCapsActionPriority();
    }

    @Override
    public long getCooldownSeconds() {
        return config.getMobCapsActionCooldownSeconds();
    }

    @Override
    public boolean apply(ActionContext context) {
        Map<SpawnCategory, Integer> limits = config.getMobCapsLimits();
        if (limits.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (World world : Bukkit.getWorlds()) {
            Map<SpawnCategory, Integer> previous = previousCaps.computeIfAbsent(world.getUID(), key -> new EnumMap<>(SpawnCategory.class));
            for (Map.Entry<SpawnCategory, Integer> entry : limits.entrySet()) {
                SpawnCategory category = entry.getKey();
                int target = entry.getValue();
                int current = world.getSpawnLimit(category);
                if (current > target) {
                    previous.putIfAbsent(category, current);
                    world.setSpawnLimit(category, target);
                    changed = true;
                }
            }
        }
        return changed;
    }

    @Override
    public boolean revert(ActionContext context) {
        if (previousCaps.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (World world : Bukkit.getWorlds()) {
            Map<SpawnCategory, Integer> previous = previousCaps.get(world.getUID());
            if (previous == null || previous.isEmpty()) {
                continue;
            }
            for (Map.Entry<SpawnCategory, Integer> entry : previous.entrySet()) {
                world.setSpawnLimit(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        previousCaps.clear();
        return changed;
    }
}
