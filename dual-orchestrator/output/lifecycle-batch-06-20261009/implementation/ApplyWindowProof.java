package dev.dual.runtime.delivery;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import dev.dual.runtime.RuntimeTestLedger;
import dev.dual.ledger.file.InterruptedTestLedger;
public class ApplyWindowProof {
 public static void main(String[] args) throws Exception {
  var f=DeliveryApplicationServiceTest.deliveryFixture(Files.createTempDirectory("b06-window-"),"ws-window");
  var reached=new CountDownLatch(1);var resume=new CountDownLatch(1);
  var ledger=InterruptedTestLedger.pauseAt("after_pending_delete",RuntimeTestLedger.ledger().frozenRuntimeIdentity(),reached,resume);
  try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
   var running=pool.submit(()->new DeliveryApplicationService(ledger).apply(f.source(),f.workstreamId(),null));
   if(!reached.await(10,TimeUnit.SECONDS))throw new AssertionError("intent barrier not reached");
   try { git(f.target(),"commit","--allow-empty","-m","external clean HEAD drift"); }
   finally { resume.countDown(); }
   try { running.get(10,TimeUnit.SECONDS);throw new AssertionError("clean HEAD drift bypassed launch guard"); }
   catch(ExecutionException expected) { if(!expected.getCause().getMessage().contains("changed before Git apply start"))throw expected; }
  }
  var plan=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().readValue(f.paths().deliveryMergePlanJson().toFile(),dev.dual.ledger.file.DeliveryMergePlan.class);
  if(plan.operation().get("process")!=null)throw new AssertionError("a child started despite launch drift");
  if(!"base\n".equals(Files.readString(f.target().resolve("README.md"))))throw new AssertionError("patch reached target");
  System.out.println("PASS: durable intent -> external clean HEAD drift -> zero Git child/patch; target preserved");
 }
 static void git(Path root,String...args)throws Exception {
  var command=new ArrayList<String>(List.of("git"));command.addAll(List.of(args));
  var p=new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
  var output=new String(p.getInputStream().readAllBytes());if(p.waitFor()!=0)throw new AssertionError(output);
 }
}
