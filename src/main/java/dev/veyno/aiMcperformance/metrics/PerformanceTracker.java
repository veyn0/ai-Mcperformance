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

    public synchronized void addSample(PerformanceSample sample) {
        samples.addLast(sample);
        Instant cutoff = sample.timestamp().minusSeconds(maxWindowSeconds);
        while (!samples.isEmpty() && samples.getFirst().timestamp().isBefore(cutoff)) {
            samples.removeFirst();
        }
    }

    public synchronized void restoreSamples(List<PerformanceSample> restoredSamples) {
        if (restoredSamples == null || restoredSamples.isEmpty()) {
            return;
        }
        restoredSamples.stream()
                .sorted(java.util.Comparator.comparing(PerformanceSample::timestamp))
                .forEach(this::addSample);
    }

    public synchronized List<PerformanceSample> getSamplesSinceSeconds(int seconds) {
        int targetSeconds = Math.max(1, seconds);
        Instant cutoff = Instant.now().minusSeconds(targetSeconds);
        return samples.stream()
                .filter(sample -> !sample.timestamp().isBefore(cutoff))
                .toList();
    }

    public synchronized List<PerformanceSample> getSamplesBetween(Instant start, Instant end) {
        if (start == null || end == null) {
            return List.of();
        }
        Instant normalizedStart = start.isAfter(end) ? end : start;
        Instant normalizedEnd = start.isAfter(end) ? start : end;
        return samples.stream()
                .filter(sample -> !sample.timestamp().isBefore(normalizedStart))
                .filter(sample -> !sample.timestamp().isAfter(normalizedEnd))
                .toList();
    }

    public synchronized PerformanceSample latestSample() {
        return samples.peekLast();
    }

    public synchronized MsptEwmaTrend msptEwmaTrend(int seconds, double alpha) {
        List<PerformanceSample> windowSamples = getSamplesSinceSeconds(seconds).stream()
                .sorted(java.util.Comparator.comparing(PerformanceSample::timestamp))
                .toList();
        if (windowSamples.isEmpty()) {
            return new MsptEwmaTrend(0.0, 0.0);
        }
        double clampedAlpha = Math.max(0.05, Math.min(1.0, alpha));
        double ewma = windowSamples.get(0).mspt();
        double firstEwma = ewma;
        Instant firstTimestamp = windowSamples.get(0).timestamp();
        Instant lastTimestamp = firstTimestamp;
        for (int i = 1; i < windowSamples.size(); i++) {
            PerformanceSample sample = windowSamples.get(i);
            ewma = clampedAlpha * sample.mspt() + (1.0 - clampedAlpha) * ewma;
            lastTimestamp = sample.timestamp();
        }
        double durationSeconds = Math.max(1.0, java.time.Duration.between(firstTimestamp, lastTimestamp).toMillis() / 1000.0);
        double trendPerSecond = (ewma - firstEwma) / durationSeconds;
        return new MsptEwmaTrend(ewma, trendPerSecond);
    }

    public synchronized StatsWindow statsWindow(int seconds, java.util.function.ToDoubleFunction<PerformanceSample> extractor) {
        List<PerformanceSample> windowSamples = getSamplesSinceSeconds(seconds);
        return StatsWindow.fromSamples(windowSamples, extractor);
    }

    public synchronized double averageMspt(int seconds) {
        return average(getSamplesSinceSeconds(seconds).stream().mapToDouble(PerformanceSample::mspt));
    }

    public synchronized double averageTps(int seconds) {
        return average(getSamplesSinceSeconds(seconds).stream().mapToDouble(PerformanceSample::tps));
    }

    public synchronized double averageCpu(int seconds) {
        return average(getSamplesSinceSeconds(seconds).stream().mapToDouble(PerformanceSample::cpuUsagePercent));
    }

    public synchronized double averageRamBytes(int seconds) {
        return average(getSamplesSinceSeconds(seconds).stream().mapToDouble(PerformanceSample::usedRamBytes));
    }

    public synchronized double averageEntities(int seconds) {
        return average(getSamplesSinceSeconds(seconds).stream().mapToDouble(PerformanceSample::entities));
    }

    public synchronized double averageChunks(int seconds) {
        return average(getSamplesSinceSeconds(seconds).stream().mapToDouble(PerformanceSample::chunks));
    }

    public synchronized double averageViewDistance(int seconds) {
        return average(getSamplesSinceSeconds(seconds).stream().mapToDouble(PerformanceSample::viewDistance));
    }

    public synchronized double averagePlayers(int seconds) {
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

    public record MsptEwmaTrend(double ewmaMspt, double trendPerSecond) {
    }
}
