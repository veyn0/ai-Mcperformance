package dev.veyno.aiMcperformance.metrics;

public enum MetricType {
    MSPT("MSPT"),
    TPS("TPS"),
    ENTITIES("Entities"),
    RAM("RAM"),
    CPU("CPU"),
    CHUNKS("Chunks"),
    VIEW_DISTANCE("View Distance");

    private final String displayName;

    MetricType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
