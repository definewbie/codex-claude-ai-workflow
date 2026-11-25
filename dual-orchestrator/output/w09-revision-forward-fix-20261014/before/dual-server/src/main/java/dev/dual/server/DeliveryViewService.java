package dev.dual.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.runtime.delivery.DeliveryApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared delivery projection and command facade for the operator API. */
@Service
public class DeliveryViewService {
    private final DeliveryApplicationService delivery;

    @Autowired
    public DeliveryViewService(ObjectMapper ignored) {
        this(new DeliveryApplicationService(ServerLedgerFactory.create()));
    }

    DeliveryViewService(DeliveryApplicationService delivery) {
        this.delivery = delivery;
    }

    public Map<String, Object> view(Path workspaceRoot, String workstreamId) {
        return project(workstreamId, delivery.view(workspaceRoot, workstreamId, null));
    }

    public Map<String, Object> merge(Path workspaceRoot, String workstreamId) throws Exception {
        return project(workstreamId, delivery.apply(workspaceRoot, workstreamId, null));
    }

    public Map<String, Object> finalizeCommit(Path workspaceRoot, String workstreamId, String commit) throws Exception {
        return project(workstreamId, delivery.finalizeCommit(workspaceRoot, workstreamId, commit));
    }

    public DeliveryApplicationService.View observe(Path root, String id) { return delivery.view(root, id, null); }

    public Map<String, Object> inSnapshot(Path root, String id, DeliveryApplicationService.View sampled,
            dev.dual.ledger.file.WorkflowFileLedger scoped) throws java.io.IOException {
        return project(id, DeliveryApplicationService.inSnapshot(sampled, scoped,
                new dev.dual.ledger.file.WorkstreamPaths(root.toAbsolutePath().normalize(), new dev.dual.core.id.DualId(id))));
    }

    public Map<String, Object> resolve(Path root, String id, byte[] request) throws Exception {
        return project(id, delivery.resolve(root, id, request));
    }

    private static Map<String, Object> project(String workstreamId, DeliveryApplicationService.View view) {
        var response = new LinkedHashMap<String, Object>();
        response.put("workstreamId", workstreamId);
        response.put("status", view.status());
        response.put("reasonCode", view.reasonCode());
        response.put("failureReason", view.reason());
        response.put("acceptanceDecision", view.acceptanceDecision());
        response.put("candidateRef", view.candidateRef());
        response.put("candidateSha256", view.candidateSha256());
        response.put("changedFiles", view.changedFiles());
        response.put("excludedFiles", view.excludedFiles());
        response.put("targetRepo", view.targetRepo());
        response.put("commit", view.commit());
        response.put("canMerge", view.canMerge());
        response.put("canFinalize", view.canFinalize());
        response.put("blockedReasons", view.reason().isBlank() ? List.of() : List.of(view.reason()));
        response.put("nextAction", view.nextAction());
        response.put("operatorNextAction", view.nextAction());
        response.put("nextCommands", view.nextCommands());
        response.put("historicalStatus", view.historicalStatus());
        response.put("targetObservation", view.targetObservation());
        response.put("operationId", view.operationId()); response.put("planSha256", view.planSha256());
        response.put("ledgerCommitSequence", view.ledgerCommitSequence()); response.put("stopBasis", view.stopBasis());
        response.put("currentActions", view.currentActions()); response.put("resolutionTemplate", view.resolutionTemplate());
        return response;
    }

}
