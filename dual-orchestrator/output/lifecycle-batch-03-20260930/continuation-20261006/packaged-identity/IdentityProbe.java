import java.nio.file.*;
import java.util.*;
import dev.dual.core.domain.*;
import dev.dual.core.id.*;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
public class IdentityProbe {
 public static void main(String[] args) throws Exception {
  var factory=Class.forName(args[0]); var create=factory.getDeclaredMethod("create"); create.setAccessible(true);
  var ledger=(WorkflowFileLedger)create.invoke(null);
  var root=Path.of(args[1]).toAbsolutePath(); Files.createDirectories(root);
  var ws=new DualId("ws-packaged-identity"); var workspace=new DualId("workspace-proof"); var paths=new WorkstreamPaths(root,ws);
  if (args[2].equals("initialize")) {
   var def=new WorkflowDefinition("identity-proof",1,"local.task",List.of(new NodeDefinition("local.task",NodeKind.TASK,"test.local",Map.of())),List.of());
   ledger.initialize(paths,Workstream.created(ws,"offline identity",new Workstream.RepositoryRef(root,root),workspace,java.time.Instant.now()),
    Workspace.primaryGitWorktree(workspace,ws,root,root,"fixture","fixture","no-git-invoked"),"no provider",WorkstreamSettingsDocument.of("auto"),"wf-identity",def);
  }
  try(var read=ledger.readSession(paths)) {
   var current=read.ledger().loadCurrent(paths);
   if(current.status()!=WorkflowStatus.RUNNING || current.activeNodeInstanceIds().size()!=1) throw new AssertionError("wrong L00 snapshot");
  }
  var json=new com.fasterxml.jackson.databind.ObjectMapper();
  Files.writeString(Path.of(args[3]),json.writerWithDefaultPrettyPrinter().writeValueAsString(ledger.frozenRuntimeIdentity()));
  System.out.println("PACKAGED_FACTORY_AND_L00_READ_OK "+factory.getName());
 }
}
