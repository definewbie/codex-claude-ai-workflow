import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class FocusedTests {
 public static void main(String[] args) {
  var listener = new SummaryGeneratingListener();
  var launcher = LauncherFactory.create();
  launcher.registerTestExecutionListeners(listener);
  launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(java.util.Arrays.stream(args).map(org.junit.platform.engine.discovery.DiscoverySelectors::selectClass).toList()).build());
  var summary = listener.getSummary();
  summary.printTo(new java.io.PrintWriter(System.out, true));
  summary.printFailuresTo(new java.io.PrintWriter(System.out, true));
  if(summary.getTestsFoundCount()==0 || summary.getTestsFailedCount()!=0 || summary.getTestsAbortedCount()!=0
      || summary.getTestsSkippedCount()!=0) System.exit(1);
 }
}
