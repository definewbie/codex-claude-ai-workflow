package dev.dual.runtime.handler;

import dev.dual.core.contract.ContractStatus;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractCompileHandlerTest {

    private static final String WORKSTREAM_ID = "ws-test-001";

    @Test
    void handlerTypeIsContractCompile() {
        var handler = new ContractCompileHandler();
        assertEquals("contract.compile", handler.type());
    }

    @Test
    void compileProducesReadyForReviewContract(@TempDir Path tempDir) throws Exception {
        var handler = new ContractCompileHandler();
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve(WORKSTREAM_ID);
        Files.createDirectories(workstreamDir);

        var requirementText = """
                # Feature Request

                Add user authentication with JWT tokens

                Files to modify: `src/auth/Login.java`, `src/auth/AuthService.java`

                Constraints: Max 200 lines per file, no external dependencies
                """;
        Files.writeString(workstreamDir.resolve("requirement.md"), requirementText, StandardCharsets.UTF_8);

        var context = new TaskHandler.TaskContext(
                tempDir,
                WORKSTREAM_ID,
                new NodeDefinition("node-1", NodeKind.TASK, "contract.compile", Map.of()),
                new NodeInstance(
                        "inst-1", "node-1", NodeKind.TASK, "contract.compile",
                        NodeStatus.READY, "stage-1", "iter-1",
                        List.of(), List.of(), Map.of(), "run-1",
                        Instant.now(), null
                )
        );

        var result = handler.execute(context);

        // Verify result structure
        assertEquals("completed", result.outcome());
        assertEquals(NodeStatus.COMPLETED, result.status());
        assertNotNull(result.result());

        // Verify result fields
        assertEquals("tc-ws-test-001-v1", result.result().get("contract_id"));
        assertEquals(ContractStatus.READY_FOR_REVIEW.name(), result.result().get("contract_status"));
        assertEquals(1, result.result().get("contract_version"));
        assertEquals(1, result.result().get("compilation_pass"));
        assertNotNull(result.result().get("generated_at"));

        // Verify artifacts exist
        var artifactsDir = workstreamDir.resolve("artifacts");
        assertTrue(Files.exists(artifactsDir.resolve("task-contract.json")),
                "task-contract.json should exist");
        assertTrue(Files.exists(artifactsDir.resolve("task-contract.md")),
                "task-contract.md should exist");

        // Verify contract content via JSON
        var json = Files.readString(artifactsDir.resolve("task-contract.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("READY_FOR_REVIEW"), "Contract should be READY_FOR_REVIEW");
        assertTrue(json.contains("Add user authentication with JWT tokens"),
                "Contract should contain the objective");

        // Verify markdown has sections
        var md = Files.readString(artifactsDir.resolve("task-contract.md"), StandardCharsets.UTF_8);
        assertTrue(md.contains("## Acceptance Criteria"), "Markdown should have AC section");
        assertTrue(md.contains("Implementation achieves:"), "Should have default AC");

        // Verify compile method directly
        var contract = handler.compile(requirementText, WORKSTREAM_ID);
        assertFalse(contract.objective().isBlank(), "Objective should not be blank");
        assertFalse(contract.acceptanceCriteria().isEmpty(), "AC should not be empty");
        assertEquals(ContractStatus.READY_FOR_REVIEW, contract.status());
    }

    @Test
    void executeCompilesContractFromReviewedIntakeArtifact(@TempDir Path tempDir) throws Exception {
        var handler = new ContractCompileHandler();
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve(WORKSTREAM_ID);
        var outputDir = workstreamDir.resolve("runs/run-001/outputs");
        Files.createDirectories(outputDir);

        Files.writeString(workstreamDir.resolve("requirement.md"), """
                目标：
                在真实项目中选择一个低风险小改动。
                """, StandardCharsets.UTF_8);
        var reviewRef = ".agent/workstreams/" + WORKSTREAM_ID + "/runs/run-001/outputs/intake-codex-product-review.md";
        Files.writeString(tempDir.resolve(reviewRef), """
                ## 1. Decision

                READY_FOR_PRODUCT_REVIEW

                The proposed task, adding a root `README.md`, is appropriately scoped.

                ## 6. User Acceptance Notes

                Acceptance should require:

                - A root-level `README.md` exists.
                - It explains project purpose, tech stack, local development startup, and directory structure.
                - No business code, schema, backend API, or unrelated files are modified.
                """, StandardCharsets.UTF_8);

        var context = new TaskHandler.TaskContext(
                tempDir,
                WORKSTREAM_ID,
                new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile", Map.of()),
                new NodeInstance(
                        "inst-contract", "contract.compile", NodeKind.TASK, "contract.compile",
                        NodeStatus.READY, "stage-contract", "iter-1",
                        List.of(reviewRef), List.of(), Map.of(), null,
                        Instant.now(), null
                )
        );

        NodeResult result = handler.execute(context);

        assertEquals(NodeStatus.COMPLETED, result.status());
        var json = Files.readString(workstreamDir.resolve("artifacts/task-contract.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("README.md"), "Contract should use reviewed intake artifact details");
        assertTrue(json.contains("root-level"), "Contract should include acceptance criteria from review");
        assertTrue(json.contains("\"included\" : [ \"README.md\" ]"), "Contract scope should include README.md");
        assertFalse(json.contains("\"objective\" : \"目标：\""), "Contract objective should not be the raw heading");
    }

    @Test
    void recompileStillUsesLatestIntakeArtifactsWhenInputRefsPointToOldContract(@TempDir Path tempDir) throws Exception {
        var handler = new ContractCompileHandler();
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve(WORKSTREAM_ID);
        var outputDir = workstreamDir.resolve("runs/run-001/outputs");
        var artifactsDir = workstreamDir.resolve("artifacts");
        Files.createDirectories(outputDir);
        Files.createDirectories(artifactsDir);

        Files.writeString(workstreamDir.resolve("requirement.md"), """
                目标：
                在真实项目中选择一个低风险小改动。
                """, StandardCharsets.UTF_8);
        Files.writeString(outputDir.resolve("intake-claude-brainstorm.md"), """
                ## 1. Requirement Restatement

                **选定任务：为项目根目录创建 `README.md`。**

                ## 4. Scope Boundaries

                **变更清单：** 新增 1 文件 `README.md`，约 60 行。

                ```dual-control
                decision: READY_FOR_PRODUCT_REVIEW
                summary: README task ready
                ```
                """, StandardCharsets.UTF_8);
        Files.writeString(artifactsDir.resolve("task-contract.md"), """
                # Task Contract

                ## Objective

                目标：
                """, StandardCharsets.UTF_8);

        var oldContractRef = ".agent/workstreams/" + WORKSTREAM_ID + "/artifacts/task-contract.md";
        var context = new TaskHandler.TaskContext(
                tempDir,
                WORKSTREAM_ID,
                new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile", Map.of()),
                new NodeInstance(
                        "inst-contract", "contract.compile", NodeKind.TASK, "contract.compile",
                        NodeStatus.READY, "stage-contract", "iter-1",
                        List.of(oldContractRef), List.of(), Map.of(), null,
                        Instant.now(), null
                )
        );

        handler.execute(context);

        var json = Files.readString(workstreamDir.resolve("artifacts/task-contract.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("为项目根目录创建 README.md"), "Recompile should recover objective from intake artifacts");
        assertTrue(json.contains("\"included\" : [ \"README.md\" ]"), "Recompile should recover README.md scope");
    }

    @Test
    void compileMaterializesScopeConstraintsNonGoalsRisksAndTestsFromFeatureSliceText() {
        var handler = new ContractCompileHandler();
        var sourceText = """
                # Benchmark Case: cancer-clinic-feature-slice

                ## 目标

                验证 dual-orchestrator 能否在真实业务功能切片中保持 contract、context、自测证据和 Codex acceptance 的一致性。

                ## File 1: `miniprogram/src/pages/record/index.vue`

                Add a B4 empty state guidance card for plans with no treatment records.

                ## File 2: `miniprogram/src/pages/treatment-record/index.vue`

                Add a prominent upload report button and align labels.

                ## In Scope

                - B4: New user empty state guidance card in `record/index.vue`.
                - B1: Prominent "上传报告" button in `treatment-record/index.vue`.
                - Title: "第 N 周期" -> "第 N 次治疗".

                ## Non-Goals

                - No backend changes.
                - No database migration.
                - No event chain display on treatment-record page.
                - No changes to report upload flow.

                ## Constraints

                - Do not modify backend API or schema.
                - Reuse existing `goTo('/pages/examination/index')`.
                - Exactly two frontend files should change.

                ## Risks

                | Risk | Severity | Mitigation |
                |------|----------|------------|
                | Forbidden medical assurance wording accidentally introduced | High | Grep changed files |

                ## Test Strategy

                - Run `cd miniprogram && npm run build:mp-weixin`.
                - Grep for forbidden terms.
                - Verify empty and non-empty exam states both show exactly one prominent upload button.
                """;

        var contract = handler.compile(sourceText, WORKSTREAM_ID);

        assertTrue(contract.scope().included().contains("miniprogram/src/pages/record/index.vue"));
        assertTrue(contract.scope().included().contains("miniprogram/src/pages/treatment-record/index.vue"));
        assertFalse(contract.scope().excluded().isEmpty(), "Excluded scope should be materialized");
        assertFalse(contract.constraints().isEmpty(), "Constraints should be materialized");
        assertFalse(contract.nonGoals().isEmpty(), "Non-goals should be materialized");
        assertFalse(contract.riskAreas().isEmpty(), "Risk areas should be materialized");
        assertFalse(contract.testRequirements().isEmpty(), "Test requirements should be materialized");
        assertTrue(contract.testRequirements().stream().anyMatch(value -> value.contains("build:mp-weixin")));
    }

    @Test
    void compileDoesNotLetHistoricalReadmeOrContextMetadataPolluteContract() {
        var handler = new ContractCompileHandler();
        var sourceText = """
                # Benchmark Case

                The old example task mentioned `README.md`, but the selected task changed later.

                ## Scope

                **Included:**
                - Implement status actions in `miniprogram/src/pages/treatment-event/index.vue`.

                **Excluded:**
                - No backend/schema change is needed.

                ## User Acceptance Notes

                Acceptance should require:

                - 用户可以在治疗安排详情页点击“标记完成”。
                - 用户可以选择延期日期并提交延期。
                - 不修改后端 API 或数据库 schema。

                - Workstream: ws-should-not-enter-contract
                - Builder Node: context.intake_codex_product_review
                - Generated At: 2026-06-02T22:04:54Z

                ## Context Package

                - Total: 9.8k / 4.0k tokens
                """;

        var contract = handler.compile(sourceText, WORKSTREAM_ID);

        assertEquals(
                List.of("miniprogram/src/pages/treatment-event/index.vue"),
                contract.scope().included(),
                "Historical README references should not override the concrete feature file");
        assertFalse(
                contract.acceptanceCriteria().stream().anyMatch(value -> value.contains("Workstream")),
                "Ledger metadata should not become acceptance criteria");
        assertFalse(
                contract.acceptanceCriteria().stream().anyMatch(value -> value.contains("Builder Node")),
                "Context package metadata should not become acceptance criteria");
        assertTrue(
                contract.acceptanceCriteria().stream().anyMatch(value -> value.contains("标记完成")),
                "Real acceptance criteria should be preserved");
    }

    @Test
    void executeCompilesCanonicalContractFromTaskContractProposalArtifact(@TempDir Path tempDir) throws Exception {
        var handler = new ContractCompileHandler();
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve(WORKSTREAM_ID);
        var outputDir = workstreamDir.resolve("runs/run-001/outputs");
        Files.createDirectories(outputDir);

        Files.writeString(workstreamDir.resolve("requirement.md"), """
                需求里曾经举过 `README.md` 的例子，但最终选定的是治疗事件详情页状态操作。
                """, StandardCharsets.UTF_8);

        var intakeRef = ".agent/workstreams/" + WORKSTREAM_ID + "/runs/run-001/outputs/intake-claude-brainstorm.md";
        Files.writeString(tempDir.resolve(intakeRef), """
                # Brainstorm

                Some prose can still mention `README.md` and ledger-looking metadata.

                - Workstream: ws-should-not-enter-contract
                - Builder Node: context.intake_codex_product_review

                <dual-artifact type="task_contract_proposal" version="1">
                {
                  "artifact_type": "task_contract_proposal",
                  "schema_version": 1,
                  "objective": "在治疗安排详情页添加标记完成和延期状态操作",
                  "scope": {
                    "included": ["miniprogram/src/pages/treatment-event/index.vue"],
                    "excluded": ["不改后端 API", "不改数据库 schema"]
                  },
                  "acceptance_criteria": [
                    "用户可以点击标记完成并刷新页面状态",
                    "用户可以选择延期日期并提交延期"
                  ],
                  "constraints": ["不引入无关重构"],
                  "non_goals": ["不修改 record 页面"],
                  "risk_areas": ["状态变更必须是用户手动触发"],
                  "test_requirements": ["运行 miniprogram 构建"],
                  "evidence_refs": ["miniprogram/src/pages/treatment-event/index.vue"],
                  "open_questions": []
                }
                </dual-artifact>

                ```dual-control
                decision: READY_FOR_PRODUCT_REVIEW
                summary: proposal ready
                ```
                """, StandardCharsets.UTF_8);

        Files.createDirectories(tempDir.resolve("miniprogram/src/pages/treatment-event"));
        Files.writeString(tempDir.resolve("miniprogram/src/pages/treatment-event/index.vue"), "<template />\n");

        var context = new TaskHandler.TaskContext(
                tempDir,
                WORKSTREAM_ID,
                new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile", Map.of()),
                new NodeInstance(
                        "inst-contract", "contract.compile", NodeKind.TASK, "contract.compile",
                        NodeStatus.READY, "stage-contract", "iter-1",
                        List.of(intakeRef), List.of(), Map.of(), null,
                        Instant.now(), null
                )
        );

        var result = handler.execute(context);

        assertEquals(NodeStatus.COMPLETED, result.status());
        var json = Files.readString(workstreamDir.resolve("artifacts/task-contract.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("在治疗安排详情页添加标记完成和延期状态操作"));
        assertTrue(json.contains("\"included\" : [ \"miniprogram/src/pages/treatment-event/index.vue\" ]"));
        assertFalse(json.contains("README.md"), "Historical prose references must not pollute proposal-based contract");
        assertFalse(json.contains("Workstream:"), "Ledger metadata must not pollute proposal-based contract");
        assertTrue(json.contains("\"createdBy\" : \"task_contract_proposal\""));
    }

    @Test
    void executeFailsInvalidTaskContractProposalInsteadOfFallingBackToMarkdown(@TempDir Path tempDir) throws Exception {
        var handler = new ContractCompileHandler();
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve(WORKSTREAM_ID);
        var outputDir = workstreamDir.resolve("runs/run-001/outputs");
        Files.createDirectories(outputDir);

        Files.writeString(workstreamDir.resolve("requirement.md"), """
                This prose mentions `README.md`, but an invalid proposal must not fallback to Markdown extraction.
                """, StandardCharsets.UTF_8);

        var intakeRef = ".agent/workstreams/" + WORKSTREAM_ID + "/runs/run-001/outputs/intake-claude-brainstorm.md";
        Files.writeString(tempDir.resolve(intakeRef), """
                <dual-artifact type="task_contract_proposal" version="1">
                {
                  "artifact_type": "task_contract_proposal",
                  "schema_version": 1,
                  "objective": "Bad proposal",
                  "scope": {
                    "included": ["Workstream: ws-noise"],
                    "excluded": []
                  },
                  "acceptance_criteria": [],
                  "constraints": [],
                  "non_goals": [],
                  "risk_areas": [],
                  "test_requirements": [],
                  "evidence_refs": [],
                  "open_questions": []
                }
                </dual-artifact>
                """, StandardCharsets.UTF_8);

        var context = new TaskHandler.TaskContext(
                tempDir,
                WORKSTREAM_ID,
                new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile", Map.of()),
                new NodeInstance(
                        "inst-contract", "contract.compile", NodeKind.TASK, "contract.compile",
                        NodeStatus.READY, "stage-contract", "iter-1",
                        List.of(intakeRef), List.of(), Map.of(), null,
                        Instant.now(), null
                )
        );

        var result = handler.execute(context);

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_INVALID", result.outcome());
        assertFalse(Files.exists(workstreamDir.resolve("artifacts/task-contract.json")),
                "Invalid proposal must not write canonical TaskContract");
    }
}
