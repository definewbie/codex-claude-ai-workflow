package dev.dual.adapter.acpx;

import java.nio.file.Path;

record SessionCompactionMeta(
        String mode,
        boolean enabled,
        String reason,
        String seedKind,
        String seedPath
) {
    static SessionCompactionMeta from(SessionCompactionPolicy.Decision decision, Path workspaceRoot, Path seedPath) {
        if (decision == null) {
            return off();
        }
        return new SessionCompactionMeta(
                decision.mode().wire(),
                decision.enabled(),
                decision.reason(),
                decision.seedKind(),
                seedPath == null ? "" : workspaceRoot.relativize(seedPath).toString()
        );
    }

    static SessionCompactionMeta off() {
        return new SessionCompactionMeta("off", false, "default:off", "", "");
    }
}
