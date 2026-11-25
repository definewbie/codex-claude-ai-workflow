package app.cryptoincome.grid.application;

import java.time.Instant;

public record SpotCandleTailRefreshResult(
        Instant toExclusive,
        int received,
        int completed,
        int newFacts,
        int duplicateFacts
) {}
