package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFinalArtifactExtractorTest {
    private final AgentFinalArtifactExtractor extractor = new AgentFinalArtifactExtractor();

    @Test
    void extractsFinalArtifactAndControlWithoutToolTranscriptFrames() {
        var result = extractor.extract("""
                [client] initialize (running)
                [tool] Read src/Main.java (completed)
                  input: {"file":"src/Main.java"}
                  output:
                    public class Main {}

                <dual-artifact type="agent_output_contract">
                {"summary":"Ready"}
                </dual-artifact>

                ```dual-control
                decision: READY_FOR_PRODUCT_REVIEW
                summary: ready
                ```

                [done] end_turn
                """);

        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("<dual-artifact"));
        assertTrue(result.get().contains("decision: READY_FOR_PRODUCT_REVIEW"));
        assertFalse(result.get().contains("[tool]"));
        assertFalse(result.get().contains("[client]"));
        assertFalse(result.get().contains("input:"));
        assertFalse(result.get().contains("output:"));
        assertFalse(result.get().contains("[done]"));
    }

    @Test
    void keepsFormalMarkdownBodyBeforeCanonicalArtifact() {
        var result = extractor.extract("""
                [tool] Read src/Main.java (completed)
                  output:
                    public class Main {}

                # Implementation Steps

                1. Update the follow-up query.

                # File-Level Changes

                - `src/Main.java`

                <dual-artifact type="agent_output_contract" version="1">
                {"summary":"Plan ready"}
                </dual-artifact>

                ```dual-control
                decision: READY_FOR_PLAN_REVIEW
                summary: ready
                ```
                """);

        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("# Implementation Steps"));
        assertTrue(result.get().contains("# File-Level Changes"));
        assertTrue(result.get().contains("<dual-artifact"));
        assertTrue(result.get().contains("decision: READY_FOR_PLAN_REVIEW"));
        assertFalse(result.get().contains("[tool]"));
        assertFalse(result.get().contains("public class Main"));
    }

    @Test
    void fallsBackToTerminalControlBlockWhenNoStructuredArtifactExists() {
        var result = extractor.extract("""
                Business summary.

                ```dual-control
                decision: PASS
                summary: pass
                ```
                """);

        assertEquals("""
                ```dual-control
                decision: PASS
                summary: pass
                ```
                """, result.orElseThrow());
    }

    @Test
    void returnsEmptyWhenTranscriptHasNoTerminalControlBlock() {
        assertTrue(extractor.extract("[tool] Read file\nNo terminal contract yet.\n").isEmpty());
    }

    @Test
    void usesLatestCompleteArtifactBeforeLastControlBlock() {
        var result = extractor.extract("""
                <dual-artifact type="agent_output_contract">
                {"summary":"old"}
                </dual-artifact>

                ```dual-control
                decision: OLD
                ```

                <dual-artifact type="agent_output_contract">
                {"summary":"new"}
                </dual-artifact>

                ```dual-control
                decision: NEW
                ```
                """);

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("\"new\""));
        assertTrue(result.get().contains("decision: NEW"));
        assertFalse(result.get().contains("\"old\""));
        assertFalse(result.get().contains("decision: OLD"));
    }

    @Test
    void doesNotCanonicalizeJsonFenceBeforeTerminalControl() {
        var result = extractor.extract("""
                Design notes.

                ```json
                {
                  "artifact_type": "agent_output_contract",
                  "decision": "READY_FOR_ARCH_REVIEW",
                  "summary": "ready"
                }
                ```

                ```dual-control
                decision: READY_FOR_ARCH_REVIEW
                summary: ready
                ```

                [done] end_turn
                """);

        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("```dual-control"));
        assertTrue(result.get().contains("decision: READY_FOR_ARCH_REVIEW"));
        assertFalse(result.get().contains("<dual-artifact"));
        assertFalse(result.get().contains("\"artifact_type\""));
        assertFalse(result.get().contains("Design notes."));
        assertFalse(result.get().contains("```json"));
        assertFalse(result.get().contains("[done]"));
    }

    @Test
    void ignoresEscapedArtifactExampleInsideCanonicalEnvelopeJson() {
        var result = extractor.extract("""
                <dual-artifact type="agent_output_contract" version="1">
                {
                  "artifact_type": "agent_output_contract",
                  "missing_evidence": [
                    "缺少 <dual-artifact type=\\"task_contract_proposal\\" version=\\"1\\"> 候选任务合同。"
                  ],
                  "decision": "NEEDS_REVISION"
                }
                </dual-artifact>

                ```dual-control
                decision: NEEDS_REVISION
                summary: revise
                ```
                """);

        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("<dual-artifact type=\"agent_output_contract\" version=\"1\">"));
        assertTrue(result.get().contains("\"missing_evidence\""));
        assertTrue(result.get().contains("decision: NEEDS_REVISION"));
    }

    @Test
    void nativeResultWithTypedArtifactEnvelopeIsExtractedWithoutControlFence() {
        var result = extractor.extractNativeArtifact("""
                # Intake Brainstorm

                Plain native claude --print result that carries only the typed envelope.

                <dual-artifact type="task_contract_proposal" version="1">
                {"artifact_type":"task_contract_proposal","decision":"PROCEED","summary":"ready"}
                </dual-artifact>
                """);

        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("<dual-artifact type=\"task_contract_proposal\""));
        assertTrue(result.get().contains("\"artifact_type\":\"task_contract_proposal\""));
        assertFalse(result.get().contains("Plain native claude"));
    }

    @Test
    void nativeResultWithFullContractBlockPrefersFullExtraction() {
        var result = extractor.extractNativeArtifact("""
                <dual-artifact type="task_contract_proposal" version="1">
                {"artifact_type":"task_contract_proposal","decision":"PROCEED","summary":"ready"}
                </dual-artifact>

                ```dual-control
                decision: PROCEED
                summary: ready
                ```
                """);

        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("<dual-artifact type=\"task_contract_proposal\""));
        assertTrue(result.get().contains("decision: PROCEED"));
    }

    @Test
    void nativeResultWithoutTypedArtifactReturnsEmpty() {
        var result = extractor.extractNativeArtifact("""
                # Intake Brainstorm

                Plain markdown only. No typed artifact envelope and no dual-control fence.
                """);

        assertTrue(result.isEmpty());
    }

    @Test
    void nativeResultWithMalformedArtifactEnvelopeReturnsEmpty() {
        var result = extractor.extractNativeArtifact("""
                # Intake Brainstorm

                <dual-artifact type="task_contract_proposal" version="1">
                {"artifact_type":"task_contract_proposal","decision":"PROCEED"
                """);

        assertTrue(result.isEmpty(), "an unclosed dual-artifact envelope must not be accepted as a typed artifact");
    }

    @Test
    void nativeDispatchRoutesToNativeExtractionByProtocolSource() {
        var nativeResult = new NativeClaudeTerminal(true, false, "end_turn", "success");
        var transcript = """
                # Brainstorm

                <dual-artifact type="task_contract_proposal" version="1">
                {"artifact_type":"task_contract_proposal"}
                </dual-artifact>
                """;

        var viaNative = extractor.extract(transcript, nativeResult);
        var viaSession = extractor.extract(transcript, null);

        assertTrue(viaNative.isPresent(), "native protocol source must extract the typed envelope without a control fence");
        assertTrue(viaSession.isEmpty(), "session protocol source still requires the dual-control fence");
    }
}
