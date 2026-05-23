# Agent Workflow Engine Milestone 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first Java workflow runtime that can represent the full Claude/Codex lifecycle as Task / Decision / Gate nodes, persist workflow state in `.agent/`, and let `dualctl start/status/resume/review` operate through the runtime instead of hardcoded `pending_action` branches.

**Architecture:** Add a new `dual-runtime` Maven module between CLI and adapters. `dual-core` owns workflow domain types, `dual-ledger-file` owns file persistence, `dual-runtime` owns routing and handler dispatch, adapters provide concrete handlers, and `dual-cli` becomes a thin command layer. Milestone 1 creates a thin lifecycle mainline and preserves acpx as the Agent transport.

**Tech Stack:** Java 21, Spring Boot 3.4.4, Maven multi-module, Jackson, Picocli, JUnit 5, acpx CLI.

---

## Scope

This plan implements:

- Workflow domain model: `Task`, `Decision`, `Gate`, `Edge`, `NodeInstance`.
- File ledger workflow state: `workflow/definition.json`, `workflow/instance.json`, `workflow/current.json`, `nodes/*.json`, `gates/*.json`, `events.jsonl`.
- Runtime handler SPI and routing.
- A built-in thin lifecycle workflow definition covering intake, design, plan, implementation, acceptance, and stage completion.
- CLI integration for `start`, `status`, `resume`, `review approve`, and `review revise`.
- Compatibility with real acpx smoke runs through `AcpxAgentTransport`.

This plan does not implement:

- PR creation.
- notification.
- dashboard.
- distributed workers.
- database persistence.
- automatic merge.
- parallel node execution.

## File Map

Create:

- `dual-orchestrator/dual-runtime/pom.xml` - Maven module for runtime and handler SPI.
- `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/NodeKind.java`
- `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/NodeStatus.java`
- `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/WorkflowStatus.java`
- `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/NodeDefinition.java`
- `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/EdgeDefinition.java`
- `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/WorkflowDefinition.java`
- `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/WorkflowInstance.java`
- `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/NodeInstance.java`
- `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/NodeResult.java`
- `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/WorkflowRuntime.java`
- `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/WorkflowCommand.java`
- `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/WorkflowRuntimeResult.java`
- `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/TaskHandler.java`
- `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/DecisionHandler.java`
- `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/GateHandler.java`
- `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/HandlerRegistry.java`
- `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/lifecycle/LifecycleWorkflowDefinitionFactory.java`
- `dual-orchestrator/dual-runtime/src/test/java/dev/dual/runtime/WorkflowRuntimeTest.java`
- `dual-orchestrator/dual-runtime/src/test/java/dev/dual/runtime/lifecycle/LifecycleWorkflowDefinitionFactoryTest.java`
- `dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkflowFileLedger.java`
- `dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkflowDefinitionDocument.java`
- `dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkflowInstanceDocument.java`
- `dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkflowCurrentDocument.java`
- `dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/NodeInstanceDocument.java`
- `dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/GateDocument.java`
- `dual-orchestrator/dual-ledger-file/src/test/java/dev/dual/ledger/file/WorkflowFileLedgerTest.java`

Modify:

- `dual-orchestrator/pom.xml` - add `dual-runtime` module.
- `dual-orchestrator/dual-cli/pom.xml` - depend on `dual-runtime`.
- `dual-orchestrator/dual-ledger-file/pom.xml` - continue depending on `dual-core`.
- `dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkstreamPaths.java` - add workflow path helpers.
- `dual-orchestrator/dual-cli/src/main/java/dev/dual/cli/StartCommand.java` - bootstrap workflow instance.
- `dual-orchestrator/dual-cli/src/main/java/dev/dual/cli/ResumeCommand.java` - delegate node execution to runtime.
- `dual-orchestrator/dual-cli/src/main/java/dev/dual/cli/StatusCommand.java` - show workflow current state.
- `dual-orchestrator/dual-cli/src/main/java/dev/dual/cli/ReviewApproveCommand.java` - resolve human gate through runtime ledger.
- `dual-orchestrator/dual-cli/src/main/java/dev/dual/cli/ReviewReviseCommand.java` - resolve human gate through runtime ledger.
- `dual-orchestrator/README.md` - document workflow engine ledger, commands, and smoke tests.

## Task 1: Add `dual-runtime` Maven Module

**Files:**

- Modify: `dual-orchestrator/pom.xml`
- Create: `dual-orchestrator/dual-runtime/pom.xml`

- [ ] **Step 1: Add a failing module check**

Run:

```bash
cd dual-orchestrator
mvn -pl dual-runtime test
```

Expected: FAIL because Maven cannot find module `dual-runtime`.

- [ ] **Step 2: Register the module in the parent POM**

In `dual-orchestrator/pom.xml`, change the modules section to:

```xml
<modules>
    <module>dual-core</module>
    <module>dual-ledger-file</module>
    <module>dual-runtime</module>
    <module>dual-adapter-acpx</module>
    <module>dual-adapter-git</module>
    <module>dual-cli</module>
</modules>
```

- [ ] **Step 3: Create runtime module POM**

Create `dual-orchestrator/dual-runtime/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>dev.dual</groupId>
        <artifactId>dual-orchestrator</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>dual-runtime</artifactId>
    <name>dual-runtime</name>

    <dependencies>
        <dependency>
            <groupId>dev.dual</groupId>
            <artifactId>dual-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>dev.dual</groupId>
            <artifactId>dual-ledger-file</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 4: Verify the empty module builds**

Run:

```bash
cd dual-orchestrator
mvn -pl dual-runtime -am test
```

Expected: PASS.

## Task 2: Add Workflow Domain Types

**Files:**

- Create: `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/NodeKind.java`
- Create: `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/NodeStatus.java`
- Create: `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/WorkflowStatus.java`
- Create: `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/NodeDefinition.java`
- Create: `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/EdgeDefinition.java`
- Create: `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/WorkflowDefinition.java`
- Create: `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/WorkflowInstance.java`
- Create: `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/NodeInstance.java`
- Create: `dual-orchestrator/dual-core/src/main/java/dev/dual/core/workflow/NodeResult.java`
- Create: `dual-orchestrator/dual-core/src/test/java/dev/dual/core/workflow/WorkflowDefinitionTest.java`

- [ ] **Step 1: Write the domain validation test**

Create `WorkflowDefinitionTest.java`:

```java
package dev.dual.core.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowDefinitionTest {
    @Test
    void acceptsDefinitionWithEntryNodeAndEdges() {
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "intake.claude_brainstorm",
                List.of(
                        new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of()),
                        new NodeDefinition("gate.human_requirement_review", NodeKind.GATE, "human.review", Map.of())
                ),
                List.of(new EdgeDefinition("intake.claude_brainstorm", "completed", "gate.human_requirement_review"))
        );

        assertThat(definition.node("intake.claude_brainstorm").kind()).isEqualTo(NodeKind.TASK);
        assertThat(definition.edgesFrom("intake.claude_brainstorm")).hasSize(1);
    }

    @Test
    void rejectsMissingEntryNode() {
        assertThatThrownBy(() -> new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "missing",
                List.of(new NodeDefinition("existing", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("entry node");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
cd dual-orchestrator
mvn -pl dual-core test -Dtest=WorkflowDefinitionTest
```

Expected: FAIL because workflow classes do not exist.

- [ ] **Step 3: Create enum types**

Create `NodeKind.java`:

```java
package dev.dual.core.workflow;

public enum NodeKind {
    TASK,
    DECISION,
    GATE
}
```

Create `NodeStatus.java`:

```java
package dev.dual.core.workflow;

public enum NodeStatus {
    CREATED,
    READY,
    RUNNING,
    COMPLETED,
    FAILED,
    BLOCKED,
    SKIPPED,
    WAITING
}
```

Create `WorkflowStatus.java`:

```java
package dev.dual.core.workflow;

public enum WorkflowStatus {
    CREATED,
    RUNNING,
    WAITING_FOR_HUMAN,
    BLOCKED,
    COMPLETED,
    FAILED
}
```

- [ ] **Step 4: Create definition records**

Create `NodeDefinition.java`:

```java
package dev.dual.core.workflow;

import java.util.Map;

public record NodeDefinition(
        String id,
        NodeKind kind,
        String handlerType,
        Map<String, Object> config
) {
    public NodeDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("node id is required");
        }
        if (kind == null) {
            throw new IllegalArgumentException("node kind is required");
        }
        if (handlerType == null || handlerType.isBlank()) {
            throw new IllegalArgumentException("handler type is required");
        }
        config = config == null ? Map.of() : Map.copyOf(config);
    }
}
```

Create `EdgeDefinition.java`:

```java
package dev.dual.core.workflow;

public record EdgeDefinition(String from, String on, String to) {
    public EdgeDefinition {
        if (from == null || from.isBlank()) {
            throw new IllegalArgumentException("edge from is required");
        }
        if (on == null || on.isBlank()) {
            throw new IllegalArgumentException("edge on is required");
        }
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("edge to is required");
        }
    }
}
```

Create `WorkflowDefinition.java`:

```java
package dev.dual.core.workflow;

import java.util.List;

public record WorkflowDefinition(
        String workflowId,
        int version,
        String entryNodeId,
        List<NodeDefinition> nodes,
        List<EdgeDefinition> edges
) {
    public WorkflowDefinition {
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflow id is required");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("workflow version must be positive");
        }
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
        edges = List.copyOf(edges == null ? List.of() : edges);
        if (nodeOrNull(entryNodeId, nodes) == null) {
            throw new IllegalArgumentException("entry node does not exist: " + entryNodeId);
        }
    }

    public NodeDefinition node(String nodeId) {
        var node = nodeOrNull(nodeId, nodes);
        if (node == null) {
            throw new IllegalArgumentException("unknown node: " + nodeId);
        }
        return node;
    }

    public List<EdgeDefinition> edgesFrom(String nodeId) {
        return edges.stream().filter(edge -> edge.from().equals(nodeId)).toList();
    }

    private static NodeDefinition nodeOrNull(String nodeId, List<NodeDefinition> candidates) {
        if (nodeId == null) {
            return null;
        }
        return candidates.stream().filter(node -> node.id().equals(nodeId)).findFirst().orElse(null);
    }
}
```

- [ ] **Step 5: Create runtime state records**

Create `WorkflowInstance.java`:

```java
package dev.dual.core.workflow;

import java.time.Instant;
import java.util.List;

public record WorkflowInstance(
        String workflowInstanceId,
        String workflowId,
        int definitionVersion,
        WorkflowStatus status,
        List<String> activeNodeInstanceIds,
        String lastEventId,
        Instant createdAt,
        Instant updatedAt
) {
    public WorkflowInstance {
        activeNodeInstanceIds = List.copyOf(activeNodeInstanceIds == null ? List.of() : activeNodeInstanceIds);
    }
}
```

Create `NodeInstance.java`:

```java
package dev.dual.core.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record NodeInstance(
        String nodeInstanceId,
        String nodeDefinitionId,
        NodeKind kind,
        String handlerType,
        NodeStatus status,
        String stageId,
        String iterationId,
        List<String> inputRefs,
        List<String> outputRefs,
        Map<String, Object> result,
        String runRef,
        Instant startedAt,
        Instant finishedAt
) {
    public NodeInstance {
        inputRefs = List.copyOf(inputRefs == null ? List.of() : inputRefs);
        outputRefs = List.copyOf(outputRefs == null ? List.of() : outputRefs);
        result = Map.copyOf(result == null ? Map.of() : result);
    }
}
```

Create `NodeResult.java`:

```java
package dev.dual.core.workflow;

import java.util.List;
import java.util.Map;

public record NodeResult(
        String outcome,
        NodeStatus status,
        List<String> outputRefs,
        Map<String, Object> result,
        String errorMessage
) {
    public NodeResult {
        outputRefs = List.copyOf(outputRefs == null ? List.of() : outputRefs);
        result = Map.copyOf(result == null ? Map.of() : result);
    }

    public static NodeResult completed(String outcome, List<String> outputRefs, Map<String, Object> result) {
        return new NodeResult(outcome, NodeStatus.COMPLETED, outputRefs, result, null);
    }

    public static NodeResult waiting(String outcome, Map<String, Object> result) {
        return new NodeResult(outcome, NodeStatus.WAITING, List.of(), result, null);
    }
}
```

- [ ] **Step 6: Verify core tests pass**

Run:

```bash
cd dual-orchestrator
mvn -pl dual-core test
```

Expected: PASS.

## Task 3: Add Workflow File Ledger

**Files:**

- Modify: `dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkstreamPaths.java`
- Create: `dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkflowFileLedger.java`
- Create: `dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkflowDefinitionDocument.java`
- Create: `dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkflowInstanceDocument.java`
- Create: `dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkflowCurrentDocument.java`
- Create: `dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/NodeInstanceDocument.java`
- Create: `dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/GateDocument.java`
- Create: `dual-orchestrator/dual-ledger-file/src/test/java/dev/dual/ledger/file/WorkflowFileLedgerTest.java`

- [ ] **Step 1: Write ledger creation test**

Create `WorkflowFileLedgerTest.java`:

```java
package dev.dual.ledger.file;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowFileLedgerTest {
    @TempDir
    Path tempDir;

    @Test
    void initializesWorkflowLedgerAndCurrentSnapshot() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-test"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "intake.claude_brainstorm",
                List.of(new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-test", definition);

        assertThat(paths.workflowDefinitionJson()).exists();
        assertThat(paths.workflowInstanceJson()).exists();
        assertThat(paths.workflowCurrentJson()).exists();
        assertThat(Files.list(paths.nodesDir())).hasSize(1);
        assertThat(Files.readString(paths.eventsJsonl())).contains("workflow_started");
    }

    @Test
    void completesNodeAndActivatesNextNodeByOutcome() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-route"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("second", NodeKind.GATE, "human.review", Map.of())
                ),
                List.of(new EdgeDefinition("first", "completed", "second"))
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-route", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst());

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.COMPLETED, "completed", List.of(), Map.of());

        var next = ledger.loadCurrent(paths);
        assertThat(next.status()).isEqualTo(WorkflowStatus.WAITING_FOR_HUMAN);
        assertThat(ledger.loadNode(paths, next.activeNodeInstanceIds().getFirst()).nodeDefinitionId()).isEqualTo("second");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd dual-orchestrator
mvn -pl dual-ledger-file test -Dtest=WorkflowFileLedgerTest
```

Expected: FAIL because workflow ledger classes and path helpers do not exist.

- [ ] **Step 3: Add workflow path helpers**

In `WorkstreamPaths.java`, add methods:

```java
public Path workflowDir() {
    return workstreamDir().resolve("workflow");
}

public Path workflowDefinitionJson() {
    return workflowDir().resolve("definition.json");
}

public Path workflowInstanceJson() {
    return workflowDir().resolve("instance.json");
}

public Path workflowCurrentJson() {
    return workflowDir().resolve("current.json");
}

public Path nodesDir() {
    return workstreamDir().resolve("nodes");
}

public Path nodeJson(String nodeInstanceId) {
    return nodesDir().resolve(nodeInstanceId + ".json");
}

public Path gatesDir() {
    return workstreamDir().resolve("gates");
}

public Path gateJson(String gateId) {
    return gatesDir().resolve(gateId + ".json");
}

public Path eventsJsonl() {
    return workstreamDir().resolve("events.jsonl");
}
```

- [ ] **Step 4: Create document records**

Create `WorkflowCurrentDocument.java`:

```java
package dev.dual.ledger.file;

import dev.dual.core.workflow.WorkflowStatus;

import java.time.Instant;
import java.util.List;

public record WorkflowCurrentDocument(
        int schemaVersion,
        String workstreamId,
        String workflowInstanceId,
        String workflowId,
        int definitionVersion,
        WorkflowStatus status,
        List<String> activeNodeInstanceIds,
        List<String> openGateIds,
        String lastEventId,
        Instant updatedAt
) {
    public WorkflowCurrentDocument {
        activeNodeInstanceIds = List.copyOf(activeNodeInstanceIds == null ? List.of() : activeNodeInstanceIds);
        openGateIds = List.copyOf(openGateIds == null ? List.of() : openGateIds);
    }
}
```

Create `NodeInstanceDocument.java`:

```java
package dev.dual.ledger.file;

import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record NodeInstanceDocument(
        int schemaVersion,
        String nodeInstanceId,
        String nodeDefinitionId,
        NodeKind kind,
        String handlerType,
        NodeStatus status,
        String stageId,
        String iterationId,
        List<String> inputRefs,
        List<String> outputRefs,
        Map<String, Object> result,
        String runRef,
        Instant startedAt,
        Instant finishedAt
) {
    public NodeInstanceDocument {
        inputRefs = List.copyOf(inputRefs == null ? List.of() : inputRefs);
        outputRefs = List.copyOf(outputRefs == null ? List.of() : outputRefs);
        result = Map.copyOf(result == null ? Map.of() : result);
    }
}
```

Create `WorkflowDefinitionDocument.java`:

```java
package dev.dual.ledger.file;

import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;

import java.util.List;

public record WorkflowDefinitionDocument(
        int schemaVersion,
        String workflowId,
        int version,
        String entryNodeId,
        List<NodeDefinition> nodes,
        List<EdgeDefinition> edges
) {
    public WorkflowDefinitionDocument {
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
        edges = List.copyOf(edges == null ? List.of() : edges);
    }
}
```

Create `WorkflowInstanceDocument.java`:

```java
package dev.dual.ledger.file;

import dev.dual.core.workflow.WorkflowStatus;

import java.time.Instant;
import java.util.List;

public record WorkflowInstanceDocument(
        int schemaVersion,
        String workflowInstanceId,
        String workflowId,
        int definitionVersion,
        WorkflowStatus status,
        List<String> activeNodeInstanceIds,
        String lastEventId,
        Instant createdAt,
        Instant updatedAt
) {
    public WorkflowInstanceDocument {
        activeNodeInstanceIds = List.copyOf(activeNodeInstanceIds == null ? List.of() : activeNodeInstanceIds);
    }
}
```

Create `GateDocument.java`:

```java
package dev.dual.ledger.file;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record GateDocument(
        int schemaVersion,
        String gateId,
        String nodeInstanceId,
        String gateType,
        String status,
        List<String> allowedActions,
        Instant openedAt,
        Instant resolvedAt,
        Map<String, Object> result
) {
    public GateDocument {
        allowedActions = List.copyOf(allowedActions == null ? List.of() : allowedActions);
        result = Map.copyOf(result == null ? Map.of() : result);
    }
}
```

- [ ] **Step 5: Implement `WorkflowFileLedger.initialize`**

Create `WorkflowFileLedger.java` with this public surface:

```java
package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkflowFileLedger {
    private final AtomicJsonWriter jsonWriter;
    private final ObjectMapper objectMapper;
    private final ObjectMapper jsonlMapper;

    public WorkflowFileLedger() {
        this(new AtomicJsonWriter());
    }

    WorkflowFileLedger(AtomicJsonWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
        this.objectMapper = jsonWriter.objectMapper();
        this.jsonlMapper = jsonWriter.objectMapper().copy().disable(SerializationFeature.INDENT_OUTPUT);
    }

    public void initialize(WorkstreamPaths paths, String workflowInstanceId, WorkflowDefinition definition) throws IOException {
        Files.createDirectories(paths.workflowDir());
        Files.createDirectories(paths.nodesDir());
        Files.createDirectories(paths.gatesDir());
        var now = Instant.now();
        var entry = definition.node(definition.entryNodeId());
        var nodeInstanceId = "node-" + entry.id().replace('.', '-') + "-001";

        jsonWriter.write(paths.workflowDefinitionJson(), new WorkflowDefinitionDocument(1, definition.workflowId(), definition.version(), definition.entryNodeId(), definition.nodes(), definition.edges()));
        jsonWriter.write(paths.workflowInstanceJson(), new WorkflowInstanceDocument(1, workflowInstanceId, definition.workflowId(), definition.version(), WorkflowStatus.RUNNING, List.of(nodeInstanceId), "evt-000001", now, now));
        jsonWriter.write(paths.workflowCurrentJson(), new WorkflowCurrentDocument(1, paths.workstreamId().value(), workflowInstanceId, definition.workflowId(), definition.version(), WorkflowStatus.RUNNING, List.of(nodeInstanceId), List.of(), "evt-000001", now));
        jsonWriter.write(paths.nodeJson(nodeInstanceId), new NodeInstanceDocument(1, nodeInstanceId, entry.id(), entry.kind(), entry.handlerType(), NodeStatus.READY, stageId(entry.id()), "iter-001", List.of(), List.of(), Map.of(), null, null, null));
        appendEvent(paths, "evt-000001", "workflow_started", Map.of("workflow_id", definition.workflowId(), "entry_node", entry.id()));
    }

    public WorkflowCurrentDocument loadCurrent(WorkstreamPaths paths) throws IOException {
        return objectMapper.readValue(paths.workflowCurrentJson().toFile(), WorkflowCurrentDocument.class);
    }

    public NodeInstanceDocument loadNode(WorkstreamPaths paths, String nodeInstanceId) throws IOException {
        return objectMapper.readValue(paths.nodeJson(nodeInstanceId).toFile(), NodeInstanceDocument.class);
    }

    public void appendEvent(WorkstreamPaths paths, String eventId, String eventType, Map<String, Object> payload) throws IOException {
        var line = jsonlMapper.writeValueAsString(Map.of(
                "event_id", eventId,
                "ts", Instant.now().toString(),
                "event_type", eventType,
                "payload", payload
        ));
        Files.writeString(paths.eventsJsonl(), line + System.lineSeparator(), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    private static String stageId(String nodeDefinitionId) {
        var prefix = nodeDefinitionId.contains(".") ? nodeDefinitionId.substring(0, nodeDefinitionId.indexOf('.')) : "workflow";
        return "stage-" + prefix;
    }
}
```

- [ ] **Step 6: Implement routing persistence**

Add to `WorkflowFileLedger`:

```java
public void completeAndRoute(
        WorkstreamPaths paths,
        WorkflowDefinition definition,
        String nodeInstanceId,
        NodeStatus completedStatus,
        String outcome,
        List<String> outputRefs,
        Map<String, Object> result
) throws IOException {
    var current = loadCurrent(paths);
    var node = loadNode(paths, nodeInstanceId);
    var finished = new NodeInstanceDocument(
            node.schemaVersion(),
            node.nodeInstanceId(),
            node.nodeDefinitionId(),
            node.kind(),
            node.handlerType(),
            completedStatus,
            node.stageId(),
            node.iterationId(),
            node.inputRefs(),
            outputRefs,
            result,
            node.runRef(),
            node.startedAt(),
            Instant.now()
    );
    jsonWriter.write(paths.nodeJson(nodeInstanceId), finished);

    var edge = definition.edgesFrom(node.nodeDefinitionId()).stream()
            .filter(candidate -> candidate.on().equals(outcome))
            .findFirst();

    if (edge.isEmpty()) {
        var next = new WorkflowCurrentDocument(1, current.workstreamId(), current.workflowInstanceId(), current.workflowId(), current.definitionVersion(), WorkflowStatus.COMPLETED, List.of(), List.of(), "evt-" + UUID.randomUUID(), Instant.now());
        jsonWriter.write(paths.workflowCurrentJson(), next);
        appendEvent(paths, next.lastEventId(), "workflow_completed", Map.of("last_node", node.nodeDefinitionId(), "outcome", outcome));
        return;
    }

    var nextDefinition = definition.node(edge.get().to());
    var nextNodeInstanceId = "node-" + nextDefinition.id().replace('.', '-') + "-" + System.currentTimeMillis();
    var nextNode = new NodeInstanceDocument(1, nextNodeInstanceId, nextDefinition.id(), nextDefinition.kind(), nextDefinition.handlerType(), nextDefinition.kind() == dev.dual.core.workflow.NodeKind.GATE ? NodeStatus.WAITING : NodeStatus.READY, stageId(nextDefinition.id()), node.iterationId(), outputRefs, List.of(), Map.of(), null, null, null);
    jsonWriter.write(paths.nodeJson(nextNodeInstanceId), nextNode);

    var status = nextDefinition.kind() == dev.dual.core.workflow.NodeKind.GATE ? WorkflowStatus.WAITING_FOR_HUMAN : WorkflowStatus.RUNNING;
    var openGates = nextDefinition.kind() == dev.dual.core.workflow.NodeKind.GATE ? List.of("gate-" + nextNodeInstanceId) : List.<String>of();
    var next = new WorkflowCurrentDocument(1, current.workstreamId(), current.workflowInstanceId(), current.workflowId(), current.definitionVersion(), status, List.of(nextNodeInstanceId), openGates, "evt-" + UUID.randomUUID(), Instant.now());
    jsonWriter.write(paths.workflowCurrentJson(), next);
    appendEvent(paths, next.lastEventId(), "node_routed", Map.of("from", node.nodeDefinitionId(), "outcome", outcome, "to", nextDefinition.id()));
}
```

- [ ] **Step 7: Verify ledger tests pass**

Run:

```bash
cd dual-orchestrator
mvn -pl dual-ledger-file test -Dtest=WorkflowFileLedgerTest
```

Expected: PASS.

## Task 4: Add Runtime Handler SPI

**Files:**

- Create: `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/WorkflowCommand.java`
- Create: `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/WorkflowRuntimeResult.java`
- Create: `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/TaskHandler.java`
- Create: `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/DecisionHandler.java`
- Create: `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/GateHandler.java`
- Create: `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/HandlerRegistry.java`
- Create: `dual-orchestrator/dual-runtime/src/test/java/dev/dual/runtime/handler/HandlerRegistryTest.java`

- [ ] **Step 1: Write registry test**

Create `HandlerRegistryTest.java`:

```java
package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandlerRegistryTest {
    @Test
    void resolvesHandlersByKindAndType() {
        var registry = new HandlerRegistry(List.of(new FakeTaskHandler()), List.of(), List.of());

        assertThat(registry.task("fake.task").type()).isEqualTo("fake.task");
    }

    @Test
    void failsFastForMissingHandler() {
        var registry = new HandlerRegistry(List.of(), List.of(), List.of());

        assertThatThrownBy(() -> registry.resolve(NodeKind.TASK, "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing handler");
    }

    private static final class FakeTaskHandler implements TaskHandler {
        @Override
        public String type() {
            return "fake.task";
        }

        @Override
        public dev.dual.core.workflow.NodeResult execute(TaskContext context) {
            return dev.dual.core.workflow.NodeResult.completed("completed", List.of(), java.util.Map.of());
        }
    }
}
```

- [ ] **Step 2: Create command and result records**

Create `WorkflowCommand.java`:

```java
package dev.dual.runtime;

import java.nio.file.Path;

public record WorkflowCommand(Path workspaceRoot, String workstreamId) {
    public WorkflowCommand {
        if (workspaceRoot == null) {
            throw new IllegalArgumentException("workspace root is required");
        }
        if (workstreamId == null || workstreamId.isBlank()) {
            throw new IllegalArgumentException("workstream id is required");
        }
    }
}
```

Create `WorkflowRuntimeResult.java`:

```java
package dev.dual.runtime;

import dev.dual.core.workflow.WorkflowStatus;

public record WorkflowRuntimeResult(
        String workstreamId,
        WorkflowStatus status,
        String activeNode,
        String message
) {
}
```

- [ ] **Step 3: Create handler interfaces**

Create `TaskHandler.java`:

```java
package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeResult;

import java.nio.file.Path;

public interface TaskHandler {
    String type();
    NodeResult execute(TaskContext context) throws Exception;

    record TaskContext(Path workspaceRoot, String workstreamId, NodeDefinition definition, NodeInstance instance) {
    }
}
```

Create `DecisionHandler.java`:

```java
package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeResult;

import java.nio.file.Path;

public interface DecisionHandler {
    String type();
    NodeResult evaluate(DecisionContext context) throws Exception;

    record DecisionContext(Path workspaceRoot, String workstreamId, NodeDefinition definition, NodeInstance instance) {
    }
}
```

Create `GateHandler.java`:

```java
package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeResult;

import java.nio.file.Path;

public interface GateHandler {
    String type();
    NodeResult openOrInspect(GateContext context) throws Exception;

    record GateContext(Path workspaceRoot, String workstreamId, NodeDefinition definition, NodeInstance instance) {
    }
}
```

- [ ] **Step 4: Create registry**

Create `HandlerRegistry.java`:

```java
package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeKind;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class HandlerRegistry {
    private final Map<String, TaskHandler> taskHandlers;
    private final Map<String, DecisionHandler> decisionHandlers;
    private final Map<String, GateHandler> gateHandlers;

    public HandlerRegistry(List<TaskHandler> taskHandlers, List<DecisionHandler> decisionHandlers, List<GateHandler> gateHandlers) {
        this.taskHandlers = taskHandlers.stream().collect(Collectors.toUnmodifiableMap(TaskHandler::type, Function.identity()));
        this.decisionHandlers = decisionHandlers.stream().collect(Collectors.toUnmodifiableMap(DecisionHandler::type, Function.identity()));
        this.gateHandlers = gateHandlers.stream().collect(Collectors.toUnmodifiableMap(GateHandler::type, Function.identity()));
    }

    public Object resolve(NodeKind kind, String type) {
        return switch (kind) {
            case TASK -> task(type);
            case DECISION -> decision(type);
            case GATE -> gate(type);
        };
    }

    public TaskHandler task(String type) {
        var handler = taskHandlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("missing handler: " + type);
        }
        return handler;
    }

    public DecisionHandler decision(String type) {
        var handler = decisionHandlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("missing handler: " + type);
        }
        return handler;
    }

    public GateHandler gate(String type) {
        var handler = gateHandlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("missing handler: " + type);
        }
        return handler;
    }
}
```

- [ ] **Step 5: Verify runtime module tests pass**

Run:

```bash
cd dual-orchestrator
mvn -pl dual-runtime test -Dtest=HandlerRegistryTest
```

Expected: PASS.

## Task 5: Implement Runtime Routing

**Files:**

- Create: `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/WorkflowRuntime.java`
- Create: `dual-orchestrator/dual-runtime/src/test/java/dev/dual/runtime/WorkflowRuntimeTest.java`
- Modify: `dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkflowFileLedger.java`

- [ ] **Step 1: Write runtime routing test**

Create `WorkflowRuntimeTest.java`:

```java
package dev.dual.runtime;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.handler.DecisionHandler;
import dev.dual.runtime.handler.GateHandler;
import dev.dual.runtime.handler.HandlerRegistry;
import dev.dual.runtime.handler.TaskHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRuntimeTest {
    @TempDir
    Path tempDir;

    @Test
    void executesActiveTaskAndRoutesToHumanGate() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-runtime"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "task.one",
                List.of(
                        new NodeDefinition("task.one", NodeKind.TASK, "fake.task", Map.of()),
                        new NodeDefinition("gate.review", NodeKind.GATE, "human.review", Map.of())
                ),
                List.of(new EdgeDefinition("task.one", "completed", "gate.review"))
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-runtime", definition);

        var runtime = new WorkflowRuntime(ledger, new HandlerRegistry(List.of(new FakeTask()), List.of(), List.of(new FakeGate())));
        var result = runtime.resume(new WorkflowCommand(tempDir, "ws-runtime"));

        assertThat(result.status()).isEqualTo(WorkflowStatus.WAITING_FOR_HUMAN);
        assertThat(result.activeNode()).contains("gate.review");
    }

    private static final class FakeTask implements TaskHandler {
        @Override
        public String type() {
            return "fake.task";
        }

        @Override
        public NodeResult execute(TaskContext context) {
            return NodeResult.completed("completed", List.of("artifact://fake"), Map.of("ok", true));
        }
    }

    private static final class FakeGate implements GateHandler {
        @Override
        public String type() {
            return "human.review";
        }

        @Override
        public NodeResult openOrInspect(GateContext context) {
            return NodeResult.waiting("open", Map.of("allowed_actions", List.of("approve", "revise")));
        }
    }
}
```

- [ ] **Step 2: Add ledger method to load workflow definition**

In `WorkflowFileLedger.java`, add:

```java
public WorkflowDefinition loadDefinition(WorkstreamPaths paths) throws IOException {
    var document = objectMapper.readValue(paths.workflowDefinitionJson().toFile(), WorkflowDefinitionDocument.class);
    return new WorkflowDefinition(
            document.workflowId(),
            document.version(),
            document.entryNodeId(),
            document.nodes(),
            document.edges()
    );
}
```

- [ ] **Step 3: Add conversion from document to core NodeInstance**

In `WorkflowFileLedger.java`, add:

```java
public dev.dual.core.workflow.NodeInstance loadCoreNode(WorkstreamPaths paths, String nodeInstanceId) throws IOException {
    var node = loadNode(paths, nodeInstanceId);
    return new dev.dual.core.workflow.NodeInstance(
            node.nodeInstanceId(),
            node.nodeDefinitionId(),
            node.kind(),
            node.handlerType(),
            node.status(),
            node.stageId(),
            node.iterationId(),
            node.inputRefs(),
            node.outputRefs(),
            node.result(),
            node.runRef(),
            node.startedAt(),
            node.finishedAt()
    );
}
```

- [ ] **Step 4: Implement `WorkflowRuntime`**

Create `WorkflowRuntime.java`:

```java
package dev.dual.runtime;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.handler.DecisionHandler;
import dev.dual.runtime.handler.GateHandler;
import dev.dual.runtime.handler.HandlerRegistry;
import dev.dual.runtime.handler.TaskHandler;

public final class WorkflowRuntime {
    private final WorkflowFileLedger ledger;
    private final HandlerRegistry handlers;

    public WorkflowRuntime(WorkflowFileLedger ledger, HandlerRegistry handlers) {
        this.ledger = ledger;
        this.handlers = handlers;
    }

    public WorkflowRuntimeResult resume(WorkflowCommand command) throws Exception {
        var paths = new WorkstreamPaths(command.workspaceRoot(), new DualId(command.workstreamId()));
        var definition = ledger.loadDefinition(paths);
        var current = ledger.loadCurrent(paths);
        if (current.activeNodeInstanceIds().isEmpty()) {
            return new WorkflowRuntimeResult(command.workstreamId(), current.status(), "", "workflow has no active node");
        }

        var node = ledger.loadCoreNode(paths, current.activeNodeInstanceIds().getFirst());
        var nodeDefinition = definition.node(node.nodeDefinitionId());
        var result = switch (nodeDefinition.kind()) {
            case TASK -> handlers.task(nodeDefinition.handlerType()).execute(new TaskHandler.TaskContext(command.workspaceRoot(), command.workstreamId(), nodeDefinition, node));
            case DECISION -> handlers.decision(nodeDefinition.handlerType()).evaluate(new DecisionHandler.DecisionContext(command.workspaceRoot(), command.workstreamId(), nodeDefinition, node));
            case GATE -> handlers.gate(nodeDefinition.handlerType()).openOrInspect(new GateHandler.GateContext(command.workspaceRoot(), command.workstreamId(), nodeDefinition, node));
        };

        if (nodeDefinition.kind() == NodeKind.GATE && result.status() == dev.dual.core.workflow.NodeStatus.WAITING) {
            return new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.WAITING_FOR_HUMAN, nodeDefinition.id(), "waiting for human review");
        }

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), result.status(), result.outcome(), result.outputRefs(), result.result());
        var next = ledger.loadCurrent(paths);
        var active = next.activeNodeInstanceIds().isEmpty() ? "" : ledger.loadNode(paths, next.activeNodeInstanceIds().getFirst()).nodeDefinitionId();
        return new WorkflowRuntimeResult(command.workstreamId(), next.status(), active, "resumed workflow");
    }
}
```

- [ ] **Step 5: Verify runtime routing**

Run:

```bash
cd dual-orchestrator
mvn -pl dual-runtime test -Dtest=WorkflowRuntimeTest
```

Expected: PASS.

## Task 6: Add Thin Lifecycle Workflow Definition

**Files:**

- Create: `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/lifecycle/LifecycleWorkflowDefinitionFactory.java`
- Create: `dual-orchestrator/dual-runtime/src/test/java/dev/dual/runtime/lifecycle/LifecycleWorkflowDefinitionFactoryTest.java`

- [ ] **Step 1: Write lifecycle definition test**

Create `LifecycleWorkflowDefinitionFactoryTest.java`:

```java
package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.NodeKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleWorkflowDefinitionFactoryTest {
    @Test
    void includesThinMainlineAcrossLifecycleStages() {
        var definition = new LifecycleWorkflowDefinitionFactory().thinMainline();

        assertThat(definition.workflowId()).isEqualTo("agent-lifecycle");
        assertThat(definition.node("intake.claude_brainstorm").kind()).isEqualTo(NodeKind.TASK);
        assertThat(definition.node("design.codex_arch_review").kind()).isEqualTo(NodeKind.TASK);
        assertThat(definition.node("plan.claude_plan").kind()).isEqualTo(NodeKind.TASK);
        assertThat(definition.node("implement.claude_implement").kind()).isEqualTo(NodeKind.TASK);
        assertThat(definition.node("acceptance.codex_verifier_review").kind()).isEqualTo(NodeKind.TASK);
        assertThat(definition.node("stage.generate_next_prompt").kind()).isEqualTo(NodeKind.TASK);
        assertThat(definition.node("gate.human_design_approval").kind()).isEqualTo(NodeKind.GATE);
        assertThat(definition.edgesFrom("intake.claude_brainstorm")).extracting("to").contains("intake.codex_product_review");
    }
}
```

- [ ] **Step 2: Implement factory**

Create `LifecycleWorkflowDefinitionFactory.java`:

```java
package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.WorkflowDefinition;

import java.util.List;
import java.util.Map;

public final class LifecycleWorkflowDefinitionFactory {
    public WorkflowDefinition thinMainline() {
        return new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "intake.claude_brainstorm",
                List.of(
                        task("intake.claude_brainstorm", "acpx.agent_run", "claude", "claude-main", "intake-claude-brainstorm"),
                        task("intake.codex_product_review", "acpx.agent_run", "codex", "codex-reviewer", "intake-codex-product-review"),
                        decision("intake.decision", "review.decision"),
                        gate("gate.human_requirement_review"),
                        task("design.claude_design", "acpx.agent_run", "claude", "claude-main", "design-claude"),
                        task("design.codex_arch_review", "acpx.agent_run", "codex", "codex-reviewer", "design-codex-review"),
                        decision("design.decision", "review.decision"),
                        gate("gate.human_design_approval"),
                        task("plan.claude_plan", "acpx.agent_run", "claude", "claude-main", "plan-claude"),
                        task("plan.codex_plan_review", "acpx.agent_run", "codex", "codex-reviewer", "plan-codex-review"),
                        decision("plan.decision", "review.decision"),
                        gate("gate.human_plan_approval"),
                        task("implement.claude_implement", "acpx.agent_run", "claude", "claude-main", "implement-claude"),
                        task("implement.claude_self_test_report", "acpx.agent_run", "claude", "claude-main", "implement-claude-self-test"),
                        task("implement.artifact_validation", "artifact.validate"),
                        task("acceptance.codex_verifier_review", "acpx.agent_run", "codex", "codex-reviewer", "acceptance-codex-review"),
                        decision("acceptance.decision", "review.decision"),
                        gate("gate.human_acceptance"),
                        task("stage.claude_handoff", "acpx.agent_run", "claude", "claude-main", "stage-claude-handoff"),
                        task("stage.generate_next_prompt", "prompt.generate_next_stage"),
                        gate("gate.human_next_stage_approval")
                ),
                List.of(
                        edge("intake.claude_brainstorm", "completed", "intake.codex_product_review"),
                        edge("intake.codex_product_review", "completed", "intake.decision"),
                        edge("intake.decision", "PASS", "gate.human_requirement_review"),
                        edge("gate.human_requirement_review", "approved", "design.claude_design"),
                        edge("design.claude_design", "completed", "design.codex_arch_review"),
                        edge("design.codex_arch_review", "completed", "design.decision"),
                        edge("design.decision", "PASS", "gate.human_design_approval"),
                        edge("gate.human_design_approval", "approved", "plan.claude_plan"),
                        edge("plan.claude_plan", "completed", "plan.codex_plan_review"),
                        edge("plan.codex_plan_review", "completed", "plan.decision"),
                        edge("plan.decision", "READY_TO_IMPLEMENT", "gate.human_plan_approval"),
                        edge("gate.human_plan_approval", "approved", "implement.claude_implement"),
                        edge("implement.claude_implement", "completed", "implement.claude_self_test_report"),
                        edge("implement.claude_self_test_report", "completed", "implement.artifact_validation"),
                        edge("implement.artifact_validation", "completed", "acceptance.codex_verifier_review"),
                        edge("acceptance.codex_verifier_review", "completed", "acceptance.decision"),
                        edge("acceptance.decision", "ACCEPT", "gate.human_acceptance"),
                        edge("gate.human_acceptance", "approved", "stage.claude_handoff"),
                        edge("stage.claude_handoff", "completed", "stage.generate_next_prompt"),
                        edge("stage.generate_next_prompt", "completed", "gate.human_next_stage_approval")
                )
        );
    }

    private static NodeDefinition task(String id, String handlerType) {
        return new NodeDefinition(id, NodeKind.TASK, handlerType, Map.of());
    }

    private static NodeDefinition task(String id, String handlerType, String agent, String session, String promptTemplate) {
        return new NodeDefinition(id, NodeKind.TASK, handlerType, Map.of("agent", agent, "session", session, "prompt_template", promptTemplate));
    }

    private static NodeDefinition decision(String id, String handlerType) {
        return new NodeDefinition(id, NodeKind.DECISION, handlerType, Map.of());
    }

    private static NodeDefinition gate(String id) {
        return new NodeDefinition(id, NodeKind.GATE, "human.review", Map.of("allowed_actions", List.of("approve", "revise")));
    }

    private static EdgeDefinition edge(String from, String on, String to) {
        return new EdgeDefinition(from, on, to);
    }
}
```

- [ ] **Step 3: Verify lifecycle definition**

Run:

```bash
cd dual-orchestrator
mvn -pl dual-runtime test -Dtest=LifecycleWorkflowDefinitionFactoryTest
```

Expected: PASS.

## Task 7: Add Minimal Built-In Handlers

**Files:**

- Create: `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/HumanReviewGateHandler.java`
- Create: `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/ReviewDecisionHandler.java`
- Create: `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/ArtifactValidateHandler.java`
- Create: `dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/PromptGenerateNextStageHandler.java`
- Create: `dual-orchestrator/dual-runtime/src/test/java/dev/dual/runtime/handler/BuiltinHandlerTest.java`

- [ ] **Step 1: Write built-in handler tests**

Create `BuiltinHandlerTest.java` with tests:

```java
package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltinHandlerTest {
    @TempDir
    Path tempDir;

    @Test
    void humanGateReturnsWaitingResult() throws Exception {
        var handler = new HumanReviewGateHandler();
        var result = handler.openOrInspect(new GateHandler.GateContext(tempDir, "ws", gateDefinition(), node("gate.human_design_approval")));

        assertThat(result.status()).isEqualTo(NodeStatus.WAITING);
        assertThat(result.outcome()).isEqualTo("open");
    }

    @Test
    void artifactValidationCompletesForMilestoneOne() throws Exception {
        var handler = new ArtifactValidateHandler();
        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws", taskDefinition("implement.artifact_validation", "artifact.validate"), node("implement.artifact_validation")));

        assertThat(result.status()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(result.outcome()).isEqualTo("completed");
    }

    private static NodeDefinition gateDefinition() {
        return new NodeDefinition("gate.human_design_approval", NodeKind.GATE, "human.review", Map.of("allowed_actions", List.of("approve", "revise")));
    }

    private static NodeDefinition taskDefinition(String id, String handler) {
        return new NodeDefinition(id, NodeKind.TASK, handler, Map.of());
    }

    private static NodeInstance node(String id) {
        return new NodeInstance("node-" + id, id, NodeKind.GATE, "human.review", NodeStatus.READY, "stage-test", "iter-001", List.of(), List.of(), Map.of(), null, null, null);
    }
}
```

- [ ] **Step 2: Implement human gate handler**

Create `HumanReviewGateHandler.java`:

```java
package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeResult;

import java.util.List;
import java.util.Map;

public final class HumanReviewGateHandler implements GateHandler {
    @Override
    public String type() {
        return "human.review";
    }

    @Override
    public NodeResult openOrInspect(GateContext context) {
        return NodeResult.waiting("open", Map.of("allowed_actions", context.definition().config().getOrDefault("allowed_actions", List.of("approve", "revise"))));
    }
}
```

- [ ] **Step 3: Implement simple decision handler**

Create `ReviewDecisionHandler.java`:

```java
package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeResult;

import java.util.List;
import java.util.Map;

public final class ReviewDecisionHandler implements DecisionHandler {
    @Override
    public String type() {
        return "review.decision";
    }

    @Override
    public NodeResult evaluate(DecisionContext context) {
        var defaultOutcome = switch (context.definition().id()) {
            case "plan.decision" -> "READY_TO_IMPLEMENT";
            case "acceptance.decision" -> "ACCEPT";
            default -> "PASS";
        };
        return NodeResult.completed(defaultOutcome, List.of(), Map.of("decision", defaultOutcome, "source", "milestone-1-default"));
    }
}
```

- [ ] **Step 4: Implement deterministic task handlers for internal tasks**

Create `ArtifactValidateHandler.java`:

```java
package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeResult;

import java.util.List;
import java.util.Map;

public final class ArtifactValidateHandler implements TaskHandler {
    @Override
    public String type() {
        return "artifact.validate";
    }

    @Override
    public NodeResult execute(TaskContext context) {
        return NodeResult.completed("completed", List.of(), Map.of("validation", "passed", "mode", "milestone-1"));
    }
}
```

Create `PromptGenerateNextStageHandler.java`:

```java
package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeResult;

import java.util.List;
import java.util.Map;

public final class PromptGenerateNextStageHandler implements TaskHandler {
    @Override
    public String type() {
        return "prompt.generate_next_stage";
    }

    @Override
    public NodeResult execute(TaskContext context) {
        return NodeResult.completed("completed", List.of("artifact://next-stage-prompt"), Map.of("generated", true));
    }
}
```

- [ ] **Step 5: Verify handler tests**

Run:

```bash
cd dual-orchestrator
mvn -pl dual-runtime test -Dtest=BuiltinHandlerTest
```

Expected: PASS.

## Task 8: Add acpx Agent Task Handler

**Files:**

- Modify: `dual-orchestrator/dual-adapter-acpx/pom.xml`
- Create: `dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxAgentTaskHandler.java`
- Create: `dual-orchestrator/dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/AcpxAgentTaskHandlerTest.java`

- [ ] **Step 1: Add runtime dependency to acpx adapter**

In `dual-adapter-acpx/pom.xml`, add:

```xml
<dependency>
    <groupId>dev.dual</groupId>
    <artifactId>dual-runtime</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **Step 2: Write prompt file creation test**

Create `AcpxAgentTaskHandlerTest.java`:

```java
package dev.dual.adapter.acpx;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.runtime.handler.TaskHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AcpxAgentTaskHandlerTest {
    @TempDir
    Path tempDir;

    @Test
    void writesPromptAndReturnsPreparedWhenConfiguredForPrepareOnly() throws Exception {
        var handler = new AcpxAgentTaskHandler(new AcpxAgentTransport(), true);
        var definition = new NodeDefinition("design.claude_design", NodeKind.TASK, "acpx.agent_run", Map.of("agent", "claude", "session", "claude-main", "prompt_template", "design-claude"));
        var instance = new NodeInstance("node-design", "design.claude_design", NodeKind.TASK, "acpx.agent_run", NodeStatus.READY, "stage-design", "iter-001", List.of(), List.of(), Map.of(), null, null, null);

        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-test", definition, instance));

        assertThat(result.outcome()).isEqualTo("completed");
        assertThat(Files.walk(tempDir.resolve(".agent")).anyMatch(path -> path.getFileName().toString().endsWith(".md"))).isTrue();
    }
}
```

- [ ] **Step 3: Implement acpx task handler**

Create `AcpxAgentTaskHandler.java`:

```java
package dev.dual.adapter.acpx;

import dev.dual.core.workflow.NodeResult;
import dev.dual.runtime.handler.TaskHandler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AcpxAgentTaskHandler implements TaskHandler {
    private final AcpxAgentTransport transport;
    private final boolean prepareOnly;

    public AcpxAgentTaskHandler(AcpxAgentTransport transport, boolean prepareOnly) {
        this.transport = transport;
        this.prepareOnly = prepareOnly;
    }

    @Override
    public String type() {
        return "acpx.agent_run";
    }

    @Override
    public NodeResult execute(TaskContext context) throws Exception {
        var agent = String.valueOf(context.definition().config().get("agent"));
        var session = String.valueOf(context.definition().config().get("session"));
        var promptTemplate = String.valueOf(context.definition().config().get("prompt_template"));
        var runId = "run-" + Instant.now().toEpochMilli();
        var runDir = context.workspaceRoot().resolve(".agent").resolve("workstreams").resolve(context.workstreamId()).resolve("runs").resolve(runId);
        var promptPath = runDir.resolve("prompts").resolve(promptTemplate + ".md");
        var stdoutPath = runDir.resolve("outputs").resolve(promptTemplate + ".md");
        var stderrPath = runDir.resolve("outputs").resolve(promptTemplate + ".stderr.log");
        Files.createDirectories(promptPath.getParent());
        Files.createDirectories(stdoutPath.getParent());
        Files.writeString(promptPath, promptFor(context.workstreamId(), context.definition().id(), promptTemplate), StandardCharsets.UTF_8);

        if (prepareOnly) {
            return NodeResult.completed("completed", List.of(context.workspaceRoot().relativize(promptPath).toString()), Map.of("run_id", runId, "agent", agent, "session", session, "prepared", true));
        }

        var request = new AcpxRunRequest(context.workspaceRoot(), session, promptPath, stdoutPath, stderrPath);
        var result = "claude".equals(agent) ? transport.runClaudeDesign(request) : transport.runCodexDesignReview(request);
        if (result.exitCode() != 0) {
            return new NodeResult("failed", dev.dual.core.workflow.NodeStatus.FAILED, List.of(context.workspaceRoot().relativize(stderrPath).toString()), Map.of("run_id", runId, "exit_code", result.exitCode()), "acpx exited with " + result.exitCode());
        }
        return NodeResult.completed("completed", List.of(context.workspaceRoot().relativize(stdoutPath).toString()), Map.of("run_id", runId, "agent", agent, "session", session, "exit_code", result.exitCode()));
    }

    private static String promptFor(String workstreamId, String nodeId, String promptTemplate) {
        return """
                # Agent Task

                Workstream: %s
                Node: %s
                Prompt template: %s

                Read the repository and the `.agent/workstreams/%s` ledger. Produce the artifact requested by this lifecycle stage. Write concise Markdown output and include a machine-readable control block when the prompt asks for one.
                """.formatted(workstreamId, nodeId, promptTemplate, workstreamId);
    }
}
```

- [ ] **Step 4: Verify acpx adapter tests**

Run:

```bash
cd dual-orchestrator
mvn -pl dual-adapter-acpx -am test -Dtest=AcpxAgentTaskHandlerTest
```

Expected: PASS.

## Task 9: Wire Runtime Into CLI

**Files:**

- Modify: `dual-orchestrator/dual-cli/pom.xml`
- Modify: `dual-orchestrator/dual-cli/src/main/java/dev/dual/cli/StartCommand.java`
- Modify: `dual-orchestrator/dual-cli/src/main/java/dev/dual/cli/ResumeCommand.java`
- Modify: `dual-orchestrator/dual-cli/src/main/java/dev/dual/cli/StatusCommand.java`

- [ ] **Step 1: Add CLI dependencies**

In `dual-cli/pom.xml`, add dependencies:

```xml
<dependency>
    <groupId>dev.dual</groupId>
    <artifactId>dual-runtime</artifactId>
    <version>${project.version}</version>
</dependency>
<dependency>
    <groupId>dev.dual</groupId>
    <artifactId>dual-adapter-acpx</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **Step 2: Bootstrap workflow in StartCommand**

In `StartCommand.call()`, after `initializeWorkstream(...)`, add:

```java
var workflowDefinition = new dev.dual.runtime.lifecycle.LifecycleWorkflowDefinitionFactory().thinMainline();
new dev.dual.ledger.file.WorkflowFileLedger().initialize(paths, "wf-" + workstreamId.value(), workflowDefinition);
```

Update console output to include:

```java
System.out.println("Workflow: agent-lifecycle@1");
System.out.println("Next: dualctl resume --workdir " + selectedWorktree + " " + workstreamId.value());
```

- [ ] **Step 3: Delegate ResumeCommand to WorkflowRuntime**

Replace the hardcoded `pendingAction` branch chain in `ResumeCommand.call()` with:

```java
var workspaceRoot = workdir.toAbsolutePath().normalize();
var paths = new WorkstreamPaths(workspaceRoot, new dev.dual.core.id.DualId(workstreamId));
try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "resume"))) {
    var ledger = new dev.dual.ledger.file.WorkflowFileLedger();
    var registry = new dev.dual.runtime.handler.HandlerRegistry(
            java.util.List.of(
                    new dev.dual.adapter.acpx.AcpxAgentTaskHandler(new AcpxAgentTransport(), false),
                    new dev.dual.runtime.handler.ArtifactValidateHandler(),
                    new dev.dual.runtime.handler.PromptGenerateNextStageHandler()
            ),
            java.util.List.of(new dev.dual.runtime.handler.ReviewDecisionHandler()),
            java.util.List.of(new dev.dual.runtime.handler.HumanReviewGateHandler())
    );
    var result = new dev.dual.runtime.WorkflowRuntime(ledger, registry)
            .resume(new dev.dual.runtime.WorkflowCommand(workspaceRoot, workstreamId));
    System.out.println("Workstream: " + result.workstreamId());
    System.out.println("Workflow status: " + result.status());
    System.out.println("Active node: " + result.activeNode());
    System.out.println(result.message());
    return result.status() == dev.dual.core.workflow.WorkflowStatus.FAILED ? 1 : 0;
}
```

- [ ] **Step 4: Show workflow current state in StatusCommand**

After existing status output, add:

```java
var paths = new dev.dual.ledger.file.WorkstreamPaths(workdir.toAbsolutePath().normalize(), new dev.dual.core.id.DualId(workstreamId));
var workflowLedger = new dev.dual.ledger.file.WorkflowFileLedger();
if (java.nio.file.Files.exists(paths.workflowCurrentJson())) {
    var current = workflowLedger.loadCurrent(paths);
    System.out.println("Workflow: " + current.workflowId() + "@" + current.definitionVersion());
    System.out.println("Workflow status: " + current.status());
    System.out.println("Active nodes: " + current.activeNodeInstanceIds());
    System.out.println("Open gates: " + current.openGateIds());
}
```

- [ ] **Step 5: Verify CLI package**

Run:

```bash
cd dual-orchestrator
mvn -pl dual-cli -am package
```

Expected: PASS.

## Task 10: Implement Human Gate Resolution

**Files:**

- Modify: `dual-orchestrator/dual-ledger-file/src/main/java/dev/dual/ledger/file/WorkflowFileLedger.java`
- Modify: `dual-orchestrator/dual-cli/src/main/java/dev/dual/cli/ReviewApproveCommand.java`
- Modify: `dual-orchestrator/dual-cli/src/main/java/dev/dual/cli/ReviewReviseCommand.java`
- Create: `dual-orchestrator/dual-ledger-file/src/test/java/dev/dual/ledger/file/WorkflowGateResolutionTest.java`

- [ ] **Step 1: Write gate approval routing test**

Create `WorkflowGateResolutionTest.java`:

```java
package dev.dual.ledger.file;

import dev.dual.core.id.DualId;
import dev.dual.runtime.lifecycle.LifecycleWorkflowDefinitionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowGateResolutionTest {
    @TempDir
    Path tempDir;

    @Test
    void approvingRequirementGateRoutesToDesignNode() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-gate"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new LifecycleWorkflowDefinitionFactory().thinMainline();
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-gate", definition);

        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst());
        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), dev.dual.core.workflow.NodeStatus.COMPLETED, "completed", java.util.List.of(), java.util.Map.of());
        ledger.completeAndRoute(paths, definition, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst(), dev.dual.core.workflow.NodeStatus.COMPLETED, "completed", java.util.List.of(), java.util.Map.of());
        ledger.completeAndRoute(paths, definition, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst(), dev.dual.core.workflow.NodeStatus.COMPLETED, "PASS", java.util.List.of(), java.util.Map.of());

        ledger.resolveOpenGate(paths, definition, "approved", "approved by test");

        var next = ledger.loadCurrent(paths);
        assertThat(ledger.loadNode(paths, next.activeNodeInstanceIds().getFirst()).nodeDefinitionId()).isEqualTo("design.claude_design");
    }
}
```

- [ ] **Step 2: Implement `resolveOpenGate`**

Add to `WorkflowFileLedger.java`:

```java
public void resolveOpenGate(WorkstreamPaths paths, WorkflowDefinition definition, String action, String note) throws IOException {
    var current = loadCurrent(paths);
    if (current.activeNodeInstanceIds().isEmpty()) {
        throw new IOException("no active gate node");
    }
    var node = loadNode(paths, current.activeNodeInstanceIds().getFirst());
    if (node.kind() != dev.dual.core.workflow.NodeKind.GATE) {
        throw new IOException("active node is not a gate: " + node.nodeDefinitionId());
    }
    completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.COMPLETED, action, List.of(), Map.of("human_action", action, "note", note == null ? "" : note));
}
```

- [ ] **Step 3: Update approve command**

In `ReviewApproveCommand.call()`, replace `approveDesign(...)` with:

```java
var ledger = new dev.dual.ledger.file.WorkflowFileLedger();
var definition = ledger.loadDefinition(paths);
ledger.resolveOpenGate(paths, definition, "approved", "approved from CLI");
```

- [ ] **Step 4: Update revise command**

In `ReviewReviseCommand.call()`, replace `requestDesignRevision(...)` with:

```java
var note = java.nio.file.Files.readString(feedbackFile.toAbsolutePath().normalize());
var ledger = new dev.dual.ledger.file.WorkflowFileLedger();
var definition = ledger.loadDefinition(paths);
ledger.resolveOpenGate(paths, definition, "revise", note);
```

- [ ] **Step 5: Verify gate tests and CLI package**

Run:

```bash
cd dual-orchestrator
mvn -pl dual-ledger-file test -Dtest=WorkflowGateResolutionTest
mvn -pl dual-cli -am package
```

Expected: both PASS.

## Task 11: Update README And Smoke Tests

**Files:**

- Modify: `dual-orchestrator/README.md`

- [ ] **Step 1: Add workflow engine overview**

Add this section to `dual-orchestrator/README.md`:

```markdown
## Workflow Engine Model

`dualctl` runs an Agent lifecycle workflow, not a single command script.

The runtime has three node kinds:

- `Task`: performs work, such as calling Claude through acpx.
- `Decision`: reads artifacts and chooses the next edge.
- `Gate`: pauses for a human or external condition.

The first lifecycle workflow covers:

1. Intake / clarification
2. Design review
3. Planning
4. Implementation and Claude self-test report
5. Codex acceptance review
6. Stage handoff and next-stage prompt
```

- [ ] **Step 2: Document ledger paths**

Add:

```markdown
## Ledger

Each workstream writes a file ledger under:

```text
.agent/workstreams/<workstream_id>/
  workflow/
    definition.json
    instance.json
    current.json
  nodes/
  gates/
  runs/
  events.jsonl
```

`events.jsonl` is the audit trail. `workflow/current.json` is a snapshot for fast `status` and `resume`.
```

- [ ] **Step 3: Document commands**

Add:

```markdown
## Commands

Start:

```bash
dualctl start "需求描述"
```

Resume:

```bash
dualctl resume --workdir <effective-worktree> <workstream_id>
```

Status:

```bash
dualctl status --workdir <effective-worktree> <workstream_id>
```

Approve a human gate:

```bash
dualctl review approve --workdir <effective-worktree> <workstream_id>
```

Request revision:

```bash
dualctl review revise --workdir <effective-worktree> --feedback-file feedback.md <workstream_id>
```
```

- [ ] **Step 4: Add real acpx smoke**

Add:

```markdown
## Real acpx Smoke

Create or verify sessions:

```bash
acpx claude sessions new --name claude-main
acpx codex sessions new --name codex-reviewer
```

Run a small workstream:

```bash
dualctl start "给 README 增加一段项目说明"
dualctl status --workdir <effective-worktree> <workstream_id>
dualctl resume --workdir <effective-worktree> <workstream_id>
```

Expected:

- `.agent/workstreams/<workstream_id>/workflow/current.json` exists.
- `events.jsonl` contains `workflow_started`.
- `nodes/*.json` shows the active node.
- acpx stdout/stderr are under `runs/<run_id>/outputs/`.
```

- [ ] **Step 5: Run docs-neutral verification**

Run:

```bash
cd dual-orchestrator
mvn test
```

Expected: PASS.

## Task 12: Final Regression

**Files:**

- All files changed by previous tasks.

- [ ] **Step 1: Run full Maven test suite**

Run:

```bash
cd dual-orchestrator
mvn test
```

Expected: PASS.

- [ ] **Step 2: Run package build**

Run:

```bash
cd dual-orchestrator
mvn -pl dual-cli -am package
```

Expected: PASS.

- [ ] **Step 3: Inspect generated module layout for IDEA**

Run:

```bash
cd dual-orchestrator
find . -name '*.iml' -o -name '.idea'
```

Expected: no output.

- [ ] **Step 4: Run a real acpx smoke in a disposable repo**

Run from a small git repository with valid acpx sessions:

```bash
dualctl start "给 README 增加一段项目说明"
dualctl resume --workdir <effective-worktree> <workstream_id>
dualctl status --workdir <effective-worktree> <workstream_id>
```

Expected:

- Worktree is created.
- Workflow current state exists.
- Runtime advances at least one active node.
- Agent prompt and outputs are written under `.agent/workstreams/<workstream_id>/runs/`.
- Failure writes ledger state and does not erase stdout/stderr.

- [ ] **Step 5: Commit**

Commit only the Java orchestrator and docs changes for this milestone:

```bash
git add dual-orchestrator docs/superpowers/specs/2026-05-23-agent-workflow-engine-architecture.md docs/superpowers/plans/2026-05-23-agent-workflow-engine-milestone-1.md
git commit -m "feat: add workflow engine runtime milestone plan"
```

Expected: commit succeeds after all verification passes.
