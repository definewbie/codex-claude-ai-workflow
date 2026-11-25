package dev.dual.runtime.handler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ProposalReferenceIdentityTest {
    @TempDir Path root;
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    @Test void identicalReferenceIsIdempotentButSameIdDifferentFullReferenceIsAmbiguousInEitherOrder() throws Exception {
        var first=initial("one");var second=initial("two");
        var r=second.reference();
        var collision=new TaskContractProposalReference(1,first.reference().proposalId(),r.workstreamId(),r.producerNodeId(),
                r.producerNodeInstanceId(),r.artifactRef(),r.sha256());
        var other=root.resolve("other.ref.json");json.writeValue(other.toFile(),collision);
        assertTrue(collision.resolveArtifact(root,"ws",json).isPresent(),"both references independently valid; do not fake rejection through invalid data");
        assertTrue(TaskContractProposalReference.resolveFromRefs(root,"ws",List.of(first.referenceRef(),first.referenceRef()),json).isPresent());
        for(var refs:List.of(List.of(first.referenceRef(),"other.ref.json"),List.of("other.ref.json",first.referenceRef())))
            assertTrue(TaskContractProposalReference.resolveFromRefs(root,"ws",refs,json).isEmpty());
    }
    private TaskContractProposalReference.Published initial(String run) throws Exception {
        var prompt=AgentResultPromptContract.render("ws",run,"intake.claude_brainstorm","READY_FOR_PRODUCT_REVIEW",Set.of("READY_FOR_PRODUCT_REVIEW"));
        var envelope=new AgentResultEnvelopeParser().parse(prompt.substring(prompt.lastIndexOf("\n{")+1)).envelope();
        var result=(AgentResultEnvelope.TaskContractProposalResult)envelope.result();
        var projector=new AgentResultDocumentProjector();
        var proposal=projector.taskContractProposal(result,List.of());
        var source=root.resolve(".agent/workstreams/ws/runs/"+run+"/outputs/intake.json");Files.createDirectories(source.getParent());
        Files.writeString(source,prompt.substring(prompt.lastIndexOf("\n{")+1));
        var map=projector.outputContract(envelope,ContractRegistry.forNode("intake.claude_brainstorm"),"acpx.agent_run","node-"+run,
                root.relativize(source).toString(),proposal,null);
        var sidecar=source.resolveSibling("intake.contract.json");json.writeValue(sidecar.toFile(),map);
        var doc=AgentOutputContractDocument.read(sidecar,json).orElseThrow();
        return TaskContractProposalReference.publishInitial(root,"ws",sidecar,doc,json);
    }
}
