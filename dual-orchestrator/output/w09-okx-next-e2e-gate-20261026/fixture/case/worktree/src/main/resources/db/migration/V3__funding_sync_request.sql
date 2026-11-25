CREATE TABLE funding_sync_request (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_key VARCHAR(128) NOT NULL,
    instrument_id VARCHAR(64) NOT NULL,
    page_limit SMALLINT UNSIGNED NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'READY',
    current_run_id BIGINT UNSIGNED NULL,
    successful_run_id BIGINT UNSIGNED NULL,
    next_attempt INT UNSIGNED NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_funding_sync_request_key UNIQUE (request_key),
    CONSTRAINT chk_funding_sync_limit CHECK (page_limit BETWEEN 1 AND 400),
    CONSTRAINT chk_funding_sync_state CHECK (state IN ('READY', 'RUNNING', 'SUCCEEDED')),
    CONSTRAINT chk_funding_sync_state_refs CHECK (
        (state = 'READY' AND current_run_id IS NULL AND successful_run_id IS NULL)
        OR (state = 'RUNNING' AND current_run_id IS NOT NULL AND successful_run_id IS NULL)
        OR (state = 'SUCCEEDED' AND current_run_id IS NULL AND successful_run_id IS NOT NULL)
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE analysis_run
    ADD COLUMN sync_request_id BIGINT UNSIGNED NULL AFTER id,
    ADD COLUMN attempt_no INT UNSIGNED NULL AFTER sync_request_id,
    ADD CONSTRAINT uk_analysis_run_sync_attempt UNIQUE (sync_request_id, attempt_no),
    ADD CONSTRAINT fk_analysis_run_sync_request
        FOREIGN KEY (sync_request_id) REFERENCES funding_sync_request (id),
    ADD CONSTRAINT chk_analysis_run_sync_attempt CHECK (
        (sync_request_id IS NULL AND attempt_no IS NULL)
        OR (sync_request_id IS NOT NULL AND attempt_no IS NOT NULL AND attempt_no > 0)
    );

ALTER TABLE funding_sync_request
    ADD CONSTRAINT fk_funding_sync_current_run
        FOREIGN KEY (current_run_id) REFERENCES analysis_run (id),
    ADD CONSTRAINT fk_funding_sync_successful_run
        FOREIGN KEY (successful_run_id) REFERENCES analysis_run (id);
