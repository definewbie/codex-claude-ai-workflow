package dev.dual.runtime.handler;

import dev.dual.core.contract.TaskContract;
import dev.dual.core.flow.BuiltInFlowOrchestrationKernel;
import dev.dual.ledger.file.WorkflowFileLedger;
import java.nio.file.*;
import java.util.*;

/** Offline proof carrier only. Original case files are read, never opened by a ledger writer. */
public final class ReadOnlyMethodProof {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("prompt")) {
            new AgentResultPromptContractTest().planPromptSeparatesRegisteredMethodFromCriterionDetails();
            return;
        }
        if (args[0].equals("suffix")) {
            var test = new VerificationReadinessTest();
            test.temporary = Path.of(args[1]);
            test.usefulSuffixAndWhitespaceStillCannotChangeTheRegisteredMethod();
            return;
        }
        var json = VerificationReadinessTest.JSON;
        var goal = TaskContractArtifactWriter.mapper().readValue(Path.of(args[1]).toFile(), TaskContract.class);
        var request = json.treeToValue(json.readTree(Path.of(args[2]).toFile()).path("request"), VerificationReadiness.Request.class);
        var plan = PlanArtifactDocument.from(json.readValue(Path.of(args[3]).toFile(), Map.class));
        var ledger = new WorkflowFileLedger(new BuiltInFlowOrchestrationKernel(), Map.of("test_binary", "readiness-v2"));
        var f = new VerificationReadinessTest.Fixture(Path.of(args[4]), List.of(), List.of(), "gate.plan",
                "ws-readonly-method-proof", ledger, goal);
        var local = f.request("readonly-method", VerificationReadiness.Action.RECORD, null);
        var ready = VerificationReadiness.record(ledger, f.root, f.ws, new VerificationReadiness.Request(
                local.schema(), local.requestId(), local.subject(), local.goal(), local.epoch(), local.criterionIds(),
                request.method(), request.executor(), request.evidenceKind(), local.environment(), local.statement(),
                request.detailOwner(), local.lifetime(), null, local.action(), null));
        // Rebase only fixture identity/environment refs. This cannot authorize the historical workstream.
        var exact = plan.verificationBindings().stream().map(b -> new TaskContract.VerificationBinding(
                b.criterionId(), request.method(), b.executor(), List.of(ready), List.of(), b.evidenceKind(),
                List.of(ready), b.detailOwner(), b.actualExitRequired(), b.expectedExitCode())).toList();
        var qualifiedGoal = f.goal();
        try (var read = ledger.readSession(f.paths)) {
            VerificationReadiness.validateBindings(read.ledger(), f.root, f.ws, qualifiedGoal, exact);
            for (int i = 0; i < exact.size(); i++) {
                var comparison = new ArrayList<>(exact);
                var b = exact.get(i);
                comparison.set(i, new TaskContract.VerificationBinding(b.criterionId(), plan.verificationBindings().get(i).method(),
                        b.executor(), b.environmentRefs(), b.authorityRefs(), b.evidenceKind(), b.feasibilityRefs(),
                        b.detailOwner(), b.actualExitRequired(), b.expectedExitCode()));
                try {
                    VerificationReadiness.validateBindings(read.ledger(), f.root, f.ws, qualifiedGoal, comparison);
                    throw new AssertionError("historical appended method was accepted: " + b.criterionId());
                } catch (java.io.IOException expected) {
                    if (!expected.getMessage().contains("readiness does not support the planned verification")) throw expected;
                }
            }
        }
        System.out.println(json.writeValueAsString(Map.of("original_method_variants_rejected", exact.size(),
                "isolated_exact_method_variant_accepted", true, "original_validation_plan_retained", plan.validationPlan(),
                "scope", "Real historical strings; synthetic isolated GOAL/readiness identity; not historical recovery or full E2E")));
    }
}
