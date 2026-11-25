-- OKX 公共网格 AI 参数的追加式观察快照。
-- OKX 展示年化仅是来源事实，不能直接作为本系统的收益建议。
CREATE TABLE grid_parameter_snapshot (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    exchange VARCHAR(16) NOT NULL,
    instrument_id VARCHAR(64) NOT NULL,
    duration VARCHAR(8) NOT NULL,
    grid_type VARCHAR(16) NOT NULL,
    lower_price DECIMAL(38, 18) NOT NULL,
    upper_price DECIMAL(38, 18) NOT NULL,
    grid_count INT UNSIGNED NOT NULL,
    annualized_rate DECIMAL(38, 18) NOT NULL,
    minimum_per_grid_profit_rate DECIMAL(38, 18) NOT NULL,
    maximum_per_grid_profit_rate DECIMAL(38, 18) NOT NULL,
    minimum_investment DECIMAL(38, 18) NOT NULL,
    investment_currency VARCHAR(16) NOT NULL,
    source_endpoint VARCHAR(255) NOT NULL,
    source_payload JSON NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_grid_parameter_lookup (exchange, instrument_id, duration, observed_at),
    CONSTRAINT chk_grid_parameter_duration CHECK (duration IN ('7D', '30D', '180D')),
    CONSTRAINT chk_grid_parameter_type CHECK (grid_type IN ('ARITHMETIC', 'GEOMETRIC')),
    CONSTRAINT chk_grid_parameter_prices CHECK (lower_price > 0 AND upper_price > lower_price),
    CONSTRAINT chk_grid_parameter_count CHECK (grid_count BETWEEN 2 AND 500),
    CONSTRAINT chk_grid_parameter_rates CHECK (
        annualized_rate >= 0
        AND minimum_per_grid_profit_rate >= 0
        AND maximum_per_grid_profit_rate >= minimum_per_grid_profit_rate
    ),
    CONSTRAINT chk_grid_parameter_minimum CHECK (minimum_investment >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
