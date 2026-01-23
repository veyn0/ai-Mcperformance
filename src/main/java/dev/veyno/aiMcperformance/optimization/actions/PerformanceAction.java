package dev.veyno.aiMcperformance.optimization.actions;

public interface PerformanceAction {
    String getName();

    int getPriority();

    long getCooldownSeconds();

    boolean apply(ActionContext context);

    boolean revert(ActionContext context);
}
