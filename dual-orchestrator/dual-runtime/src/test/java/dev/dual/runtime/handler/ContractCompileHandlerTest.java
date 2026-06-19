package dev.dual.runtime.handler;

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
    void executeFailsWhenTaskContractProposalIsMissing(@TempDir Path tempDir) throws Exception {
        var handler = new ContractCompileHandler();
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve(WORKSTREAM_ID);
        Files.createDirectories(workstreamDir);

        Files.writeString(workstreamDir.resolve("requirement.md"), """
                # Feature Request

                Add user authentication with JWT tokens.
                """, StandardCharsets.UTF_8);

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

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_MISSING", result.outcome());
        assertFalse(Files.exists(workstreamDir.resolve("artifacts/task-contract.json")),
                "Strict mode must not write a canonical TaskContract without a proposal");
    }

    @Test
    void executeDoesNotCompileContractFromReviewMarkdownWithoutClaudeProposal(@TempDir Path tempDir) throws Exception {
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

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_MISSING", result.outcome());
        assertFalse(Files.exists(workstreamDir.resolve("artifacts/task-contract.json")),
                "Review prose must not become the canonical TaskContract");
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

                <dual-artifact type="task_contract_proposal" version="1">
                {
                  "artifact_type": "task_contract_proposal",
                  "schema_version": 1,
                  "objective": "为项目根目录创建 README.md",
                  "scope": {
                    "included": ["README.md"],
                    "excluded": ["不改业务代码"]
                  },
                  "acceptance_criteria": [
                    "根目录存在 README.md",
                    "README 说明项目用途和本地开发方式"
                  ],
                  "constraints": ["只新增 README.md"],
                  "non_goals": ["不修改后端 API"],
                  "risk_areas": ["不要覆盖已有文档"],
                  "test_requirements": ["检查 git diff"],
                  "evidence_refs": ["README.md"],
                  "open_questions": []
                }
                </dual-artifact>

                ```dual-control
                decision: READY_FOR_PRODUCT_REVIEW
                summary: README task ready
                ```
                """, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("README.md"), "# Fixture\n", StandardCharsets.UTF_8);
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
        assertTrue(json.contains("task_contract_proposal"));
    }

    @Test
    void executeUsesTrustedClaudeProposalWhenReviewOutputQuotesMalformedArtifact(@TempDir Path tempDir) throws Exception {
        var handler = new ContractCompileHandler();
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve(WORKSTREAM_ID);
        var outputDir = workstreamDir.resolve("runs/run-001/outputs");
        Files.createDirectories(outputDir);

        Files.writeString(workstreamDir.resolve("requirement.md"), """
                实现补录页保存后引导。
                """, StandardCharsets.UTF_8);

        var intakeRef = ".agent/workstreams/" + WORKSTREAM_ID + "/runs/run-001/outputs/intake-claude-brainstorm.md";
        Files.writeString(tempDir.resolve(intakeRef), """
                <dual-artifact type="task_contract_proposal" version="1">
                {
                  "artifact_type": "task_contract_proposal",
                  "schema_version": 1,
                  "objective": "补录既往治疗保存成功后展示引导卡片",
                  "scope": {
                    "included": ["miniprogram/src/pages/retro/index.vue"],
                    "excluded": ["不改后端 API"]
                  },
                  "acceptance_criteria": [
                    "保存成功后显示引导卡片",
                    "主按钮跳转治疗记录详情页"
                  ],
                  "constraints": ["只做 B2"],
                  "non_goals": ["不创建检查报告"],
                  "risk_areas": ["跳转目标不能选错"],
                  "test_requirements": ["运行前端构建"],
                  "evidence_refs": ["miniprogram/src/pages/retro/index.vue"],
                  "open_questions": []
                }
                </dual-artifact>
                """, StandardCharsets.UTF_8);

        var reviewRef = ".agent/workstreams/" + WORKSTREAM_ID + "/runs/run-001/outputs/intake-codex-product-review.md";
        Files.writeString(tempDir.resolve(reviewRef), """
                ## Decision

                READY_FOR_PRODUCT_REVIEW

                The review output quoted a line-numbered artifact from Claude:

                <dual-artifact type="task_contract_proposal" version="1">
                400: {
                401:   "artifact_type": "task_contract_proposal"
                402: }
                </dual-artifact>
                """, StandardCharsets.UTF_8);

        Files.createDirectories(tempDir.resolve("miniprogram/src/pages/retro"));
        Files.writeString(tempDir.resolve("miniprogram/src/pages/retro/index.vue"), "<template />\n");

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

        var result = handler.execute(context);

        assertEquals(NodeStatus.COMPLETED, result.status());
        var json = Files.readString(workstreamDir.resolve("artifacts/task-contract.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("补录既往治疗保存成功后展示引导卡片"));
        assertTrue(json.contains("\"included\" : [ \"miniprogram/src/pages/retro/index.vue\" ]"));
    }

    @Test
    void executeMaterializesContractResolutionForProposalOpenQuestionsAfterHumanGate(@TempDir Path tempDir) throws Exception {
        var handler = new ContractCompileHandler();
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve(WORKSTREAM_ID);
        var outputDir = workstreamDir.resolve("runs/run-001/outputs");
        Files.createDirectories(outputDir);
        Files.createDirectories(workstreamDir.resolve("nodes"));
        Files.createDirectories(workstreamDir.resolve("gates"));

        Files.writeString(workstreamDir.resolve("requirement.md"), "Add mandatory review state.\n", StandardCharsets.UTF_8);
        var intakeRef = ".agent/workstreams/" + WORKSTREAM_ID + "/runs/run-001/outputs/intake-claude-brainstorm.md";
        Files.writeString(tempDir.resolve(intakeRef), """
                <dual-artifact type="task_contract_proposal" version="1">
                {
                  "artifact_type": "task_contract_proposal",
                  "schema_version": 1,
                  "objective": "Add mandatory READY_FOR_REVIEW status",
                  "scope": {
                    "included": ["src/issueWorkflow.js"],
                    "excluded": ["No external dependencies"]
                  },
                  "acceptance_criteria": [
                    "READY_FOR_REVIEW status exists",
                    "IN_PROGRESS to DONE is rejected"
                  ],
                  "constraints": ["Keep transition map explicit"],
                  "non_goals": ["Do not add UI"],
                  "risk_areas": ["Breaking existing direct done behavior"],
                  "test_requirements": ["Run node --test"],
                  "evidence_refs": ["src/issueWorkflow.js"],
                  "open_questions": ["Should review be mandatory?"]
                }
                </dual-artifact>
                """, StandardCharsets.UTF_8);
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/issueWorkflow.js"), "export {}\n");
        Files.writeString(workstreamDir.resolve("nodes/node-gate-human_requirement_review-005.json"), """
                {
                  "schemaVersion": 1,
                  "nodeInstanceId": "node-gate-human_requirement_review-005",
                  "nodeDefinitionId": "gate.human_requirement_review",
                  "kind": "GATE",
                  "handlerType": "human.review",
                  "status": "COMPLETED",
                  "stageId": "stage-gate",
                  "iterationId": "iter-001",
                  "inputRefs": [],
                  "outputRefs": [],
                  "result": {},
                  "runRef": null,
                  "startedAt": null,
                  "finishedAt": null
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(workstreamDir.resolve("gates/gate-node-gate-human_requirement_review-005.json"), """
                {
                  "schemaVersion": 1,
                  "gateId": "gate-node-gate-human_requirement_review-005",
                  "nodeInstanceId": "node-gate-human_requirement_review-005",
                  "gateType": "human.review",
                  "status": "resolved",
                  "allowedActions": ["approved", "revise"],
                  "openedAt": "2026-06-06T00:00:00Z",
                  "resolvedAt": "2026-06-06T00:01:00Z",
                  "result": {
                    "human_action": "approved",
                    "note": "Review is mandatory; remove direct IN_PROGRESS to DONE."
                  }
                }
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

        assertEquals(NodeStatus.COMPLETED, result.status());
        var resolutionJson = Files.readString(workstreamDir.resolve("artifacts/contract-resolution.json"));
        assertTrue(resolutionJson.contains("\"decision\" : \"APPROVE\""));
        assertTrue(resolutionJson.contains("Should review be mandatory?"));
        assertTrue(resolutionJson.contains("Review is mandatory; remove direct IN_PROGRESS to DONE."));
        assertTrue(resolutionJson.contains("\"approved_by\" : \"human_gate\""));
        assertTrue(Files.readString(workstreamDir.resolve("artifacts/task-contract.json"))
                .contains("Add mandatory READY_FOR_REVIEW status"));
    }

    @Test
    void executeRequiresContractResolutionWhenProposalHasOpenQuestionsWithoutHumanGate(@TempDir Path tempDir) throws Exception {
        var handler = new ContractCompileHandler();
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve(WORKSTREAM_ID);
        var outputDir = workstreamDir.resolve("runs/run-001/outputs");
        Files.createDirectories(outputDir);

        Files.writeString(workstreamDir.resolve("requirement.md"), "Add mandatory review state.\n", StandardCharsets.UTF_8);
        var intakeRef = ".agent/workstreams/" + WORKSTREAM_ID + "/runs/run-001/outputs/intake-claude-brainstorm.md";
        Files.writeString(tempDir.resolve(intakeRef), """
                <dual-artifact type="task_contract_proposal" version="1">
                {
                  "artifact_type": "task_contract_proposal",
                  "schema_version": 1,
                  "objective": "Add mandatory READY_FOR_REVIEW status",
                  "scope": {
                    "included": ["src/issueWorkflow.js"],
                    "excluded": ["No external dependencies"]
                  },
                  "acceptance_criteria": [
                    "READY_FOR_REVIEW status exists",
                    "IN_PROGRESS to DONE is rejected"
                  ],
                  "constraints": ["Keep transition map explicit"],
                  "non_goals": ["Do not add UI"],
                  "risk_areas": ["Breaking existing direct done behavior"],
                  "test_requirements": ["Run node --test"],
                  "evidence_refs": ["src/issueWorkflow.js"],
                  "open_questions": ["Should review be mandatory?"]
                }
                </dual-artifact>
                """, StandardCharsets.UTF_8);
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/issueWorkflow.js"), "export {}\n");

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
        assertEquals("CONTRACT_RESOLUTION_REQUIRED", result.outcome());
        assertFalse(Files.exists(workstreamDir.resolve("artifacts/task-contract.json")));
    }

    @Test
    void executeAcceptsExistingDirectoryScopeRefs(@TempDir Path tempDir) throws Exception {
        var handler = new ContractCompileHandler();
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve(WORKSTREAM_ID);
        var outputDir = workstreamDir.resolve("runs/run-001/outputs");
        Files.createDirectories(outputDir);
        Files.createDirectories(tempDir.resolve("backend/tests"));
        Files.writeString(tempDir.resolve("backend/tests/test_contract.py"), "# fixture\n", StandardCharsets.UTF_8);

        Files.writeString(workstreamDir.resolve("requirement.md"), """
                目标：
                修改业务逻辑并运行后端测试目录。
                """, StandardCharsets.UTF_8);
        var intakeRef = ".agent/workstreams/" + WORKSTREAM_ID + "/runs/run-001/outputs/intake-claude-brainstorm.md";
        Files.writeString(tempDir.resolve(intakeRef), """
                <dual-artifact type="task_contract_proposal" version="1">
                {
                  "artifact_type": "task_contract_proposal",
                  "schema_version": 1,
                  "objective": "修改业务逻辑并验证后端测试",
                  "scope": {
                    "included": ["backend/tests/"],
                    "excluded": ["不修改数据库 schema"]
                  },
                  "acceptance_criteria": [
                    "业务逻辑变更有测试覆盖",
                    "后端测试通过"
                  ],
                  "constraints": ["不修改数据库 schema"],
                  "non_goals": ["不新增 API"],
                  "risk_areas": ["测试目录较大，需要只运行相关测试时说明原因"],
                  "test_requirements": ["运行 backend/tests 相关测试"],
                  "evidence_refs": ["backend/tests/"],
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

        assertEquals(NodeStatus.COMPLETED, result.status());
        var json = Files.readString(workstreamDir.resolve("artifacts/task-contract.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("backend/tests/"));
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
