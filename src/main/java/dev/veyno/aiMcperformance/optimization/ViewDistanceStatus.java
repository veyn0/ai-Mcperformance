package dev.veyno.aiMcperformance.optimization;

public record ViewDistanceStatus(
        int currentViewDistance,
        boolean increaseRecommended,
        boolean decreaseRecommended,
        long cooldownRemainingSeconds,
        int predictedViewDistanceIncrease,
        int predictedChunkIncrease
) {
}
