package dev.veyno.aiMcperformance.optimization.actions;

import dev.veyno.aiMcperformance.config.PerformanceConfig;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.SpawnCategory;

public class EntityActivationRangeAction implements PerformanceAction {
    private final PerformanceConfig config;
    private final Map<UUID, Map<SpawnCategory, Integer>> previousRanges = new HashMap<>();
    private Method getMethod;
    private Method setMethod;
    private boolean resolved;
    private boolean unsupportedLogged;

    public EntityActivationRangeAction(PerformanceConfig config) {
        this.config = config;
    }

    @Override
    public String getName() {
        return "entity-activation-range";
    }

    @Override
    public int getPriority() {
        return config.getEntityActivationRangeActionPriority();
    }

    @Override
    public long getCooldownSeconds() {
        return config.getEntityActivationRangeActionCooldownSeconds();
    }

    @Override
    public boolean apply(ActionContext context) {
        if (!resolveMethods()) {
            logUnsupported(context);
            return false;
        }
        int target = config.getEntityActivationRangeActionTarget();
        boolean changed = false;
        for (World world : Bukkit.getWorlds()) {
            Map<SpawnCategory, Integer> previous = previousRanges.computeIfAbsent(world.getUID(), key -> new EnumMap<>(SpawnCategory.class));
            for (SpawnCategory category : SpawnCategory.values()) {
                Integer current = invokeGet(world, category);
                if (current == null) {
                    continue;
                }
                if (current > target) {
                    previous.putIfAbsent(category, current);
                    invokeSet(world, category, target);
                    changed = true;
                }
            }
        }
        return changed;
    }

    @Override
    public boolean revert(ActionContext context) {
        if (previousRanges.isEmpty()) {
            return false;
        }
        if (!resolveMethods()) {
            logUnsupported(context);
            previousRanges.clear();
            return false;
        }
        boolean changed = false;
        for (World world : Bukkit.getWorlds()) {
            Map<SpawnCategory, Integer> previous = previousRanges.get(world.getUID());
            if (previous == null || previous.isEmpty()) {
                continue;
            }
            for (Map.Entry<SpawnCategory, Integer> entry : previous.entrySet()) {
                invokeSet(world, entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        previousRanges.clear();
        return changed;
    }

    private boolean resolveMethods() {
        if (resolved) {
            return getMethod != null && setMethod != null;
        }
        resolved = true;
        Optional<Method> get = resolveMethod("getEntityActivationRange", SpawnCategory.class);
        Optional<Method> set = resolveMethod("setEntityActivationRange", SpawnCategory.class, int.class);
        if (get.isPresent() && set.isPresent()) {
            getMethod = get.get();
            setMethod = set.get();
        }
        return getMethod != null && setMethod != null;
    }

    private Optional<Method> resolveMethod(String name, Class<?>... args) {
        try {
            return Optional.of(World.class.getMethod(name, args));
        } catch (NoSuchMethodException ignored) {
            return Optional.empty();
        }
    }

    private Integer invokeGet(World world, SpawnCategory category) {
        try {
            return (Integer) getMethod.invoke(world, category);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void invokeSet(World world, SpawnCategory category, int value) {
        try {
            setMethod.invoke(world, category, value);
        } catch (Exception ignored) {
            // Ignore failed updates to unsupported categories.
        }
    }

    private void logUnsupported(ActionContext context) {
        if (!unsupportedLogged) {
            context.plugin().getLogger().warning("Entity activation range adjustment is not supported on this server build.");
            unsupportedLogged = true;
        }
    }
}
