import dev.dual.runtime.handler.AgentResultEnvelopeParser;
import java.nio.file.*;
public class EnumDiagnosticProbe {
 public static void main(String[] args) throws Exception {
  var result = new AgentResultEnvelopeParser().parse(Files.readString(Path.of(args[0])));
  System.out.println(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result));
 }
}
