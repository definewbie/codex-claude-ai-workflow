package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkdownArtifactFieldsTest {
    @Test
    void extractsNumberedListSectionByAlias() {
        var markdown = """
                ## 3. Gaps / Must Fix

                - Fix acceptance evidence.
                - Preserve source fields.

                ## 4. Product Acceptance

                Looks good.
                """;

        assertEquals(
                List.of("Fix acceptance evidence.", "Preserve source fields."),
                MarkdownArtifactFields.listSection(markdown, "Must Fix", "Gaps / Must Fix")
        );
    }

    @Test
    void extractsCompactTextSection() {
        var markdown = """
                ## Verification Adequacy

                Developer self-test passed.

                Independent smoke is still missing.

                ## Residual Risks

                - Mobile QA.
                """;

        assertEquals(
                "Developer self-test passed.\nIndependent smoke is still missing.",
                MarkdownArtifactFields.textSection(markdown, "Verification Adequacy")
        );
    }
}
