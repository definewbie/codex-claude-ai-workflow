CREATE TABLE candle_sync_execution (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    state VARCHAR(16) NOT NULL,
    instrument_id VARCHAR(64) NOT NULL,
    bar VARCHAR(16) NOT NULL,
    window_from TIMESTAMP(6) NOT NULL,
    window_to TIMESTAMP(6) NOT NULL,
    coverage VARCHAR(32) NULL,
    pages INT UNSIGNED NOT NULL DEFAULT 0,
    received INT UNSIGNED NOT NULL DEFAULT 0,
    completed_in_window INT UNSIGNED NOT NULL DEFAULT 0,
    new_facts INT UNSIGNED NOT NULL DEFAULT 0,
    duplicate_facts INT UNSIGNED NOT NULL DEFAULT 0,
    cursor_ts TIMESTAMP(6) NULL,
    failure_category VARCHAR(32) NULL,
    failure_message VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    active_slot TINYINT GENERATED ALWAYS AS (
        CASE WHEN state IN ('QUEUED','RUNNING') THEN 1 ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_candle_sync_single_active (active_slot),
    INDEX idx_candle_sync_state_created (state, created_at),
    CONSTRAINT chk_candle_sync_state CHECK (state IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')),
    CONSTRAINT chk_candle_sync_terminal CHECK (
        (state IN ('QUEUED','RUNNING') AND completed_at IS NULL)
        OR (state IN ('SUCCEEDED','FAILED') AND completed_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
