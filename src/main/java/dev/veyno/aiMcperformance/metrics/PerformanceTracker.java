package dev.veyno.aiMcperformance.metrics;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class PerformanceTracker {
    private final Deque<PerformanceSample> samples = new ArrayDeque<>();
    private final int maxWindowSeconds;

    public PerformanceTracker(int maxWindowSeconds) {
        this.maxWindowSeconds = Math.max(1, maxWindowSeconds);
    }

    public void addSample(PerformanceSample sample) {
        samples.addLast(sample);
        Instant cutoff = sample.timestamp().minusSeconds(maxWindowSeconds);
        while (!samples.isEmpty() && samples.getFirst().timestamp().isBefore(cutoff)) {
            samples.removeFirst();
        }
    }

    public List<PerformanceSample> getSamplesSinceSeconds(int seconds) {
        int targetSeconds = Math.max(1, seconds);
        Instant cutoff = Instant.now().minusSeconds(targetSeconds);
        return samples.stream()
                .filter(sample -> !sample.timestamp().isBefore(cutoff))
                .toList();
    }

    public PerformanceSample latestSample() {
        return samples.peekLast();
    }

    public double averageMspt(int seconds) {
        return average(getSamplesSinceSeconds(seconds).stream().mapToDouble(PerformanceSample::mspt));
    }

    public double averageTps(int seconds) {
        return average(getSamplesSinceSeconds(seconds).stream().mapToDouble(PerformanceSample::tps));
    }

    public double averageCpu(int seconds) {
        return average(getSamplesSinceSeconds(seconds).stream().mapToDouble(PerformanceSample::cpuUsagePercent));
    }

    public double averageRamBytes(int seconds) {
        return average(getSamplesSinceSeconds(seconds).stream().mapToDouble(PerformanceSample::usedRamBytes));
    }

    public double averageEntities(int seconds) {
        return average(getSamplesSinceSeconds(seconds).stream().mapToDouble(PerformanceSample::entities));
    }

    public double averageChunks(int seconds) {
        return average(getSamplesSinceSeconds(seconds).stream().mapToDouble(PerformanceSample::chunks));
    }

    public double averageViewDistance(int seconds) {
        return average(getSamplesSinceSeconds(seconds).stream().mapToDouble(PerformanceSample::viewDistance));
    }

    public double averagePlayers(int seconds) {
        return average(getSamplesSinceSeconds(seconds).stream().mapToDouble(PerformanceSample::players));
    }

    private double average(java.util.stream.DoubleStream stream) {
        double[] stats = stream.toArray();
        if (stats.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : stats) {
            sum += value;
        }
        return sum / stats.length;
    }
}
