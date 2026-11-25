package dev.dual.adapter.acpx;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import static java.lang.System.out;
public class RealCurrentUsageProof {
  public static void main(String[] args) throws Exception {
    var mapper = new ObjectMapper();
    if (args[0].equals("historical")) {
      long total = 0;
      for (var row : mapper.readTree(Path.of(args[2]).toFile())) {
        var usage = new AcpxUsageReader(Path.of(args[1]),1,0).read(row.path("agent").asText(),
          new AcpxPromptCompletion(row.path("request").asText(),"end_turn","fixed",row.path("session").asText()),
          Path.of(row.path("transcript").asText()),Path.of(row.path("stderr").asText())).orElseThrow();
        if (!AcpxUsage.PROVIDER_RUN_USAGE.equals(usage.measurementType()) || usage.totalTokens()!=row.path("expected").asLong())
          throw new AssertionError(row.path("run_id")+" "+usage);
        total+=usage.totalTokens();out.println(row.path("run_id").asText()+" "+mapper.writeValueAsString(usage));
      }
      if(total!=403420) throw new AssertionError("wrong diagnostic sum "+total);
      out.println("DIAGNOSTIC_ONLY_TOTAL="+total+"; OLD_LEDGER_UNCHANGED; NATIVE_CALLS=0");
    } else {
      var d=Path.of(args[1]); Files.createDirectories(d);
      var id="44444444-4444-4444-8444-444444444444";
      Files.writeString(d.resolve("stderr"),"[acpx] session check ("+id+")");
      Files.writeString(d.resolve("current"),"{\"id\":4,\"result\":{\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":20,\"outputTokens\":1,\"totalTokens\":21}}}\n");
      var usage=new AcpxUsageReader(d,1,0).read("codex",new AcpxPromptCompletion("3","end_turn","answer","s"),d.resolve("current"),d.resolve("stderr")).orElseThrow();
      if(!AcpxUsage.UNAVAILABLE.equals(usage.measurementType())) throw new AssertionError("wrong request accepted "+usage);
      out.println("STRICT_REQUEST_GUARD_PASS");
    }
  }
}
