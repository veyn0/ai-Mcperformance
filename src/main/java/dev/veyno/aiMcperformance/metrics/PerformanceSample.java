package dev.veyno.aiMcperformance.metrics;

public record PerformanceSample(
        double mspt,
        double tps,
        int entities,
        int chunks,
        long usedRamBytes,
        double cpuUsagePercent
) {
}
