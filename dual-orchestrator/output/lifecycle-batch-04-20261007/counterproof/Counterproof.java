public class Counterproof {
 public static void main(String[] args) throws Exception {
  var type=Class.forName("dev.dual.runtime.handler.NativePermissionSequenceTest");
  var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);var test=ctor.newInstance();
  var root=type.getDeclaredField("root");root.setAccessible(true);root.set(test,java.nio.file.Files.createDirectories(java.nio.file.Path.of(args[1])));
  var method=type.getDeclaredMethod("savedHumanChoicesHaveZeroAuthorityThenEachExactRequestCommitsOnce");method.setAccessible(true);
  try {method.invoke(test); if(args[0].equals("mutant")) throw new Exception("counterproof did not reject duplicate C"); System.out.println("CONTROL_PASS");}
  catch(java.lang.reflect.InvocationTargetException failure) {
   if(args[0].equals("mutant") && failure.getCause() instanceof AssertionError) {System.out.println("EXPECTED_DUPLICATE_C_FAILURE: "+failure.getCause());return;}
   throw failure;
  }
 }
}