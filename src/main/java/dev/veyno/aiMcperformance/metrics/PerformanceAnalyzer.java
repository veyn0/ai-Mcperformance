package dev.veyno.aiMcperformance.metrics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class PerformanceAnalyzer {
    public AnalysisResult analyze(
            List<PerformanceSample> samples,
            int windowSeconds,
            double spikeMsptThreshold,
            double correlationThreshold,
            int peakWindowSeconds,
            int peakWindowCount,
            double cpuThreshold,
            int entityThreshold
    ) {
        List<PerformanceSample> windowSamples = samples == null ? List.of() : samples;
        StatsWindow msptStats = StatsWindow.fromSamples(windowSamples, PerformanceSample::mspt);
        long spikeCount = windowSamples.stream()
                .filter(sample -> sample.mspt() >= spikeMsptThreshold)
                .count();
        Correlations correlations = new Correlations(
                StatsWindow.correlation(windowSamples, PerformanceSample::mspt, sample -> sample.entities()),
                StatsWindow.correlation(windowSamples, PerformanceSample::mspt, sample -> sample.chunks()),
                StatsWindow.correlation(windowSamples, PerformanceSample::mspt, sample -> sample.players())
        );
        List<PeakWindow> peakWindows = computePeakWindows(windowSamples, peakWindowSeconds, peakWindowCount);
        List<String> bottlenecks = identifyBottlenecks(
                windowSamples,
                msptStats,
                correlations,
                spikeMsptThreshold,
                correlationThreshold,
                cpuThreshold,
                entityThreshold,
                windowSeconds
        );
        return new AnalysisResult(msptStats, spikeCount, correlations, peakWindows, bottlenecks);
    }

    private List<PeakWindow> computePeakWindows(List<PerformanceSample> samples, int windowSeconds, int topN) {
        if (samples == null || samples.isEmpty()) {
            return List.of();
        }
        int effectiveWindowSeconds = Math.max(5, windowSeconds);
        int effectiveTopN = Math.max(1, topN);
        List<PerformanceSample> sorted = samples.stream()
                .sorted(Comparator.comparing(PerformanceSample::timestamp))
                .toList();
        List<PeakWindow> windows = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            PerformanceSample startSample = sorted.get(i);
            Instant windowEnd = startSample.timestamp().plusSeconds(effectiveWindowSeconds);
            List<PerformanceSample> windowSamples = new ArrayList<>();
            for (int j = i; j < sorted.size(); j++) {
                PerformanceSample sample = sorted.get(j);
                if (sample.timestamp().isAfter(windowEnd)) {
                    break;
                }
                windowSamples.add(sample);
            }
            if (windowSamples.size() < 2) {
                continue;
            }
            StatsWindow msptStats = StatsWindow.fromSamples(windowSamples, PerformanceSample::mspt);
            windows.add(new PeakWindow(
                    startSample.timestamp(),
                    windowEnd,
                    msptStats,
                    average(windowSamples, PerformanceSample::entities),
                    average(windowSamples, PerformanceSample::chunks),
                    average(windowSamples, PerformanceSample::players)
            ));
        }
        windows.sort(Comparator.<PeakWindow>comparingDouble(window -> window.msptStats().p95())
                .thenComparingDouble(window -> window.msptStats().average())
                .reversed());
        if (windows.size() <= effectiveTopN) {
            return windows;
        }
        return windows.subList(0, effectiveTopN);
    }

    private List<String> identifyBottlenecks(
            List<PerformanceSample> samples,
            StatsWindow msptStats,
            Correlations correlations,
            double spikeMsptThreshold,
            double correlationThreshold,
            double cpuThreshold,
            int entityThreshold,
            int windowSeconds
    ) {
        List<String> hints = new ArrayList<>();
        if (!msptStats.hasData()) {
            return hints;
        }
        if (msptStats.p95() >= spikeMsptThreshold) {
            hints.add("Hohe MSPT-Spitzen (P95 " + formatOneDecimal(msptStats.p95()) + "ms) im letzten "
                    + windowSeconds + "s-Fenster.");
        }
        if (Math.abs(correlations.entities()) >= correlationThreshold) {
            hints.add("MSPT korreliert mit Entities (r=" + formatTwoDecimal(correlations.entities()) + ").");
        }
        if (Math.abs(correlations.chunks()) >= correlationThreshold) {
            hints.add("MSPT korreliert mit Chunks (r=" + formatTwoDecimal(correlations.chunks()) + ").");
        }
        if (Math.abs(correlations.players()) >= correlationThreshold) {
            hints.add("MSPT korreliert mit Spielern (r=" + formatTwoDecimal(correlations.players()) + ").");
        }
        double avgCpu = average(samples, PerformanceSample::cpuUsagePercent);
        if (avgCpu >= cpuThreshold && cpuThreshold > 0.0) {
            hints.add("CPU-Auslastung hoch (Ø " + formatOneDecimal(avgCpu) + "%).");
        }
        double avgEntities = average(samples, PerformanceSample::entities);
        if (avgEntities >= entityThreshold && entityThreshold > 0) {
            hints.add("Viele Entities aktiv (Ø " + formatOneDecimal(avgEntities) + ").");
        }
        return hints;
    }

    private double average(List<PerformanceSample> samples, java.util.function.ToDoubleFunction<PerformanceSample> extractor) {
        if (samples == null || samples.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (PerformanceSample sample : samples) {
            sum += extractor.applyAsDouble(sample);
        }
        return sum / samples.size();
    }

    private String formatOneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private String formatTwoDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    public record Correlations(double entities, double chunks, double players) {
        public Correlations {
            entities = normalize(entities);
            chunks = normalize(chunks);
            players = normalize(players);
        }

        private static double normalize(double value) {
            if (Double.isFinite(value)) {
                return value;
            }
            return 0.0;
        }
    }

    public record PeakWindow(
            Instant start,
            Instant end,
            StatsWindow msptStats,
            double averageEntities,
            double averageChunks,
            double averagePlayers
    ) {
        public PeakWindow {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            Objects.requireNonNull(msptStats, "msptStats");
        }
    }

    public record AnalysisResult(
            StatsWindow msptStats,
            long spikeCount,
            Correlations correlations,
            List<PeakWindow> peakWindows,
            List<String> bottlenecks
    ) {
        public AnalysisResult {
            Objects.requireNonNull(msptStats, "msptStats");
            Objects.requireNonNull(correlations, "correlations");
            peakWindows = peakWindows == null ? List.of() : List.copyOf(peakWindows);
            bottlenecks = bottlenecks == null ? List.of() : List.copyOf(bottlenecks);
        }
    }
}
