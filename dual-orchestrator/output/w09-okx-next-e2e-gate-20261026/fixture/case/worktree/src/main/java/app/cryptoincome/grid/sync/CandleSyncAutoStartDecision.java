package app.cryptoincome.grid.sync;

import java.time.Instant;

public record CandleSyncAutoStartDecision(
        CandleSyncExecution execution,
        Instant automaticRetryAt
) {
    public boolean blocked() {
        return automaticRetryAt != null;
    }
}
