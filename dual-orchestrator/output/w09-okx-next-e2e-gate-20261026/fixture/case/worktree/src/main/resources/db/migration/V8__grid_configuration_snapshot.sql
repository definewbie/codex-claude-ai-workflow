-- 用户在 OKX App 最终确认并准备执行的配置。
-- 与 grid_parameter_snapshot（公共 API 候选）分开，禁止把候选误当成实际配置。
CREATE TABLE grid_configuration_snapshot (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    exchange VARCHAR(16) NOT NULL,
    instrument_id VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    recommendation_horizon_label VARCHAR(32) NOT NULL,
    lower_price DECIMAL(38, 18) NOT NULL,
    upper_price DECIMAL(38, 18) NOT NULL,
    grid_count INT UNSIGNED NOT NULL,
    grid_type VARCHAR(16) NOT NULL,
    displayed_annualized_rate DECIMAL(38, 18) NULL,
    displayed_minimum_per_grid_rate DECIMAL(38, 18) NULL,
    displayed_maximum_per_grid_rate DECIMAL(38, 18) NULL,
    profit_reinvestment_enabled BOOLEAN NOT NULL,
    auto_earn_enabled BOOLEAN NOT NULL,
    trailing_up_enabled BOOLEAN NOT NULL,
    trailing_down_enabled BOOLEAN NOT NULL,
    take_profit_price DECIMAL(38, 18) NULL,
    stop_loss_price DECIMAL(38, 18) NULL,
    source_reference VARCHAR(255) NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_grid_configuration_lookup (exchange, instrument_id, observed_at),
    CONSTRAINT chk_grid_configuration_source CHECK (source_type = 'OKX_APP_MANUAL_CONFIRMATION'),
    CONSTRAINT chk_grid_configuration_type CHECK (grid_type IN ('ARITHMETIC', 'GEOMETRIC')),
    CONSTRAINT chk_grid_configuration_prices CHECK (lower_price > 0 AND upper_price > lower_price),
    CONSTRAINT chk_grid_configuration_count CHECK (grid_count BETWEEN 2 AND 500),
    CONSTRAINT chk_grid_configuration_displayed_rates CHECK (
        (displayed_annualized_rate IS NULL OR displayed_annualized_rate >= 0)
        AND (displayed_minimum_per_grid_rate IS NULL OR displayed_minimum_per_grid_rate >= 0)
        AND (displayed_maximum_per_grid_rate IS NULL OR displayed_maximum_per_grid_rate >= 0)
        AND (displayed_minimum_per_grid_rate IS NULL OR displayed_maximum_per_grid_rate IS NULL
             OR displayed_maximum_per_grid_rate >= displayed_minimum_per_grid_rate)
    ),
    CONSTRAINT chk_grid_configuration_tp CHECK (take_profit_price IS NULL OR take_profit_price > 0),
    CONSTRAINT chk_grid_configuration_sl CHECK (stop_loss_price IS NULL OR stop_loss_price > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
