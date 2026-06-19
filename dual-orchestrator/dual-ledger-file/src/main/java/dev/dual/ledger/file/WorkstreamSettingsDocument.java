package dev.dual.ledger.file;

import dev.dual.core.domain.PromptLanguage;

public record WorkstreamSettingsDocument(
        int schemaVersion,
        String promptLanguage
) {
    public WorkstreamSettingsDocument {
        promptLanguage = PromptLanguage.normalize(promptLanguage);
    }

    public static WorkstreamSettingsDocument of(String promptLanguage) {
        return new WorkstreamSettingsDocument(1, promptLanguage);
    }
}
