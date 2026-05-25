package dev.dual.cli;

import java.util.Set;

final class CliSupportedHandlers {
    static final Set<String> TYPES = Set.of(
            "acpx.agent_run",
            "artifact.validate",
            "context.build_package",
            "human.review",
            "prompt.generate_next_stage",
            "report.generate_final",
            "review.decision"
    );

    private CliSupportedHandlers() {
    }
}
