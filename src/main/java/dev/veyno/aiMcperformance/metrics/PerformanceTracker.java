package dev.veyno.aiMcperformance.metrics;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class PerformanceTracker {
    private final Deque<PerformanceSample> samples = new ArrayDeque<>();
    private final int maxSamples;

    public PerformanceTracker(int maxSamples) {
        this.maxSamples = Math.max(1, maxSamples);
    }

    public void addSample(PerformanceSample sample) {
        samples.addLast(sample);
        while (samples.size() > maxSamples) {
            samples.removeFirst();
        }
    }

    public List<PerformanceSample> getLastSamples(int count) {
        int target = Math.max(0, count);
        if (samples.isEmpty() || target <= 0) {
            return List.of();
        }
        int skip = Math.max(0, samples.size() - target);
        return samples.stream().skip(skip).toList();
    }

    public double averageMspt(int seconds) {
        return average(samplesForSeconds(seconds).stream().mapToDouble(PerformanceSample::mspt));
    }

    public double averageTps(int seconds) {
        return average(samplesForSeconds(seconds).stream().mapToDouble(PerformanceSample::tps));
    }

    public double averageCpu(int seconds) {
        return average(samplesForSeconds(seconds).stream().mapToDouble(PerformanceSample::cpuUsagePercent));
    }

    public double averageRamBytes(int seconds) {
        return average(samplesForSeconds(seconds).stream().mapToDouble(PerformanceSample::usedRamBytes));
    }

    public double averageEntities(int seconds) {
        return average(samplesForSeconds(seconds).stream().mapToDouble(PerformanceSample::entities));
    }

    public double averageChunks(int seconds) {
        return average(samplesForSeconds(seconds).stream().mapToDouble(PerformanceSample::chunks));
    }

    private List<PerformanceSample> samplesForSeconds(int seconds) {
        return getLastSamples(seconds);
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
