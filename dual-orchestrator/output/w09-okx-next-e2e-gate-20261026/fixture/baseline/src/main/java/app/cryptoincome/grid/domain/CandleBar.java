package app.cryptoincome.grid.domain;

import java.time.Duration;

public enum CandleBar {
    M1("1m", Duration.ofMinutes(1));

    private final String providerValue;
    private final Duration duration;

    CandleBar(String providerValue, Duration duration) {
        this.providerValue = providerValue;
        this.duration = duration;
    }

    public String providerValue() { return providerValue; }

    public Duration duration() { return duration; }
}
