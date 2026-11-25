package dev.dual.runtime.handler;

import dev.dual.core.id.DualId;
import dev.dual.ledger.file.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Program-owned revision input in the existing R01. It is not an approval or a model assertion. */
public record ContractRevisionBinding(String iterationId, TaskContractProposalReference baseProposalReference,
        List<AgentResultEnvelope.ReviewObligation> obligations, List<String> allowedForms) {
    private static final AtomicJsonWriter JSON = new AtomicJsonWriter();
    public ContractRevisionBinding {
        if (iterationId == null || iterationId.isBlank() || baseProposalReference == null
                || obligations == null || obligations.isEmpty() || allowedForms == null
                || !allowedForms.contains("REVISION_PATCH")
                || !Set.of("REVISION_PATCH", "GOAL_REPROPOSAL").containsAll(allowedForms)
                || new HashSet<>(allowedForms).size() != allowedForms.size())
            throw new IllegalArgumentException("invalid program revision binding");
        obligations = List.copyOf(obligations); allowedForms = List.copyOf(allowedForms);
    }
    public record Captured(TaskContractRevisionContextResolver.Context context, ContractRevisionBinding binding, List<Path> inputs) {}
    public Map<String,Object> toMap() {
        return Map.of("iteration_id",iterationId,"base_proposal_reference",baseProposalReference.toMap(),
                "obligations",obligations,"allowed_forms",allowedForms);
    }
    static ContractRevisionBinding from(com.fasterxml.jackson.databind.JsonNode tree) throws IOException {
        try {
            if (!tree.isObject() || tree.size()!=4) throw new IOException("invalid revision binding fields");
            var reference=JSON.objectMapper().treeToValue(tree.path("base_proposal_reference"),TaskContractProposalReference.class);
            var obligations=JSON.objectMapper().readerForListOf(AgentResultEnvelope.ReviewObligation.class)
                    .<List<AgentResultEnvelope.ReviewObligation>>readValue(tree.path("obligations"));
            var forms=JSON.objectMapper().readerForListOf(String.class).<List<String>>readValue(tree.path("allowed_forms"));
            return new ContractRevisionBinding(tree.path("iteration_id").asText(),reference,obligations,forms);
        } catch (Exception failure) { throw new IOException("invalid program revision binding",failure); }
    }
    static void verifyDispatch(TaskHandler.TaskContext context, com.fasterxml.jackson.databind.JsonNode fixed) throws IOException {
        if (!context.definition().id().equals("intake.claude_brainstorm")) return;
        var captured = new TaskContractRevisionContextResolver(context.ledger()).captureRevision(context);
        var actual = fixed.path("binding").path("contract_revision");
        if (captured.binding()==null ? !actual.isMissingNode() : !captured.binding().equals(from(actual)))
            throw new IOException("revision baseline/obligations/forms differ from R01");
    }

    /** Rebuild exact R01 -> bound execution claim, without searching by model ID or using a latest baseline. */
    static ContractRevisionBinding read(Path root, String ws, String nodeId, String runId) throws IOException {
        var paths=new WorkstreamPaths(root,new DualId(ws));
        var run=paths.runsDir().resolve(runId).normalize();
        if (!run.getParent().equals(paths.runsDir()) || !runId.matches("[A-Za-z0-9._-]+")) throw new IOException("foreign run");
        List<Path> matches;
        try(var files=Files.list(run.resolve("outputs"))) {
            matches=files.filter(f->f.getFileName().toString().endsWith("-prepared.json")).toList();
        }
        if(matches.size()!=1) throw new IOException("missing or ambiguous R01");
        var path=matches.getFirst();var fixed=JSON.readTree(path);
        var executionId=fixed.path("execution").asText();
        if(!executionId.matches("[A-Za-z0-9._-]+")) throw new IOException("invalid execution identity");
        var execution=JSON.read(paths.nodeExecutionJson(executionId),NodeExecutionDocument.class);
        var prepared=AgentRunCommitProtocol.boundPreparation(paths,execution);
        if(!prepared.path().equals(path) || !fixed.path("workstream").asText().equals(ws)
                || !fixed.path("node").asText().equals(nodeId) || !execution.nodeInstanceId().equals(nodeId)
                || !execution.nodeDefinitionId().equals("intake.claude_brainstorm")
                || !fixed.path("claim").equals(JSON.objectMapper().valueToTree(execution.result().get("claim"))))
            throw new IOException("revision R01 is not the author's bound claim");
        AgentRunCommitProtocol.verifyPreparedInputs(root,fixed);
        var tree=fixed.path("binding").path("contract_revision");
        if(tree.isMissingNode()) return null;
        var binding=from(tree);
        if(!JSON.read(paths.nodeJson(nodeId),NodeInstanceDocument.class).iterationId().equals(binding.iterationId())) throw new IOException("foreign revision iteration");
        var base=root.resolve(binding.baseProposalReference().artifactRef()).normalize();
        if(!base.startsWith(root.toAbsolutePath().normalize()) || !Files.isRegularFile(base)
                || !TaskContractArtifactWriter.sha256(base).equals(binding.baseProposalReference().sha256())
                || !fixed.path("inputs").path(binding.baseProposalReference().artifactRef()).asText()
                    .equals(binding.baseProposalReference().sha256())) throw new IOException("base content identity differs from R01");
        return binding;
    }
    static ContractRevisionBinding verifyPublication(Path root,String ws,String nodeId,String runId,
            TaskContractRevisionContextResolver.Context context) throws IOException {
        var binding=context.revision() ? read(root,ws,nodeId,runId) : null;
        if(context.revision() != (binding!=null) || binding!=null &&
                (!binding.iterationId().equals(context.iteration().iterationId())
                || !binding.baseProposalReference().equals(context.previousReference())
                || !binding.obligations().equals(context.obligations())))
            throw new IOException("publication baseline differs from R01");
        return binding;
    }
    static boolean validSource(Path root,String ws,AgentOutputContractDocument doc) {
        try {
            var raw=new AgentResultEnvelopeParser().parse(Files.readString(root.resolve(doc.sourceOutputRef())));
            if(!raw.valid() || !raw.envelope().runId().equals(doc.runId())
                    || !raw.envelope().workstreamId().equals(ws) || !raw.envelope().nodeId().equals(doc.nodeId())) return false;
            var derivation=doc.proposalDerivation();
            if(derivation==null) {
                if (!(raw.envelope().result() instanceof AgentResultEnvelope.TaskContractProposalResult p) || p.baseProposalId()!=null) return false;
                var outputs=root.resolve(doc.sourceOutputRef()).getParent();
                try(var paths=Files.list(outputs)) {
                    if(paths.anyMatch(f->f.getFileName().toString().endsWith("-prepared.json")))
                        return read(root,ws,doc.nodeInstanceId(),doc.runId())==null;
                }
                return true; // A standalone initial artifact still needs ledger lineage before it can grant authority.
            }
            var binding=read(root,ws,doc.nodeInstanceId(),doc.runId());
            if(binding==null || !derivation.validFor(binding.baseProposalReference(),doc.sourceOutputRef())) return false;
            if(raw.envelope().result() instanceof AgentResultEnvelope.TaskContractRevisionPatchResult patch)
                return derivation.kind().equals("revision_patch_merge") && patch.baseProposalId().equals(binding.baseProposalReference().proposalId());
            if(!(raw.envelope().result() instanceof AgentResultEnvelope.TaskContractProposalResult p)
                    || !derivation.kind().equals("goal_reproposal") || !binding.allowedForms().contains("GOAL_REPROPOSAL")
                    || !binding.baseProposalReference().proposalId().equals(p.baseProposalId())) return false;
            var previous=binding.baseProposalReference().resolveVerifiedProposal(root,ws,JSON.objectMapper()).orElseThrow();
            var projected=new AgentResultDocumentProjector().taskContractProposal(p,doc.taskContractProposal().scope().included());
            if(!projected.equals(doc.taskContractProposal()) || !p.scope().included().stream()
                    .map(AgentResultEnvelope.ProposalScopePath::path).toList().equals(doc.taskContractProposal().scope().included().stream()
                        .map(dev.dual.core.contract.TaskContract.ScopeEntry::path).toList())) return false;
            var validator=new TaskContractProposalValidator();
            return validator.validateGoalReproposal(previous,doc.taskContractProposal()).isEmpty()
                    && validator.validateRevisionCoverage(doc.taskContractProposal(),binding.obligations()).isEmpty();
        } catch(Exception failure) { return false; }
    }
}
