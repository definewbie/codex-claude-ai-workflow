package dev.dual.runtime.handler;

import java.nio.file.*;
import java.util.*;
import dev.dual.core.workflow.*;
import dev.dual.core.contract.TaskContract;
import dev.dual.ledger.file.*;

/** Offline evidence supplement; deliberately wrong test checker, no production hook or provider. */
public final class ClockComparisonProof {
    public static void main(String[] args) throws Exception {
        var flow = new PhasedContractContinuationTest.Flow(Path.of(args[0]));
        flow.untilPause(); flow.recordReadiness();
        ReviewGateActions.resolve(flow.base.ledger, flow.base.paths, "revise", "Bind environment"); flow.untilPause();
        ReviewGateActions.resolve(flow.base.ledger, flow.base.paths, "approve", "Approve actual candidate");
        flow.testQualification = context -> {
            try {
                return context.ledger().inspect(flow.base.paths, (scoped, files) -> {
                var scopedContext = new TaskHandler.TaskContext(context.workspaceRoot(), context.workstreamId(), context.definition(), context.instance(), scoped);
                var qualified = TaskContractProjectionVerifier.resolve(scoped, flow.base.root, flow.base.ws).qualified().orElseThrow();
                var goal = TaskContractProjectionVerifier.resolve(scoped, flow.base.root, flow.base.ws, TaskContract.Phase.GOAL).qualified().orElseThrow();
                var observations = new LinkedHashMap<String, Object>();
                VerificationReadiness.validateBindings(scoped, flow.base.root, flow.base.ws, goal,
                        qualified.contract().verificationBindings(), observations);
                var trace = new ContractQualificationTrace(scoped, flow.base.root);
                trace.checkReadiness(ContractQualificationChecks.values("readiness", observations), true);
                return AgentRunGovernanceContext.qualificationFailure(scopedContext, trace.failure(
                        ContractQualificationTrace.Rule.CANDIDATE_READINESS, VerificationReadiness.REQUIRED,
                        "deliberately wrong test checker with valid observed readiness"), "test clock comparison").result();
                });
            } catch (Exception failed) { throw new RuntimeException(failed); }
        };
        flow.untilPause();
        var node = flow.base.ledger.loadNode(flow.base.paths, flow.base.ledger.loadCurrent(flow.base.paths).activeNodeInstanceIds().getFirst());
        var pair = ContractQualificationChecks.continuation(node.result().get("continuation_failure"));
        var decision = ContractQualificationRecovery.resolvingSource(flow.base.paths, node).orElseThrow().checks();
        var old = decision.checks().stream().filter(c -> c.ruleId().equals(pair.failedCheck().ruleId())).findFirst().orElseThrow();
        var oldTimes = ((Map<?, ?>)old.inputValues().get("readiness")).get("check_times");
        var newTimes = ((Map<?, ?>)pair.failedCheck().inputValues().get("readiness")).get("check_times");
        if (oldTimes.equals(newTimes)) throw new AssertionError("no clock difference was actually observed");
        var assessment = RecoveryAssessment.from(node, flow.base.root, flow.base.ws);
        if (!"qualification_contract_contradiction".equals(assessment.classification()) || assessment.retrySafe())
            throw new AssertionError(assessment.toString());
        flow.testQualification = null;
        flow.base.ledger.retryActiveNode(flow.base.paths, "Remove only test checker; actual inputs remain valid"); flow.untilPause();
        if (flow.base.ledger.loadCurrent(flow.base.paths).status()!=WorkflowStatus.COMPLETED) throw new AssertionError("lawful continuation failed");
        System.out.println("PASS: actual check times differ; unchanged readiness is CONTRADICTION, not INPUT_DRIFT; lawful continuation completed. TEST MECHANISM ONLY; native/provider runs=0.");
    }
}
