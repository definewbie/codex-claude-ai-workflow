package dev.dual.server;

record SchedulerDecision(
        Action action,
        String reason,
        String recoveryClassification,
        String retryReadiness
) {
    enum Action {
        RESUME,
        AUTO_RECOVER,
        SKIP
    }

    static SchedulerDecision resume() {
        return new SchedulerDecision(Action.RESUME, "", "", "");
    }

    static SchedulerDecision autoRecover(String recoveryClassification, String retryReadiness) {
        return new SchedulerDecision(Action.AUTO_RECOVER, "", recoveryClassification, retryReadiness);
    }

    static SchedulerDecision skip(String reason) {
        return new SchedulerDecision(Action.SKIP, reason, "", "");
    }
}
