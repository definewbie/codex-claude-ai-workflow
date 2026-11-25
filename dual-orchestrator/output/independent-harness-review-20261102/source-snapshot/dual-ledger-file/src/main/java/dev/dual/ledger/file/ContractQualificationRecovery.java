package dev.dual.ledger.file;

import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.ContractQualificationChecks.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Fixed diagnostic evidence reader. This class neither runs qualification nor grants recovery authority. */
public final class ContractQualificationRecovery {
    private static final AtomicJsonWriter JSON = new AtomicJsonWriter();
    private ContractQualificationRecovery() {}

    public record Source(String ref, String sha256, NodeInstanceDocument node, Decision checks) {}

    /** Follow only the target's byte-verified incoming chain, never a latest/history search. */
    public static Optional<Source> resolvingSource(WorkstreamPaths paths, NodeInstanceDocument target) throws IOException {
        var cursor = target;
        var visited = new HashSet<String>();
        while (visited.add(cursor.nodeInstanceId())) {
            var id = cursor.inputTransition().get("source_node_instance_id");
            if (!(id instanceof String source) || source.isBlank()) return Optional.empty();
            var path = paths.nodeJson(source);
            var bytes = Files.readAllBytes(path);
            var sha = sha(bytes);
            if (!sha.equals(cursor.inputTransition().get("source_node_sha256"))) throw new IOException("diagnostic route source bytes changed");
            var next = JSON.objectMapper().readValue(bytes, NodeInstanceDocument.class);
            if (!next.nodeInstanceId().equals(source) || next.status() != NodeStatus.COMPLETED
                    || !cursor.inputRefs().equals(next.outputRefs())) throw new IOException("diagnostic incoming route is not the completed source");
            if ("gate.contract_review".equals(next.nodeDefinitionId())) {
                if (!"approved".equals(next.result().get("human_action"))) throw new IOException("diagnostic source is not an approval");
                final Decision checks;
                try { checks = ContractQualificationChecks.decision(next.result().get("decision_checks")); }
                catch (IllegalArgumentException invalid) { throw new IOException("source lacks trustworthy decision checks", invalid); }
                if (!checks.gateNodeInstanceId().equals(next.nodeInstanceId()) || !checks.subjectRefs().equals(next.inputRefs())
                        || !checks.sourceNodeInstanceId().equals(next.result().get("source_node_instance_id")))
                    throw new IOException("decision check subject differs from its owner");
                // The gate and node are two members of L04, not two independent authorities.
                GateDocument gate = null;
                try (var entries = Files.list(paths.gatesDir())) {
                    for (var p : entries.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                        var candidate = JSON.objectMapper().readValue(Files.readAllBytes(p), GateDocument.class);
                        if (next.nodeInstanceId().equals(candidate.nodeInstanceId())) {
                            if (gate != null) throw new IOException("duplicate decision gate owner");
                            gate = candidate;
                        }
                    }
                }
                if (gate == null || !"resolved".equals(gate.status()) || !"approved".equals(gate.result().get("human_action"))
                        || !next.result().get("decision_checks").equals(gate.result().get("decision_checks"))
                        || !next.inputRefs().equals(gate.result().get("subject_input_refs")))
                    throw new IOException("gate/node decision evidence differs");
                var compilerPath = paths.nodeJson(checks.sourceNodeInstanceId());
                var compilerBytes = Files.readAllBytes(compilerPath);
                var compiler = JSON.objectMapper().readValue(compilerBytes, NodeInstanceDocument.class);
                if (!sha(compilerBytes).equals(next.inputTransition().get("source_node_sha256"))
                        || !checks.sourceNodeInstanceId().equals(next.inputTransition().get("source_node_instance_id"))
                        || compiler.status() != NodeStatus.COMPLETED || !"contract.compile".equals(compiler.handlerType())
                        || !compiler.outputRefs().equals(next.inputRefs())) throw new IOException("decision compiler binding changed");
                return Optional.of(new Source(paths.workspaceRoot().relativize(path).toString(), sha, next, checks));
            }
            cursor = next;
        }
        throw new IOException("diagnostic route lineage cycle");
    }

    public static String classify(NodeInstanceDocument target, Path root, String workstream, ContinuationFailure failure) {
        try {
            var paths = new WorkstreamPaths(root, new dev.dual.core.id.DualId(workstream));
            if (!"PAIRED".equals(failure.status()) || !failure.targetNodeInstanceId().equals(target.nodeInstanceId())
                    || !failure.targetInputRefs().equals(target.inputRefs())
                    || !failure.targetInputTransition().equals(target.inputTransition())) return "UNKNOWN";
            var preparation = target.result().get("input_preparation_failure") instanceof Map<?, ?> value ? value : Map.of();
            var originalCode = "TASK_CONTRACT_PROJECTION_QUALIFICATION_FAILED".equals(target.result().get("failure_code"))
                    ? target.result().get("reason") : preparation.get("code");
            if (!failure.failureCode().equals(originalCode)) return "UNKNOWN";
            var source = resolvingSource(paths, target).orElseThrow();
            var decision = source.checks();
            if (!source.ref().equals(failure.decisionRef()) || !source.sha256().equals(failure.decisionSha256())
                    || !source.node().nodeInstanceId().equals(failure.sourceNodeInstanceId())
                    || !"approved".equals(failure.action()) || !decision.subjectRefs().equals(failure.subjectRefs())
                    || decision.familyCoverage() != Coverage.COVERED
                    || !decision.runtimeIdentitySha256().equals(failure.runtimeIdentitySha256())) return "UNKNOWN";
            var failed = failure.failedCheck();
            var matching = decision.checks().stream().filter(c -> c.ruleId().equals(failed.ruleId())
                    && c.ruleVersion().equals(failed.ruleVersion())
                    && (c.result() == Result.NOT_CHECKED || !isEvent(failed) || c.inputRefs().stream().anyMatch(ref -> failed.inputRefs().stream()
                        .anyMatch(now -> ref.ref().equals(now.ref()) && ref.eventId().equals(now.eventId()) && ref.sequence() == now.sequence())))).toList();
            if (matching.size() != 1 || failed.result() != Result.FAILED) return "UNKNOWN";
            var previous = matching.getFirst();
            if (previous.result() == Result.NOT_CHECKED && !failed.inputValues().isEmpty()) return "CONSUMER_PREREQUISITE";
            if (previous.result() != Result.PASSED || !previous.comparable()) return "UNKNOWN";
            // Compare actual observations, excluding passage of time and short-circuit coverage markers.
            var oldValues = comparableValues(previous.inputValues());
            var newValues = comparableValues(failed.inputValues());
            if (hasObservedDifference(oldValues, newValues) || changedSameReference(previous.inputRefs(), failed.inputRefs())) return "INPUT_DRIFT";
            if (failed.comparable() && oldValues.equals(newValues) && previous.inputRefs().equals(failed.inputRefs())) return "CONTRADICTION";
            return "UNKNOWN";
        } catch (IOException | RuntimeException invalid) { return "UNKNOWN"; }
    }

    private static boolean isEvent(Check check) { return check.inputRefs().stream().anyMatch(ref -> "ROUTE_EVENT".equals(ref.kind())); }

    private static Object comparableValues(Map<String, Object> values) {
        // Only the readiness producer's root metadata describes observation time/coverage.
        // Actual inputs elsewhere with the same field names remain part of identity.
        var result = new TreeMap<String, Object>(values);
        if (values.get("readiness") instanceof Map<?, ?> raw) {
            var readiness = new TreeMap<String, Object>();
            raw.forEach((key, value) -> { if (!List.of("check_times", "fully_observed").contains(key))
                readiness.put((String) key, value); });
            result.put("readiness", readiness);
        }
        return result;
    }
    private static boolean hasObservedDifference(Object before, Object after) {
        if (before instanceof Map<?, ?> b && after instanceof Map<?, ?> a) {
            for (var key : a.keySet()) if (b.containsKey(key) && hasObservedDifference(b.get(key), a.get(key))) return true;
            return false; // absence in a partial observation is not an observed change.
        }
        return !Objects.equals(before, after);
    }
    private static boolean changedSameReference(List<InputReference> before, List<InputReference> after) {
        for (var b : before) for (var a : after)
            if (b.ref().equals(a.ref()) && b.eventId().equals(a.eventId()) && b.sequence() == a.sequence()
                    && (!b.kind().equals(a.kind()) || !b.sha256().equals(a.sha256()))) return true;
        return false;
    }
    private static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
