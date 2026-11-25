package app.cryptoincome.funding.domain;

public enum StaleRecoveryOutcome {
    RECOVERED,
    NOT_FOUND,
    NOT_RUNNING,
    RUN_MISMATCH,
    NOT_STALE
}
