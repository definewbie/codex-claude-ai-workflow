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

                <dual-artifact type="agent_output_contract" version="1">
                {
                  "artifact_type": "agent_output_contract",
                  "schema_version": 1,
                  "lifecycle_status": "READY",
                  "decision": "READY_FOR_ARCH_REVIEW",
                  "summary": "Architecture is ready.",
                  "blocking": false,
                  "checks": ["reviewed design"],
                  "evidence": ["design matches contract"],
                  "missing_evidence": [],
                  "must_fix": [],
                  "acceptance_notes": [],
                  "open_risks": [],
                  "changed_files": [],
                  "verification_summary": "Architecture review is complete.",
                  "failure": {"category_hint":"","contract_gaps":[],"out_of_scope_files":[],"missing_tests":[],"context_gaps":[],"reason":""}
                }
                </dual-artifact>

                ```dual-control
                decision: READY_FOR_ARCH_REVIEW
                summary: Architecture is ready.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        var validated = validator.validate(tempDir, agentNode("design.codex_arch_review"), result);

        assertEquals(NodeStatus.COMPLETED, validated.status());
        assertEquals("completed", validated.outcome());
        assertTrue(validated.outputRefs().contains(outputRef));
        assertTrue(validated.outputRefs().contains("review.contract.json"));
    }

    @Test
    void writesContractSidecarForValidAgentOutput() throws Exception {
        var outputRef = writeOutput("review.md", """
                Review passed.

                <dual-artifact type="agent_output_contract" version="1">
                {
                  "artifact_type": "agent_output_contract",
                  "schema_version": 1,
                  "lifecycle_status": "READY",
                  "decision": "READY_FOR_ARCH_REVIEW",
                  "summary": "Architecture is ready.",
                  "blocking": false,
                  "checks": ["reviewed design"],
                  "evidence": ["design matches contract"],
                  "missing_evidence": [],
                  "must_fix": [],
                  "acceptance_notes": [],
                  "open_risks": [],
                  "changed_files": [],
                  "verification_summary": "Architecture review is complete.",
                  "failure": {"category_hint":"","contract_gaps":[],"out_of_scope_files":[],"missing_tests":[],"context_gaps":[],"reason":""}
                }
                </dual-artifact>

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
    void passesValidTaskContractProposalArtifact() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# Sample\n");
        var outputRef = writeOutput("intake.md", """
                <dual-artifact type="task_contract_proposal" version="1">
                {
                  "artifact_type": "task_contract_proposal",
                  "schema_version": 1,
                  "objective": "Update the sample workflow.",
                  "scope": {
                    "included": ["README.md"],
                    "excluded": ["new: out-of-scope.md"]
                  },
                  "acceptance_criteria": [
                    "README describes the changed workflow.",
                    "No unrelated files are changed."
                  ],
                  "test_requirements": ["Inspect README diff."],
                  "constraints": ["Keep the change documentation-only."],
                  "non_goals": ["Do not edit application code."],
                  "risk_areas": ["Scope creep"],
                  "evidence_refs": ["README.md"],
                  "open_questions": []
                }
                </dual-artifact>

                ```dual-control
                decision: READY_FOR_PRODUCT_REVIEW
                summary: Task contract proposal is ready.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "claude"));

        var validated = validator.validate(
                tempDir,
                agentNode("intake.claude_brainstorm", "task_contract_proposal"),
                result
        );

        assertEquals(NodeStatus.COMPLETED, validated.status());
        assertTrue(validated.outputRefs().contains("intake.contract.json"));
        var sidecar = Files.readString(tempDir.resolve("intake.contract.json"));
        assertTrue(sidecar.contains("\"source_artifact_type\" : \"task_contract_proposal\""));
        assertTrue(sidecar.contains("\"decision\" : \"READY_FOR_PRODUCT_REVIEW\""));
        assertTrue(sidecar.contains("Task contract proposal emitted and mechanically validated."));
    }

    @Test
    void failsWhenTaskContractProposalArtifactIsMissing() throws Exception {
        var outputRef = writeOutput("intake.md", """
                ```dual-control
                decision: READY_FOR_PRODUCT_REVIEW
                summary: Task contract proposal is ready.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "claude"));

        var validated = validator.validate(
                tempDir,
                agentNode("intake.claude_brainstorm", "task_contract_proposal"),
                result
        );

        assertEquals(NodeStatus.FAILED, validated.status());
        assertEquals("AGENT_OUTPUT_CONTRACT_VIOLATION", validated.outcome());
        assertTrue(String.valueOf(validated.result().get("message")).contains("task_contract_proposal"));
    }

    @Test
    void positiveDecisionWithMustFixFailsContractEvenWhenTextClaimsNoBlockingIssue() throws Exception {
        var outputRef = writeOutput("review.md", """
                # Review

                <dual-artifact type="agent_output_contract" version="1">
                {
                  "artifact_type": "agent_output_contract",
                  "schema_version": 1,
                  "lifecycle_status": "READY",
                  "decision": "READY_FOR_PRODUCT_REVIEW",
                  "summary": "Intake is ready.",
                  "blocking": false,
                  "checks": ["reviewed requirement"],
                  "evidence": ["requirement is understood"],
                  "missing_evidence": [],
                  "must_fix": ["None. The proposal preserves the user intent."],
                  "acceptance_notes": [],
                  "open_risks": ["Modal wording must stay non-medical."],
                  "changed_files": [],
                  "verification_summary": "Product review is complete.",
                  "failure": {"category_hint":"","contract_gaps":[],"out_of_scope_files":[],"missing_tests":[],"context_gaps":[],"reason":""}
                }
                </dual-artifact>

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
    void updatesRunMetadataWhenRequiredStructuredArtifactIsMissing() throws Exception {
        var runRef = writeRunMetadata("run-missing-artifact", "completed");
        var outputRef = writeOutput("acceptance.md", """
                # Acceptance

                ```dual-control
                decision: ACCEPT_WITH_NOTES
                summary: Accepted with verification notes.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of(
                "agent", "codex",
                "run_metadata", runRef
        ));

        var validated = validator.validate(tempDir, agentNode("acceptance.codex_verifier_review"), result);

        assertEquals(NodeStatus.FAILED, validated.status());
        var runMetadata = Files.readString(tempDir.resolve(runRef));
        assertTrue(runMetadata.contains("\"schemaVersion\" : 3"));
        assertTrue(runMetadata.contains("\"status\" : \"failed\""));
        assertTrue(runMetadata.contains("\"failureReason\" : \"required_artifact_contract_failed\""));
        assertTrue(runMetadata.contains("\"executionOutcome\""));
        assertTrue(runMetadata.contains("\"status\" : \"CONTRACT_FAILED\""));
        assertTrue(runMetadata.contains("\"recoveryCategory\" : \"ARTIFACT_CONTRACT_FAILURE\""));
        assertTrue(runMetadata.contains("\"terminalControlResult\""));
        assertTrue(runMetadata.contains("\"artifactContractResults\""));
        assertTrue(runMetadata.contains("\"present\" : false"));
        assertTrue(runMetadata.contains("\"valid\" : false"));
    }

    @Test
    void updatesRunMetadataWhenAgentOutputContractIsValid() throws Exception {
        var runRef = writeRunMetadata("run-valid-contract", "completed");
        var outputRef = writeOutput("review.md", """
                Review passed.

                <dual-artifact type="agent_output_contract" version="1">
                {
                  "artifact_type": "agent_output_contract",
                  "schema_version": 1,
                  "lifecycle_status": "READY",
                  "decision": "READY_FOR_ARCH_REVIEW",
                  "summary": "Architecture is ready.",
                  "blocking": false,
                  "checks": ["reviewed design"],
                  "evidence": ["design matches contract"],
                  "missing_evidence": [],
                  "must_fix": [],
                  "acceptance_notes": [],
                  "open_risks": [],
                  "changed_files": [],
                  "verification_summary": "Architecture review is complete.",
                  "failure": {"category_hint":"","contract_gaps":[],"out_of_scope_files":[],"missing_tests":[],"context_gaps":[],"reason":""}
                }
                </dual-artifact>

                ```dual-control
                decision: READY_FOR_ARCH_REVIEW
                summary: Architecture is ready.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of(
                "agent", "codex",
                "run_metadata", runRef
        ));

        var validated = validator.validate(tempDir, agentNode("design.codex_arch_review"), result);

        assertEquals(NodeStatus.COMPLETED, validated.status());
        var runMetadata = Files.readString(tempDir.resolve(runRef));
        assertTrue(runMetadata.contains("\"schemaVersion\" : 3"));
        assertTrue(runMetadata.contains("\"status\" : \"completed\""));
        assertTrue(runMetadata.contains("\"executionOutcome\""));
        assertTrue(runMetadata.contains("\"status\" : \"SUCCESS\""));
        assertTrue(runMetadata.contains("\"nodeMaySucceed\" : true"));
        assertTrue(runMetadata.contains("\"terminalControlResult\""));
        assertTrue(runMetadata.contains("\"artifactContractResults\""));
        assertTrue(runMetadata.contains("\"present\" : true"));
        assertTrue(runMetadata.contains("\"valid\" : true"));
    }

    @Test
    void writesOutcomeForFailedAgentRunInsteadOfSkippingValidation() throws Exception {
        var runRef = writeRunMetadata("run-failed-incomplete", "failed");
        var stderrRef = writeOutput("review.stderr.log", "agent needs reconnect");
        var result = new NodeResult(
                "RECOVERABLE_AGENT_RECONNECT",
                NodeStatus.FAILED,
                List.of(stderrRef),
                Map.of(
                        "agent", "codex",
                        "run_metadata", runRef,
                        "exit_code", 5,
                        "acpx_failure.reason", "RECOVERABLE_AGENT_RECONNECT"
                ),
                "agent needs reconnect"
        );

        var validated = validator.validate(tempDir, agentNode("design.codex_arch_review"), result);

        assertEquals(NodeStatus.FAILED, validated.status());
        var runMetadata = Files.readString(tempDir.resolve(runRef));
        assertTrue(runMetadata.contains("\"schemaVersion\" : 3"));
        assertTrue(runMetadata.contains("\"executionOutcome\""));
        assertTrue(runMetadata.contains("\"status\" : \"OUTPUT_INCOMPLETE\""));
        assertTrue(runMetadata.contains("\"type\" : \"AGENT_RECONNECT_REQUIRED\""));
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
    void planReviewUsesStructuredAgentOutputContractInsteadOfMarkdownSections() throws Exception {
        var outputRef = writeOutput("plan-review.md", """
                **Must Fix Before Implementation**

                - This bold markdown section is human-readable only.

                <dual-artifact type="agent_output_contract" version="1">
                {
                  "artifact_type": "agent_output_contract",
                  "schema_version": 1,
                  "lifecycle_status": "NEEDS_REVISION",
                  "decision": "NEEDS_REVISION",
                  "summary": "Plan commands must be corrected before implementation.",
                  "blocking": true,
                  "checks": ["reviewed plan"],
                  "evidence": ["plan includes raw shell commands"],
                  "missing_evidence": [],
                  "must_fix": ["Rewrite shell commands with rtk prefixes."],
                  "acceptance_notes": [],
                  "open_risks": ["Executor may run unguarded commands."],
                  "changed_files": [],
                  "verification_summary": "Plan is not execution-ready.",
                  "failure": {
                    "category_hint": "PLAN_EXECUTION_DISCIPLINE",
                    "contract_gaps": ["Raw command discipline is missing."],
                    "out_of_scope_files": [],
                    "missing_tests": [],
                    "context_gaps": [],
                    "reason": "Plan needs revision before implementation."
                  }
                }
                </dual-artifact>

                ```dual-control
                decision: NEEDS_REVISION
                summary: Plan commands must be corrected before implementation.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        var validated = validator.validate(tempDir, agentNode("plan.codex_plan_review", "agent_output_contract"), result);

        assertEquals(NodeStatus.COMPLETED, validated.status());
        var sidecar = Files.readString(tempDir.resolve("plan-review.contract.json"));
        assertTrue(sidecar.contains("Rewrite shell commands with rtk prefixes."));
        assertTrue(sidecar.contains("\"source_artifact_type\" : \"agent_output_contract\""));
        assertTrue(sidecar.contains("\"evidence_count\" : 1"));
        assertTrue(!sidecar.contains("This bold markdown section is human-readable only."));
    }

    @Test
    void latestStructuredAgentOutputContractIsAuthoritativeAndDoesNotFallBack() throws Exception {
        var outputRef = writeOutput("plan-review.md", """
                <dual-artifact type="agent_output_contract" version="1">
                {
                  "artifact_type": "agent_output_contract",
                  "schema_version": 1,
                  "lifecycle_status": "NEEDS_REVISION",
                  "decision": "NEEDS_REVISION",
                  "summary": "Earlier quoted artifact should not be trusted.",
                  "blocking": true,
                  "checks": [],
                  "evidence": [],
                  "missing_evidence": [],
                  "must_fix": ["Earlier must-fix."],
                  "acceptance_notes": [],
                  "open_risks": [],
                  "changed_files": [],
                  "verification_summary": "",
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

                <dual-artifact type="agent_output_contract" version="1">
                {
                  "artifact_type": "agent_output_contract",
                  "schema_version": 1,
                  "lifecycle_status": "NEEDS_REVISION",
                  "decision": "NEEDS_REVISION",
                  "summary": "Latest artifact is malformed semantically.",
                  "blocking": false,
                  "checks": [],
                  "evidence": [],
                  "missing_evidence": [],
                  "must_fix": [],
                  "acceptance_notes": [],
                  "open_risks": [],
                  "changed_files": [],
                  "verification_summary": "",
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
                decision: NEEDS_REVISION
                summary: Latest artifact is malformed semantically.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        var validated = validator.validate(tempDir, agentNode("plan.codex_plan_review", "agent_output_contract"), result);

        assertEquals(NodeStatus.FAILED, validated.status());
        assertTrue(String.valueOf(validated.result().get("message"))
                .contains("NEGATIVE_STATUS_MISSING_EVIDENCE"));
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
    void acceptsRejectedAcceptanceReviewWhenLifecycleStatusMeansInsufficientEvidence() throws Exception {
        var outputRef = writeOutput("acceptance.md", """
                <dual-artifact type="acceptance_review" version="1">
                {
                  "artifact_type": "acceptance_review",
                  "schema_version": 1,
                  "lifecycle_status": "INSUFFICIENT_EVIDENCE",
                  "decision": "REJECT",
                  "summary": "Rejected because the generated evidence does not prove the implementation.",
                  "blocking": true,
                  "checks": ["inspected generated git evidence"],
                  "evidence": ["acceptance review found generated evidence files"],
                  "missing_evidence": ["git evidence reports zero changed files while branch diff has implementation commits"],
                  "must_fix": ["Regenerate git evidence from the workstream base commit."],
                  "acceptance_notes": [],
                  "open_risks": [],
                  "changed_files": [],
                  "verification_summary": "Evidence is insufficient for acceptance.",
                  "failure": {
                    "category_hint": "evidence_gap",
                    "contract_gaps": [],
                    "out_of_scope_files": [],
                    "missing_tests": ["branch diff evidence"],
                    "context_gaps": [],
                    "reason": "Generated evidence is empty."
                  }
                }
                </dual-artifact>

                ```dual-control
                decision: REJECT
                summary: Rejected because generated evidence is insufficient.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        var validated = validator.validate(tempDir, agentNode("acceptance.codex_verifier_review"), result);

        assertEquals(NodeStatus.COMPLETED, validated.status());
        var sidecar = Files.readString(tempDir.resolve("acceptance.contract.json"));
        assertTrue(sidecar.contains("\"decision\" : \"REJECT\""));
        assertTrue(sidecar.contains("\"stage_outcome\" : \"INSUFFICIENT_EVIDENCE\""));
        assertTrue(sidecar.contains("\"lifecycle_status\" : \"INSUFFICIENT_EVIDENCE\""));
        assertTrue(sidecar.contains("git evidence reports zero changed files"));
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

    @Test
    void rejectsPlanOutputWhenFormalPlanBodyIsMissing() throws Exception {
        var outputRef = writeOutput("plan.md", """
                <dual-artifact type="agent_output_contract" version="1">
                {
                  "artifact_type": "agent_output_contract",
                  "schema_version": 1,
                  "lifecycle_status": "READY",
                  "decision": "READY_FOR_PLAN_REVIEW",
                  "summary": "Plan is ready for review.",
                  "blocking": false,
                  "checks": ["reviewed requirements"],
                  "evidence": ["contract reviewed"],
                  "missing_evidence": [],
                  "must_fix": [],
                  "acceptance_notes": [],
                  "open_risks": [],
                  "changed_files": [],
                  "verification_summary": "Plan will be validated during implementation.",
                  "failure": {"category_hint":"","contract_gaps":[],"out_of_scope_files":[],"missing_tests":[],"context_gaps":[],"reason":""}
                }
                </dual-artifact>

                ```dual-control
                decision: READY_FOR_PLAN_REVIEW
                summary: Plan is ready for review.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "claude"));

        var validated = validator.validate(tempDir, agentNode("plan.claude_plan", "agent_output_contract"), result);

        assertEquals(NodeStatus.FAILED, validated.status());
        assertEquals("AGENT_OUTPUT_CONTRACT_VIOLATION", validated.outcome());
        assertTrue(String.valueOf(validated.result().get("parse_error")).contains("FORMAL_ARTIFACT_BODY_MISSING"));
        assertTrue(String.valueOf(validated.result().get("message")).contains("Implementation Steps"));
    }

    @Test
    void acceptsPlanOutputWhenFormalPlanBodyContainsRequiredSections() throws Exception {
        var outputRef = writeOutput("plan.md", """
                # Implementation Steps

                1. Update the follow-up summary query boundary.

                # File-Level Changes

                - `src/followup.js`: add the reminder filter.

                # Validation Plan

                - Run the targeted follow-up summary tests.

                # Rollback Notes

                - Revert the query change if validation fails.

                # Known Risks

                - Existing records may not include reminder metadata.

                # Human Review Checklist

                - Confirm reminder wording and affected patient records.

                <dual-artifact type="agent_output_contract" version="1">
                {
                  "artifact_type": "agent_output_contract",
                  "schema_version": 1,
                  "lifecycle_status": "READY",
                  "decision": "READY_FOR_PLAN_REVIEW",
                  "summary": "Plan is ready for review.",
                  "blocking": false,
                  "checks": ["reviewed requirements"],
                  "evidence": ["formal plan body is present"],
                  "missing_evidence": [],
                  "must_fix": [],
                  "acceptance_notes": [],
                  "open_risks": [],
                  "changed_files": [],
                  "verification_summary": "Plan will be validated during implementation.",
                  "failure": {"category_hint":"","contract_gaps":[],"out_of_scope_files":[],"missing_tests":[],"context_gaps":[],"reason":""}
                }
                </dual-artifact>

                ```dual-control
                decision: READY_FOR_PLAN_REVIEW
                summary: Plan is ready for review.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "claude"));

        var validated = validator.validate(tempDir, agentNode("plan.claude_plan", "agent_output_contract"), result);

        assertEquals(NodeStatus.COMPLETED, validated.status());
        assertTrue(validated.outputRefs().contains("plan.contract.json"));
    }

    private String writeOutput(String name, String content) throws Exception {
        var path = tempDir.resolve(name);
        Files.writeString(path, content);
        return tempDir.relativize(path).toString();
    }

    private String writeRunMetadata(String runId, String status) throws Exception {
        var path = tempDir.resolve(".agent/workstreams/ws-test/runs").resolve(runId).resolve("run.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
                {
                  "schemaVersion": 2,
                  "runId": "%s",
                  "status": "%s"
                }
                """.formatted(runId, status));
        return tempDir.relativize(path).toString();
    }

    @Test
    void rereadReasonsFlowFromEnvelopeToSidecarAndRunMetadata() throws Exception {
        var runRef = writeRunMetadata("run-reread-flow", "completed");
        var outputRef = writeOutput("plan-reread.md", """
                <dual-artifact type="agent_output_contract" version="1">
                {
                  "artifact_type": "agent_output_contract",
                  "schema_version": 1,
                  "lifecycle_status": "NEEDS_REVISION",
                  "decision": "NEEDS_REVISION",
                  "summary": "Plan needs current file contents before implementation.",
                  "blocking": true,
                  "checks": [], "evidence": [], "missing_evidence": [],
                  "must_fix": ["Rewrite shell commands."],
                  "acceptance_notes": [], "open_risks": [], "changed_files": [],
                  "verification_summary": "Plan is not execution-ready.",
                  "failure": {"category_hint":"PLAN_EXECUTION_DISCIPLINE","contract_gaps":["gap"],"out_of_scope_files":[],"missing_tests":[],"context_gaps":[],"reason":"needs revision"},
                  "reread_reasons": [
                    {"path":"src/executor.js","reason":"exact current content is needed before editing","stage":"plan","evidence":"editing command dispatch"}
                  ]
                }
                </dual-artifact>

                ```dual-control
                decision: NEEDS_REVISION
                summary: Plan needs current file contents before implementation.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex", "run_metadata", runRef));

        var validated = validator.validate(tempDir, agentNode("plan.codex_plan_review", "agent_output_contract"), result);

        assertEquals(NodeStatus.COMPLETED, validated.status());
        var sidecar = Files.readString(tempDir.resolve((String) validated.result().get("contract_sidecar_ref")));
        assertTrue(sidecar.contains("reread_reasons"), sidecar);
        assertTrue(sidecar.contains("src/executor.js"), sidecar);
        var runMetadata = Files.readString(tempDir.resolve(runRef));
        assertTrue(runMetadata.contains("reread_reasons"), runMetadata);
        assertTrue(runMetadata.contains("src/executor.js"), runMetadata);
    }

    @Test
    void invalidRereadReasonsAreRejected() throws Exception {
        var runRef = writeRunMetadata("run-reread-bad", "completed");
        var outputRef = writeOutput("plan-badreread.md", """
                <dual-artifact type="agent_output_contract" version="1">
                {
                  "artifact_type": "agent_output_contract",
                  "schema_version": 1,
                  "lifecycle_status": "NEEDS_REVISION",
                  "decision": "NEEDS_REVISION",
                  "summary": "Plan needs revision.",
                  "blocking": true,
                  "checks": [], "evidence": [], "missing_evidence": [],
                  "must_fix": ["Rewrite shell commands."],
                  "acceptance_notes": [], "open_risks": [], "changed_files": [],
                  "verification_summary": "Plan is not execution-ready.",
                  "failure": {"category_hint":"PLAN_EXECUTION_DISCIPLINE","contract_gaps":["gap"],"out_of_scope_files":[],"missing_tests":[],"context_gaps":[],"reason":"needs revision"},
                  "reread_reasons": [
                    {"path":"src/executor.js","reason":"because I felt like it"}
                  ]
                }
                </dual-artifact>

                ```dual-control
                decision: NEEDS_REVISION
                summary: Plan needs revision.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex", "run_metadata", runRef));

        var validated = validator.validate(tempDir, agentNode("plan.codex_plan_review", "agent_output_contract"), result);

        assertEquals(NodeStatus.FAILED, validated.status());
    }

    @Test
    void rejectsReusableFileRereadWithoutMatchingRereadReason() throws Exception {
        writeReadReuseContract("src/executor.js");
        var runRef = writeRunMetadataWithReads("run-reread-missing", "src/executor.js");
        var outputRef = writeOutput("implement.md", """
                <dual-artifact type="agent_output_contract" version="1">
                {
                  "artifact_type": "agent_output_contract",
                  "schema_version": 1,
                  "lifecycle_status": "READY",
                  "decision": "IMPLEMENTED",
                  "summary": "Implementation complete.",
                  "blocking": false,
                  "checks": ["edited executor"],
                  "evidence": ["implementation updated"],
                  "missing_evidence": [],
                  "must_fix": [],
                  "acceptance_notes": [],
                  "open_risks": [],
                  "changed_files": ["src/executor.js"],
                  "verification_summary": "Ready for self-test.",
                  "failure": {"category_hint":"","contract_gaps":[],"out_of_scope_files":[],"missing_tests":[],"context_gaps":[],"reason":""},
                  "reread_reasons": []
                }
                </dual-artifact>

                ```dual-control
                decision: IMPLEMENTED
                summary: Implementation complete.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of(
                "agent", "claude",
                "run_metadata", runRef
        ));

        var validated = validator.validate(tempDir, "ws-test", "node-1",
                agentNode("implement.claude_implement", "agent_output_contract"), result);

        assertEquals(NodeStatus.FAILED, validated.status());
        assertTrue(String.valueOf(validated.result().get("parse_error")).contains("REREAD_REASON_MISSING"));
        assertTrue(String.valueOf(validated.result().get("message")).contains("src/executor.js"));
    }

    @Test
    void acceptsReusableFileRereadWithMatchingRereadReason() throws Exception {
        writeReadReuseContract("src/executor.js");
        var runRef = writeRunMetadataWithReads("run-reread-recorded", "src/executor.js");
        var outputRef = writeOutput("implement.md", """
                <dual-artifact type="agent_output_contract" version="1">
                {
                  "artifact_type": "agent_output_contract",
                  "schema_version": 1,
                  "lifecycle_status": "READY",
                  "decision": "IMPLEMENTED",
                  "summary": "Implementation complete.",
                  "blocking": false,
                  "checks": ["edited executor"],
                  "evidence": ["implementation updated"],
                  "missing_evidence": [],
                  "must_fix": [],
                  "acceptance_notes": [],
                  "open_risks": [],
                  "changed_files": ["src/executor.js"],
                  "verification_summary": "Ready for self-test.",
                  "failure": {"category_hint":"","contract_gaps":[],"out_of_scope_files":[],"missing_tests":[],"context_gaps":[],"reason":""},
                  "reread_reasons": [
                    {"path":"src/executor.js","reason":"exact current content is needed before editing","stage":"implement","evidence":"editing executor"}
                  ]
                }
                </dual-artifact>

                ```dual-control
                decision: IMPLEMENTED
                summary: Implementation complete.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of(
                "agent", "claude",
                "run_metadata", runRef
        ));

        var validated = validator.validate(tempDir, "ws-test", "node-1",
                agentNode("implement.claude_implement", "agent_output_contract"), result);

        assertEquals(NodeStatus.COMPLETED, validated.status());
    }

    private static NodeDefinition agentNode(String nodeId) {
        return new NodeDefinition(nodeId, NodeKind.TASK, "acpx.agent_run", Map.of());
    }

    private static NodeDefinition agentNode(String nodeId, String expectedArtifact) {
        return new NodeDefinition(
                nodeId,
                NodeKind.TASK,
                "acpx.agent_run",
                Map.of("expected_artifact", expectedArtifact)
        );
    }

    private String writeRunMetadataWithReads(String runId, String... readFiles) throws Exception {
        var path = tempDir.resolve(".agent/workstreams/ws-test/runs").resolve(runId).resolve("run.json");
        Files.createDirectories(path.getParent());
        var reads = java.util.Arrays.stream(readFiles)
                .map(file -> "\"" + file + "\"")
                .collect(java.util.stream.Collectors.joining(", "));
        Files.writeString(path, """
                {
                  "schemaVersion": 3,
                  "runId": "%s",
                  "status": "completed",
                  "toolUsage": {
                    "readFiles": [%s]
                  }
                }
                """.formatted(runId, reads));
        return tempDir.relativize(path).toString();
    }

    private void writeReadReuseContract(String... reusableFiles) throws Exception {
        var path = tempDir.resolve(".agent/workstreams/ws-test/memory/read-reuse-contract.json");
        Files.createDirectories(path.getParent());
        var files = java.util.Arrays.stream(reusableFiles)
                .map(file -> "{\"path\":\"" + file + "\",\"summary\":\"previously read\",\"trusted\":true}")
                .collect(java.util.stream.Collectors.joining(", "));
        Files.writeString(path, """
                {
                  "schemaVersion": 1,
                  "reusableReadFiles": [%s]
                }
                """.formatted(files));
    }
}
