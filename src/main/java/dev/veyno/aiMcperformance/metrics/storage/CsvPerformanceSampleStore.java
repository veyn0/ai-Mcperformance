package dev.veyno.aiMcperformance.metrics.storage;

import dev.veyno.aiMcperformance.metrics.PerformanceSample;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class CsvPerformanceSampleStore implements PerformanceSampleStore {
    private static final String HEADER = "timestamp,mspt,tps,entities,chunks,usedRamBytes,cpuUsagePercent,viewDistance,players";
    private static final int COLUMN_COUNT = 9;

    private final Plugin plugin;
    private final Logger logger;
    private final Path filePath;
    private final long flushIntervalMillis;
    private final Queue<PerformanceSample> pendingSamples = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    private final AtomicLong lastFlushMillis = new AtomicLong(0L);

    public CsvPerformanceSampleStore(Plugin plugin, Path filePath, int flushIntervalSeconds) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.filePath = filePath;
        this.flushIntervalMillis = Math.max(1, flushIntervalSeconds) * 1000L;
    }

    @Override
    public void addSample(PerformanceSample sample) {
        if (sample != null) {
            pendingSamples.add(sample);
        }
    }

    @Override
    public void requestFlush() {
        scheduleFlush(false);
    }

    @Override
    public void flushNowAsync() {
        scheduleFlush(true);
    }

    @Override
    public List<PerformanceSample> loadSamples() {
        if (!Files.exists(filePath)) {
            return List.of();
        }
        List<PerformanceSample> result = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("timestamp")) {
                    continue;
                }
                PerformanceSample sample = parseLine(line);
                if (sample != null) {
                    result.add(sample);
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to read samples from " + filePath, e);
        }
        return result;
    }

    private void scheduleFlush(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastFlushMillis.get() < flushIntervalMillis) {
            return;
        }
        if (!flushScheduled.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                flushPending();
                lastFlushMillis.set(System.currentTimeMillis());
            } finally {
                flushScheduled.set(false);
            }
        });
    }

    private void flushPending() {
        List<PerformanceSample> snapshot = drainPending();
        if (snapshot.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(filePath.getParent());
            boolean writeHeader = Files.notExists(filePath);
            try (var writer = Files.newBufferedWriter(
                    filePath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            )) {
                if (writeHeader) {
                    writer.write(HEADER);
                    writer.newLine();
                }
                for (PerformanceSample sample : snapshot) {
                    writer.write(formatLine(sample));
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to write samples to " + filePath, e);
        }
    }

    private List<PerformanceSample> drainPending() {
        List<PerformanceSample> drained = new ArrayList<>();
        PerformanceSample sample;
        while ((sample = pendingSamples.poll()) != null) {
            drained.add(sample);
        }
        return drained;
    }

    private String formatLine(PerformanceSample sample) {
        return String.join(",",
                DateTimeFormatter.ISO_INSTANT.format(sample.timestamp()),
                Double.toString(sample.mspt()),
                Double.toString(sample.tps()),
                Integer.toString(sample.entities()),
                Integer.toString(sample.chunks()),
                Long.toString(sample.usedRamBytes()),
                Double.toString(sample.cpuUsagePercent()),
                Integer.toString(sample.viewDistance()),
                Integer.toString(sample.players())
        );
    }

    private PerformanceSample parseLine(String line) {
        String[] parts = line.split(",", -1);
        if (parts.length < COLUMN_COUNT) {
            return null;
        }
        try {
            return new PerformanceSample(
                    Instant.parse(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]),
                    Long.parseLong(parts[5]),
                    Double.parseDouble(parts[6]),
                    Integer.parseInt(parts[7]),
                    Integer.parseInt(parts[8])
            );
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "Skipping invalid sample line: " + line, e);
            return null;
        }
    }
}
