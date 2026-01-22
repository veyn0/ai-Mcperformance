package dev.veyno.aiMcperformance.metrics;

import java.time.Instant;

public record PerformanceSample(
        Instant timestamp,
        double mspt,
        double tps,
        int entities,
        int chunks,
        long usedRamBytes,
        double cpuUsagePercent,
        int viewDistance,
        int players
) {
}
