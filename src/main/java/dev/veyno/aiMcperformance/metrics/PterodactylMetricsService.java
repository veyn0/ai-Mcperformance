package dev.veyno.aiMcperformance.metrics;

import dev.veyno.aiMcperformance.config.PerformanceConfig;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class PterodactylMetricsService {
    private final Plugin plugin;
    private final PerformanceConfig config;
    private final AtomicReference<Double> cpuUsage = new AtomicReference<>(0.0);
    private Object client;
    private boolean available;

    public PterodactylMetricsService(Plugin plugin, PerformanceConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        if (!config.isPterodactylEnabled()) {
            return;
        }
        try {
            Class<?> builderClass = Class.forName("com.mattmalec.pterodactyl4j.PteroBuilder");
            client = builderClass
                    .getMethod("createClient", String.class, String.class)
                    .invoke(null, config.getPterodactylPanelUrl(), config.getPterodactylApiKey());
            available = true;
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("Pterodactyl4J not available, CPU usage will be disabled.");
            available = false;
        }
    }

    public void schedule() {
        if (!available) {
            return;
        }
        int refreshSeconds = Math.max(5, config.getPterodactylRefreshSeconds());
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshCpuUsage, 20L, refreshSeconds * 20L);
    }

    public double getCpuUsage() {
        return cpuUsage.get();
    }

    private void refreshCpuUsage() {
        if (!available || client == null) {
            return;
        }
        CompletableFuture
                .supplyAsync(this::fetchCpuUsage)
                .thenAccept(cpuUsage::set)
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to fetch CPU usage: " + ex.getMessage());
                    return null;
                });
    }

    private double fetchCpuUsage() {
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
            return 0.0;
        }
    }
}
