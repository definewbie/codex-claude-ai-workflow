package dev.dual.server;

import java.util.Map;

final class SchedulerPolicy {
    private static final String RUNNING = "RUNNING";
    private static final String WAITING_FOR_RECOVERY = "WAITING_FOR_RECOVERY";
    private static final String RECOVERABLE_AGENT_RUNTIME = "recoverable_agent_runtime";
    private static final String RETRY_AFTER_EXTERNAL_FIX = "RETRY_AFTER_EXTERNAL_FIX";

    SchedulerDecision decide(String workflowStatus, boolean autoRecover, Map<String, Object> recoveryAssessment) {
        if (RUNNING.equals(workflowStatus)) {
            return SchedulerDecision.resume();
        }
        if (autoRecover && WAITING_FOR_RECOVERY.equals(workflowStatus)) {
            return recoveryDecision(recoveryAssessment);
        }
        return SchedulerDecision.skip(workflowStatus);
    }

    private SchedulerDecision recoveryDecision(Map<String, Object> assessment) {
        var classification = text(assessment.get("classification"));
        var readiness = text(assessment.get("retryReadiness"));
        if (RECOVERABLE_AGENT_RUNTIME.equals(classification) && RETRY_AFTER_EXTERNAL_FIX.equals(readiness)) {
            return SchedulerDecision.autoRecover(classification, readiness);
        }
        return SchedulerDecision.skip(recoverySkipReason(classification, readiness));
    }

    private static String recoverySkipReason(String classification, String readiness) {
        if (classification.isBlank() && readiness.isBlank()) {
            return "recovery:not_assessed";
        }
        return "recovery:" + classification + "/" + readiness;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
