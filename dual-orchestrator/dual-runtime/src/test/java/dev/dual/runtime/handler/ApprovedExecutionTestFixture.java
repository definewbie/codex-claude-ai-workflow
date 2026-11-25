package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dual.core.contract.TaskContract;
import java.nio.file.*;
import java.util.*;

/** Full phased approval path for tests of downstream consumers; the tool process remains simulated. */
final class ApprovedExecutionTestFixture {
    static AuthoredStageSequenceTest.Fixture create(Path root, String ws, List<TaskContract.ScopeEntry> entries) throws Exception {
        var f = new AuthoredStageSequenceTest.Fixture(root, entries.getFirst().path(), AuthoredStageSequenceTest.PlanTopology.DIRECT_HUMAN, ws);
        f.outputMutation = result -> {
            if (result.has("design")) {
                var files = ((ObjectNode) result.get("design")).putArray("planned_files");
                entries.forEach(entry -> files.addObject().put("path", entry.path()).put("operation", entry.operation().name())
                        .put("responsibility", "Implement the approved requirement"));
            }
            if (result.has("plan")) {
                var plan = (ObjectNode) result.get("plan");
                var paths = plan.putArray("edit_files"); var files = plan.putArray("files");
                entries.forEach(entry -> { paths.add(entry.path()); files.addObject().put("path", entry.path()).put("operation", entry.operation().name()); });
            }
        };
        f.before("implement.claude_implement");
        // The test's readiness tool is a fixture dependency, outside the business edit set.
        if (Files.isDirectory(root.resolve(".git")))
            Files.writeString(root.resolve(".git/info/exclude"), "\ntest-tool.lock\n.agent/\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return f;
    }
}
