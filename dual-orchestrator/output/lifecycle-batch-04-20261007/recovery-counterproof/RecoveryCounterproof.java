import java.nio.file.*;
public class RecoveryCounterproof {
 public static void main(String[] args) throws Exception {
  var type=Class.forName("dev.dual.ledger.file.RecoveryAssessmentTest");var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);var test=constructor.newInstance();
  var field=type.getDeclaredField("tempDir");field.setAccessible(true);field.set(test,Files.createTempDirectory("recovery-counterproof-"));
  var method=type.getDeclaredMethod("unknownPermissionReceiptRequiresExternalReconciliationInsteadOfJsonRepairOrAutomaticRetry");method.setAccessible(true);
  try {method.invoke(test);if(args[0].equals("mutant"))throw new AssertionError("mutant escaped actual user-flow assertion");System.out.println("CONTROL_PASS");}
  catch(java.lang.reflect.InvocationTargetException error){if(!args[0].equals("mutant") || !(error.getCause() instanceof AssertionError))throw error;System.out.println("EXPECTED_TYPED_RECOVERY_FAILURE: "+error.getCause());}
 }
}