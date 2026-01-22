package dev.veyno.aiMcperformance.config;

import org.bukkit.configuration.file.FileConfiguration;

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

    public int getViewDistanceStep() {
        return config.getInt("view-distance.step", 1);
    }

    public double getViewDistanceHighMspt() {
        return config.getDouble("view-distance.high-mspt", 50.0);
    }

    public double getViewDistanceLowMspt() {
        return config.getDouble("view-distance.low-mspt", 35.0);
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

    public int getViewDistanceMaxStepMultiplier() {
        return config.getInt("view-distance.max-step-multiplier", 4);
    }

    public double getViewDistanceOverageMsptPerStep() {
        return config.getDouble("view-distance.overage-mspt-per-step", 5.0);
    }

    public double getViewDistanceRapidMsptIncrease() {
        return config.getDouble("view-distance.rapid-mspt-increase", 15.0);
    }

    public int getViewDistanceRapidSampleWindowSeconds() {
        return config.getInt("view-distance.rapid-sample-window-seconds", 10);
    }

    public int getViewDistanceRapidBaselineWindowSeconds() {
        return config.getInt("view-distance.rapid-baseline-window-seconds", 60);
    }

    public int getViewDistanceRapidCooldownSeconds() {
        return config.getInt("view-distance.rapid-cooldown-seconds", 15);
    }
}
