package dev.veyno.aiMcperformance.optimization.actions;

import dev.veyno.aiMcperformance.config.PerformanceConfig;
import org.bukkit.plugin.Plugin;

public class ActionContext {
    private final Plugin plugin;
    private final PerformanceConfig config;

    public ActionContext(Plugin plugin, PerformanceConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public Plugin plugin() {
        return plugin;
    }

    public PerformanceConfig config() {
        return config;
    }
}
