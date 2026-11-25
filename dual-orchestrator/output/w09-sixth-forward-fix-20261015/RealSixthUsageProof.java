package dev.dual.adapter.acpx;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.runtime.handler.AgentResultEnvelopeParser;
import java.nio.file.*;
public class RealSixthUsageProof {
  public static void main(String[] args) throws Exception {
    var mapper = new ObjectMapper(); long total = 0;
    for (var row : mapper.readTree(Path.of(args[1]).toFile())) {
      var parsed = new AgentResultEnvelopeParser().parse(Files.readString(Path.of(row.path("output").asText())));
      if (row.path("unknown_field").asBoolean()) {
        if (parsed.valid() || parsed.diagnostic().kind()!=AgentResultEnvelopeParser.ParseFailureKind.UNKNOWN_FIELD
            || !"response".equals(parsed.diagnostic().fieldName())
            || !parsed.diagnostic().allowedFields().equals(java.util.List.of("resolved_by_clause_ids","review_obligation_id")))
          throw new AssertionError("Raw must remain refused with precise diagnostic: "+parsed);
      } else if(!parsed.valid()) throw new AssertionError("Previously valid raw rejected "+parsed);
      var usage = new AcpxUsageReader(Path.of(args[0]),1,0).read(row.path("agent").asText(),
          new AcpxPromptCompletion(row.path("request").asText(),"end_turn","fixed",row.path("session").asText()),
          Path.of(row.path("transcript").asText()),Path.of(row.path("stderr").asText())).orElseThrow();
      if (!AcpxUsage.PROVIDER_RUN_USAGE.equals(usage.measurementType()) || usage.totalTokens()!=row.path("expected").asLong()
          || !AcpxUsage.ATTR_EXACT_REQUEST_MATCH.equals(usage.attribution()))
        throw new AssertionError(row.path("run_id")+" "+usage);
      total+=usage.totalTokens();
      System.out.println(mapper.writeValueAsString(java.util.Map.of("run",row.path("run_id").asText(),
          "occurrence",row.path("occurrence").asInt(),"usage",usage,"parse",parsed.valid()?"VALID":parsed.diagnostic())));
    }
    if(total!=70801)throw new AssertionError("diagnostic sum "+total);
    System.out.println("DIAGNOSTIC_ONLY_TOTAL="+total+"; NO_LEDGER_REWRITE; NO_NATIVE_CALLS");
  }
}
