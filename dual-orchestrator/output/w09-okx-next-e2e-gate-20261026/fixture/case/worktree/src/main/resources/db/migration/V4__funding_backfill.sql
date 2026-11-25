CREATE TABLE funding_backfill_request (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_key VARCHAR(128) NOT NULL,
    instrument_id VARCHAR(64) NOT NULL,
    window_start TIMESTAMP(3) NOT NULL,
    window_end TIMESTAMP(3) NOT NULL,
    page_limit SMALLINT UNSIGNED NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'IDLE',
    coverage_state VARCHAR(24) NOT NULL DEFAULT 'IN_PROGRESS',
    cursor_ts TIMESTAMP(3) NULL,
    pages_fetched INT UNSIGNED NOT NULL DEFAULT 0,
    earliest_fact_time TIMESTAMP(3) NULL,
    latest_fact_time TIMESTAMP(3) NULL,
    next_attempt INT UNSIGNED NOT NULL DEFAULT 1,
    current_run_id BIGINT UNSIGNED NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_funding_backfill_key UNIQUE (request_key),
    CONSTRAINT chk_funding_backfill_limit CHECK (page_limit BETWEEN 1 AND 400),
    CONSTRAINT chk_funding_backfill_state CHECK (state IN ('IDLE', 'RUNNING', 'COMPLETE')),
    CONSTRAINT chk_funding_backfill_coverage CHECK (
        coverage_state IN ('IN_PROGRESS', 'COMPLETE', 'BUDGET_EXHAUSTED', 'SOURCE_EXHAUSTED')
    ),
    CONSTRAINT chk_funding_backfill_state_refs CHECK (
        (state = 'IDLE' AND current_run_id IS NULL)
        OR (state = 'RUNNING' AND current_run_id IS NOT NULL)
        OR (state = 'COMPLETE' AND current_run_id IS NULL AND coverage_state = 'COMPLETE')
    ),
    CONSTRAINT chk_funding_backfill_window CHECK (window_start < window_end)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE funding_backfill_page (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    backfill_request_id BIGINT UNSIGNED NOT NULL,
    run_id BIGINT UNSIGNED NOT NULL,
    page_no INT UNSIGNED NOT NULL,
    after_cursor TIMESTAMP(3) NOT NULL,
    received INT UNSIGNED NOT NULL,
    new_facts INT UNSIGNED NOT NULL,
    oldest_time TIMESTAMP(3) NULL,
    newest_time TIMESTAMP(3) NULL,
    next_cursor TIMESTAMP(3) NULL,
    fetched_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_funding_backfill_page UNIQUE (backfill_request_id, page_no),
    CONSTRAINT fk_funding_backfill_page_request
        FOREIGN KEY (backfill_request_id) REFERENCES funding_backfill_request (id),
    CONSTRAINT fk_funding_backfill_page_run
        FOREIGN KEY (run_id) REFERENCES analysis_run (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- analysis_run 从单一同步所有者扩展为双所有者: sync_request_id 或 backfill_request_id 二选一
ALTER TABLE analysis_run
    DROP CHECK chk_analysis_run_sync_attempt;

ALTER TABLE analysis_run
    ADD COLUMN backfill_request_id BIGINT UNSIGNED NULL AFTER sync_request_id,
    ADD CONSTRAINT uk_analysis_run_backfill_attempt UNIQUE (backfill_request_id, attempt_no),
    ADD CONSTRAINT fk_analysis_run_backfill_request
        FOREIGN KEY (backfill_request_id) REFERENCES funding_backfill_request (id),
    ADD CONSTRAINT chk_analysis_run_owner CHECK (
        (sync_request_id IS NULL AND backfill_request_id IS NULL AND attempt_no IS NULL)
        OR (sync_request_id IS NOT NULL AND backfill_request_id IS NULL
            AND attempt_no IS NOT NULL AND attempt_no > 0)
        OR (sync_request_id IS NULL AND backfill_request_id IS NOT NULL
            AND attempt_no IS NOT NULL AND attempt_no > 0)
    );

ALTER TABLE funding_backfill_request
    ADD CONSTRAINT fk_funding_backfill_current_run
        FOREIGN KEY (current_run_id) REFERENCES analysis_run (id);
