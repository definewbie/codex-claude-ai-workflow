-- 第一版固定网格回测只保存 OKX BTC-USDT 的完成 1 分钟 K 线。
-- 同一 open_time 的上游修订以新 payload_hash 追加，历史版本不覆盖。
CREATE TABLE spot_candle_fact (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    exchange VARCHAR(16) NOT NULL,
    instrument_id VARCHAR(64) NOT NULL,
    bar VARCHAR(8) NOT NULL,
    open_time TIMESTAMP(3) NOT NULL,
    open_price DECIMAL(38, 18) NOT NULL,
    high_price DECIMAL(38, 18) NOT NULL,
    low_price DECIMAL(38, 18) NOT NULL,
    close_price DECIMAL(38, 18) NOT NULL,
    base_volume DECIMAL(38, 18) NOT NULL,
    quote_volume DECIMAL(38, 18) NOT NULL,
    confirmed BOOLEAN NOT NULL,
    source_endpoint VARCHAR(255) NOT NULL,
    source_payload JSON NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    first_observed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_spot_candle_content UNIQUE (
        exchange, instrument_id, bar, open_time, payload_hash
    ),
    INDEX idx_spot_candle_asof (
        exchange, instrument_id, bar, open_time, first_observed_at
    ),
    CONSTRAINT chk_spot_candle_bar CHECK (bar = '1m'),
    CONSTRAINT chk_spot_candle_confirmed CHECK (confirmed = TRUE),
    CONSTRAINT chk_spot_candle_prices CHECK (
        open_price > 0 AND close_price > 0 AND low_price > 0
        AND high_price >= open_price AND high_price >= close_price AND high_price >= low_price
        AND low_price <= open_price AND low_price <= close_price
    ),
    CONSTRAINT chk_spot_candle_volume CHECK (base_volume >= 0 AND quote_volume >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
