CREATE TABLE funding_rate_fact (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    exchange VARCHAR(16) NOT NULL,
    instrument_type VARCHAR(16) NOT NULL,
    instrument_id VARCHAR(64) NOT NULL,
    formula_type VARCHAR(16) NOT NULL,
    predicted_rate DECIMAL(38, 20) NOT NULL,
    realized_rate DECIMAL(38, 20) NOT NULL,
    funding_time TIMESTAMP(3) NOT NULL,
    settlement_method VARCHAR(32) NOT NULL,
    source_endpoint VARCHAR(255) NOT NULL,
    source_payload JSON NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    first_observed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_funding_rate_fact_content UNIQUE (
        exchange, instrument_id, funding_time, payload_hash
    ),
    INDEX idx_funding_rate_fact_timeline (exchange, instrument_id, funding_time),
    CONSTRAINT chk_funding_instrument_type CHECK (instrument_type IN ('SWAP', 'FUTURES')),
    CONSTRAINT chk_funding_formula_type CHECK (formula_type IN ('NO_RATE', 'WITH_RATE')),
    CONSTRAINT chk_funding_settlement_method CHECK (
        settlement_method IN ('CURRENT_PERIOD', 'NEXT_PERIOD')
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE funding_rate_observation (
    analysis_run_id BIGINT UNSIGNED NOT NULL,
    funding_rate_fact_id BIGINT UNSIGNED NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (analysis_run_id, funding_rate_fact_id),
    INDEX idx_funding_observation_fact (funding_rate_fact_id),
    CONSTRAINT fk_funding_observation_run
        FOREIGN KEY (analysis_run_id) REFERENCES analysis_run (id),
    CONSTRAINT fk_funding_observation_fact
        FOREIGN KEY (funding_rate_fact_id) REFERENCES funding_rate_fact (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
