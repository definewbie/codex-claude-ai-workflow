CREATE TABLE analysis_run (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    idempotency_key VARCHAR(128) NOT NULL,
    run_type VARCHAR(32) NOT NULL,
    mode VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    failure_code VARCHAR(64) NULL,
    failure_detail VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_analysis_run_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_analysis_run_type CHECK (
        run_type IN ('SYNC_MARKET', 'SNAPSHOT_ACCOUNT', 'BACKTEST', 'EVALUATE', 'REPORT')
    ),
    CONSTRAINT chk_analysis_run_mode CHECK (mode IN ('BACKTEST', 'SHADOW', 'LIVE')),
    CONSTRAINT chk_analysis_run_status CHECK (status IN ('CREATED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_analysis_run_timeline CHECK (
        (status = 'CREATED' AND started_at IS NULL AND completed_at IS NULL)
        OR (status = 'RUNNING' AND started_at IS NOT NULL AND completed_at IS NULL)
        OR (status IN ('SUCCEEDED', 'FAILED') AND started_at IS NOT NULL AND completed_at IS NOT NULL)
    ),
    CONSTRAINT chk_analysis_run_failure CHECK (
        (status = 'FAILED' AND failure_code IS NOT NULL AND failure_detail IS NOT NULL)
        OR (status <> 'FAILED' AND failure_code IS NULL AND failure_detail IS NULL)
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
