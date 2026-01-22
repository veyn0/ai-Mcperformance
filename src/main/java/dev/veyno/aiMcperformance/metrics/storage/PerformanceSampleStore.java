package dev.veyno.aiMcperformance.metrics.storage;

import dev.veyno.aiMcperformance.metrics.PerformanceSample;
import java.util.List;

public interface PerformanceSampleStore {
    void addSample(PerformanceSample sample);

    void requestFlush();

    void flushNowAsync();

    List<PerformanceSample> loadSamples();
}
