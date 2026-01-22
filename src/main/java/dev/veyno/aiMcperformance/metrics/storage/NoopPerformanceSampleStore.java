package dev.veyno.aiMcperformance.metrics.storage;

import dev.veyno.aiMcperformance.metrics.PerformanceSample;
import java.util.List;

public class NoopPerformanceSampleStore implements PerformanceSampleStore {
    @Override
    public void addSample(PerformanceSample sample) {
    }

    @Override
    public void requestFlush() {
    }

    @Override
    public void flushNowAsync() {
    }

    @Override
    public List<PerformanceSample> loadSamples() {
        return List.of();
    }
}
