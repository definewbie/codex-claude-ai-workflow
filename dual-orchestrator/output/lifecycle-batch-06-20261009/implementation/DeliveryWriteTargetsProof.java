package dev.dual.runtime.delivery;
import java.nio.file.*;
import java.util.*;
import dev.dual.runtime.RuntimeTestLedger;
import dev.dual.ledger.file.*;
import com.fasterxml.jackson.databind.ObjectMapper;
public class DeliveryWriteTargetsProof {
 static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
 static int stage(WorkstreamPaths p) {
  try { if(!Files.exists(p.deliveryMergePlanJson()))return 0;var plan=JSON.readValue(p.deliveryMergePlanJson().toFile(),DeliveryMergePlan.class);
   if(plan.status()==DeliveryMergeStatus.APPLIED)return 4;
   if(plan.operation().get("process_exit")!=null)return 3;
   if(plan.operation().get("process")!=null)return 2;return 1;
  }catch(Exception e){throw new RuntimeException(e);}
 }
 public static void main(String[] args)throws Exception {
  var identity=RuntimeTestLedger.ledger().frozenRuntimeIdentity();int n=0;
  for(String target:List.of("plan","current","merge.patch","changed-files.txt","excluded-files.txt")) {
   int last=target.equals("plan")||target.equals("current")?4:1;
   for(int phase=1;phase<=last;phase++)for(boolean after:List.of(false,true)) {
    final int selected=phase; var f=DeliveryApplicationServiceTest.deliveryFixture(Files.createTempDirectory("b06-target-write-"),"ws-write");
    var p=f.paths();Path file=target.equals("plan")?p.deliveryMergePlanJson():target.equals("current")?p.workflowCurrentJson():p.deliveryMergePlanJson().getParent().resolve(target);
    WorkflowFileLedger faults;
    if(!after) faults=InterruptedTestLedger.onTarget(actual->actual.getFileName().equals(file.getFileName())&&(!target.equals("plan")&&!target.equals("current")||stage(p)==(target.equals("plan")?selected-1:selected)),identity);
    else faults=InterruptedTestLedger.atWhen("after_replace",()-> {
      if(target.equals("plan"))return stage(p)==selected;
      if(target.equals("current"))try{return JSON.readTree(file.toFile()).path("commitSequence").asLong()==selected+1;}catch(Exception e){throw new RuntimeException(e);}
      return Files.exists(file);
    },identity);
    try{new DeliveryApplicationService(faults).apply(f.source(),f.workstreamId(),null);throw new AssertionError("fault did not fire: "+target+phase+after);}
    catch(java.io.IOException expected){}
    String before=Files.readString(f.target().resolve("README.md"));
    if(!"PENDING".equals(new DeliveryApplicationService(RuntimeTestLedger.ledger()).view(f.source(),f.workstreamId(),null).status()))throw new AssertionError("partial write exposed current facts");
    DeliveryApplicationServiceTest.ColdReader.main(new String[]{f.source().toString(),f.workstreamId()});
    if(!before.equals(Files.readString(f.target().resolve("README.md"))))throw new AssertionError("repair dispatched Git");
    n++;
   }
  }
  System.out.println("PASS: "+n+" concrete plan/current/compatibility write failures; pending readers blocked, fixed repair zero reapply");
 }
}
