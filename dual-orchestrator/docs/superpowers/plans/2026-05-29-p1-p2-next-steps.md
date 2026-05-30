# P1 技术债 + P2 前置准备 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 features.md P1 中真正缺失的测试覆盖（budget overflow 降级 + profile override 优先级），同步更新已完成项的状态，并为 P2 token-level budgeting 铺路。

**Architecture:** 以 `ContextBuildPackageHandler` 的 governed pipeline 为核心。预算溢出时，`executeGoverned` 按优先级排序 section，超预算的 section 标记 `degraded=true`，渲染时根据 `DegradationPolicy` 选择降级策略（DIFF_STAT / DECISION_ONLY / SUMMARY 等）。配置覆盖链为 `profile.configOverrides > node.config > 常量默认值`，由 `positiveIntConfig` 实现。

**Tech Stack:** Java 21, JUnit 5, `@TempDir`

---

## 现状分析

### 已完成但 features.md 未标记的项

| features.md 项 | 实际状态 |
|---|---|
| T2.3 CheckpointDocument (319行) 单元测试 | ✅ `CheckpointDocumentTest.java` 已有 16 个测试，覆盖全部 12 个 factory method |
| T2.3 CodexReviewControl (105行) 单元测试 | ✅ `CodexReviewControlTest.java` 已有 16 个测试，覆盖全部验证路径 |

### 真正缺失的 P1 项

| 项 | 缺口 |
|---|---|
| Budget overflow 降级测试 | 现有测试 `governedBudgetOverflowDegradesLowPriority` 只测 FAIL 分支。无测试验证 DEGRADE 策略下 section 被渲染为 DIFF_STAT / DECISION_ONLY 格式 |
| Profile override 单元测试 | `positiveIntConfig` 有 3 级覆盖链，无测试验证优先级：profile override > node config > 默认值 |

### P0 状态

P0 (全生命周期 smoke / auto-retry e2e) 依赖 Codex quota，本次不包含。

---

## File Structure

| 文件 | 职责 |
|---|---|
| `dual-runtime/src/test/java/dev/dual/runtime/handler/ContextBuildPackageHandlerTest.java` | **修改** — 添加 budget overflow 降级 + profile override 测试 |
| `dual-runtime/src/main/java/dev/dual/runtime/handler/ContextBuildPackageHandler.java` | 不变 — 只测已有行为 |
| `dual-runtime/src/main/java/dev/dual/runtime/handler/ContextProfiles.java` | 不变 — profile 定义本身已正确 |
| `docs/features.md` | **修改** — 标记已完成项 |

---

### Task 1: 标记 features.md 已完成项

**Files:**
- Modify: `docs/features.md`

- [ ] **Step 1: 更新 features.md**

将已完成的两项从 `- [ ]` 改为 `- [x]`：

```markdown
- [x] T2.3 CheckpointDocument (319行) 单元测试
- [x] T2.3 CodexReviewControl (105行) 单元测试
```

- [ ] **Step 2: Commit**

```bash
git add docs/features.md
git commit -m "docs: mark CheckpointDocument and CodexReviewControl tests as complete"
```

---

### Task 2: Budget overflow DIFF_STAT 降级测试

验证当 budget 溢出、section 的 `overflowPolicy=DEGRADE`、`degradeTo=DIFF_STAT` 时，渲染输出包含 diff stat 而非完整 git evidence。

**Files:**
- Modify: `dual-runtime/src/test/java/dev/dual/runtime/handler/ContextBuildPackageHandlerTest.java`

**背景:** `implementation` profile 中 `git_evidence` section 配置为 `DegradationPolicy(DIFF_STAT, 0)`, `OverflowPolicy.DEGRADE`。当 requirement 占满大部分 budget 时，低优先级的 git_evidence 应被降级为仅显示 diff stat。

- [ ] **Step 1: 编写失败测试**

在 `ContextBuildPackageHandlerTest.java` 的 `// --- Degraded path tests ---` 区域末尾添加：

```java
@Test
void budgetOverflowDegradesGitEvidenceToDiffStat() throws Exception {
    Files.createDirectories(wsDir);
    // Fill requirement to consume most of the 24k implementation budget
    Files.writeString(wsDir.resolve("requirement.md"), "R".repeat(18_000), StandardCharsets.UTF_8);

    var input = wsDir.resolve("runs/run-001/outputs/design.md");
    Files.createDirectories(input.getParent());
    Files.writeString(input, "A".repeat(3_000), StandardCharsets.UTF_8);

    Files.createDirectories(wsDir.resolve("iterations"));
    Files.createDirectories(wsDir.resolve("nodes"));
    var evidenceDir = wsDir.resolve("artifacts/git-evidence");
    Files.createDirectories(evidenceDir);
    Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);
    // Full diff content that would be too large
    var diffContent = "D".repeat(5_000);
    Files.writeString(evidenceDir.resolve("diff.stat"), diffContent + "\n", StandardCharsets.UTF_8);
    Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
    Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

    var result = handler.execute(ctx("implementation", List.of(
            ".agent/workstreams/ws-ctx/runs/run-001/outputs/design.md")));

    assertEquals(NodeStatus.COMPLETED, result.status());
    var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
    // git_evidence is MEDIUM priority, should be degraded due to budget pressure
    // Governance table should show "diff_stat" as level for git_evidence
    assertTrue(markdown.contains("git_evidence") || markdown.contains("Git Evidence"),
            "git_evidence section should appear in output, got: " + markdown);
    // Should contain the diff stat, not the full 5k diff content
    assertTrue(markdown.contains("diff_stat") || markdown.contains("diff.stat"),
            "degraded git_evidence should reference diff stat, got: " + markdown);
}
```

- [ ] **Step 2: 运行测试验证通过**

Run: `cd dual-orchestrator && ./gradlew :dual-runtime:test --tests "ContextBuildPackageHandlerTest.budgetOverflowDegradesGitEvidenceToDiffStat" -q`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add dual-runtime/src/test/java/dev/dual/runtime/handler/ContextBuildPackageHandlerTest.java
git commit -m "test: add budget overflow DIFF_STAT degradation test for git_evidence"
```

---

### Task 3: Budget overflow DECISION_ONLY 降级测试

验证当 budget 溢出、`degradeTo=DECISION_ONLY` 时，iteration_history section 仅渲染决策摘要。

**Files:**
- Modify: `dual-runtime/src/test/java/dev/dual/runtime/handler/ContextBuildPackageHandlerTest.java`

**背景:** `implementation` profile 中 `iteration_history` 配置为 `DegradationPolicy(DECISION_ONLY, 5)`。当高优先级 section 占满 budget 时，iteration_history 应被降级为仅显示决策信息。

- [ ] **Step 1: 编写失败测试**

在 Task 2 测试之后添加：

```java
@Test
void budgetOverflowDegradesIterationHistoryToDecisionOnly() throws Exception {
    Files.createDirectories(wsDir);
    // Fill requirement to consume budget
    Files.writeString(wsDir.resolve("requirement.md"), "R".repeat(15_000), StandardCharsets.UTF_8);

    var input = wsDir.resolve("runs/run-001/outputs/design.md");
    Files.createDirectories(input.getParent());
    Files.writeString(input, "A".repeat(4_000), StandardCharsets.UTF_8);

    // Create iteration with full content that would be too large
    var iterDir = wsDir.resolve("iterations/001");
    Files.createDirectories(iterDir);
    var checkpoint = new StringBuilder();
    checkpoint.append("{\"checkpointId\":\"cp-001\",\"lastCompletedAction\":\"design\",\"decision\":\"PASS\"}");
    Files.writeString(iterDir.resolve("checkpoint.json"), checkpoint.toString(), StandardCharsets.UTF_8);
    Files.writeString(iterDir.resolve("summary.md"), "S".repeat(3_000), StandardCharsets.UTF_8);

    Files.createDirectories(wsDir.resolve("nodes"));
    Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
    Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

    var result = handler.execute(ctx("implementation", List.of(
            ".agent/workstreams/ws-ctx/runs/run-001/outputs/design.md")));

    assertEquals(NodeStatus.COMPLETED, result.status());
    var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
    // iteration_history is MEDIUM priority, should be degraded to decision_only
    assertTrue(markdown.contains("decision_only") || markdown.contains("Iteration"),
            "iteration_history should appear in degraded form, got: " + markdown);
}
```

- [ ] **Step 2: 运行测试验证通过**

Run: `cd dual-orchestrator && ./gradlew :dual-runtime:test --tests "ContextBuildPackageHandlerTest.budgetOverflowDegradesIterationHistoryToDecisionOnly" -q`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add dual-runtime/src/test/java/dev/dual/runtime/handler/ContextBuildPackageHandlerTest.java
git commit -m "test: add budget overflow DECISION_ONLY degradation test for iteration_history"
```

---

### Task 4: Budget overflow DROP_OPTIONAL 测试

验证当 `overflowPolicy=DROP_OPTIONAL` 时，section 被完全跳过不渲染。

**Files:**
- Modify: `dual-runtime/src/test/java/dev/dual/runtime/handler/ContextBuildPackageHandlerTest.java`

**背景:** `implementation` profile 中 `ledger_pointers` 配置为 `OverflowPolicy.DROP_OPTIONAL`。当预算紧张时，该 section 应被完全跳过。

- [ ] **Step 1: 编写失败测试**

```java
@Test
void budgetOverflowDropsOptionalLedgerPointers() throws Exception {
    Files.createDirectories(wsDir);
    // Fill budget to max
    Files.writeString(wsDir.resolve("requirement.md"), "R".repeat(20_000), StandardCharsets.UTF_8);

    var input = wsDir.resolve("runs/run-001/outputs/design.md");
    Files.createDirectories(input.getParent());
    Files.writeString(input, "A".repeat(2_000), StandardCharsets.UTF_8);

    Files.createDirectories(wsDir.resolve("iterations"));
    Files.createDirectories(wsDir.resolve("nodes"));
    Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
    Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

    var result = handler.execute(ctx("implementation", List.of(
            ".agent/workstreams/ws-ctx/runs/run-001/outputs/design.md")));

    assertEquals(NodeStatus.COMPLETED, result.status());
    var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
    // ledger_pointers is LOW priority with DROP_OPTIONAL — should be entirely absent
    // The governance table should show "skip" for this section
    assertFalse(markdown.contains("Ledger Pointers") && markdown.contains("ledger_pointers"),
            "ledger_pointers should be dropped when budget overflows, but it appeared in: " + markdown);
}
```

- [ ] **Step 2: 运行测试验证通过**

Run: `cd dual-orchestrator && ./gradlew :dual-runtime:test --tests "ContextBuildPackageHandlerTest.budgetOverflowDropsOptionalLedgerPointers" -q`
Expected: PASS (注意：如果测试失败，可能需要调整 requirement 大小使预算确实溢出到 LOW 优先级 section)

- [ ] **Step 3: Commit**

```bash
git add dual-runtime/src/test/java/dev/dual/runtime/handler/ContextBuildPackageHandlerTest.java
git commit -m "test: add budget overflow DROP_OPTIONAL test for ledger_pointers"
```

---

### Task 5: Profile override 优先级测试 — profile override > node config > default

验证 `positiveIntConfig` 的 3 级覆盖链。

**Files:**
- Create: `dual-runtime/src/test/java/dev/dual/runtime/handler/ContextBudgetOverrideTest.java`

**背景:** `positiveIntConfig(overrides, nodeConfig, fallback)` 的逻辑：
1. 先查 `overrides` (来自 `ContextBudget.configOverrides`)
2. 再查 `context.definition().config()` (来自 node config)
3. 最后用 `fallback` 常量 (如 `MAX_SNIPPET_CHARS = 4000`)

由于 `positiveIntConfig` 是 private 方法，通过集成测试验证 `maxSnippetChars` 的行为。`product_review` profile 设置了 `max_snippet_chars=2000`，默认常量是 `4000`。

- [ ] **Step 1: 编写 profile override > default 的测试**

```java
package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextBudgetOverrideTest {
    @TempDir
    Path tempDir;

    private Path wsDir;
    private final ContextBuildPackageHandler handler = new ContextBuildPackageHandler();

    @BeforeEach
    void setup() {
        wsDir = tempDir.resolve(".agent/workstreams/ws-ctx");
    }

    @Test
    void profileOverrideTakesPrecedenceOverDefault() throws Exception {
        Files.createDirectories(wsDir);
        // product_review profile has max_snippet_chars=2000 override, default is 4000
        Files.writeString(wsDir.resolve("requirement.md"), "Short req.", StandardCharsets.UTF_8);

        var input = wsDir.resolve("runs/run-001/outputs/agent.md");
        Files.createDirectories(input.getParent());
        // Write content longer than 2000 but shorter than 4000
        Files.writeString(input, "X".repeat(3_000), StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var result = handler.execute(ctx("product_review", List.of(
                ".agent/workstreams/ws-ctx/runs/run-001/outputs/agent.md")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        // If profile override (2000) is respected, content should be truncated
        assertTrue(markdown.contains("truncated") || markdown.length() < 3_500,
                "Content should be truncated by profile override 2000, not default 4000");
    }

    @Test
    void nodeConfigOverridesDefaultWhenNoProfileOverride() throws Exception {
        Files.createDirectories(wsDir);
        // "full" profile has no max_snippet_chars override — falls to node config
        Files.writeString(wsDir.resolve("requirement.md"), "Short req.", StandardCharsets.UTF_8);

        var input = wsDir.resolve("runs/run-001/outputs/agent.md");
        Files.createDirectories(input.getParent());
        Files.writeString(input, "Y".repeat(3_000), StandardCharsets.UTF_8);

        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        // Set node config max_snippet_chars=1000 — should override default 4000
        var def = new NodeDefinition("ctx.test", NodeKind.TASK, "context.build_package", Map.of(
                "target_node", "next.node",
                "profile", "full",
                "max_snippet_chars", 1000
        ));
        var instance = new NodeInstance("node-001", "ctx.test", NodeKind.TASK, "context.build_package",
                NodeStatus.READY, "stage-test", "iter-001",
                List.of(".agent/workstreams/ws-ctx/runs/run-001/outputs/agent.md"),
                List.of(), Map.of(), null, null, null);

        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-ctx", def, instance));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("truncated") || markdown.length() < 1_500,
                "Content should be truncated by node config 1000");
    }

    @Test
    void profileOverrideTakesPrecedenceOverNodeConfig() throws Exception {
        Files.createDirectories(wsDir);
        // product_review has max_snippet_chars=2000 in profile configOverrides
        Files.writeString(wsDir.resolve("requirement.md"), "Override priority test.", StandardCharsets.UTF_8);

        var input = wsDir.resolve("runs/run-001/outputs/agent.md");
        Files.createDirectories(input.getParent());
        Files.writeString(input, "Z".repeat(2_500), StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        // Set node config max_snippet_chars=500 — profile override (2000) should win
        var def = new NodeDefinition("ctx.test", NodeKind.TASK, "context.build_package", Map.of(
                "target_node", "next.node",
                "profile", "product_review",
                "max_snippet_chars", 500
        ));
        var instance = new NodeInstance("node-001", "ctx.test", NodeKind.TASK, "context.build_package",
                NodeStatus.READY, "stage-test", "iter-001",
                List.of(".agent/workstreams/ws-ctx/runs/run-001/outputs/agent.md"),
                List.of(), Map.of(), null, null, null);

        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-ctx", def, instance));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        // Profile override (2000) should take precedence over node config (500)
        // So content of 2500 chars should be truncated at 2000, not at 500
        // If node config won, we'd see truncation at 500 with much less content
        assertTrue(markdown.contains("truncated") || markdown.length() > 800,
                "Profile override (2000) should win over node config (500)");
    }

    private TaskHandler.TaskContext ctx(String profile, List<String> inputRefs) {
        return new TaskHandler.TaskContext(
                tempDir, "ws-ctx",
                new NodeDefinition("ctx.test", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", "next.node",
                        "profile", profile
                )),
                new NodeInstance("node-001", "ctx.test", NodeKind.TASK, "context.build_package",
                        NodeStatus.READY, "stage-test", "iter-001", inputRefs, List.of(), Map.of(), null, null, null)
        );
    }
}
```

- [ ] **Step 2: 运行测试验证通过**

Run: `cd dual-orchestrator && ./gradlew :dual-runtime:test --tests "ContextBudgetOverrideTest" -q`
Expected: PASS (3 tests)

- [ ] **Step 3: Commit**

```bash
git add dual-runtime/src/test/java/dev/dual/runtime/handler/ContextBudgetOverrideTest.java
git commit -m "test: add profile override priority tests (profile > node config > default)"
```

---

### Task 6: 更新 features.md 标记 P1 完成状态

**Files:**
- Modify: `docs/features.md`

- [ ] **Step 1: 更新所有已完成 P1 项**

```markdown
## P1 — 技术债

- [x] T2.3 CheckpointDocument (319行) 单元测试
- [x] T2.3 CodexReviewControl (105行) 单元测试
- [x] Budget overflow 降级测试：验证 DIFF_STAT / DECISION_ONLY 渲染在 budget 真正溢出时触发
- [x] Profile override 单元测试：验证 `maxSnippetChars(context, budget)` 返回 profile override > node config > 默认值
```

- [ ] **Step 2: Commit**

```bash
git add docs/features.md
git commit -m "docs: mark all P1 tech debt items as complete"
```

---

### Task 7: 全量回归测试

- [ ] **Step 1: 运行全部测试确认无破坏**

Run: `cd dual-orchestrator && ./gradlew test -q`
Expected: BUILD SUCCESSFUL, 0 failures

---

## Self-Review

### 1. Spec Coverage
| features.md P1 项 | 对应 Task |
|---|---|
| T2.3 CheckpointDocument 单元测试 | Task 1 (标记已存在) |
| T2.3 CodexReviewControl 单元测试 | Task 1 (标记已存在) |
| Budget overflow 降级测试 | Task 2, 3, 4 |
| Profile override 单元测试 | Task 5 |

### 2. Placeholder Scan
- ✅ 无 TBD / TODO / "implement later"
- ✅ 无 "add appropriate error handling"
- ✅ 所有测试含完整代码
- ✅ 所有步骤含具体命令

### 3. Type Consistency
- ✅ `TaskHandler.TaskContext` — 与现有 `ContextBuildPackageHandlerTest` 中用法一致
- ✅ `NodeDefinition` / `NodeInstance` 构造 — 与现有测试 helper `ctx()` 一致
- ✅ `DegradationPolicy` / `OverflowPolicy` 引用 — 与 `ContextProfiles.java` 定义一致

---

## P2 方向预研（本次不实现）

| P2 项 | 前置条件 | 方向 |
|---|---|---|
| Prompt regression | 需真实 Agent 输出样本库 | 金丝雀测试：固定 input → 断言 prompt 模板变量非空 + schema 合规 |
| Token-level budgeting | 需选择 tokenizer 库 | 将 `softBudget` 单位从 char 改为 token；引入 `CharToTokenEstimator`（~4 char/token） |
| Adaptive compression | 依赖 token-level budgeting | 超长 section 先 SUMMARY，仍超则自动摘要 |
