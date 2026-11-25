package app.cryptoincome.grid.application;

import app.cryptoincome.grid.domain.CandleBackfillCoverage;

import java.time.Instant;

public record SpotCandleBackfillResult(
        CandleBackfillCoverage coverage,
        int pages,
        int received,
        int completedInWindow,
        int newFacts,
        int duplicateFacts,
        Instant cursor
) {}
