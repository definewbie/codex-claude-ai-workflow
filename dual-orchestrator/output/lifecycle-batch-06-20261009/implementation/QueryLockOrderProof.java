package dev.dual.server;
import java.nio.file.*;
import java.util.*;
import dev.dual.ledger.file.*;
import dev.dual.core.id.*;
import dev.dual.core.workflow.*;
import dev.dual.runtime.delivery.DeliveryApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
public class QueryLockOrderProof {
 public static void main(String[] args) throws Exception {
  var root=Files.createTempDirectory("b06-query-proof-");var paths=new WorkstreamPaths(root,new DualId("ws-query-proof"));
  var ledger=ServerLedgerFactory.create();
  ServerTestLedger.initialize(ledger,paths,"wf-query",new WorkflowDefinition("query",1,"work",List.of(new NodeDefinition("work",NodeKind.TASK,"noop.task",Map.of())),List.of()));
  var observed=new java.util.concurrent.atomic.AtomicInteger();var composed=new java.util.concurrent.atomic.AtomicInteger();
  var facade=new DeliveryViewService(new ObjectMapper()) {
   @Override public DeliveryApplicationService.View observe(Path r,String id) {
    try(var outside=ledger.readSession(paths)) { observed.incrementAndGet(); }
    catch(Exception held) { throw new AssertionError("observe called while L held",held); }
    return super.observe(r,id);
   }
   @Override public Map<String,Object> inSnapshot(Path r,String id,DeliveryApplicationService.View sample,WorkflowFileLedger scoped) throws java.io.IOException {
    try(var unexpected=ledger.readSession(paths)) { throw new AssertionError("composition is outside original L"); }
    catch(WorkstreamLockedException correct) { composed.incrementAndGet(); }
    return super.inSnapshot(r,id,sample,scoped);
   }
  };
  var query=new WorkstreamQueryService(new ObjectMapper().findAndRegisterModules(),facade);
  var status=query.status(root,paths.workstreamId().value());
  var overview=query.overview(root,paths.workstreamId().value());
  if(observed.get()!=2||composed.get()!=2) throw new AssertionError("two current projections did not share the required ordering");
  if(!"RUNNING".equals(status.get("status"))) throw new AssertionError(status);
  System.out.println("PASS: real status/overview invoke observer before L and compose only inside L");
 }
}
