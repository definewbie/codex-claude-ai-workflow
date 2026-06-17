package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentArtifactEnvelopeParserTest {
    @Test
    void ignoresInlineTagMentionsAndReadsTopLevelEnvelope() {
        var markdown = """
                The required format is `<dual-artifact type="stage_handoff" version="1">` followed by JSON.

                <dual-artifact type="stage_handoff" version="1">
                {
                  "artifact_type": "stage_handoff",
                  "decision": "HANDOFF_READY"
                }
                </dual-artifact>
                """;

        var envelope = new AgentArtifactEnvelopeParser().latest(markdown, "stage_handoff");

        assertTrue(envelope.isPresent());
        assertEquals("stage_handoff", envelope.get().type());
        assertTrue(envelope.get().json().startsWith("{"));
        assertTrue(envelope.get().json().contains("\"decision\": \"HANDOFF_READY\""));
    }
}
