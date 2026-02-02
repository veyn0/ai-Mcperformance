package dev.veyno.aiMcperformance.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class SchedulerUtil {
    private static final boolean FOLIA = "Folia".equalsIgnoreCase(Bukkit.getServer().getName());

    private SchedulerUtil() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static ScheduledTask runAtFixedRate(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> task.run(),
                delayTicks,
                periodTicks
        );
    }

    public static ScheduledTask runDelayed(Plugin plugin, Runnable task, long delayTicks) {
        return Bukkit.getGlobalRegionScheduler().runDelayed(
                plugin,
                scheduledTask -> task.run(),
                delayTicks
        );
    }

    public static ScheduledTask runAsync(Plugin plugin, Runnable task) {
        return Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    public static ScheduledTask runAsyncAtFixedRate(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> task.run(),
                ticksToMillis(delayTicks),
                ticksToMillis(periodTicks),
                TimeUnit.MILLISECONDS
        );
    }

    public static void runGlobal(Plugin plugin, Runnable task) {
        if (!FOLIA) {
            task.run();
            return;
        }
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    public static void runOnWorld(Plugin plugin, World world, Runnable task) {
        if (!FOLIA) {
            task.run();
            return;
        }
        Location location = world.getSpawnLocation();
        Bukkit.getRegionScheduler().execute(plugin, location, task);
    }

    public static void runOnLocation(Plugin plugin, Location location, Runnable task) {
        if (!FOLIA) {
            task.run();
            return;
        }
        Bukkit.getRegionScheduler().execute(plugin, location, task);
    }

    public static void runOnPlayer(Plugin plugin, Player player, Runnable task) {
        if (!FOLIA) {
            task.run();
            return;
        }
        player.getScheduler().execute(plugin, task, () -> {
        }, 1L);
    }

    private static long ticksToMillis(long ticks) {
        return Math.max(0L, ticks) * 50L;
    }
}
