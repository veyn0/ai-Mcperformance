package dev.veyno.aiMcperformance.metrics;

import com.sun.management.OperatingSystemMXBean;
import dev.veyno.aiMcperformance.config.PerformanceConfig;
import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class PterodactylMetricsService {
    private final Plugin plugin;
    private final PerformanceConfig config;
    private final AtomicReference<Double> cpuUsage = new AtomicReference<>(0.0);
    private Object client;
    private boolean available;
    private boolean useLocalFallback;
    private OperatingSystemMXBean operatingSystemMXBean;
    private BukkitTask refreshTask;

    public PterodactylMetricsService(Plugin plugin, PerformanceConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        operatingSystemMXBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        available = false;
        useLocalFallback = false;
        client = null;
        String priority = config.getPterodactylCpuPriority().toLowerCase(Locale.ROOT);
        if ("local-only".equals(priority)) {
            activateLocalFallback("Configured for local-only CPU monitoring.");
            return;
        }
        if (config.isPterodactylEnabled()) {
            try {
                Class<?> builderClass = Class.forName("com.mattmalec.pterodactyl4j.PteroBuilder");
                client = builderClass
                        .getMethod("createClient", String.class, String.class)
                        .invoke(null, config.getPterodactylPanelUrl(), config.getPterodactylApiKey());
                available = true;
            } catch (ReflectiveOperationException ex) {
                plugin.getLogger().warning("Pterodactyl4J not available, falling back to local CPU usage.");
                available = false;
            }
        }
        if (!available) {
            activateLocalFallback("Pterodactyl CPU monitoring unavailable.");
        }
    }

    public void schedule() {
        if (!available && !useLocalFallback) {
            return;
        }
        int refreshSeconds = Math.max(5, config.getPterodactylRefreshSeconds());
        stop();
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshCpuUsage, 20L, refreshSeconds * 20L);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public double getCpuUsage() {
        return cpuUsage.get();
    }

    private void refreshCpuUsage() {
        if (useLocalFallback) {
            cpuUsage.set(fetchLocalCpuUsage());
            return;
        }
        if (!available || client == null) {
            return;
        }
        CompletableFuture
                .supplyAsync(this::fetchPterodactylCpuUsage)
                .thenAccept(cpuUsage::set)
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to fetch CPU usage: " + ex.getMessage());
                    activateLocalFallback("Failed to fetch Pterodactyl CPU usage.");
                    return null;
                });
    }

    private double fetchPterodactylCpuUsage() {
        try {
            Object retrieved = client.getClass()
                    .getMethod("retrieveServerByIdentifier", String.class)
                    .invoke(client, config.getPterodactylServerIdentifier());
            Object server = retrieved.getClass().getMethod("execute").invoke(retrieved);
            Object utilizationAction = server.getClass().getMethod("retrieveUtilization").invoke(server);
            Object utilization = utilizationAction.getClass().getMethod("execute").invoke(utilizationAction);
            Object cpu = utilization.getClass().getMethod("getCpu").invoke(utilization);
            return cpu instanceof Number ? ((Number) cpu).doubleValue() : 0.0;
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Pterodactyl4J error: " + ex.getMessage());
            activateLocalFallback("Pterodactyl API error.");
            return 0.0;
        }
    }

    private double fetchLocalCpuUsage() {
        if (operatingSystemMXBean == null) {
            return 0.0;
        }
        double load = operatingSystemMXBean.getProcessCpuLoad();
        if (load < 0.0) {
            load = operatingSystemMXBean.getSystemCpuLoad();
        }
        if (load < 0.0) {
            return 0.0;
        }
        return Math.min(100.0, Math.max(0.0, load * 100.0));
    }

    private void activateLocalFallback(String reason) {
        if (useLocalFallback) {
            return;
        }
        if (operatingSystemMXBean == null) {
            plugin.getLogger().warning("Local CPU metrics unavailable: OperatingSystemMXBean not found.");
            return;
        }
        useLocalFallback = true;
        available = false;
        plugin.getLogger().info(reason);
    }
}
