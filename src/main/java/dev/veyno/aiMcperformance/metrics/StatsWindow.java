package dev.veyno.aiMcperformance.metrics;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;

public record StatsWindow(
        int sampleCount,
        double average,
        double median,
        double p90,
        double p95,
        double variance,
        double ewma,
        double slope
) {
    public static final double DEFAULT_EWMA_ALPHA = 0.3;

    public static StatsWindow fromSamples(
            List<PerformanceSample> samples,
            ToDoubleFunction<PerformanceSample> extractor
    ) {
        if (samples == null || samples.isEmpty()) {
            return new StatsWindow(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        int count = samples.size();
        double[] values = new double[count];
        double sum = 0.0;
        for (int i = 0; i < count; i++) {
            double value = extractor.applyAsDouble(samples.get(i));
            values[i] = value;
            sum += value;
        }
        double average = sum / count;
        double variance = variance(values, average);
        double ewma = ewma(values, DEFAULT_EWMA_ALPHA);
        double slope = slope(samples, extractor);
        double[] sorted = Arrays.copyOf(values, count);
        Arrays.sort(sorted);
        double median = percentile(sorted, 0.5);
        double p90 = percentile(sorted, 0.90);
        double p95 = percentile(sorted, 0.95);
        return new StatsWindow(count, average, median, p90, p95, variance, ewma, slope);
    }

    public boolean hasData() {
        return sampleCount > 0;
    }

    public static double correlation(
            List<PerformanceSample> samples,
            ToDoubleFunction<PerformanceSample> xExtractor,
            ToDoubleFunction<PerformanceSample> yExtractor
    ) {
        if (samples == null || samples.size() < 2) {
            return 0.0;
        }
        int count = samples.size();
        double sumX = 0.0;
        double sumY = 0.0;
        double sumX2 = 0.0;
        double sumY2 = 0.0;
        double sumXY = 0.0;
        for (PerformanceSample sample : samples) {
            double x = xExtractor.applyAsDouble(sample);
            double y = yExtractor.applyAsDouble(sample);
            sumX += x;
            sumY += y;
            sumX2 += x * x;
            sumY2 += y * y;
            sumXY += x * y;
        }
        double numerator = count * sumXY - sumX * sumY;
        double denomX = count * sumX2 - sumX * sumX;
        double denomY = count * sumY2 - sumY * sumY;
        double denominator = Math.sqrt(denomX * denomY);
        if (denominator == 0.0) {
            return 0.0;
        }
        return numerator / denominator;
    }

    private static double variance(double[] values, double mean) {
        if (values.length == 0) {
            return 0.0;
        }
        double sumSq = 0.0;
        for (double value : values) {
            double diff = value - mean;
            sumSq += diff * diff;
        }
        return Math.max(0.0, sumSq / values.length);
    }

    private static double ewma(double[] values, double alpha) {
        if (values.length == 0) {
            return 0.0;
        }
        double result = values[0];
        for (int i = 1; i < values.length; i++) {
            result = alpha * values[i] + (1.0 - alpha) * result;
        }
        return result;
    }

    private static double slope(List<PerformanceSample> samples, ToDoubleFunction<PerformanceSample> extractor) {
        if (samples.size() < 2) {
            return 0.0;
        }
        PerformanceSample first = samples.get(0);
        double sumX = 0.0;
        double sumY = 0.0;
        double sumXY = 0.0;
        double sumX2 = 0.0;
        for (PerformanceSample sample : samples) {
            double x = Duration.between(first.timestamp(), sample.timestamp()).toMillis() / 1000.0;
            double y = extractor.applyAsDouble(sample);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        int count = samples.size();
        double denominator = count * sumX2 - sumX * sumX;
        if (denominator == 0.0) {
            return 0.0;
        }
        return (count * sumXY - sumX * sumY) / denominator;
    }

    private static double percentile(double[] sorted, double percentile) {
        if (sorted.length == 0) {
            return 0.0;
        }
        if (sorted.length == 1) {
            return sorted[0];
        }
        double index = percentile * (sorted.length - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sorted[lower];
        }
        double weight = index - lower;
        return sorted[lower] * (1.0 - weight) + sorted[upper] * weight;
    }
}
