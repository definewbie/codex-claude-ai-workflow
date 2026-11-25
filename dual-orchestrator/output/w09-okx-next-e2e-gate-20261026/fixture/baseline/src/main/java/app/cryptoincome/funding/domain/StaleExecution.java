package app.cryptoincome.funding.domain;

import java.time.Instant;

public record StaleExecution(
        String requestKey,
        long requestId,
        long runId,
        String instrumentId,
        int pageLimit,
        Instant startedAt
) {
}
