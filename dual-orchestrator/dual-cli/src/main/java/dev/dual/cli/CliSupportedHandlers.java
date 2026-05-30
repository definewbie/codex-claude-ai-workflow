package dev.dual.cli;

import java.util.Set;

final class CliSupportedHandlers {
    static final Set<String> TYPES = Set.of(
            "acpx.agent_run",
            "acceptance.failure_classify",
            "artifact.validate",
            "context.build_package",
            "contract.compile",
            "delivery.create_pr",
            "delivery.git_commit",
            "delivery.prepare_pr",
            "human.review",
            "prompt.generate_next_stage",
            "report.generate_final",
            "review.decision"
    );

    private CliSupportedHandlers() {
    }
}
