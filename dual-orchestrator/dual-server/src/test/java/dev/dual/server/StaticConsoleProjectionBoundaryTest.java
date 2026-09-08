package dev.dual.server;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticConsoleProjectionBoundaryTest {
    @Test
    void staticConsolesDoNotInferPrimaryAttentionFromWorkflowStatus() throws Exception {
        var console = Files.readString(Path.of("src/main/resources/static/console.js"));
        var mgt = Files.readString(Path.of("src/main/resources/static/mgt/app.js"));

        assertTrue(console.contains("overview.whyStopped"));
        assertTrue(console.contains("overview.nextAction"));
        assertTrue(mgt.contains("data.whyStopped"));
        assertTrue(mgt.contains("data.nextAction"));

        assertFalse(console.contains("friendlyWhy("),
                "primary console must consume WorkstreamAttention via API, not infer whyStopped from status");
        assertFalse(console.contains("friendlyNext("),
                "primary console must consume WorkstreamAttention via API, not infer nextAction from status");
        assertFalse(mgt.contains("friendlyWhyStopped("),
                "management console must consume WorkstreamAttention via API, not infer whyStopped from status");
        assertFalse(mgt.contains("friendlyNextAction("),
                "management console must consume WorkstreamAttention via API, not infer nextAction from status");
        assertFalse(mgt.contains("derivedAttention("),
                "management console must render API attention items directly, not synthesize secondary attention");
        assertFalse(mgt.contains("staleRunningSignal("),
                "management console must consume runtime recovery attention from API, not infer stale running locally");
        assertTrue(mgt.contains("hasRuntimeRecoveryAttention(data)"));
        assertTrue(mgt.contains("item.category === \"RUNTIME_RECOVERY\""));
        assertTrue(console.contains("assessment.allowedActions"));
        assertTrue(console.contains("approve_public_websearch"));
        assertTrue(console.contains("/recovery/approve-websearch"));
        assertTrue(console.contains("network.canApprove === true"));
        assertTrue(console.contains("network.canCancel === true"));
        assertTrue(console.contains("network.status === \"INVALID\""));
        assertTrue(console.contains("warning.textContent = `网络授权不可用：${network.reason"));
        assertTrue(console.contains("/recovery/network?workdir="));
        assertTrue(console.contains("asList(network.targets).includes(target)"));
        assertFalse(console.contains("assessment.classification === \"agent_permission_approval_required\""),
                "console must consume allowedActions instead of reinterpreting recovery classification");
        assertTrue(console.contains("delivery.canFinalize"));
        assertTrue(console.contains("/delivery/finalize"));
        assertFalse(console.contains("delivery.status === \"APPLIED\""),
                "console must consume shared delivery capabilities instead of interpreting lifecycle status");
        assertFalse(console.contains("state.overview?.deliveryMerge"));
        assertFalse(console.contains("state.status?.deliveryMerge"));
        assertFalse(mgt.contains("data.deliveryMerge"));
        assertFalse(console.contains("item.workflowStatus"));
        assertFalse(console.contains("task?.workflowStatus"));
        assertFalse(mgt.contains("item.workflowStatus"));
        assertTrue(mgt.contains("data.deliveryView"));
        assertTrue(mgt.contains("WORKFLOW_NOT_DELIVERABLE"));
    }
}
