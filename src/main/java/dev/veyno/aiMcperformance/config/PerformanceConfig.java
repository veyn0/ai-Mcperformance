package dev.veyno.aiMcperformance.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.SpawnCategory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class PerformanceConfig {
    private FileConfiguration config;

    public PerformanceConfig(FileConfiguration config) {
        this.config = config;
    }

    public void reload(FileConfiguration config) {
        this.config = config;
    }

    public boolean isSamplingEnabled() {
        return config.getBoolean("sampling.enabled", true);
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

    public boolean isStorageEnabled() {
        return config.getBoolean("storage.enabled", true);
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

    public boolean isBossBarEnabled() {
        return config.getBoolean("bossbar.enabled", true);
    }

    public boolean isBossBarStatsEnabled() {
        return config.getBoolean("bossbar.use-rollup-stats", false);
    }

    public boolean isStatusOverviewEnabled() {
        return config.getBoolean("status-overview.enabled", false);
    }

    public int getStatusOverviewIntervalMinutes() {
        return config.getInt("status-overview.interval-minutes", 5);
    }

    public boolean isStatusOverviewChatEnabled() {
        return config.getBoolean("status-overview.broadcast.chat", false);
    }

    public boolean isStatusOverviewConsoleEnabled() {
        return config.getBoolean("status-overview.broadcast.console", true);
    }

    public int getStatusOverviewSampleWindowSeconds() {
        return config.getInt("status-overview.sample-window-seconds", 60);
    }

    public List<String> getStatusOverviewLines() {
        List<String> lines = config.getStringList("status-overview.lines");
        return lines != null ? lines : List.of();
    }

    public int getReportWindowSeconds() {
        return config.getInt("report.window-seconds", 300);
    }

    public boolean isReportEnabled() {
        return config.getBoolean("report.enabled", true);
    }

    public double getReportSpikeMsptThreshold() {
        return config.getDouble("report.spike-mspt", 50.0);
    }

    public double getReportCorrelationThreshold() {
        return config.getDouble("report.correlation-threshold", 0.5);
    }

    public int getReportPeakWindowSeconds() {
        return config.getInt("report.peak-window-seconds", 30);
    }

    public int getReportPeakWindowCount() {
        return config.getInt("report.peak-window-count", 3);
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

    public String getPterodactylCpuPriority() {
        return config.getString("pterodactyl.cpu-priority", "pterodactyl-first");
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

    public boolean isTestModeEnabled() {
        return config.getBoolean("test-mode.enabled", false);
    }

    public int getTestModeDurationMinutes() {
        return config.getInt("test-mode.duration-minutes", 20);
    }

    public int getTestModeStepIntervalMinutes() {
        return config.getInt("test-mode.step-interval-minutes", 2);
    }

    public String getTestModeAction() {
        return config.getString("test-mode.action", "view-distance");
    }

    public int getTestModeValueStart() {
        return config.getInt("test-mode.value.start", 4);
    }

    public int getTestModeValueStep() {
        return config.getInt("test-mode.value.step", 1);
    }

    public int getTestModeValueMax() {
        return config.getInt("test-mode.value.max", 12);
    }

    public int getTestModeMobCapsStart() {
        return config.getInt("test-mode.mob-caps.start", 0);
    }

    public int getTestModeMobCapsStep() {
        return config.getInt("test-mode.mob-caps.step", 2);
    }

    public int getTestModeMobCapsMax() {
        return config.getInt("test-mode.mob-caps.max", 0);
    }

    public Map<SpawnCategory, Integer> getTestModeMobCapsBaseLimits() {
        Map<SpawnCategory, Integer> limits = new EnumMap<>(SpawnCategory.class);
        var section = config.getConfigurationSection("test-mode.mob-caps.base-limits");
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

    public Map<SpawnCategory, Integer> getTestModeMobCapsMaxLimits() {
        Map<SpawnCategory, Integer> limits = new EnumMap<>(SpawnCategory.class);
        var section = config.getConfigurationSection("test-mode.mob-caps.max-limits");
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

    public String getTestModeSummaryPath() {
        return config.getString("test-mode.output.summary-path", "tests/load-test-summary.csv");
    }

    public String getTestModeSamplePath() {
        return config.getString("test-mode.output.sample-path", "tests/load-test-samples.csv");
    }

    public boolean isTestModeWriteRawSamples() {
        return config.getBoolean("test-mode.output.write-raw-samples", true);
    }

    public boolean isTestModePlayerDistributionEnabled() {
        return config.getBoolean("test-mode.player-distribution.enabled", false);
    }

    public String getTestModePlayerDistributionMode() {
        return config.getString("test-mode.player-distribution.mode", "spread");
    }

    public int getTestModePlayerSpreadRadius() {
        return config.getInt("test-mode.player-distribution.spread-radius", 2000);
    }

    public int getTestModePlayerClusterRadius() {
        return config.getInt("test-mode.player-distribution.cluster-radius", 64);
    }

    public String getTestModePlayerDistributionWorld() {
        return config.getString("test-mode.player-distribution.world", "");
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

    public String getMessageReload() {
        return message("messages.reload", "AI-McPerformance-Konfiguration neu geladen.");
    }

    public String getMessageOnlyPlayer() {
        return message("messages.only-player", "Dieser Befehl kann nur im Spiel genutzt werden.");
    }

    public String getMessageUsageMonitor() {
        return message("messages.usage.monitor",
                "Verwendung: /performance monitor <mspt|tps|entities|ram|cpu|chunks|viewdistance> <on|off|toggle>");
    }

    public String getMessageUsageFeature() {
        return message("messages.usage.feature",
                "Oder: /performance feature <sampling|storage|bossbar|report|pterodactyl|viewdistance|actions> <on|off|toggle>");
    }

    public String getMessageUsageReload() {
        return message("messages.usage.reload", "Oder: /performance reload");
    }

    public String getMessageUsageTestMode() {
        return message("messages.usage.test",
                "Oder: /performance test <start|stop|status> [view-distance|simulation-distance|mob-caps]");
    }

    public String getMessageTestModeDisabled() {
        return message("messages.test.disabled", "Der Belastungstest-Modus ist deaktiviert.");
    }

    public String getMessageTestModeStarted() {
        return message("messages.test.started", "Belastungstest gestartet.");
    }

    public String getMessageTestModeStopped() {
        return message("messages.test.stopped", "Belastungstest beendet.");
    }

    public String getMessageTestModeAlreadyRunning() {
        return message("messages.test.already-running", "Belastungstest läuft bereits.");
    }

    public String getMessageTestModeNotRunning() {
        return message("messages.test.not-running", "Es läuft aktuell kein Belastungstest.");
    }

    public String getMessageTestModeInvalidAction() {
        return message("messages.test.invalid-action", "Unbekannte Test-Aktion: {action}");
    }

    public String getMessageTestModeStatus() {
        return message("messages.test.status",
                "Belastungstest: Aktion {action}, Step {step}/{total_steps}, Wert {value}, Start {started}, Ende {ends}");
    }

    public String getMessageBossBarDisabled() {
        return message("messages.bossbar.disabled", "BossBar-Monitoring ist derzeit deaktiviert.");
    }

    public String getMessageBossBarUnknownMetric() {
        return message("messages.bossbar.unknown-metric", "Unbekanntes Metric: {metric}");
    }

    public String getMessageBossBarToggle() {
        return message("messages.bossbar.toggle", "BossBar für {metric} {state}.");
    }

    public String getMessageFeatureUnknown() {
        return message("messages.feature.unknown", "Unbekanntes Feature: {feature}");
    }

    public String getMessageFeatureToggle() {
        return message("messages.feature.toggle", "Feature {feature} {state}.");
    }

    public String getMessageReportDisabled() {
        return message("messages.report.disabled", "Performance-Reports sind derzeit deaktiviert.");
    }

    public String getMessageReportEmpty() {
        return message("messages.report.empty", "Keine Daten für den Report verfügbar.");
    }

    public String getMessageReportHeader() {
        return message("messages.report.header", "Performance-Report ({window}s):");
    }

    public String getMessageReportMspt() {
        return message("messages.report.mspt",
                "MSPT Ø {mspt_avg}ms, P95 {mspt_p95}ms, Spikes {spike_count}");
    }

    public String getMessageReportCorrelation() {
        return message("messages.report.correlation",
                "Korrelation MSPT: Entities r={corr_entities}, Chunks r={corr_chunks}, Spieler r={corr_players}");
    }

    public String getMessageReportBottleneckNone() {
        return message("messages.report.bottlenecks.none", "Engpässe: keine auffälligen Indikatoren.");
    }

    public String getMessageReportBottleneckHeader() {
        return message("messages.report.bottlenecks.header", "Engpässe:");
    }

    public String getMessageReportBottleneckItem() {
        return message("messages.report.bottlenecks.item", " - {bottleneck}");
    }

    public String getMessageReportPeakHeader() {
        return message("messages.report.peaks.header", "Top-{count} Peak-Windows ({window}s):");
    }

    public String getMessageReportPeakItem() {
        return message("messages.report.peaks.item",
                " {index}) {start} - {end}: P95 {mspt_p95}ms, Ø {mspt_avg}ms, Ø Entities {entities}, Ø Chunks {chunks}, Ø Spieler {players}");
    }

    private String message(String path, String fallback) {
        return config.getString(path, fallback);
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
