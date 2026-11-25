package dev.dual.adapter.acpx;
import java.nio.file.Path;
import java.util.Set;
import dev.dual.runtime.handler.AgentResultPromptContract;
public class RealUsageProof {
    public static void main(String[] args) {
        if (args[0].equals("render")) {
            for (var node : new String[]{"intake.claude_brainstorm", "intake.codex_product_review"}) {
                var decision = node.contains("brainstorm") ? "READY_FOR_PRODUCT_REVIEW" : "READY_FOR_GOAL_APPROVAL";
                var prompt = AgentResultPromptContract.render("ws", "run", node, decision, Set.of(decision));
                if (!prompt.contains("empty tracked-path inventory permits evidence_refs=[]")
                        || !prompt.contains("Never copy .agent/ or .git/ references into evidence_refs")) throw new AssertionError("missing product evidence guard");
            }
            System.out.println("{\"shared_render_proof\":\"PASS\",\"native_calls\":0}");
            return;
        }
        var reader = new AcpxUsageReader(Path.of(args[1]), 1, 0);
        var primary = reader.read("claude", new AcpxPromptCompletion("3", "end_turn", "not reproduced", args[2]), Path.of(args[3])).orElseThrow();
        var correction = reader.read("claude", new AcpxPromptCompletion("4", "end_turn", "not reproduced", args[2]), Path.of(args[4])).orElseThrow();
        var sum = AcpxUsage.aggregatePromptOccurrences(primary, correction);
        if (primary.totalTokens()!=36945 || correction.totalTokens()!=26548 || sum.totalTokens()!=63493) throw new AssertionError("wrong attribution " + primary.measurementType()+"/"+primary.totalTokens()+"/"+correction.totalTokens()+"/"+sum.totalTokens());
        System.out.println("{\"primary\":" + primary.totalTokens() + ",\"correction\":" + correction.totalTokens()
                + ",\"total\":" + sum.totalTokens() + ",\"attribution\":\"" + primary.attribution()
                + "\",\"provider\":\"" + primary.providerId() + "\",\"model\":\"" + primary.model()
                + "\",\"billing_mode\":\"" + primary.billingMode() + "\",\"native_calls\":0}");
    }
}
