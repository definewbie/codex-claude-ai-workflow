package dev.dual.runtime.handler;

import dev.dual.core.contract.TaskContract;
import dev.dual.runtime.RuntimeTestLedger;
import java.nio.file.*;
import java.util.*;

/** Test data builder: draft display text is input, never authority. It creates actual phased approvals. */
final class TaskContractProjectionFixture {
    static String seedCompileBinding(Path root, String ws) throws Exception {
        var mapper = TaskContractArtifactWriter.mapper();
        var draftPath = root.resolve(".agent/workstreams/" + ws + "/artifacts/task-contract.json");
        var draft = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(draftPath.toFile());
        draft.put("schema_version", 2); draft.put("phase", "GOAL"); draft.put("epoch", 1);
        for (var key : List.of("acceptance_criteria", "test_requirements")) {
            var raw = draft.path(key); var clauses = mapper.createArrayNode(); int i = 0;
            for (var clause : raw) {
                if (clause.isTextual()) clauses.addObject().put("id", (key.equals("acceptance_criteria") ? "AC-" : "T-") + (++i)).put("text", clause.asText());
                else clauses.add(clause);
            }
            draft.set(key, clauses);
        }
        var template = mapper.treeToValue(draft, TaskContract.class);
        var scope = template.scope().included();
        if (scope.isEmpty()) throw new IllegalArgumentException("projection fixture needs proposed file operations");
        var f = new AuthoredStageSequenceTest.Fixture(root, scope.getFirst().path(), AuthoredStageSequenceTest.PlanTopology.DIRECT_HUMAN,
                ws, null, RuntimeTestLedger.ledger(), template);
        f.outputMutation = result -> {
            if (result.has("design")) {
                var files = ((com.fasterxml.jackson.databind.node.ObjectNode) result.get("design")).putArray("planned_files");
                scope.forEach(e -> files.addObject().put("path", e.path()).put("operation", e.operation().name()).put("responsibility", "Proposed product change"));
            }
            if (result.has("plan")) {
                var plan = (com.fasterxml.jackson.databind.node.ObjectNode) result.get("plan");
                plan.set("files", mapper.valueToTree(scope));
                var files = plan.putArray("edit_files"); scope.forEach(e -> files.add(e.path()));
            }
        };
        f.before("implement.claude_implement");
        var qualified = TaskContractProjectionVerifier.resolve(f.ledger, root, ws).qualified().orElseThrow();
        return ".agent/workstreams/" + ws + "/nodes/" + qualified.compileNodeInstanceId() + ".json";
    }
}
