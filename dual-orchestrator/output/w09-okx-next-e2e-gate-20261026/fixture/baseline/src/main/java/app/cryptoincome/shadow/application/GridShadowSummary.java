package app.cryptoincome.shadow.application;

import app.cryptoincome.grid.domain.GridScenarioParameters;
import app.cryptoincome.shadow.ShadowStudyState;

import java.time.Instant;

public record GridShadowSummary(
        long id,
        ShadowStudyState state,
        GridShadowStatus status,
        Instant startAt,
        int targetDays,
        long observedMinutes,
        GridScenarioParameters candidate,
        GridScenarioParameters baseline,
        Instant createdAt
) {}
