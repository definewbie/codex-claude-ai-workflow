package app.cryptoincome.grid.application;

import java.time.Instant;

public record SpotCandleBackfillProgress(
        int pages,
        int received,
        int completedInWindow,
        int newFacts,
        int duplicateFacts,
        Instant cursor
) {}
