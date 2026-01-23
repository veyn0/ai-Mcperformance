package dev.veyno.aiMcperformance.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.SpawnCategory;

import java.util.EnumMap;
import java.util.Map;

public class PerformanceConfig {
    private final FileConfiguration config;

    public PerformanceConfig(FileConfiguration config) {
        this.config = config;
    }

    public int getSampleIntervalSeconds() {
        return config.getInt("sampling.interval-seconds", 1);
    }

    public int getMaxSampleWindowSeconds() {
        return config.getInt("sampling.max-window-seconds", 300);
    }

    public int getLongTermSampleWindowSeconds() {
        int seconds = config.getInt("sampling.long-window-seconds", 172800);
        int min = 86400;
        int max = 259200;
        return Math.max(min, Math.min(max, seconds));
    }

    public String getStorageType() {
        return config.getString("storage.type", "csv");
    }

    public String getStoragePath() {
        return config.getString("storage.path", "samples/metrics.csv");
    }

    public int getStorageFlushIntervalSeconds() {
        return config.getInt("storage.flush-interval-seconds", 30);
    }

    public boolean isStorageRestoreOnStartEnabled() {
        return config.getBoolean("storage.restore-on-start", true);
    }

    public String getBossBarTitleFormat() {
        return config.getString("bossbar.title-format", "{metric}: {values}");
    }

    public boolean isBossBarStatsEnabled() {
        return config.getBoolean("bossbar.use-rollup-stats", false);
    }

    public int getReportWindowSeconds() {
        return config.getInt("report.window-seconds", 300);
    }

    public double getReportSpikeMsptThreshold() {
        return config.getDouble("report.spike-mspt", 50.0);
    }

    public double getReportCorrelationThreshold() {
        return config.getDouble("report.correlation-threshold", 0.5);
    }

    public boolean isPterodactylEnabled() {
        return config.getBoolean("pterodactyl.enabled", false);
    }

    public String getPterodactylPanelUrl() {
        return config.getString("pterodactyl.panel-url", "");
    }

    public String getPterodactylApiKey() {
        return config.getString("pterodactyl.api-key", "");
    }

    public String getPterodactylServerIdentifier() {
        return config.getString("pterodactyl.server-identifier", "");
    }

    public int getPterodactylRefreshSeconds() {
        return config.getInt("pterodactyl.refresh-seconds", 15);
    }

    public boolean isDynamicViewDistanceEnabled() {
        return config.getBoolean("view-distance.enabled", true);
    }

    public int getViewDistanceMin() {
        return config.getInt("view-distance.min", 4);
    }

    public int getViewDistanceMax() {
        return config.getInt("view-distance.max", 12);
    }

    public double getViewDistanceTargetMspt() {
        return config.getDouble("view-distance.target-mspt", 40.0);
    }

    public double getViewDistanceEwmaAlpha() {
        return config.getDouble("view-distance.ewma-alpha", 0.3);
    }

    public int getViewDistanceMaxAdjustPerMinute() {
        return config.getInt("view-distance.max-adjust-per-minute", 6);
    }

    public int getViewDistanceIncreaseStableSeconds() {
        return config.getInt("view-distance.increase-stable-seconds", 30);
    }

    public int getViewDistanceSampleWindowSeconds() {
        return config.getInt("view-distance.sample-window-seconds", 60);
    }

    public int getViewDistanceCheckIntervalSeconds() {
        return config.getInt("view-distance.check-interval-seconds", 15);
    }

    public int getViewDistanceCooldownSeconds() {
        return config.getInt("view-distance.cooldown-seconds", 60);
    }

    public boolean isActionEngineEnabled() {
        return config.getBoolean("actions.enabled", true);
    }

    public int getActionEngineCheckIntervalSeconds() {
        return config.getInt("actions.check-interval-seconds", 15);
    }

    public int getActionEngineSampleWindowSeconds() {
        return config.getInt("actions.sample-window-seconds", 30);
    }

    public double getActionMsptThreshold() {
        return config.getDouble("actions.thresholds.mspt", 45.0);
    }

    public double getActionCpuThreshold() {
        return config.getDouble("actions.thresholds.cpu", 85.0);
    }

    public int getActionEntityThreshold() {
        return config.getInt("actions.thresholds.entities", 2500);
    }

    public double getActionMsptRecovery() {
        return config.getDouble("actions.recovery.mspt", 38.0);
    }

    public double getActionCpuRecovery() {
        return config.getDouble("actions.recovery.cpu", 70.0);
    }

    public int getActionEntityRecovery() {
        return config.getInt("actions.recovery.entities", 2000);
    }

    public int getSimulationDistanceActionPriority() {
        return config.getInt("actions.simulation-distance.priority", 30);
    }

    public int getSimulationDistanceActionCooldownSeconds() {
        return config.getInt("actions.simulation-distance.cooldown-seconds", 120);
    }

    public int getSimulationDistanceActionTarget() {
        return config.getInt("actions.simulation-distance.target", 5);
    }

    public int getSimulationDistanceActionMin() {
        return config.getInt("actions.simulation-distance.min", 4);
    }

    public int getSimulationDistanceActionMax() {
        return config.getInt("actions.simulation-distance.max", 10);
    }

    public int getEntityActivationRangeActionPriority() {
        return config.getInt("actions.entity-activation-range.priority", 20);
    }

    public int getEntityActivationRangeActionCooldownSeconds() {
        return config.getInt("actions.entity-activation-range.cooldown-seconds", 180);
    }

    public int getEntityActivationRangeActionTarget() {
        return config.getInt("actions.entity-activation-range.target", 16);
    }

    public int getMobCapsActionPriority() {
        return config.getInt("actions.mob-caps.priority", 10);
    }

    public int getMobCapsActionCooldownSeconds() {
        return config.getInt("actions.mob-caps.cooldown-seconds", 180);
    }

    public Map<SpawnCategory, Integer> getMobCapsLimits() {
        Map<SpawnCategory, Integer> limits = new EnumMap<>(SpawnCategory.class);
        var section = config.getConfigurationSection("actions.mob-caps.limits");
        if (section == null) {
            return limits;
        }
        for (String key : section.getKeys(false)) {
            SpawnCategory category = parseSpawnCategory(key);
            if (category != null) {
                limits.put(category, section.getInt(key));
            }
        }
        return limits;
    }

    private SpawnCategory parseSpawnCategory(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim().toUpperCase().replace('-', '_');
        try {
            return SpawnCategory.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
