package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactContractValidatorTest {
    @TempDir
    Path tempDir;

    private final ArtifactContractValidator validator = new ArtifactContractValidator();

    @Test
    void passesValidAgentOutputContract() throws Exception {
        var outputRef = writeOutput("review.md", """
                Review passed.

                ```dual-control
                decision: READY_FOR_ARCH_REVIEW
                summary: Architecture is ready.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        var validated = validator.validate(tempDir, agentNode("design.codex_arch_review"), result);

        assertEquals(NodeStatus.COMPLETED, validated.status());
        assertTrue(validated.outputRefs().contains(outputRef));
        assertTrue(validated.outputRefs().contains("review.contract.json"));
    }

    @Test
    void writesContractSidecarForValidAgentOutput() throws Exception {
        var outputRef = writeOutput("review.md", """
                Review passed.

                ```dual-control
                decision: READY_FOR_ARCH_REVIEW
                summary: Architecture is ready.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        var validated = validator.validate(tempDir, agentNode("design.codex_arch_review"), result);

        var sidecarRef = "review.contract.json";
        assertTrue(validated.outputRefs().contains(sidecarRef));
        assertEquals(sidecarRef, validated.result().get("contract_sidecar_ref"));

        var sidecar = Files.readString(tempDir.resolve(sidecarRef));
        assertTrue(sidecar.contains("\"artifact_type\" : \"agent_output_contract\""));
        assertTrue(sidecar.contains("\"node_id\" : \"design.codex_arch_review\""));
        assertTrue(sidecar.contains("\"decision\" : \"READY_FOR_ARCH_REVIEW\""));
        assertTrue(sidecar.contains("\"summary\" : \"Architecture is ready.\""));
        assertTrue(sidecar.contains("\"source_output_ref\" : \"review.md\""));
    }

    @Test
    void positiveDecisionWithMustFixFailsContractEvenWhenTextClaimsNoBlockingIssue() throws Exception {
        var outputRef = writeOutput("review.md", """
                # Review

                ## Must Fix

                - None. The proposal preserves the user intent.

                ## Product Risks

                - Modal wording must stay non-medical.

                ```dual-control
                decision: READY_FOR_PRODUCT_REVIEW
                summary: Intake is ready.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        var validated = validator.validate(tempDir, agentNode("intake.codex_product_review"), result);

        assertEquals(NodeStatus.FAILED, validated.status());
        assertEquals("AGENT_OUTPUT_CONTRACT_VIOLATION", validated.outcome());
        assertTrue(String.valueOf(validated.result().get("message"))
                .contains("READY_FOR_PRODUCT_REVIEW cannot include blocking=true or must_fix"));
    }

    @Test
    void failsWhenRequiredStructuredArtifactIsMissing() throws Exception {
        var outputRef = writeOutput("acceptance.md", """
                # Acceptance

                ## Gaps / Must Fix

                - Markdown-only machine facts must not be trusted.

                ```dual-control
                decision: ACCEPT_WITH_NOTES
                summary: Accepted with verification notes.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        var validated = validator.validate(tempDir, agentNode("acceptance.codex_verifier_review"), result);

        assertEquals(NodeStatus.FAILED, validated.status());
        assertEquals("AGENT_OUTPUT_CONTRACT_VIOLATION", validated.outcome());
        assertTrue(String.valueOf(validated.result().get("message")).contains("acceptance_review"));
    }

    @Test
    void writesContractFailureDiagnosticWhenRuntimeContextIsPresent() throws Exception {
        Files.createDirectories(tempDir.resolve(".agent/workstreams/ws-test"));
        var outputRef = writeOutput("acceptance.md", """
                # Acceptance

                ```dual-control
                decision: ACCEPT
                summary: Accepted without structured artifact.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));

        var validated = validator.validate(
                tempDir,
                "ws-test",
                "node-acceptance-001",
                agentNode("acceptance.codex_verifier_review"),
                result
        );

        assertEquals(NodeStatus.FAILED, validated.status());
        var diagnosticRef = String.valueOf(validated.result().get("contract_failure_diagnostic_ref"));
        assertEquals(".agent/workstreams/ws-test/artifacts/contract-failures/node-acceptance-001.json", diagnosticRef);
        assertTrue(validated.outputRefs().contains(diagnosticRef));
        var diagnostic = Files.readString(tempDir.resolve(diagnosticRef));
        assertTrue(diagnostic.contains("\"artifact_type\" : \"contract_failure_diagnostic\""));
        assertTrue(diagnostic.contains("\"category\" : \"missing_structured_artifact\""));
        assertTrue(diagnostic.contains("\"recommended_owner\" : \"agent_or_prompt\""));
        assertTrue(diagnostic.contains("typed <dual-artifact>"));
        assertTrue(Files.exists(tempDir.resolve(".agent/workstreams/ws-test/artifacts/contract-failures/node-acceptance-001.md")));
    }

    @Test
    void sidecarUsesStructuredAcceptanceArtifactInsteadOfMarkdownSections() throws Exception {
        var outputRef = writeOutput("acceptance.md", """
                # Acceptance

                ## Gaps / Must Fix

                - Markdown-only must fix must be ignored.

                <dual-artifact type="acceptance_review" version="1">
                {
                  "artifact_type": "acceptance_review",
                  "schema_version": 1,
                  "decision": "ACCEPT_WITH_NOTES",
                  "summary": "Accepted with structured verification notes.",
                  "blocking": false,
                  "checks": ["inspected git diff"],
                  "evidence": ["JSON evidence item"],
                  "missing_evidence": [],
                  "must_fix": [],
                  "acceptance_notes": ["JSON acceptance note"],
                  "open_risks": ["JSON open risk"],
                  "changed_files": ["src/main/App.java"],
                  "verification_summary": "JSON verification summary",
                  "failure": {
                    "category_hint": "",
                    "contract_gaps": [],
                    "out_of_scope_files": [],
                    "missing_tests": [],
                    "context_gaps": [],
                    "reason": ""
                  }
                }
                </dual-artifact>

                ```dual-control
                decision: ACCEPT_WITH_NOTES
                summary: Accepted with verification notes.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        validator.validate(tempDir, agentNode("acceptance.codex_verifier_review"), result);

        var sidecar = Files.readString(tempDir.resolve("acceptance.contract.json"));
        assertTrue(sidecar.contains("\"must_fix\""));
        assertTrue(sidecar.contains("\"must_fix\" : [ ]"));
        assertTrue(!sidecar.contains("Markdown-only must fix must be ignored."));
        assertTrue(sidecar.contains("\"changed_files\""));
        assertTrue(sidecar.contains("src/main/App.java"));
        assertTrue(sidecar.contains("\"verification_summary\" : \"JSON verification summary\""));
        assertTrue(sidecar.contains("\"open_risks\""));
        assertTrue(sidecar.contains("JSON open risk"));
        assertTrue(sidecar.contains("\"source_artifact_type\" : \"acceptance_review\""));
        assertTrue(sidecar.contains("\"lifecycle_status\" : \"ACCEPTED_WITH_NOTES\""));
        assertTrue(sidecar.contains("\"evidence_state\""));
        assertTrue(sidecar.contains("\"evidence_count\" : 1"));
    }

    @Test
    void acceptsAcceptedWithNotesWhenMissingEvidenceIsNonBlocking() throws Exception {
        var outputRef = writeOutput("acceptance.md", """
                <dual-artifact type="acceptance_review" version="1">
                {
                  "artifact_type": "acceptance_review",
                  "schema_version": 1,
                  "lifecycle_status": "ACCEPTED_WITH_NOTES",
                  "decision": "ACCEPT_WITH_NOTES",
                  "summary": "Accepted but with material missing evidence.",
                  "blocking": false,
                  "checks": ["inspected diff"],
                  "evidence": ["diff contains requested doc update"],
                  "missing_evidence": ["No build/typecheck evidence for a code change."],
                  "must_fix": [],
                  "acceptance_notes": [],
                  "open_risks": [],
                  "changed_files": ["docs/example.md"],
                  "verification_summary": "Verification is incomplete.",
                  "failure": {
                    "category_hint": "",
                    "contract_gaps": [],
                    "out_of_scope_files": [],
                    "missing_tests": [],
                    "context_gaps": [],
                    "reason": ""
                  }
                }
                </dual-artifact>

                ```dual-control
                decision: ACCEPT_WITH_NOTES
                summary: Accepted with notes.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        var validated = validator.validate(tempDir, agentNode("acceptance.codex_verifier_review"), result);

        assertEquals(NodeStatus.COMPLETED, validated.status());
        var sidecar = Files.readString(tempDir.resolve("acceptance.contract.json"));
        assertTrue(sidecar.contains("\"lifecycle_status\" : \"ACCEPTED_WITH_NOTES\""));
        assertTrue(sidecar.contains("\"missing_evidence_count\" : 1"));
        assertTrue(sidecar.contains("No build/typecheck evidence for a code change."));
    }

    @Test
    void failsAcceptedWhenMissingEvidenceIsMaterial() throws Exception {
        var outputRef = writeOutput("acceptance.md", """
                <dual-artifact type="acceptance_review" version="1">
                {
                  "artifact_type": "acceptance_review",
                  "schema_version": 1,
                  "lifecycle_status": "ACCEPTED",
                  "decision": "ACCEPT",
                  "summary": "Accepted despite missing evidence.",
                  "blocking": false,
                  "checks": ["inspected diff"],
                  "evidence": ["diff contains requested doc update"],
                  "missing_evidence": ["No build/typecheck evidence for a code change."],
                  "must_fix": [],
                  "acceptance_notes": [],
                  "open_risks": [],
                  "changed_files": ["docs/example.md"],
                  "verification_summary": "Verification is incomplete.",
                  "failure": {
                    "category_hint": "",
                    "contract_gaps": [],
                    "out_of_scope_files": [],
                    "missing_tests": [],
                    "context_gaps": [],
                    "reason": ""
                  }
                }
                </dual-artifact>

                ```dual-control
                decision: ACCEPT
                summary: Accepted.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        var validated = validator.validate(tempDir, agentNode("acceptance.codex_verifier_review"), result);

        assertEquals(NodeStatus.FAILED, validated.status());
        assertTrue(String.valueOf(validated.result().get("message"))
                .contains("FINAL_ACCEPTANCE_EVIDENCE_CONFLICT"));
    }

    @Test
    void acceptsReadySelfTestWithMissingEvidenceForDownstreamVerifier() throws Exception {
        var outputRef = writeOutput("self-test.md", """
                # Self-Test

                <dual-artifact type="self_test_report" version="1">
                {
                  "artifact_type": "self_test_report",
                  "schema_version": 1,
                  "lifecycle_status": "READY",
                  "decision": "SELF_TEST_COMPLETE",
                  "summary": "Build passed; runtime device verification is deferred to verifier.",
                  "blocking": false,
                  "checks_run": ["npm run build:mp-weixin"],
                  "checks_skipped": ["WeChat device smoke: simulator unavailable"],
                  "evidence": ["Build completed successfully."],
                  "missing_evidence": ["Runtime screenshot requires WeChat devtools."],
                  "must_fix": [],
                  "acceptance_notes": ["Codex verifier should decide whether deferred runtime evidence is acceptable."],
                  "open_risks": [],
                  "verification_summary": "Self-test evidence is ready for verifier review.",
                  "failure": {
                    "category_hint": "",
                    "contract_gaps": [],
                    "out_of_scope_files": [],
                    "missing_tests": [],
                    "context_gaps": [],
                    "reason": ""
                  }
                }
                </dual-artifact>

                ```dual-control
                decision: SELF_TEST_COMPLETE
                summary: Self-test completed with deferred runtime evidence.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "claude"));
        var validated = validator.validate(tempDir, agentNode("implement.claude_self_test_report"), result);

        assertEquals(NodeStatus.COMPLETED, validated.status());
        var sidecar = Files.readString(tempDir.resolve("self-test.contract.json"));
        assertTrue(sidecar.contains("\"lifecycle_status\" : \"READY\""));
        assertTrue(sidecar.contains("\"missing_evidence_count\" : 1"));
        assertTrue(sidecar.contains("Runtime screenshot requires WeChat devtools."));
    }

    @Test
    void failsWhenLifecycleStatusConflictsWithDecision() throws Exception {
        var outputRef = writeOutput("acceptance.md", """
                <dual-artifact type="acceptance_review" version="1">
                {
                  "artifact_type": "acceptance_review",
                  "schema_version": 1,
                  "lifecycle_status": "REJECTED",
                  "decision": "ACCEPT",
                  "summary": "Accepted.",
                  "blocking": false,
                  "checks": ["inspected diff"],
                  "evidence": ["diff matches scope"],
                  "missing_evidence": [],
                  "must_fix": [],
                  "acceptance_notes": [],
                  "open_risks": [],
                  "changed_files": ["docs/example.md"],
                  "verification_summary": "Sufficient.",
                  "failure": {
                    "category_hint": "",
                    "contract_gaps": [],
                    "out_of_scope_files": [],
                    "missing_tests": [],
                    "context_gaps": [],
                    "reason": ""
                  }
                }
                </dual-artifact>

                ```dual-control
                decision: ACCEPT
                summary: Accepted.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        var validated = validator.validate(tempDir, agentNode("acceptance.codex_verifier_review"), result);

        assertEquals(NodeStatus.FAILED, validated.status());
        assertTrue(String.valueOf(validated.result().get("message"))
                .contains("STRUCTURED_ARTIFACT_STATUS_MISMATCH"));
    }

    @Test
    void failsWhenLifecycleStatusIsUnknown() throws Exception {
        var outputRef = writeOutput("acceptance.md", """
                <dual-artifact type="acceptance_review" version="1">
                {
                  "artifact_type": "acceptance_review",
                  "schema_version": 1,
                  "lifecycle_status": "MOSTLY_OK",
                  "decision": "ACCEPT",
                  "summary": "Accepted.",
                  "blocking": false,
                  "checks": ["inspected diff"],
                  "evidence": ["diff matches scope"],
                  "missing_evidence": [],
                  "must_fix": [],
                  "acceptance_notes": [],
                  "open_risks": [],
                  "changed_files": ["docs/example.md"],
                  "verification_summary": "Sufficient.",
                  "failure": {
                    "category_hint": "",
                    "contract_gaps": [],
                    "out_of_scope_files": [],
                    "missing_tests": [],
                    "context_gaps": [],
                    "reason": ""
                  }
                }
                </dual-artifact>

                ```dual-control
                decision: ACCEPT
                summary: Accepted.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        var validated = validator.validate(tempDir, agentNode("acceptance.codex_verifier_review"), result);

        assertEquals(NodeStatus.FAILED, validated.status());
        assertTrue(String.valueOf(validated.result().get("message"))
                .contains("STRUCTURED_ARTIFACT_UNKNOWN_STATUS"));
    }

    @Test
    void failsWhenControlDecisionDiffersFromStructuredArtifactDecision() throws Exception {
        var outputRef = writeOutput("acceptance.md", """
                <dual-artifact type="acceptance_review" version="1">
                {
                  "artifact_type": "acceptance_review",
                  "schema_version": 1,
                  "decision": "ACCEPT",
                  "summary": "Accepted.",
                  "blocking": false,
                  "checks": [],
                  "evidence": [],
                  "missing_evidence": [],
                  "must_fix": [],
                  "acceptance_notes": [],
                  "failure": {
                    "category_hint": "",
                    "contract_gaps": [],
                    "out_of_scope_files": [],
                    "missing_tests": [],
                    "context_gaps": [],
                    "reason": ""
                  }
                }
                </dual-artifact>

                ```dual-control
                decision: REJECT
                summary: Rejected.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        var validated = validator.validate(tempDir, agentNode("acceptance.codex_verifier_review"), result);

        assertEquals(NodeStatus.FAILED, validated.status());
        assertEquals("AGENT_OUTPUT_CONTRACT_VIOLATION", validated.outcome());
        assertTrue(String.valueOf(validated.result().get("message")).contains("CONTROL_ARTIFACT_DECISION_MISMATCH"));
    }

    @Test
    void sidecarIncludesStructuredVerificationEvidence() throws Exception {
        var outputRef = writeOutput("self-test.md", """
                # Self-Test

                ## Checks Run

                - Markdown checks must be ignored.

                <dual-artifact type="self_test_report" version="1">
                {
                  "artifact_type": "self_test_report",
                  "schema_version": 1,
                  "decision": "SELF_TEST_COMPLETE",
                  "summary": "Self-test completed with device validation noted as a release risk.",
                  "checks_run": ["npm test", "inspected changed route manually"],
                  "checks_skipped": ["device smoke: simulator unavailable"],
                  "evidence": ["Test output showed 24 passing tests."],
                  "missing_evidence": [],
                  "acceptance_notes": ["Runtime visual check should happen before release."],
                  "verification_summary": "Structured self-test summary"
                }
                </dual-artifact>

                ```dual-control
                decision: SELF_TEST_COMPLETE
                summary: Self-test completed with device validation noted as a release risk.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "claude"));
        validator.validate(tempDir, agentNode("implement.claude_self_test_report"), result);

        var sidecar = Files.readString(tempDir.resolve("self-test.contract.json"));
        assertTrue(sidecar.contains("\"checks_run\""));
        assertTrue(sidecar.contains("npm test"));
        assertTrue(!sidecar.contains("Markdown checks must be ignored."));
        assertTrue(sidecar.contains("\"checks_skipped\""));
        assertTrue(sidecar.contains("simulator unavailable"));
        assertTrue(sidecar.contains("\"evidence\""));
        assertTrue(sidecar.contains("24 passing tests"));
        assertTrue(sidecar.contains("\"missing_evidence\" : [ ]"));
        assertTrue(sidecar.contains("\"acceptance_notes\""));
        assertTrue(sidecar.contains("Runtime visual check should happen before release."));
        assertTrue(sidecar.contains("\"source_artifact_type\" : \"self_test_report\""));
    }

    @Test
    void failsMissingRequiredControlKey() throws Exception {
        var outputRef = writeOutput("review.md", """
                Review passed.

                ```dual-control
                decision: READY_FOR_ARCH_REVIEW
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        var validated = validator.validate(tempDir, agentNode("design.codex_arch_review"), result);

        assertEquals(NodeStatus.FAILED, validated.status());
        assertEquals("AGENT_OUTPUT_CONTRACT_VIOLATION", validated.outcome());
        assertEquals("AGENT_OUTPUT_CONTRACT_VIOLATION", validated.result().get("reason"));
        assertTrue(String.valueOf(validated.result().get("message")).contains("summary"));
    }

    @Test
    void ignoresPreparedAgentRun() throws Exception {
        var result = NodeResult.completed("completed", List.of("prompts/design.md"), Map.of("prepared", true));
        var validated = validator.validate(tempDir, agentNode("design.codex_arch_review"), result);

        assertEquals(result, validated);
    }

    @Test
    void ignoresNonAgentTaskHandler() throws Exception {
        var result = NodeResult.completed("completed", List.of(), Map.of("contract_status", "READY_FOR_REVIEW"));
        var node = new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile", Map.of());

        assertEquals(result, validator.validate(tempDir, node, result));
    }

    private String writeOutput(String name, String content) throws Exception {
        var path = tempDir.resolve(name);
        Files.writeString(path, content);
        return tempDir.relativize(path).toString();
    }

    private static NodeDefinition agentNode(String nodeId) {
        return new NodeDefinition(nodeId, NodeKind.TASK, "acpx.agent_run", Map.of());
    }
}
