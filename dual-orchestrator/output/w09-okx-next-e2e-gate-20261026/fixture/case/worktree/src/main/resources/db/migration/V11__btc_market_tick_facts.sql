CREATE TABLE btc_market_tick_fact (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    exchange VARCHAR(16) NOT NULL,
    instrument_id VARCHAR(64) NOT NULL,
    last_price DECIMAL(38, 18) NOT NULL,
    bid_price DECIMAL(38, 18) NULL,
    ask_price DECIMAL(38, 18) NULL,
    provider_ts TIMESTAMP(3) NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    source_endpoint VARCHAR(255) NOT NULL,
    source_payload JSON NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_btc_market_tick_content UNIQUE (exchange, instrument_id, provider_ts, payload_hash),
    INDEX idx_btc_market_tick_latest (exchange, instrument_id, provider_ts, observed_at),
    CONSTRAINT chk_btc_market_tick_price CHECK (
        last_price > 0
        AND (bid_price IS NULL OR bid_price > 0)
        AND (ask_price IS NULL OR ask_price > 0)
        AND (bid_price IS NULL OR ask_price IS NULL OR bid_price <= ask_price)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
