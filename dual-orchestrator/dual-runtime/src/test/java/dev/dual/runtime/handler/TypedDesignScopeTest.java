package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.workflow.*;
import dev.dual.runtime.WorkflowCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Proposals are governed by GOAL; exact files become authority only after independent plan review and EXECUTION approval. */
class TypedDesignScopeTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path root;

    @Test void newPathsAcrossBusinessesReachIndependentReviewerWithoutExecutionAuthority() throws Exception {
        int i = 0;
        for (var path : List.of("converter/render.py", "renamed/convert.py", "site/home.html", "renamed/index.html", "docs/转换 说明.md")) {
            var f = fixture("business-" + i++, path);
            f.advanceToGate();
            assertEquals("gate.human_design_approval", f.currentNode(), f.currentDocument());
            assertEquals(List.of("design.claude_design", "design.codex_arch_review"), f.calls);
            assertFalse(Files.exists(f.root.resolve(path)), "a proposal is not a write");
            assertTrue(TaskContractProjectionVerifier.resolve(f.ledger, f.root, f.ws, TaskContract.Phase.EXECUTION).qualified().isEmpty());
            var review = f.inputs.get("design.codex_arch_review");
            assertTrue(review.render(f.root).contains(path));
            assertEquals("GOAL", JSON.readTree(f.root.resolve(review.contract().contract().ref()).toFile()).path("phase").asText());
        }
    }

    @Test void excludedAndControlPathsNeverPublishOrStartReviewer() throws Exception {
        int i = 0;
        for (var path : List.of("secrets.txt", ".git/config", ".agent/private")) {
            var f = fixture("excluded-" + i++, path);
            f.advanceToGate();
            rejected(f, path.equals("secrets.txt") ? "PROPOSED_SCOPE_EXCLUDED" : "DESIGN_SCOPE_PATH_INVALID");
        }
    }

    @Test void nonCanonicalPathsNeverPublishEvenWhenProseClaimsCompliance() throws Exception {
        int i = 0;
        for (var path : List.of("../escape", "/absolute", "./src/a.ext", "src//a.ext", " src/a.ext")) {
            var f = fixture("malformed-" + i++, path);
            f.advanceToGate(); rejected(f, "DESIGN_SCOPE_PATH_INVALID");
        }
    }

    @Test void duplicateDeclarationsAreRejectedButEmptyDesignRemainsLegal() throws Exception {
        var duplicate = fixture("duplicate", "src/file.ext");
        duplicate.outputMutation = result -> {
            if (!result.has("design")) return;
            var files = (com.fasterxml.jackson.databind.node.ArrayNode) result.at("/design/planned_files");
            files.add(files.get(0).deepCopy());
        };
        duplicate.advanceToGate(); rejected(duplicate, "DESIGN_SCOPE_PATH_DUPLICATE");
        var empty = fixture("empty", "src/file.ext");
        empty.outputMutation = result -> {
            if (result.has("design")) ((ObjectNode) result.get("design")).putArray("planned_files");
        };
        empty.advanceToGate();
        assertEquals("gate.human_design_approval", empty.currentNode(), empty.currentDocument());
        assertTrue(empty.calls.contains("design.codex_arch_review"));
    }

    @Test void proposedOperationIsNotMistakenForAnAlreadyGrantedExecutionOperation() throws Exception {
        var f = fixture("operation", "src/existing.ext");
        f.outputMutation = result -> {
            if (result.has("design")) ((ObjectNode) result.at("/design/planned_files/0")).put("operation", "MODIFY");
        };
        f.advanceToGate();
        assertEquals("gate.human_design_approval", f.currentNode(), f.currentDocument());
        assertTrue(f.calls.contains("design.codex_arch_review"));
        assertTrue(TaskContractProjectionVerifier.resolve(f.ledger, f.root, f.ws, TaskContract.Phase.EXECUTION).qualified().isEmpty());
    }

    @Test void changedOrMissingApprovedGoalStopsBeforeAuthorAndReviewer() throws Exception {
        for (var missing : List.of(false, true)) {
            var f = fixture("goal-" + missing, "src/file.ext");
            var qualified = TaskContractProjectionVerifier.resolve(f.ledger, f.root, f.ws, TaskContract.Phase.GOAL).qualified().orElseThrow();
            var path = f.root.resolve(qualified.contractRef());
            if (missing) Files.delete(path); else Files.writeString(path, Files.readString(path) + " ");
            f.advanceToGate();
            assertTrue(f.calls.isEmpty(), "no provider receives unqualified inputs");
            assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status());
        }
    }

    @Test void tamperingPublishedDesignStopsContextAndReviewer() throws Exception {
        var f = fixture("subject", "src/file.ext");
        f.runtime.resume(new WorkflowCommand(f.root, f.ws));
        assertEquals("ctx.design", f.currentNode());
        var author = f.inputs.get("design.claude_design");
        var output = f.runDir(author.runId()).resolve("outputs/result.contract.json");
        assertTrue(Files.isRegularFile(output));
        Files.writeString(output, Files.readString(output) + " ");
        var before = f.ledger.loadCurrent(f.paths).activeNodeInstanceIds();
        var error = assertThrows(IllegalStateException.class, f::advanceToGate);
        assertTrue(error.getMessage().contains("lineage is invalid"), error.toString());
        assertFalse(f.calls.contains("design.codex_arch_review"));
        assertEquals(before, f.ledger.loadCurrent(f.paths).activeNodeInstanceIds());
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status());
    }

    @Test void undeclaredDesignFieldsCannotBePublished() throws Exception {
        var f = fixture("shape", "src/file.ext");
        f.outputMutation = result -> { if (result.has("design")) result.putArray("undeclared_responses"); };
        f.advanceToGate(); rejected(f, "undeclared_responses");
    }

    private AuthoredStageSequenceTest.Fixture fixture(String name, String path) throws Exception {
        return new AuthoredStageSequenceTest.Fixture(root.resolve(name), path);
    }
    private void rejected(AuthoredStageSequenceTest.Fixture f, String code) throws Exception {
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status(), f.currentDocument());
        assertEquals(List.of("design.claude_design"), f.calls, "invalid design cannot dispatch its reviewer");
        assertTrue(f.currentDocument().contains(code), f.currentDocument());
        assertFalse(Files.exists(f.runDir(f.inputs.get("design.claude_design").runId()).resolve("outputs/result.contract.json")));
    }
}
