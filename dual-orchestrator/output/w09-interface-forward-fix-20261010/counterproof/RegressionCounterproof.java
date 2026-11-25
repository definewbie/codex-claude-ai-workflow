
import java.nio.file.*;
public class RegressionCounterproof {
 public static void main(String[] args) throws Exception {
  boolean compatibility=args[0].equals("compatibility");
  String name=compatibility?"dev.dual.adapter.acpx.AcpxCompatibilitySequenceTest":"dev.dual.adapter.acpx.WorkflowFirstLifecycleSequenceTest";
  var type=Class.forName(name);var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);var test=ctor.newInstance();
  var field=type.getDeclaredField("root");field.setAccessible(true);field.set(test,Files.createTempDirectory("w09-counterproof-"));
  var method=type.getDeclaredMethod(compatibility?"optionalEngineIdentityDoesNotBlockPrimaryOrCorrectionOrInventAnIdentity":"interfaceFailureKeepsRunNodeRecoveryAndSummaryConsistentAcrossColdRead",compatibility?String.class:boolean.class);method.setAccessible(true);
  var owner=Class.forName(compatibility?"dev.dual.adapter.acpx.AcpxSessionCompatibility":"dev.dual.ledger.file.FailureSummaryWriter");
  System.out.println("OWNER="+owner.getProtectionDomain().getCodeSource().getLocation());
  try {method.invoke(test,compatibility?args[2]:Boolean.valueOf(args[2]));if(args[1].equals("mutant"))throw new AssertionError("mutant escaped user-flow assertion");System.out.println("CONTROL_PASS");}
  catch(java.lang.reflect.InvocationTargetException error){if(!args[1].equals("mutant") || !(error.getCause() instanceof AssertionError))throw error;System.out.println("EXPECTED_ASSERTION_FAILURE="+error.getCause());}
 }
}
