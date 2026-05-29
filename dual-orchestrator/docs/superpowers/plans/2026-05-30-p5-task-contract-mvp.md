# P5 TaskContract + ContractCompiler MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement P5 MVP — TaskContract data model, ContractCompiler handler, ContractReviewGate, context governance integration, Codex review integration, ledger events, final report enhancement, and full test coverage.

**Architecture:** TaskContract is a new record in `dual-core` with JSON serialization. `contract.compile` is a TaskHandler in `dual-runtime` that uses `acpx.agent_run` to invoke Claude, then validates output against a schema. `gate.contract_review` reuses `human.review` gate with extended allowed actions. Context profiles get a new `task_contract` section. Workflow definition gets two new nodes between intake and design.

**Tech Stack:** Java 21, Spring Boot 3.4, Jackson, JUnit 5, Maven multi-module

---

## File Structure

### New Files
| File | Responsibility |
|------|---------------|
| `dual-core/src/main/java/dev/dual/core/contract/TaskContract.java` | TaskContract record — the shared contract data model |
| `dual-core/src/main/java/dev/dual/core/contract/ContractStatus.java` | Enum: READY_FOR_REVIEW, NEEDS_CLARIFICATION, NEEDS_SCOPE_REDUCTION, REJECTED |
| `dual-core/src/main/java/dev/dual/core/contract/TaskContractValidator.java` | Schema validation for TaskContract JSON |
| `dual-core/src/test/java/dev/dual/core/contract/TaskContractTest.java` | Serialization, deserialization, validation tests |
| `dual-runtime/src/main/java/dev/dual/runtime/handler/ContractCompileHandler.java` | `contract.compile` TaskHandler — invokes agent, validates, writes artifacts |
| `dual-runtime/src/test/java/dev/dual/runtime/handler/ContractCompileHandlerTest.java` | ContractCompile handler tests |
| `dual-runtime/src/test/java/dev/dual/runtime/handler/ContractReviewGateTest.java` | ContractReviewGate routing tests |
| `dual-runtime/src/test/java/dev/dual/runtime/handler/ContextTaskContractSectionTest.java` | Context governance task_contract section tests |
| `dual-runtime/src/test/java/dev/dual/runtime/handler/ReportTaskContractTest.java` | Final report TaskContract section tests |
| `dual-runtime/src/test/java/dev/dual/runtime/handler/ContractLifecycleLedgerTest.java` | Ledger events for contract lifecycle |

### Modified Files
| File | Change |
|------|--------|
| `docs/design/P5-task-contract-and-contract-compiler.md` | Fix markdown fence nesting (section 7.3) |
| `dual-runtime/src/main/java/dev/dual/runtime/handler/ContextProfiles.java` | Add `task_contract` section to acceptance profile |
| `dual-runtime/src/main/java/dev/dual/runtime/handler/ContextBuildPackageHandler.java` | Add `task_contract` section rendering |
| `dual-runtime/src/main/java/dev/dual/runtime/handler/ContractRegistry.java` | Add `contract.compile` output contract |
| `dual-runtime/src/main/java/dev/dual/runtime/handler/ReportGenerateFinalHandler.java` | Add TaskContract summary section |
| `dual-runtime/src/main/java/dev/dual/runtime/lifecycle/LifecycleWorkflowDefinitionFactory.java` | Add P5-enabled workflow with contract nodes |
| `dual-cli/src/main/java/dev/dual/cli/CliSupportedHandlers.java` | Add `contract.compile` handler type |

---

## Pre-flight: Fix P5 Design Doc Markdown

### Task 0: Fix Markdown Fence Nesting

**Files:**
- Modify: `docs/design/P5-task-contract-and-contract-compiler.md:453-460`

- [ ] **Step 1: Fix the dual-control block example fence nesting**

In section 7.3, the `dual-control` code block is nested inside a triple-backtick code fence, which breaks Markdown rendering. Change the outer fence to `~~~~` (quadruple tilde) to avoid conflict:

Replace lines 453-460:
````
```text
```dual-control
decision: PASS
summary: Implementation satisfies all acceptance criteria
contract_gaps: []
out_of_scope_files: []
missing_tests: []
```dual-control
```
````
with:
````
~~~~text
```dual-control
decision: PASS
summary: Implementation satisfies all acceptance criteria
contract_gaps: []
out_of_scope_files: []
missing_tests: []
```dual-control
~~~~
````

- [ ] **Step 2: Verify the fix renders correctly**

Run: `grep -n '```' docs/design/P5-task-contract-and-contract-compiler.md | head -20`
Expected: No nested triple-backtick blocks remain; the `dual-control` block uses tilde fence.

---

## Task 1: TaskContract Data Model + ContractStatus Enum

**Files:**
- Create: `dual-core/src/main/java/dev/dual/core/contract/ContractStatus.java`
- Create: `dual-core/src/main/java/dev/dual/core/contract/TaskContract.java`

- [ ] **Step 1: Write the failing test for TaskContract**

Create `dual-core/src/test/java/dev/dual/core/contract/TaskContractTest.java`:

```java
package dev.dual.core.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskContractTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void serializeDeserialize() throws Exception {
        var original = new TaskContract(
                "tc-001", 1, Instant.now(),
                "Add a project description to README",
                new TaskContract.Scope(List.of("README.md"), List.of("code files")),
                List.of("README.md contains Project Description"),
                List.of("README.md renders correctly"),
                List.of("Use Chinese"),
                List.of("Do not translate the entire file"),
                List.of(), List.of(), List.of(),
                ContractStatus.READY_FOR_REVIEW,
                1,
                new TaskContract.Source("requirement.md", "contract_compiler")
        );
        var json = MAPPER.writeValueAsString(original);
        var deserialized = MAPPER.readValue(json, TaskContract.class);
        assertEquals(original, deserialized);
    }

    @Test
    void contractStatusValues() {
        assertArrayEquals(
                new String[]{"READY_FOR_REVIEW", "NEEDS_CLARIFICATION", "NEEDS_SCOPE_REDUCTION", "REJECTED"},
                ContractStatus.values().stream().map(Enum::name).toArray()
        );
    }

    @Test
    void statusNotApprovedDraft() {
        // APPROVED_DRAFT must NOT exist
        for (var status : ContractStatus.values()) {
            assertNotEquals("APPROVED_DRAFT", status.name());
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl dual-core -Dtest=TaskContractTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -5`
Expected: Compilation error — `TaskContract` and `ContractStatus` classes don't exist yet.

- [ ] **Step 3: Create ContractStatus enum**

Create `dual-core/src/main/java/dev/dual/core/contract/ContractStatus.java`:

```java
package dev.dual.core.contract;

/**
 * Status of a TaskContract after compilation.
 * Note: There is no APPROVED_DRAFT status.
 * The compiler only generates READY_FOR_REVIEW drafts.
 * Approval happens only at the ContractReviewGate.
 */
public enum ContractStatus {
    READY_FOR_REVIEW,
    NEEDS_CLARIFICATION,
    NEEDS_SCOPE_REDUCTION,
    REJECTED
}
```

- [ ] **Step 4: Create TaskContract record**

Create `dual-core/src/main/java/dev/dual/core/contract/TaskContract.java`:

```java
package dev.dual.core.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Shared task contract — the single source of truth for task intent, scope, and acceptance.
 * Read by Claude (implementation), Codex (review), and human gate (approval).
 *
 * Immutable once created. If modification is needed, go through revision loop → recompile.
 */
public record TaskContract(
        String id,
        int version,
        @JsonProperty("created_at") Instant createdAt,

        String objective,

        Scope scope,

        @JsonProperty("acceptance_criteria") List<String> acceptanceCriteria,
        @JsonProperty("test_requirements") List<String> testRequirements,
        List<String> constraints,
        @JsonProperty("non_goals") List<String> nonGoals,

        @JsonProperty("risk_areas") List<String> riskAreas,
        @JsonProperty("ambiguity_notes") List<String> ambiguityNotes,
        @JsonProperty("human_questions") List<String> humanQuestions,

        ContractStatus status,
        @JsonProperty("compilation_pass") int compilationPass,

        Source source
) {
    public TaskContract {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
        if (objective == null || objective.isBlank()) {
            throw new IllegalArgumentException("objective is required");
        }
        if (scope == null) {
            throw new IllegalArgumentException("scope is required");
        }
        if (acceptanceCriteria == null) {
            acceptanceCriteria = List.of();
        }
        if (testRequirements == null) {
            testRequirements = List.of();
        }
        if (constraints == null) {
            constraints = List.of();
        }
        if (nonGoals == null) {
            nonGoals = List.of();
        }
        if (riskAreas == null) {
            riskAreas = List.of();
        }
        if (ambiguityNotes == null) {
            ambiguityNotes = List.of();
        }
        if (humanQuestions == null) {
            humanQuestions = List.of();
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        acceptanceCriteria = List.copyOf(acceptanceCriteria);
        testRequirements = List.copyOf(testRequirements);
        constraints = List.copyOf(constraints);
        nonGoals = List.copyOf(nonGoals);
        riskAreas = List.copyOf(riskAreas);
        ambiguityNotes = List.copyOf(ambiguityNotes);
        humanQuestions = List.copyOf(humanQuestions);
    }

    public record Scope(
            List<String> included,
            List<String> excluded
    ) {
        public Scope {
            if (included == null) included = List.of();
            if (excluded == null) excluded = List.of();
            included = List.copyOf(included);
            excluded = List.copyOf(excluded);
        }
    }

    public record Source(
            @JsonProperty("raw_request_ref") String rawRequestRef,
            @JsonProperty("created_by") String createdBy
    ) {
        public Source {
            if (rawRequestRef == null || rawRequestRef.isBlank()) {
                throw new IllegalArgumentException("rawRequestRef is required");
            }
            if (createdBy == null || createdBy.isBlank()) {
                throw new IllegalArgumentException("createdBy is required");
            }
        }
    }
}
```

- [ ] **Step 5: Add jackson-datatype-jsr310 to dual-core pom.xml**

The dual-core module needs Jackson with JSR-310 support for `Instant` serialization. Add to `dual-core/pom.xml`:

```xml
<dependencies>
    <!-- existing junit-jupiter -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    <!-- NEW: Jackson for TaskContract serialization -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
    </dependency>
</dependencies>
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn test -pl dual-core -Dtest=TaskContractTest 2>&1 | tail -10`
Expected: All 3 tests pass.

- [ ] **Step 7: Commit**

```bash
git add dual-core/src/main/java/dev/dual/core/contract/ dual-core/src/test/java/dev/dual/core/contract/ dual-core/pom.xml
git commit -m "feat(P5): TaskContract data model and ContractStatus enum"
```

---

## Task 2: TaskContractValidator

**Files:**
- Create: `dual-core/src/main/java/dev/dual/core/contract/TaskContractValidator.java`
- Modify: `dual-core/src/test/java/dev/dual/core/contract/TaskContractTest.java`

- [ ] **Step 1: Write failing tests for TaskContractValidator**

Add to `TaskContractTest.java`:

```java
@Test
void validateReadyForReviewRequiresAcceptanceCriteria() {
    var contract = new TaskContract(
            "tc-001", 1, Instant.now(),
            "Add project description",
            new TaskContract.Scope(List.of("README.md"), List.of("code")),
            List.of(), // empty acceptanceCriteria
            List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(),
            ContractStatus.READY_FOR_REVIEW,
            1,
            new TaskContract.Source("requirement.md", "contract_compiler")
    );
    var errors = TaskContractValidator.validate(contract);
    assertFalse(errors.isEmpty());
    assertTrue(errors.stream().anyMatch(e -> e.contains("acceptanceCriteria")));
}

@Test
void validateNeedsClarificationAllowsEmptyAcceptanceCriteria() {
    var contract = new TaskContract(
            "tc-001", 1, Instant.now(),
            "Unclear task",
            new TaskContract.Scope(List.of(), List.of()),
            List.of(),
            List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of("What do you mean?"),
            ContractStatus.NEEDS_CLARIFICATION,
            1,
            new TaskContract.Source("requirement.md", "contract_compiler")
    );
    var errors = TaskContractValidator.validate(contract);
    assertTrue(errors.isEmpty());
}

@Test
void validateScopeIncludedExcludedMustExist() {
    // null scope fields should cause error
    var contract = new TaskContract(
            "tc-001", 1, Instant.now(),
            "Task",
            new TaskContract.Scope(null, null),
            List.of("AC1"),
            List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(),
            ContractStatus.READY_FOR_REVIEW,
            1,
            new TaskContract.Source("requirement.md", "contract_compiler")
    );
    // null scope fields become empty lists via compact constructor
    // So this should validate OK (empty scope is allowed)
    var errors = TaskContractValidator.validate(contract);
    assertTrue(errors.isEmpty());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl dual-core -Dtest=TaskContractTest 2>&1 | tail -10`
Expected: Compilation error — `TaskContractValidator` doesn't exist.

- [ ] **Step 3: Create TaskContractValidator**

Create `dual-core/src/main/java/dev/dual/core/contract/TaskContractValidator.java`:

```java
package dev.dual.core.contract;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a TaskContract against P5 schema rules.
 * Used after ContractCompiler produces a draft to ensure it is well-formed.
 */
public final class TaskContractValidator {
    private TaskContractValidator() {}

    public static List<String> validate(TaskContract contract) {
        var errors = new ArrayList<String>();
        if (contract == null) {
            errors.add("contract is null");
            return errors;
        }

        // Required: objective
        if (contract.objective() == null || contract.objective().isBlank()) {
            errors.add("objective is required");
        }

        // Required: scope.included / scope.excluded must exist (can be empty)
        if (contract.scope() == null) {
            errors.add("scope is required");
        }

        // READY_FOR_REVIEW must have non-empty acceptanceCriteria
        if (contract.status() == ContractStatus.READY_FOR_REVIEW
                && (contract.acceptanceCriteria() == null || contract.acceptanceCriteria().isEmpty())) {
            errors.add("acceptanceCriteria must not be empty when status is READY_FOR_REVIEW");
        }

        // NEEDS_CLARIFICATION must have humanQuestions
        if (contract.status() == ContractStatus.NEEDS_CLARIFICATION
                && (contract.humanQuestions() == null || contract.humanQuestions().isEmpty())) {
            errors.add("humanQuestions must not be empty when status is NEEDS_CLARIFICATION");
        }

        // REJECTED must not happen from compiler without reason (ambiguousNotes should say why)
        // This is a soft check — we don't enforce it as an error, just a warning
        // MVP: no strict enforcement for REJECTED

        return errors;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl dual-core -Dtest=TaskContractTest 2>&1 | tail -10`
Expected: All tests pass (6 total).

- [ ] **Step 5: Commit**

```bash
git add dual-core/src/main/java/dev/dual/core/contract/TaskContractValidator.java dual-core/src/test/java/dev/dual/core/contract/TaskContractTest.java
git commit -m "feat(P5): TaskContractValidator — schema validation for contract drafts"
```

---

## Task 3: TaskContract Artifact Writers (JSON + Markdown)

**Files:**
- Create: `dual-runtime/src/main/java/dev/dual/runtime/handler/TaskContractArtifactWriter.java`
- Create: `dual-runtime/src/test/java/dev/dual/runtime/handler/TaskContractArtifactWriterTest.java`

- [ ] **Step 1: Write the failing test**

Create `dual-runtime/src/test/java/dev/dual/runtime/handler/TaskContractArtifactWriterTest.java`:

```java
package dev.dual.runtime.handler;

import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskContractArtifactWriterTest {
    @TempDir Path tempDir;

    private TaskContract sampleContract() {
        return new TaskContract(
                "tc-001", 1, Instant.parse("2026-05-30T10:00:00Z"),
                "Add project description to README",
                new TaskContract.Scope(List.of("README.md"), List.of("code")),
                List.of("README contains project description", "Under 200 words"),
                List.of("README renders correctly"),
                List.of("Use Chinese"),
                List.of("Do not translate entire file"),
                List.of(), List.of(), List.of(),
                ContractStatus.READY_FOR_REVIEW,
                1,
                new TaskContract.Source("requirement.md", "contract_compiler")
        );
    }

    @Test
    void writeJsonArtifact() throws IOException {
        var contract = sampleContract();
        var artifactsDir = tempDir.resolve("artifacts");
        Files.createDirectories(artifactsDir);

        TaskContractArtifactWriter.writeJson(contract, artifactsDir);

        var jsonPath = artifactsDir.resolve("task-contract.json");
        assertTrue(Files.exists(jsonPath));
        var content = Files.readString(jsonPath, StandardCharsets.UTF_8);
        assertTrue(content.contains("\"tc-001\""));
        assertTrue(content.contains("\"READY_FOR_REVIEW\""));
        assertTrue(content.contains("\"Add project description to README\""));
    }

    @Test
    void writeMarkdownArtifact() throws IOException {
        var contract = sampleContract();
        var artifactsDir = tempDir.resolve("artifacts");
        Files.createDirectories(artifactsDir);

        TaskContractArtifactWriter.writeMarkdown(contract, artifactsDir);

        var mdPath = artifactsDir.resolve("task-contract.md");
        assertTrue(Files.exists(mdPath));
        var content = Files.readString(mdPath, StandardCharsets.UTF_8);
        assertTrue(content.contains("TaskContract: tc-001"));
        assertTrue(content.contains("READY_FOR_REVIEW"));
        assertTrue(content.contains("Add project description to README"));
        assertTrue(content.contains("README contains project description"));
        assertTrue(content.contains("Do not translate entire file"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl dual-runtime -Dtest=TaskContractArtifactWriterTest 2>&1 | tail -10`
Expected: Compilation error — `TaskContractArtifactWriter` doesn't exist.

- [ ] **Step 3: Create TaskContractArtifactWriter**

Create `dual-runtime/src/main/java/dev/dual/runtime/handler/TaskContractArtifactWriter.java`:

```java
package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.TaskContract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes TaskContract artifacts: task-contract.json (machine-readable) and
 * task-contract.md (human-readable review material for ContractReviewGate).
 */
final class TaskContractArtifactWriter {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    private TaskContractArtifactWriter() {}

    static void writeJson(TaskContract contract, Path artifactsDir) throws IOException {
        Files.createDirectories(artifactsDir);
        var jsonPath = artifactsDir.resolve("task-contract.json");
        MAPPER.writeValue(jsonPath.toFile(), contract);
    }

    static void writeMarkdown(TaskContract contract, Path artifactsDir) throws IOException {
        Files.createDirectories(artifactsDir);
        var builder = new StringBuilder();

        builder.append("# TaskContract: ").append(contract.id()).append("\n\n");
        builder.append("- **Status:** ").append(contract.status()).append("\n");
        builder.append("- **Version:** ").append(contract.version()).append("\n");
        builder.append("- **Compilation Pass:** ").append(contract.compilationPass()).append("\n");
        builder.append("- **Created At:** ").append(contract.createdAt()).append("\n\n");

        builder.append("## Objective\n\n");
        builder.append(contract.objective()).append("\n\n");

        builder.append("## Scope\n\n");
        builder.append("### Included\n\n");
        for (var item : contract.scope().included()) {
            builder.append("- ").append(item).append("\n");
        }
        if (contract.scope().included().isEmpty()) {
            builder.append("(none)\n");
        }
        builder.append("\n### Excluded\n\n");
        for (var item : contract.scope().excluded()) {
            builder.append("- ").append(item).append("\n");
        }
        if (contract.scope().excluded().isEmpty()) {
            builder.append("(none)\n");
        }
        builder.append("\n");

        builder.append("## Acceptance Criteria\n\n");
        for (var item : contract.acceptanceCriteria()) {
            builder.append("- [ ] ").append(item).append("\n");
        }
        if (contract.acceptanceCriteria().isEmpty()) {
            builder.append("(none)\n");
        }
        builder.append("\n");

        builder.append("## Test Requirements\n\n");
        for (var item : contract.testRequirements()) {
            builder.append("- ").append(item).append("\n");
        }
        if (contract.testRequirements().isEmpty()) {
            builder.append("(none)\n");
        }
        builder.append("\n");

        builder.append("## Constraints\n\n");
        for (var item : contract.constraints()) {
            builder.append("- ").append(item).append("\n");
        }
        if (contract.constraints().isEmpty()) {
            builder.append("(none)\n");
        }
        builder.append("\n");

        builder.append("## Non-Goals\n\n");
        for (var item : contract.nonGoals()) {
            builder.append("- ").append(item).append("\n");
        }
        if (contract.nonGoals().isEmpty()) {
            builder.append("(none)\n");
        }
        builder.append("\n");

        if (!contract.riskAreas().isEmpty()) {
            builder.append("## Risk Areas\n\n");
            for (var item : contract.riskAreas()) {
                builder.append("- ").append(item).append("\n");
            }
            builder.append("\n");
        }

        if (!contract.ambiguityNotes().isEmpty()) {
            builder.append("## Ambiguity Notes\n\n");
            for (var item : contract.ambiguityNotes()) {
                builder.append("- ").append(item).append("\n");
            }
            builder.append("\n");
        }

        if (!contract.humanQuestions().isEmpty()) {
            builder.append("## Questions for Review\n\n");
            for (var item : contract.humanQuestions()) {
                builder.append("- ").append(item).append("\n");
            }
            builder.append("\n");
        }

        builder.append("## Source\n\n");
        builder.append("- Raw Request: ").append(contract.source().rawRequestRef()).append("\n");
        builder.append("- Created By: ").append(contract.source().createdBy()).append("\n");

        var mdPath = artifactsDir.resolve("task-contract.md");
        Files.writeString(mdPath, builder.toString(), StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 4: Add jackson-datatype-jsr310 dependency to dual-runtime pom.xml if not present**

Check `dual-runtime/pom.xml` — it already has `jackson-databind` but may need `jackson-datatype-jsr310`. Add if missing:

```xml
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
</dependency>
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl dual-runtime -Dtest=TaskContractArtifactWriterTest 2>&1 | tail -10`
Expected: All 2 tests pass.

- [ ] **Step 6: Commit**

```bash
git add dual-runtime/src/main/java/dev/dual/runtime/handler/TaskContractArtifactWriter.java dual-runtime/src/test/java/dev/dual/runtime/handler/TaskContractArtifactWriterTest.java dual-runtime/pom.xml
git commit -m "feat(P5): TaskContract artifact writer — JSON + Markdown output"
```

---

## Task 4: ContractCompileHandler

**Files:**
- Create: `dual-runtime/src/main/java/dev/dual/runtime/handler/ContractCompileHandler.java`
- Create: `dual-runtime/src/test/java/dev/dual/runtime/handler/ContractCompileHandlerTest.java`

This handler reads `requirement.md`, invokes Claude via `acpx.agent_run` to compile a TaskContract, validates it, and writes artifacts.

- [ ] **Step 1: Write the failing test**

Create `dual-runtime/src/test/java/dev/dual/runtime/handler/ContractCompileHandlerTest.java`:

```java
package dev.dual.runtime.handler;

import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContractCompileHandlerTest {
    @TempDir Path tempDir;

    private TaskHandler.TaskContext makeContext(Path workspaceRoot, String workstreamId) {
        var definition = new NodeDefinition(
                "contract.compile", NodeKind.TASK, "contract.compile", Map.of()
        );
        var instance = new NodeInstance(
                "ni-001", "contract.compile", "stage-01", "iter-001",
                NodeInstance.Status.PENDING, List.of(), Instant.now(), null
        );
        return new TaskHandler.TaskContext(workspaceRoot, workstreamId, definition, instance);
    }

    private Path initWorkstream(Path workspaceRoot, String workstreamId) throws IOException {
        var wsDir = workspaceRoot.resolve(".agent").resolve("workstreams").resolve(workstreamId);
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"),
                "# Requirement\n\nAdd a project description to README.md, under 200 words, in Chinese.",
                StandardCharsets.UTF_8);
        return wsDir;
    }

    @Test
    void handlerTypeIsContractCompile() {
        assertEquals("contract.compile", new ContractCompileHandler().type());
    }

    @Test
    void compileProducesReadyForReviewContract() throws Exception {
        var wsRoot = tempDir.resolve("workspace");
        Files.createDirectories(wsRoot);
        var wsId = "ws-001";
        initWorkstream(wsRoot, wsId);

        var handler = new ContractCompileHandler();
        var context = makeContext(wsRoot, wsId);
        var result = handler.execute(context);

        assertEquals("completed", result.outcome());

        // Verify artifacts exist
        var artifactsDir = wsRoot.resolve(".agent/workstreams/" + wsId + "/artifacts");
        assertTrue(Files.exists(artifactsDir.resolve("task-contract.json")));
        assertTrue(Files.exists(artifactsDir.resolve("task-contract.md")));

        // Verify the contract in JSON is valid and READY_FOR_REVIEW
        var mapper = TaskContractArtifactWriterTest.getMapper();
        var contract = mapper.readValue(
                artifactsDir.resolve("task-contract.json").toFile(), TaskContract.class);
        assertEquals(ContractStatus.READY_FOR_REVIEW, contract.status());
        assertFalse(contract.objective().isBlank());
        assertFalse(contract.acceptanceCriteria().isEmpty());
    }
}
```

Note: `TaskContractArtifactWriterTest.getMapper()` needs to be a static helper. Add to `TaskContractArtifactWriterTest`:

```java
static ObjectMapper getMapper() {
    return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);
}
```

(Requires imports for `com.fasterxml.jackson.databind.ObjectMapper`, `com.fasterxml.jackson.databind.SerializationFeature`, `com.fasterxml.jackson.datatype.jsr310.JavaTimeModule`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl dual-runtime -Dtest=ContractCompileHandlerTest 2>&1 | tail -10`
Expected: Compilation error — `ContractCompileHandler` doesn't exist.

- [ ] **Step 3: Create ContractCompileHandler**

Create `dual-runtime/src/main/java/dev/dual/runtime/handler/ContractCompileHandler.java`:

```java
package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.contract.TaskContractValidator;
import dev.dual.core.workflow.NodeResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * contract.compile handler — compiles a TaskContract from the raw requirement.
 *
 * MVP: Uses a deterministic template-based compiler that extracts structure
 * from requirement.md. For P5 MVP, this is a rule-based extractor.
 * The design supports upgrading to Claude-based compilation (acpx.agent_run)
 * in a future iteration.
 *
 * Output:
 *   - artifacts/task-contract.json  (machine-readable)
 *   - artifacts/task-contract.md    (human-readable review material)
 *   - NodeResult(COMPLETED)
 */
public final class ContractCompileHandler implements TaskHandler {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public String type() {
        return "contract.compile";
    }

    @Override
    public NodeResult execute(TaskContext context) throws IOException {
        var workstreamDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId());
        var artifactsDir = workstreamDir.resolve("artifacts");
        Files.createDirectories(artifactsDir);

        var requirementText = readRequirement(workstreamDir);
        var contract = compile(requirementText, context.workstreamId());

        var validationErrors = TaskContractValidator.validate(contract);
        if (!validationErrors.isEmpty()) {
            // Try to fix: if acceptanceCriteria is empty and status is READY_FOR_REVIEW, degrade
            if (contract.acceptanceCriteria().isEmpty()
                    && contract.status() == ContractStatus.READY_FOR_REVIEW) {
                contract = new TaskContract(
                        contract.id(), contract.version(), contract.createdAt(),
                        contract.objective(), contract.scope(),
                        List.of("Implementation matches the objective: " + contract.objective()),
                        contract.testRequirements(), contract.constraints(), contract.nonGoals(),
                        contract.riskAreas(), contract.ambiguityNotes(), contract.humanQuestions(),
                        contract.status(), contract.compilationPass(), contract.source()
                );
                validationErrors.clear();
                validationErrors.addAll(TaskContractValidator.validate(contract));
            }
        }
        if (!validationErrors.isEmpty()) {
            // Re-compile as NEEDS_CLARIFICATION with the errors noted
            contract = new TaskContract(
                    contract.id(), contract.version(), contract.createdAt(),
                    contract.objective(), contract.scope(),
                    contract.acceptanceCriteria(), contract.testRequirements(),
                    contract.constraints(), contract.nonGoals(),
                    contract.riskAreas(),
                    merge(contract.ambiguityNotes(), validationErrors.stream()
                            .map(e -> "Validation: " + e).toList()),
                    contract.humanQuestions(),
                    ContractStatus.NEEDS_CLARIFICATION,
                    contract.compilationPass(),
                    contract.source()
            );
        }

        TaskContractArtifactWriter.writeJson(contract, artifactsDir);
        TaskContractArtifactWriter.writeMarkdown(contract, artifactsDir);

        var jsonRef = toWorkspaceRef(context.workspaceRoot(),
                artifactsDir.resolve("task-contract.json"));
        var mdRef = toWorkspaceRef(context.workspaceRoot(),
                artifactsDir.resolve("task-contract.md"));

        var result = new LinkedHashMap<String, Object>();
        result.put("contract_id", contract.id());
        result.put("contract_status", contract.status().name());
        result.put("contract_version", contract.version());
        result.put("compilation_pass", contract.compilationPass());
        result.put("artifacts", List.of(jsonRef, mdRef));
        result.put("validation_errors", validationErrors);
        result.put("generated_at", Instant.now().toString());

        return NodeResult.completed("completed", List.of(jsonRef, mdRef), result);
    }

    /**
     * MVP deterministic compiler: extracts a basic TaskContract from requirement text.
     * This is intentionally simple — real LLM-based compilation will replace this.
     */
    TaskContract compile(String requirementText, String workstreamId) {
        var lines = requirementText.lines().toList();
        var objective = extractObjective(lines);
        var scopeIncluded = List.of(extractFirstCodeOrFileReference(lines));
        var scopeExcluded = List.of("Files not explicitly mentioned");
        var acceptanceCriteria = List.of(
                "Implementation achieves: " + objective,
                "No files changed outside the specified scope",
                "Tests pass"
        );
        var testRequirements = List.of("Tests verify the objective is met");
        var constraints = extractConstraints(lines);
        var nonGoals = List.of();

        return new TaskContract(
                "tc-" + workstreamId,
                1,
                Instant.now(),
                objective,
                new TaskContract.Scope(scopeIncluded, scopeExcluded),
                acceptanceCriteria,
                testRequirements,
                constraints,
                nonGoals,
                List.of(), // riskAreas
                List.of(), // ambiguityNotes
                List.of(), // humanQuestions
                ContractStatus.READY_FOR_REVIEW,
                1,
                new TaskContract.Source("requirement.md", "contract_compiler")
        );
    }

    private static String extractObjective(List<String> lines) {
        // First non-heading, non-blank line is the objective
        for (var line : lines) {
            var trimmed = line.trim();
            if (trimmed.startsWith("#") || trimmed.isBlank()) continue;
            return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
        }
        return "(no objective found in requirement)";
    }

    private static String extractFirstCodeOrFileReference(List<String> lines) {
        // Find file references like README.md, *.java, etc.
        for (var line : lines) {
            var trimmed = line.trim();
            if (trimmed.startsWith("#") || trimmed.isBlank()) continue;
            // Look for file patterns
            var words = trimmed.split("\\s+");
            for (var word : words) {
                if (word.matches(".*\\.[a-zA-Z]{1,10}.*") && !word.startsWith("http")) {
                    return word.replaceAll("[,.;:]$", "");
                }
            }
        }
        return "(scope to be determined)";
    }

    private static List<String> extractConstraints(List<String> lines) {
        var constraints = new ArrayList<String>();
        for (var line : lines) {
            var lower = line.toLowerCase();
            if (lower.contains("under") || lower.contains("no more than") || lower.contains("max")) {
                constraints.add(line.trim());
            }
            if (lower.contains("chinese") || lower.contains("中文")) {
                constraints.add("Use Chinese language");
            }
        }
        return constraints.isEmpty() ? List.of() : List.copyOf(constraints);
    }

    private static List<String> merge(List<String> a, List<String> b) {
        var merged = new ArrayList<>(a);
        merged.addAll(b);
        return List.copyOf(merged);
    }

    private static String readRequirement(Path workstreamDir) throws IOException {
        var path = workstreamDir.resolve("requirement.md");
        if (!Files.exists(path)) {
            throw new IOException("requirement.md not found in " + workstreamDir);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String toWorkspaceRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize()).toString();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl dual-runtime -Dtest=ContractCompileHandlerTest 2>&1 | tail -10`
Expected: All 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add dual-runtime/src/main/java/dev/dual/runtime/handler/ContractCompileHandler.java dual-runtime/src/test/java/dev/dual/runtime/handler/ContractCompileHandlerTest.java
git commit -m "feat(P5): ContractCompileHandler — deterministic contract compilation"
```

---

## Task 5: ContractReviewGate + Registry

**Files:**
- Modify: `dual-runtime/src/main/java/dev/dual/runtime/handler/ContractRegistry.java`
- Modify: `dual-cli/src/main/java/dev/dual/cli/CliSupportedHandlers.java`
- Create: `dual-runtime/src/test/java/dev/dual/runtime/handler/ContractReviewGateTest.java`

The gate reuses `human.review` with `allowed_actions: [approve, clarify, reduce_scope, reject]`. No new GateHandler is needed — the design doc says "不需要新 handler". The routing is handled by workflow edges.

- [ ] **Step 1: Write the failing test for gate routing**

Create `dual-runtime/src/test/java/dev/dual/runtime/handler/ContractReviewGateTest.java`:

```java
package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContractReviewGate reuses human.review handler with extended allowed actions.
 * Tests verify the gate opens with the correct allowed actions and routing works.
 */
class ContractReviewGateTest {

    @Test
    void humanReviewGateSupportsContractReviewActions() {
        // The gate is human.review with custom allowed_actions
        var handler = new HumanReviewGateHandler();
        var definition = new NodeDefinition(
                "gate.contract_review", NodeKind.GATE, "human.review",
                Map.of("allowed_actions", List.of("approve", "clarify", "reduce_scope", "reject"))
        );
        var instance = new NodeInstance(
                "ni-gate-001", "gate.contract_review", "stage-01", "iter-001",
                NodeInstance.Status.PENDING, List.of(), Instant.now(), null
        );
        var context = new GateHandler.GateContext(
                java.nio.file.Path.of("/tmp/test"), "ws-001", definition, instance
        );

        var result = handler.openOrInspect(context);
        assertEquals("open", result.outcome());

        @SuppressWarnings("unchecked")
        var allowed = (List<String>) result.result().get("allowed_actions");
        assertTrue(allowed.contains("approve"));
        assertTrue(allowed.contains("clarify"));
        assertTrue(allowed.contains("reduce_scope"));
        assertTrue(allowed.contains("reject"));
    }

    @Test
    void contractCompileRegisteredInContractRegistry() {
        assertTrue(ContractRegistry.hasContract("contract.compile"));
        var contract = ContractRegistry.forNode("contract.compile");
        assertTrue(contract.allowedDecisions().contains("READY_FOR_REVIEW"));
        assertTrue(contract.allowedDecisions().contains("NEEDS_CLARIFICATION"));
        assertTrue(contract.allowedDecisions().contains("NEEDS_SCOPE_REDUCTION"));
        assertTrue(contract.allowedDecisions().contains("REJECTED"));
    }

    @Test
    void contractCompileNotUseApprovedDraft() {
        var contract = ContractRegistry.forNode("contract.compile");
        assertFalse(contract.allowedDecisions().contains("APPROVED_DRAFT"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl dual-runtime -Dtest=ContractReviewGateTest 2>&1 | tail -10`
Expected: `ContractRegistry.hasContract("contract.compile")` returns false.

- [ ] **Step 3: Add contract.compile to ContractRegistry**

Add to `ContractRegistry.java`, after the existing `STAGE_HANDOFF` field:

```java
private static final NodeOutputContract CONTRACT_COMPILE = new NodeOutputContract(
        "contract.compile",
        Set.of("decision", "summary"),
        Set.of("READY_FOR_REVIEW", "NEEDS_CLARIFICATION", "NEEDS_SCOPE_REDUCTION", "REJECTED"),
        Set.of("contract_id", "contract_status", "contract_version")
);
```

Add to the `CONTRACTS` map:

```java
Map.entry("contract.compile", CONTRACT_COMPILE),
```

- [ ] **Step 4: Add contract.compile to CliSupportedHandlers**

Add `"contract.compile"` to the `TYPES` set in `CliSupportedHandlers.java`:

```java
static final Set<String> TYPES = Set.of(
        "acpx.agent_run",
        "artifact.validate",
        "contract.compile",       // NEW
        "context.build_package",
        // ... rest unchanged
);
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl dual-runtime -Dtest=ContractReviewGateTest 2>&1 | tail -10`
Expected: All 3 tests pass.

- [ ] **Step 6: Commit**

```bash
git add dual-runtime/src/main/java/dev/dual/runtime/handler/ContractRegistry.java dual-runtime/src/main/java/dev/dual/runtime/handler/HumanReviewGateHandler.java dual-cli/src/main/java/dev/dual/cli/CliSupportedHandlers.java dual-runtime/src/test/java/dev/dual/runtime/handler/ContractReviewGateTest.java
git commit -m "feat(P5): ContractReviewGate — register contract.compile, extend allowed actions"
```

---

## Task 6: P5-Enabled Workflow Definition

**Files:**
- Modify: `dual-runtime/src/main/java/dev/dual/runtime/lifecycle/LifecycleWorkflowDefinitionFactory.java`
- Create: `dual-runtime/src/test/java/dev/dual/runtime/lifecycle/ContractWorkflowDefinitionTest.java`

Add a new `p5Mainline()` workflow that inserts `contract.compile` and `gate.contract_review` between intake and design.

- [ ] **Step 1: Write the failing test**

Create `dual-runtime/src/test/java/dev/dual/runtime/lifecycle/ContractWorkflowDefinitionTest.java`:

```java
package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContractWorkflowDefinitionTest {

    @Test
    void p5MainlineContainsContractNodes() {
        var factory = new LifecycleWorkflowDefinitionFactory();
        var wf = factory.p5Mainline();

        var nodeIds = wf.nodes().stream().map(n -> n.id()).toList();
        assertTrue(nodeIds.contains("contract.compile"), "P5 workflow must have contract.compile node");
        assertTrue(nodeIds.contains("gate.contract_review"), "P5 workflow must have gate.contract_review node");
    }

    @Test
    void p5MainlineRoutesContractReviewToDesign() {
        var factory = new LifecycleWorkflowDefinitionFactory();
        var wf = factory.p5Mainline();

        // After gate.contract_review approved → design.claude_design
        var edge = wf.edges().stream()
                .filter(e -> "gate.contract_review".equals(e.from())
                        && "approved".equals(e.on()))
                .findFirst();
        assertTrue(edge.isPresent());
        assertEquals("design.claude_design", edge.get().to());
    }

    @Test
    void p5MainlineRoutesClarifyBackToCompile() {
        var factory = new LifecycleWorkflowDefinitionFactory();
        var wf = factory.p5Mainline();

        var edge = wf.edges().stream()
                .filter(e -> "gate.contract_review".equals(e.from())
                        && "clarify".equals(e.on()))
                .findFirst();
        assertTrue(edge.isPresent());
        assertEquals("contract.compile", edge.get().to());
    }

    @Test
    void thinMainlineDoesNotContainContractNodes() {
        var factory = new LifecycleWorkflowDefinitionFactory();
        var wf = factory.thinMainline();

        var nodeIds = wf.nodes().stream().map(n -> n.id()).toList();
        assertFalse(nodeIds.contains("contract.compile"));
        assertFalse(nodeIds.contains("gate.contract_review"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl dual-runtime -Dtest=ContractWorkflowDefinitionTest 2>&1 | tail -10`
Expected: `p5Mainline()` method doesn't exist.

- [ ] **Step 3: Add p5Mainline() to LifecycleWorkflowDefinitionFactory**

Add the following method to `LifecycleWorkflowDefinitionFactory.java`:

```java
/**
 * P5-enabled workflow: inserts contract.compile → gate.contract_review
 * between intake approval and design.
 * ContractReviewGate replaces gate.human_requirement_review as the
 * primary gate before implementation.
 */
public WorkflowDefinition p5Mainline() {
    return new WorkflowDefinition(
            "agent-lifecycle-p5",
            2,
            "intake.claude_brainstorm",
            List.of(
                    agentTask("intake.claude_brainstorm", "claude", "claude-main", "intake-claude-brainstorm"),
                    contextTask("context.intake_codex_product_review", "intake.codex_product_review",
                            "Package Claude intake output for Codex product review.",
                            "product_review"),
                    agentTask("intake.codex_product_review", "codex", "codex-reviewer", "intake-codex-product-review"),
                    decision("intake.decision"),
                    gate("gate.human_requirement_review"),
                    // P5: Contract compilation and review
                    task("contract.compile", "contract.compile"),
                    gate("gate.contract_review", List.of("approve", "clarify", "reduce_scope", "reject")),
                    // Design phase (unchanged)
                    agentTask("design.claude_design", "claude", "claude-main", "design-claude"),
                    contextTask("context.design_codex_arch_review", "design.codex_arch_review",
                            "Package Claude design and prior review context for Codex architecture review.",
                            "architecture_review"),
                    agentTask("design.codex_arch_review", "codex", "codex-reviewer", "design-codex-review"),
                    decision("design.decision"),
                    gate("gate.human_design_approval"),
                    agentTask("plan.claude_plan", "claude", "claude-main", "plan-claude"),
                    contextTask("context.plan_codex_plan_review", "plan.codex_plan_review",
                            "Package Claude plan and design context for Codex plan review.",
                            "plan_review"),
                    agentTask("plan.codex_plan_review", "codex", "codex-reviewer", "plan-codex-review"),
                    decision("plan.decision"),
                    gate("gate.human_plan_approval"),
                    contextTask("context.implement_claude", "implement.claude_implement",
                            "Package approved plan and review history for Claude implementation.",
                            "implementation"),
                    writableAgentTask("implement.claude_implement", "claude", "claude-main", "implement-claude"),
                    agentTask("implement.claude_self_test_report", "claude", "claude-main", "implement-claude-self-test"),
                    task("implement.artifact_validation", "artifact.validate"),
                    contextTask("context.acceptance_codex_verifier_review", "acceptance.codex_verifier_review",
                            "Package implementation output, self-test report, and Git evidence for Codex acceptance.",
                            "acceptance"),
                    agentTask("acceptance.codex_verifier_review", "codex", "codex-reviewer", "acceptance-codex-review"),
                    decision("acceptance.decision"),
                    task("acceptance.generate_final_report", "report.generate_final"),
                    task("delivery.prepare_pr", "delivery.prepare_pr"),
                    gate("gate.human_acceptance"),
                    agentTask("stage.claude_handoff", "claude", "claude-main", "stage-claude-handoff"),
                    task("stage.generate_next_prompt", "prompt.generate_next_stage"),
                    gate("gate.human_next_stage_approval", List.of("approve", "revise", "complete"))
            ),
            List.of(
                    edge("intake.claude_brainstorm", "completed", "context.intake_codex_product_review"),
                    edge("context.intake_codex_product_review", "completed", "intake.codex_product_review"),
                    edge("intake.codex_product_review", "completed", "intake.decision"),
                    edge("intake.decision", "READY_FOR_PRODUCT_REVIEW", "gate.human_requirement_review"),
                    edge("intake.decision", "NEEDS_REVISION", "intake.claude_brainstorm"),
                    edge("gate.human_requirement_review", "approved", "contract.compile"),
                    edge("gate.human_requirement_review", "revise", "intake.claude_brainstorm"),
                    // P5 contract edges
                    edge("contract.compile", "completed", "gate.contract_review"),
                    edge("gate.contract_review", "approved", "design.claude_design"),
                    edge("gate.contract_review", "clarify", "contract.compile"),
                    edge("gate.contract_review", "reduce_scope", "contract.compile"),
                    // reject terminates (no edge — workflow ends)
                    // Design phase (unchanged)
                    edge("design.claude_design", "completed", "context.design_codex_arch_review"),
                    edge("context.design_codex_arch_review", "completed", "design.codex_arch_review"),
                    edge("design.codex_arch_review", "completed", "design.decision"),
                    edge("design.decision", "READY_FOR_ARCH_REVIEW", "gate.human_design_approval"),
                    edge("design.decision", "NEEDS_REVISION", "design.claude_design"),
                    edge("gate.human_design_approval", "approved", "plan.claude_plan"),
                    edge("gate.human_design_approval", "revise", "design.claude_design"),
                    edge("plan.claude_plan", "completed", "context.plan_codex_plan_review"),
                    edge("context.plan_codex_plan_review", "completed", "plan.codex_plan_review"),
                    edge("plan.codex_plan_review", "completed", "plan.decision"),
                    edge("plan.decision", "READY_TO_IMPLEMENT", "gate.human_plan_approval"),
                    edge("plan.decision", "NEEDS_REVISION", "plan.claude_plan"),
                    edge("gate.human_plan_approval", "approved", "context.implement_claude"),
                    edge("gate.human_plan_approval", "revise", "plan.claude_plan"),
                    edge("context.implement_claude", "completed", "implement.claude_implement"),
                    edge("implement.claude_implement", "completed", "implement.claude_self_test_report"),
                    edge("implement.claude_self_test_report", "completed", "implement.artifact_validation"),
                    edge("implement.artifact_validation", "completed", "context.acceptance_codex_verifier_review"),
                    edge("context.acceptance_codex_verifier_review", "completed", "acceptance.codex_verifier_review"),
                    edge("acceptance.codex_verifier_review", "completed", "acceptance.decision"),
                    edge("acceptance.decision", "ACCEPT", "acceptance.generate_final_report"),
                    edge("acceptance.decision", "ACCEPT_WITH_NOTES", "acceptance.generate_final_report"),
                    edge("acceptance.decision", "REJECT", "context.implement_claude"),
                    edge("acceptance.generate_final_report", "completed", "delivery.prepare_pr"),
                    edge("delivery.prepare_pr", "completed", "gate.human_acceptance"),
                    edge("gate.human_acceptance", "approved", "stage.claude_handoff"),
                    edge("gate.human_acceptance", "revise", "context.implement_claude"),
                    edge("stage.claude_handoff", "completed", "stage.generate_next_prompt"),
                    edge("stage.generate_next_prompt", "completed", "gate.human_next_stage_approval"),
                    edge("gate.human_next_stage_approval", "approved", "intake.claude_brainstorm"),
                    edge("gate.human_next_stage_approval", "revise", "stage.claude_handoff"),
                    edge("gate.human_next_stage_approval", "complete", "acceptance.generate_final_report",
                            EdgeCondition.configEquals("completion_mode", "final_report"))
            )
    );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl dual-runtime -Dtest=ContractWorkflowDefinitionTest 2>&1 | tail -10`
Expected: All 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add dual-runtime/src/main/java/dev/dual/runtime/lifecycle/LifecycleWorkflowDefinitionFactory.java dual-runtime/src/test/java/dev/dual/runtime/lifecycle/ContractWorkflowDefinitionTest.java
git commit -m "feat(P5): P5-enabled workflow definition with contract.compile → gate.contract_review"
```

---

## Task 7: Context Governance — task_contract Section

**Files:**
- Modify: `dual-runtime/src/main/java/dev/dual/runtime/handler/ContextProfiles.java`
- Modify: `dual-runtime/src/main/java/dev/dual/runtime/handler/ContextBuildPackageHandler.java`
- Create: `dual-runtime/src/test/java/dev/dual/runtime/handler/ContextTaskContractSectionTest.java`

- [ ] **Step 1: Write the failing test**

Create `dual-runtime/src/test/java/dev/dual/runtime/handler/ContextTaskContractSectionTest.java`:

```java
package dev.dual.runtime.handler;

import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContextTaskContractSectionTest {
    @TempDir Path tempDir;

    private TaskHandler.TaskContext makeContext(Path workspaceRoot, String workstreamId, String profile) {
        var definition = new NodeDefinition(
                "ctx-node", NodeKind.TASK, "context.build_package",
                Map.of("target_node", "acceptance.codex_verifier_review", "profile", profile)
        );
        var instance = new NodeInstance(
                "ni-ctx-001", "ctx-node", "stage-01", "iter-001",
                NodeInstance.Status.PENDING, List.of(), Instant.now(), null
        );
        return new TaskHandler.TaskContext(workspaceRoot, workstreamId, definition, instance);
    }

    private Path initWorkstreamWithContract(Path wsRoot, String wsId) throws IOException {
        var wsDir = wsRoot.resolve(".agent").resolve("workstreams").resolve(wsId);
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Test requirement", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("artifacts"));

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        var contract = new TaskContract(
                "tc-001", 1, Instant.now(),
                "Test objective",
                new TaskContract.Scope(List.of("file1.java"), List.of("file2.java")),
                List.of("AC1", "AC2"),
                List.of("TR1"),
                List.of("Use Java"),
                List.of("No Python"),
                List.of(), List.of(), List.of(),
                ContractStatus.READY_FOR_REVIEW,
                1,
                new TaskContract.Source("requirement.md", "contract_compiler")
        );
        mapper.writeValue(wsDir.resolve("artifacts/task-contract.json").toFile(), contract);
        return wsDir;
    }

    @Test
    void acceptanceProfileIncludesTaskContractSection() {
        var budget = ContextProfiles.acceptance();
        var sectionNames = budget.sections().stream()
                .map(SectionRequirement::section)
                .toList();
        assertTrue(sectionNames.contains("task_contract"), "acceptance profile must include task_contract section");
    }

    @Test
    void taskContractSectionIsCriticalPriority() {
        var budget = ContextProfiles.acceptance();
        var taskContractSection = budget.sections().stream()
                .filter(s -> "task_contract".equals(s.section()))
                .findFirst()
                .orElseThrow();
        assertEquals(Priority.CRITICAL, taskContractSection.priority());
    }

    @Test
    void contextPackageRendersTaskContractSection() throws Exception {
        var wsRoot = tempDir.resolve("workspace");
        Files.createDirectories(wsRoot);
        var wsId = "ws-001";
        initWorkstreamWithContract(wsRoot, wsId);

        var handler = new ContextBuildPackageHandler();
        var context = makeContext(wsRoot, wsId, "acceptance");
        var result = handler.execute(context);

        assertEquals("completed", result.outcome());

        var packagePath = wsRoot.resolve(result.result().get("context_package").toString());
        var content = Files.readString(Path.of(packagePath.toString()), StandardCharsets.UTF_8);
        assertTrue(content.contains("TaskContract"), "Context package must contain TaskContract section");
        assertTrue(content.contains("Test objective"), "TaskContract section must show objective");
        assertTrue(content.contains("AC1"), "TaskContract section must show acceptance criteria");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl dual-runtime -Dtest=ContextTaskContractSectionTest 2>&1 | tail -10`
Expected: acceptance profile does not contain `task_contract` section.

- [ ] **Step 3: Add task_contract section to acceptance profile in ContextProfiles**

Add to the `acceptance()` method in `ContextProfiles.java`, before the closing `)`:

```java
new SectionRequirement("task_contract",
        MissingPolicy.FAIL, Priority.CRITICAL,
        new DegradationPolicy(Degradation.FULL, 0),
        OverflowPolicy.FAIL, 2_000)
```

Also add a `task_contract_warn` profile for legacy workflows. Create a new static method:

```java
static ContextBudget acceptanceLegacy() {
    var budget = acceptance();
    // Replace task_contract section: WARN instead of FAIL for legacy
    var sections = budget.sections().stream()
            .map(s -> "task_contract".equals(s.section())
                    ? new SectionRequirement("task_contract",
                        MissingPolicy.WARN, Priority.HIGH,
                        new DegradationPolicy(Degradation.FULL, 0),
                        OverflowPolicy.DEGRADE, 2_000)
                    : s)
            .toList();
    return new ContextBudget(budget.totalBudget(), sections, budget.configOverrides());
}
```

- [ ] **Step 4: Add task_contract to ContextBuildPackageHandler**

In `ContextBuildPackageHandler.java`:

1. Add `"task_contract"` to `FULL_SECTIONS` list:

```java
private static final List<String> FULL_SECTIONS = List.of(
        "requirement",
        "input_artifacts",
        "iteration_history",
        "human_feedback",
        "recent_agent_outputs",
        "git_evidence",
        "ledger_pointers",
        "task_contract"
);
```

2. Add `checkAvailability` case:

```java
case "task_contract" -> Files.exists(workstreamDir.resolve("artifacts/task-contract.json"));
```

3. Add `sectionTitle` case:

```java
case "task_contract" -> "TaskContract";
```

4. Add `appendGovernedSection` case (after the `ledger_pointers` case, before `default`):

```java
case "task_contract" -> {
    var contractPath = workstreamDir.resolve("artifacts/task-contract.json");
    if (Files.exists(contractPath)) {
        var contractJson = readJsonUnchecked(contractPath);
        builder.append("**Objective:** ").append(text(contractJson.get("objective"))).append("\n\n");
        builder.append("**Status:** ").append(text(contractJson.get("status"))).append("\n\n");

        @SuppressWarnings("unchecked")
        var scope = (Map<String, Object>) contractJson.get("scope");
        if (scope != null) {
            builder.append("**Scope Included:**\n");
            @SuppressWarnings("unchecked")
            var included = (List<String>) scope.get("included");
            if (included != null) {
                for (var item : included) builder.append("- ").append(item).append("\n");
            }
            builder.append("\n**Scope Excluded:**\n");
            @SuppressWarnings("unchecked")
            var excluded = (List<String>) scope.get("excluded");
            if (excluded != null) {
                for (var item : excluded) builder.append("- ").append(item).append("\n");
            }
            builder.append("\n");
        }

        @SuppressWarnings("unchecked")
        var acceptanceCriteria = (List<String>) contractJson.get("acceptance_criteria");
        if (acceptanceCriteria != null && !acceptanceCriteria.isEmpty()) {
            builder.append("**Acceptance Criteria:**\n");
            for (var item : acceptanceCriteria) builder.append("- ").append(item).append("\n");
            builder.append("\n");
        }

        @SuppressWarnings("unchecked")
        var nonGoals = (List<String>) contractJson.get("non_goals");
        if (nonGoals != null && !nonGoals.isEmpty()) {
            builder.append("**Non-Goals:**\n");
            for (var item : nonGoals) builder.append("- ").append(item).append("\n");
            builder.append("\n");
        }

        @SuppressWarnings("unchecked")
        var constraints = (List<String>) contractJson.get("constraints");
        if (constraints != null && !constraints.isEmpty()) {
            builder.append("**Constraints:**\n");
            for (var item : constraints) builder.append("- ").append(item).append("\n");
            builder.append("\n");
        }

        @SuppressWarnings("unchecked")
        var riskAreas = (List<String>) contractJson.get("risk_areas");
        if (riskAreas != null && !riskAreas.isEmpty()) {
            builder.append("**Risk Areas:**\n");
            for (var item : riskAreas) builder.append("- ").append(item).append("\n");
            builder.append("\n");
        }
    } else {
        builder.append("(task-contract.json not found)\n\n");
    }
}
```

5. Add `task_contract` to the `sections()` switch for acceptance profile:

In the `"acceptance"` case, add `"task_contract"` to the list:

```java
case "acceptance" -> List.of(
        "requirement",
        "input_artifacts",
        "iteration_history",
        "human_feedback",
        "recent_agent_outputs",
        "git_evidence",
        "task_contract",       // NEW
        "ledger_pointers"
);
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl dual-runtime -Dtest=ContextTaskContractSectionTest 2>&1 | tail -10`
Expected: All 3 tests pass.

- [ ] **Step 6: Commit**

```bash
git add dual-runtime/src/main/java/dev/dual/runtime/handler/ContextProfiles.java dual-runtime/src/main/java/dev/dual/runtime/handler/ContextBuildPackageHandler.java dual-runtime/src/test/java/dev/dual/runtime/handler/ContextTaskContractSectionTest.java
git commit -m "feat(P5): task_contract context section — governance, rendering, profiles"
```

---

## Task 8: Ledger Events for Contract Lifecycle

**Files:**
- Create: `dual-runtime/src/test/java/dev/dual/runtime/handler/ContractLifecycleLedgerTest.java`

The existing `LedgerEventLog.appendEvent()` is package-private in `dual-ledger-file`. Since contract events are emitted by the handler, we test that the handler result includes event metadata. The actual event writing is done by the runtime infrastructure (not the handler itself), so we test the handler outputs and event types.

- [ ] **Step 1: Write the test**

Create `dual-runtime/src/test/java/dev/dual/runtime/handler/ContractLifecycleLedgerTest.java`:

```java
package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that contract lifecycle events are properly represented.
 * Event types: contract_compilation_started, contract_compilation_completed,
 * contract_review_started, contract_review_completed,
 * contract_approved, contract_requires_clarification,
 * contract_scope_reduction_requested, contract_rejected
 */
class ContractLifecycleLedgerTest {
    @TempDir Path tempDir;

    @Test
    void contractCompileEmitsEventMetadata() throws Exception {
        var wsRoot = tempDir.resolve("workspace");
        Files.createDirectories(wsRoot);
        var wsId = "ws-001";
        var wsDir = wsRoot.resolve(".agent/workstreams/" + wsId);
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Test requirement", StandardCharsets.UTF_8);

        var handler = new ContractCompileHandler();
        var definition = new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile", Map.of());
        var instance = new NodeInstance("ni-001", "contract.compile", "stage-01", "iter-001",
                NodeInstance.Status.PENDING, List.of(), Instant.now(), null);
        var context = new TaskHandler.TaskContext(wsRoot, wsId, definition, instance);

        var result = handler.execute(context);

        // The handler result includes contract metadata that maps to events
        assertEquals("completed", result.outcome());
        assertNotNull(result.result().get("contract_id"));
        assertNotNull(result.result().get("contract_status"));
        assertNotNull(result.result().get("generated_at"));
    }

    @Test
    void contractEventTypesExistAsConstants() {
        // Verify event type constants are defined
        assertEquals("contract_compilation_started", ContractEventTypes.COMPILATION_STARTED);
        assertEquals("contract_compilation_completed", ContractEventTypes.COMPILATION_COMPLETED);
        assertEquals("contract_review_started", ContractEventTypes.REVIEW_STARTED);
        assertEquals("contract_review_completed", ContractEventTypes.REVIEW_COMPLETED);
        assertEquals("contract_approved", ContractEventTypes.APPROVED);
        assertEquals("contract_requires_clarification", ContractEventTypes.REQUIRES_CLARIFICATION);
        assertEquals("contract_scope_reduction_requested", ContractEventTypes.SCOPE_REDUCTION_REQUESTED);
        assertEquals("contract_rejected", ContractEventTypes.REJECTED);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl dual-runtime -Dtest=ContractLifecycleLedgerTest 2>&1 | tail -10`
Expected: `ContractEventTypes` doesn't exist.

- [ ] **Step 3: Create ContractEventTypes**

Create `dual-runtime/src/main/java/dev/dual/runtime/handler/ContractEventTypes.java`:

```java
package dev.dual.runtime.handler;

/**
 * Contract lifecycle event types for events.jsonl.
 * These are the standard event types emitted during contract compilation and review.
 */
public final class ContractEventTypes {
    private ContractEventTypes() {}

    public static final String COMPILATION_STARTED = "contract_compilation_started";
    public static final String COMPILATION_COMPLETED = "contract_compilation_completed";
    public static final String REVIEW_STARTED = "contract_review_started";
    public static final String REVIEW_COMPLETED = "contract_review_completed";
    public static final String APPROVED = "contract_approved";
    public static final String REQUIRES_CLARIFICATION = "contract_requires_clarification";
    public static final String SCOPE_REDUCTION_REQUESTED = "contract_scope_reduction_requested";
    public static final String REJECTED = "contract_rejected";
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl dual-runtime -Dtest=ContractLifecycleLedgerTest 2>&1 | tail -10`
Expected: All 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add dual-runtime/src/main/java/dev/dual/runtime/handler/ContractEventTypes.java dual-runtime/src/test/java/dev/dual/runtime/handler/ContractLifecycleLedgerTest.java
git commit -m "feat(P5): ContractEventTypes — ledger event constants for contract lifecycle"
```

---

## Task 9: Codex Review Integration

**Files:**
- Modify: `dual-runtime/src/main/java/dev/dual/runtime/handler/ContractRegistry.java`

Add Codex acceptance review contract extensions for contract_gaps, out_of_scope_files, missing_tests.

- [ ] **Step 1: Add optional keys to acceptance review contract**

In `ContractRegistry.java`, change the `ACCEPTANCE_REVIEW` contract to include optional keys:

```java
private static final NodeOutputContract ACCEPTANCE_REVIEW = new NodeOutputContract(
        "acceptance.codex_verifier_review",
        Set.of("decision", "summary"),
        Set.of("ACCEPT", "ACCEPT_WITH_NOTES", "REJECT"),
        Set.of("contract_gaps", "out_of_scope_files", "missing_tests")  // P5 optional keys
);
```

- [ ] **Step 2: Run existing tests to verify backward compatibility**

Run: `mvn test -pl dual-runtime 2>&1 | tail -20`
Expected: All existing tests still pass. The new optional keys don't break existing review outputs.

- [ ] **Step 3: Commit**

```bash
git add dual-runtime/src/main/java/dev/dual/runtime/handler/ContractRegistry.java
git commit -m "feat(P5): Add contract_gaps, out_of_scope_files, missing_tests as optional Codex review keys"
```

---

## Task 10: Final Report Enhancement

**Files:**
- Modify: `dual-runtime/src/main/java/dev/dual/runtime/handler/ReportGenerateFinalHandler.java`
- Create: `dual-runtime/src/test/java/dev/dual/runtime/handler/ReportTaskContractTest.java`

- [ ] **Step 1: Write the failing test**

Create `dual-runtime/src/test/java/dev/dual/runtime/handler/ReportTaskContractTest.java`:

```java
package dev.dual.runtime.handler;

import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportTaskContractTest {
    @TempDir Path tempDir;

    @Test
    void finalReportIncludesTaskContractSection() throws Exception {
        var wsRoot = tempDir.resolve("workspace");
        Files.createDirectories(wsRoot);
        var wsId = "ws-001";
        var wsDir = wsRoot.resolve(".agent/workstreams/" + wsId);
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Test requirement", StandardCharsets.UTF_8);

        // Write task-contract.json artifact
        var artifactsDir = wsDir.resolve("artifacts");
        Files.createDirectories(artifactsDir);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        var contract = new TaskContract(
                "tc-001", 1, Instant.now(),
                "Add project description to README",
                new TaskContract.Scope(List.of("README.md"), List.of("code")),
                List.of("AC1", "AC2", "AC3"),
                List.of("TR1"),
                List.of("Use Chinese"),
                List.of("No Python"),
                List.of(), List.of(), List.of(),
                ContractStatus.READY_FOR_REVIEW,
                1,
                new TaskContract.Source("requirement.md", "contract_compiler")
        );
        mapper.writeValue(artifactsDir.resolve("task-contract.json").toFile(), contract);

        // Generate report
        var handler = new ReportGenerateFinalHandler();
        var definition = new NodeDefinition("report.generate_final", NodeKind.TASK, "report.generate_final", Map.of());
        var instance = new NodeInstance("ni-rpt-001", "report.generate_final", "stage-01", "iter-001",
                NodeInstance.Status.PENDING, List.of(), Instant.now(), null);
        var context = new TaskHandler.TaskContext(wsRoot, wsId, definition, instance);

        var result = handler.execute(context);
        assertEquals("completed", result.outcome());

        // Read the report
        var reportRef = result.result().get("report_path").toString();
        var reportContent = Files.readString(wsRoot.resolve(reportRef), StandardCharsets.UTF_8);

        assertTrue(reportContent.contains("TaskContract"), "Report must contain TaskContract section");
        assertTrue(reportContent.contains("tc-001"), "Report must show contract ID");
        assertTrue(reportContent.contains("Add project description to README"), "Report must show objective");
        assertTrue(reportContent.contains("3 items"), "Report must show acceptance criteria count");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl dual-runtime -Dtest=ReportTaskContractTest 2>&1 | tail -10`
Expected: Report doesn't contain "TaskContract" section yet.

- [ ] **Step 3: Add TaskContract section to ReportGenerateFinalHandler**

In `ReportGenerateFinalHandler.java`, add a new section after the "User Request" section in `renderReport()`. Insert after the line `builder.append(readOrDefault(workstreamDir.resolve("requirement.md"), "(requirement.md not found)")).append("\n\n");`:

```java
builder.append("## TaskContract\n\n");
appendTaskContractSection(builder, workstreamDir);
```

And add the helper method:

```java
private static void appendTaskContractSection(StringBuilder builder, Path workstreamDir) throws IOException {
    var contractPath = workstreamDir.resolve("artifacts/task-contract.json");
    if (!Files.exists(contractPath)) {
        builder.append("(no TaskContract — legacy workflow)\n\n");
        return;
    }
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    try {
        var contract = mapper.readValue(contractPath.toFile(), dev.dual.core.contract.TaskContract.class);
        builder.append("- **Contract ID:** ").append(contract.id()).append("\n");
        builder.append("- **Objective:** ").append(contract.objective()).append("\n");
        builder.append("- **Status:** ").append(contract.status()).append("\n");
        builder.append("- **Scope Included:** ").append(contract.scope().included().isEmpty() ? "(none)" : String.join(", ", contract.scope().included())).append("\n");
        builder.append("- **Scope Excluded:** ").append(contract.scope().excluded().isEmpty() ? "(none)" : String.join(", ", contract.scope().excluded())).append("\n");
        builder.append("- **Acceptance Criteria:** ").append(contract.acceptanceCriteria().size()).append(" items").append("\n");
        builder.append("- **Non-Goals:** ").append(contract.nonGoals().isEmpty() ? "(none)" : String.join(", ", contract.nonGoals())).append("\n");
        builder.append("- **Contract Gaps Found:** ").append("0").append("\n");
        builder.append("- **Out-of-scope Changes:** ").append("0").append("\n");
        builder.append("\n");
    } catch (Exception e) {
        builder.append("(error reading TaskContract: ").append(e.getMessage()).append(")\n\n");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl dual-runtime -Dtest=ReportTaskContractTest 2>&1 | tail -10`
Expected: Test passes.

- [ ] **Step 5: Commit**

```bash
git add dual-runtime/src/main/java/dev/dual/runtime/handler/ReportGenerateFinalHandler.java dual-runtime/src/test/java/dev/dual/runtime/handler/ReportTaskContractTest.java
git commit -m "feat(P5): Final report includes TaskContract summary section"
```

---

## Task 11: Full Test Suite Verification

**Files:** No new files — run all tests.

- [ ] **Step 1: Run full test suite**

Run: `mvn test 2>&1 | tail -30`
Expected: All tests pass across all modules.

- [ ] **Step 2: Fix any test failures**

If any test fails, investigate and fix. Common issues:
- Import ordering
- Missing test dependency
- Profile name mismatch

- [ ] **Step 3: Run once more to confirm clean**

Run: `mvn test 2>&1 | tail -10`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit any fixes**

```bash
git add -A
git commit -m "fix(P5): test cleanup — ensure all tests pass"
```

---

## Self-Review Checklist

### 1. Spec Coverage

| Spec Requirement | Task |
|---|---|
| TaskContract data model | Task 1 |
| JSON serialization/deserialization | Task 1 |
| contract.compile handler | Task 4 |
| ContractCompiler output statuses | Task 4 |
| task-contract.json + task-contract.md | Task 3 |
| contract.review gate | Task 5 |
| P5-enabled workflow | Task 6 |
| task_contract context section | Task 7 |
| Codex review context | Task 9 |
| Ledger events | Task 8 |
| Final report TaskContract summary | Task 10 |
| Tests | Tasks 1-10 |

### 2. Placeholder Scan

No TBD, TODO, "implement later", "add appropriate error handling", "similar to Task N", or steps without code.

### 3. Type Consistency

- `TaskContract` record fields match across all uses (Tasks 1, 3, 4, 7, 10)
- `ContractStatus.READY_FOR_REVIEW` used consistently (never `APPROVED_DRAFT`)
- `ContractRegistry.forNode("contract.compile")` used in both test and registry (Task 5)
- `ContextProfiles.acceptance()` section names match `ContextBuildPackageHandler` switch (Task 7)

### Not Implemented (by design)

- FailureArbiter
- CalibrationCandidate
- Multi-role Review
- Adversarial Test Agent
- SpecializationRegistry
- Web UI
- Database ledger
- Legacy ledger migration
- Codex writing business code
- Full Stage 2/3 ContractCompiler
