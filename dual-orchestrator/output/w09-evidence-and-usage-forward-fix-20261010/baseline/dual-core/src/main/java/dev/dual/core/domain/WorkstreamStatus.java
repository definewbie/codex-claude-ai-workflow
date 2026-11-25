package dev.dual.core.domain;

public enum WorkstreamStatus {
    CREATED,
    RUNNING,
    WAITING_FOR_HUMAN,
    PAUSED_RESUME_REQUIRED,
    BLOCKED,
    FAILED,
    DESIGN_APPROVED
}
