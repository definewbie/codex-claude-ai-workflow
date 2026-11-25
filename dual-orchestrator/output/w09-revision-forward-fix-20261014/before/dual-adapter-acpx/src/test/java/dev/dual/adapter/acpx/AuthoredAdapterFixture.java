package dev.dual.adapter.acpx;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import dev.dual.runtime.handler.*;
import java.nio.file.Files;
import java.util.*;

/** Real phase approvals and independent review predecessors; only provider execution is an offline fixture. */
final class AuthoredAdapterFixture {
    static TaskHandler.TaskContext before(TaskHandler.TaskContext requested) throws Exception {
        var root = requested.workspaceRoot();
        var ws = requested.workstreamId();
        var paths = new WorkstreamPaths(root, new DualId(ws));
        var ledger = requested.ledger();
        if (!Files.exists(paths.workflowDefinitionJson())) {
            new AuthoredStageSequenceTest.Fixture(root, "repo/relative/new.ext",
                    AuthoredStageSequenceTest.PlanTopology.DIRECT_HUMAN, ws, null, ledger)
                    .before(requested.definition().id());
        }
        var current = ledger.loadCurrent(paths);
        var active = ledger.loadCoreNode(paths, current.activeNodeInstanceIds().getFirst());
        if (!active.nodeDefinitionId().equals(requested.definition().id()))
            throw new IllegalStateException("fixture requested another stage before completing current stage: " + active.nodeDefinitionId());
        var declared = ledger.loadDefinition(paths).nodes().stream()
                .filter(node -> node.id().equals(active.nodeDefinitionId())).findFirst().orElseThrow();
        var config = new LinkedHashMap<>(declared.config());
        config.putAll(requested.definition().config());
        var definition = new NodeDefinition(declared.id(), declared.kind(), requested.definition().handlerType(), config);
        // No copied node ids, rewritten route events or fabricated approval nodes.
        return AcpxTestLedger.claim(new TaskHandler.TaskContext(root, ws, definition, active, ledger));
    }
}
