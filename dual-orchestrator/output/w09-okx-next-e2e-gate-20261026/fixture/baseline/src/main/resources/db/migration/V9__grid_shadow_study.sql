CREATE TABLE grid_shadow_study (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    state VARCHAR(16) NOT NULL,
    configuration_snapshot_id BIGINT UNSIGNED NOT NULL,
    start_at TIMESTAMP(6) NOT NULL,
    target_days INT UNSIGNED NOT NULL,
    capital DECIMAL(38,18) NOT NULL,
    simple_earn_apr DECIMAL(38,18) NOT NULL,
    maker_fee_rate DECIMAL(38,18) NOT NULL,
    taker_fee_rate DECIMAL(38,18) NOT NULL,
    exit_slippage_bps DECIMAL(38,18) NOT NULL,
    maximum_drawdown_rate DECIMAL(38,18) NOT NULL,
    maximum_path_divergence_rate DECIMAL(38,18) NOT NULL,
    maximum_ending_btc_value_ratio DECIMAL(38,18) NOT NULL,
    candidate_lower_price DECIMAL(38,18) NOT NULL,
    candidate_upper_price DECIMAL(38,18) NOT NULL,
    candidate_grid_count INT UNSIGNED NOT NULL,
    candidate_take_profit_price DECIMAL(38,18) NULL,
    candidate_stop_loss_price DECIMAL(38,18) NULL,
    baseline_lower_price DECIMAL(38,18) NOT NULL,
    baseline_upper_price DECIMAL(38,18) NOT NULL,
    baseline_grid_count INT UNSIGNED NOT NULL,
    entry_reference_price DECIMAL(38,18) NOT NULL,
    evidence_as_of TIMESTAMP(6) NOT NULL,
    evidence_reference VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    stopped_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_grid_shadow_configuration FOREIGN KEY (configuration_snapshot_id)
        REFERENCES grid_configuration_snapshot(id),
    CONSTRAINT chk_grid_shadow_state CHECK (state IN ('ACTIVE','STOPPED')),
    CONSTRAINT chk_grid_shadow_days CHECK (target_days BETWEEN 7 AND 14),
    CONSTRAINT chk_grid_shadow_values CHECK (capital > 0 AND simple_earn_apr >= 0
        AND maker_fee_rate >= 0 AND taker_fee_rate >= 0 AND exit_slippage_bps >= 0
        AND maximum_drawdown_rate BETWEEN 0 AND 1
        AND maximum_path_divergence_rate BETWEEN 0 AND 1
        AND maximum_ending_btc_value_ratio BETWEEN 0 AND 1),
    CONSTRAINT chk_grid_shadow_candidate CHECK (candidate_upper_price > candidate_lower_price
        AND candidate_grid_count BETWEEN 2 AND 500),
    CONSTRAINT chk_grid_shadow_baseline CHECK (baseline_upper_price > baseline_lower_price
        AND baseline_grid_count BETWEEN 2 AND 500),
    CONSTRAINT chk_grid_shadow_entry CHECK (
        entry_reference_price BETWEEN candidate_lower_price AND candidate_upper_price
        AND entry_reference_price BETWEEN baseline_lower_price AND baseline_upper_price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE grid_shadow_daily_fact (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    shadow_study_id BIGINT UNSIGNED NOT NULL,
    variant_role VARCHAR(16) NOT NULL,
    valuation_at TIMESTAMP(6) NOT NULL,
    candle_count INT UNSIGNED NOT NULL,
    net_income DECIMAL(38,18) NOT NULL,
    simple_earn_income DECIMAL(38,18) NOT NULL,
    buy_hold_income DECIMAL(38,18) NOT NULL,
    maximum_drawdown_rate DECIMAL(38,18) NOT NULL,
    path_divergence_rate DECIMAL(38,18) NOT NULL,
    ending_btc_value_ratio DECIMAL(38,18) NOT NULL,
    completed_cycles INT UNSIGNED NOT NULL,
    total_fees DECIMAL(38,18) NOT NULL,
    exit_reason VARCHAR(16) NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_grid_shadow_daily_fact (shadow_study_id, variant_role, valuation_at),
    INDEX idx_grid_shadow_latest (shadow_study_id, valuation_at),
    CONSTRAINT fk_grid_shadow_fact_study FOREIGN KEY (shadow_study_id)
        REFERENCES grid_shadow_study(id),
    CONSTRAINT chk_grid_shadow_variant CHECK (variant_role IN ('CANDIDATE','BASELINE')),
    CONSTRAINT chk_grid_shadow_fact_values CHECK (candle_count > 0
        AND maximum_drawdown_rate BETWEEN 0 AND 1
        AND path_divergence_rate BETWEEN 0 AND 1
        AND ending_btc_value_ratio BETWEEN 0 AND 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
