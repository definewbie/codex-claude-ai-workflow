package dev.dual.runtime.handler;

import dev.dual.ledger.file.ContractQualificationChecks;
import dev.dual.ledger.file.ContractQualificationChecks.*;
import dev.dual.ledger.file.LedgerSession;
import dev.dual.ledger.file.WorkflowFileLedger;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Local value collector for this checker. No global state, rule interpretation or routing authority. */
final class ContractQualificationTrace {
    enum Rule {
        LEGACY_FLAG("TaskContractProjectionVerifier.requiresContract.legacy_contract_qualification_flag_is_not_supported"),
        REQUIRED_PHASE_MISSING("TaskContractProjectionVerifier.requiredPhase.required_contract_phase_is_missing"),
        REQUIRED_PHASE_INVALID("TaskContractProjectionVerifier.requiredPhase.invalid_contract_phase"),
        SNAPSHOT_AVAILABLE("TaskContractProjectionVerifier.resolve.dynamic_diagnostic_or_i_o_failure"),
        GOAL_EPOCH("TaskContractProjectionVerifier.resolveGoal.goal_authorization_epoch_is_missing_or_duplicated"),
        GOAL_OUTPUTS("TaskContractProjectionVerifier.resolveGoal.goal_compile_does_not_publish_its_occurrence_artifacts"),
        GOAL_IDENTITY("TaskContractProjectionVerifier.resolveGoal.goal_identity_shape_or_immutable_bytes_do_not_match_its_compiler"),
        GOAL_APPROVAL_BYTES("TaskContractProjectionVerifier.resolveGoal.goal_approval_reference_is_not_an_unchanged_workstream_gate"),
        GOAL_APPROVAL_ROUTE("TaskContractProjectionVerifier.resolveGoal.goal_compiler_is_not_routed_from_the_matching_completed_human_decision"),
        GOAL_REVIEW_SUBJECT("TaskContractProjectionVerifier.resolveGoal.approved_review_subject_is_not_verifiable"),
        GOAL_PROPOSAL_BYTES("TaskContractProjectionVerifier.resolveGoal.goal_does_not_bind_the_approved_proposal_bytes"),
        GOAL_EVENT_ID("TaskContractProjectionVerifier.resolveGoal.invalid_route_event_identity"),
        GOAL_EVENT_ORDER("TaskContractProjectionVerifier.resolveGoal.unordered_workflow_events"),
        GOAL_EVENT_BINDING("TaskContractProjectionVerifier.resolveGoal.invalid_goal_approval_routing_occurrence"),
        GOAL_EVENT_PRESENT("TaskContractProjectionVerifier.resolveGoal.goal_approval_route_is_missing"),
        EXECUTION_GATE_PRESENT("TaskContractProjectionVerifier.resolveExecution.contract_approval_gate_node_is_missing"),
        EXECUTION_GATE_COMPLETED("TaskContractProjectionVerifier.resolveExecution.latest_contract_approval_gate_is_not_completed"),
        EXECUTION_GATE_LINEAGE("TaskContractProjectionVerifier.resolveExecution.latest_contract_gate_lineage_or_status_is_invalid"),
        EXECUTION_APPROVED("TaskContractProjectionVerifier.resolveExecution.latest_contract_gate_action_is_not_approved"),
        EXECUTION_NOTE("TaskContractProjectionVerifier.resolveExecution.contract_gate_note_does_not_match_node_result"),
        EXECUTION_SUBJECT("TaskContractProjectionVerifier.resolveExecution.contract_gate_subject_refs_do_not_match_node_input_refs"),
        EXECUTION_SOURCE("TaskContractProjectionVerifier.resolveExecution.contract_gate_source_occurrence_identity_is_missing_or_inconsistent"),
        EXECUTION_COMPILER("TaskContractProjectionVerifier.resolveExecution.approved_source_is_not_a_completed_contract_compile_node"),
        EXECUTION_EVENT_SOURCE("TaskContractProjectionVerifier.resolveExecution.routing_event_source_does_not_match_approved_compile_node"),
        EXECUTION_NEWER_COMPILER("TaskContractProjectionVerifier.resolveExecution.completed_contract_compile_is_newer_than_the_approved_source"),
        EXECUTION_ARTIFACTS("TaskContractProjectionVerifier.resolveExecution.approved_contract_or_requirement_resolution_artifact_is_missing"),
        EXECUTION_OUTPUTS("TaskContractProjectionVerifier.resolveExecution.approved_compile_gate_refs_do_not_bind_both_contract_artifacts"),
        EXECUTION_PARSE("TaskContractProjectionVerifier.resolveExecution.approved_contract_artifacts_cannot_be_parsed"),
        EXECUTION_SHAPE("TaskContractProjectionVerifier.resolveExecution.task_contract_json_is_invalid"),
        EXECUTION_HASH_BINDING("TaskContractProjectionVerifier.resolveExecution.approved_contract_compile_has_incomplete_artifact_sha_binding"),
        EXECUTION_HASH("TaskContractProjectionVerifier.resolveExecution.approved_contract_artifact_sha_256_does_not_match_compile_result"),
        EXECUTION_IDENTITY("TaskContractProjectionVerifier.resolveExecution.task_contract_json_identity_does_not_match_compile_result"),
        CURRENT_READINESS("TaskContractProjectionVerifier.resolveExecution.dynamic_diagnostic_or_i_o_failure"),
        TECHNICAL_APPROVAL("TaskContractProjectionVerifier.currentVerificationBindings.invalid_technical_continuation_approval_binding"),
        TECHNICAL_REVIEW("TaskContractProjectionVerifier.currentVerificationBindings.technical_continuation_lost_its_independent_reviews"),
        TECHNICAL_BOUNDARY("TaskContractProjectionVerifier.currentVerificationBindings.technical_revision_changed_the_execution_authorization_boundary"),
        TECHNICAL_SOURCE("TaskContractProjectionVerifier.currentVerificationBindings.technical_continuation_route_source_changed"),
        TECHNICAL_CYCLE("TaskContractProjectionVerifier.currentVerificationBindings.technical_continuation_lineage_cycle"),
        GOAL_REQUIRED("TaskContractProjectionVerifier.validateExecutionInputs.current_goal_is_required"),
        EXECUTION_GOAL("TaskContractProjectionVerifier.validateExecutionInputs.execution_contract_no_longer_binds_the_approved_goal"),
        EXECUTION_DECLARATIONS("TaskContractProjectionVerifier.validateExecutionInputs.nonessential_declarations_must_bind_the_frozen_node_contracts"),
        EXECUTION_REVIEWS("TaskContractProjectionVerifier.validateExecutionInputs.execution_contract_must_consume_its_routed_independent_reviews"),
        CANDIDATE_READINESS("TaskContractProjectionVerifier.validateExecutionInputs.dynamic_diagnostic_or_i_o_failure"),
        CANDIDATE_CURRENT("TaskContractProjectionVerifier.executionCandidate.contract_candidate_requires_its_current_waiting_gate"),
        CANDIDATE_SUBJECT("TaskContractProjectionVerifier.executionCandidate.contract_gate_subject_or_route_is_inconsistent"),
        CANDIDATE_COMPILER("TaskContractProjectionVerifier.executionCandidate.contract_gate_does_not_bind_its_completed_execution_compiler"),
        CANDIDATE_OUTPUTS("TaskContractProjectionVerifier.executionCandidate.contract_candidate_references_are_not_published_by_its_compiler"),
        CANDIDATE_IDENTITY("TaskContractProjectionVerifier.executionCandidate.contract_candidate_bytes_or_identity_changed"),
        CANDIDATE_PASSTHROUGH("TaskContractProjectionVerifier.executionCandidate.typed_failure_passthrough"),
        PAYLOAD_REQUIRED("TaskContractProjectionVerifier.trustedWrappedPayload.qualified_task_contract_is_required"),
        PAYLOAD_PASSTHROUGH("TaskContractProjectionVerifier.trustedWrappedPayload.typed_failure_passthrough"),
        ACCEPTANCE_CONTEXT("TaskContractProjectionVerifier.resolveRoutedAcceptance.dynamic_diagnostic_or_i_o_failure"),
        ACCEPTANCE_CONTRACT("TaskContractProjectionVerifier.resolveRoutedAcceptance.qualified_task_contract_is_required"),
        EVENT_PARSE("TaskContractProjectionVerifier.lastContractGateOccurrence.workflow_event_history_contains_invalid_json"),
        EVENT_ID("TaskContractProjectionVerifier.lastContractGateOccurrence.routing_event_does_not_have_a_workflow_event_id"),
        EVENT_ORDER("TaskContractProjectionVerifier.lastContractGateOccurrence.workflow_event_history_is_not_strictly_ordered"),
        EVENT_SOURCE("TaskContractProjectionVerifier.lastContractGateOccurrence.contract_gate_routing_event_has_incomplete_source_lineage"),
        EVENT_READ("TaskContractProjectionVerifier.lastContractGateOccurrence.cannot_read_workflow_event_history"),
        GATE_DOCUMENT_PRESENT("TaskContractProjectionVerifier.readGate.contract_approval_gate_document_is_missing"),
        GATE_DOCUMENT_PARSE("TaskContractProjectionVerifier.readGate.contract_approval_gate_document_cannot_be_parsed"),
        NODE_DOCUMENT_READ("TaskContractProjectionVerifier.readNodes.cannot_read_node_ledger_document"),
        NODE_DIRECTORY_READ("TaskContractProjectionVerifier.readNodes.cannot_list_node_ledger"),
        ACCEPTANCE_CONTEXT_READ("TaskContractProjectionVerifier.fromRefs.cannot_read_routed_acceptance_context_metadata"),
        ACCEPTANCE_CONTEXT_UNIQUE("TaskContractProjectionVerifier.fromRefs.multiple_routed_acceptance_context_metadata_documents"),
        ACCEPTANCE_CONTEXT_BINDING("TaskContractProjectionVerifier.fromRefs.routed_acceptance_context_metadata_has_no_task_contract_binding"),
        ACCEPTANCE_HASH("TaskContractProjectionVerifier.AcceptanceContextBinding.validate.routed_context_sha_does_not_match_current_task_contract"),
        ACCEPTANCE_LINEAGE("TaskContractProjectionVerifier.AcceptanceContextBinding.validate.routed_context_compile_ref_does_not_match_current_binding"),
        APPROVAL_REQUIRED("AgentRunGovernanceContext.approved_contract_required"),
        CONTEXT_REQUIRED("AgentRunGovernanceContext.routed_context_required");
        final String id;
        Rule(String id) { this.id = id; }
    }
    private final Map<String, Check> checks = new LinkedHashMap<>();
    private final Map<Path, InputReference> observedFiles = new LinkedHashMap<>();
    private final Path root;
    final String runtimeIdentity;
    final String version;
    Path root() { return root; }
    ContractQualificationTrace(WorkflowFileLedger ledger, Path root) {
        this.root = root.toAbsolutePath().normalize();
        runtimeIdentity = ContractQualificationChecks.identity(ledger.frozenRuntimeIdentity());
        try {
            var parts = new LinkedHashMap<String, Object>(); parts.put("runtime", runtimeIdentity);
            for (var type : List.of(TaskContractProjectionVerifier.class, ContractQualificationTrace.class, Rule.class, Rejection.class, ContextRejection.class,
                    VerificationReadiness.class, ContractQualificationChecks.class)) {
                try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                    if (input == null) throw new IOException("loaded checker bytes unavailable");
                    parts.put(type.getName(), TaskContractArtifactWriter.sha256(input.readAllBytes()));
                }
            }
            version = ContractQualificationChecks.identity(parts);
        } catch (IOException unavailable) { throw new IllegalStateException("cannot measure qualification rule identity", unavailable); }
    }
    boolean rejects(Rule rule, boolean rejected, Map<String, Object> values, Path... refs) {
        var inputs = new ArrayList<InputReference>(); boolean comparable = !values.isEmpty();
        for (var path : refs) {
            var ref = observedFiles.get(path.toAbsolutePath().normalize());
            if (ref == null) comparable = false; else inputs.add(ref);
        }
        checks.put(rule.id, new Check(rule.id, version, rejected ? Result.FAILED : Result.PASSED,
                comparable ? "" : "input bytes were not fully observed", inputs, values, comparable));
        return rejected;
    }
    void deferred(Rule rule, String why) {
        if (!checks.containsKey(rule.id)) checks.put(rule.id, new Check(rule.id, version, Result.NOT_CHECKED, why, List.of(), Map.of(), false));
    }
    Check failed(Rule rule) {
        var check = checks.values().stream().filter(c -> c.ruleId().equals(rule.id) && c.result() == Result.FAILED).reduce((a, b) -> b).orElse(null);
        if (check != null) return check;
        return new Check(rule.id, version, Result.FAILED,
                "read/delegated inputs unavailable; this does not prove a contradictory check", List.of(), Map.of(), false);
    }
    TaskContractProjectionVerifier.Failure failure(Rule rule, String reason, String message) {
        return new TaskContractProjectionVerifier.Failure(reason, message, failed(rule));
    }
    TaskContractProjectionVerifier.Failure failure(Rule rule, String reason, Exception cause) {
        var actual = cause instanceof NamedRejection rejection ? rejection.check() : failed(rule);
        return new TaskContractProjectionVerifier.Failure(reason, cause.getMessage(), actual);
    }
    IOException rejection(TaskContractProjectionVerifier.Failure failure) { return new Rejection(failure.reason() + ": " + failure.message(), failure.check()); }
    IOException rejection(Rule rule, String message) { return new Rejection(message, failed(rule)); }
    private interface NamedRejection { Check check(); }
    private static final class Rejection extends IOException implements NamedRejection {
        final Check check;
        Rejection(String message, Check check) { super(message); this.check = check; }
        public Check check() { return check; }
    }

    IllegalArgumentException contextRejection(Rule rule, String message, Throwable cause) {
        return new ContextRejection(message, cause, failed(rule));
    }
    private static final class ContextRejection extends IllegalArgumentException implements NamedRejection {
        final Check check;
        ContextRejection(String message, Throwable cause, Check check) { super(message, cause); this.check = check; }
        public Check check() { return check; }
    }

    static ContinuationFailure continuation(TaskHandler.TaskContext context, String code, Check failed, String consumer) {
        var trace = new ContractQualificationTrace(context.ledger(), context.workspaceRoot());
        var paths = new dev.dual.ledger.file.WorkstreamPaths(context.workspaceRoot(), new dev.dual.core.id.DualId(context.workstreamId()));
        String status = "DIAGNOSTIC_INSUFFICIENT", why = "source/check evidence unavailable";
        dev.dual.ledger.file.ContractQualificationRecovery.Source source = null;
        Map<String, Object> targetTransition = Map.of();
        try {
            var pairing = context.ledger().inspect(paths, (scoped, files) -> {
                var target = scoped.loadNode(paths, context.instance().nodeInstanceId());
                if (!target.inputRefs().equals(context.instance().inputRefs())
                        || !target.nodeDefinitionId().equals(context.definition().id()))
                    throw new IOException("target input occurrence changed");
                return new Pairing(dev.dual.ledger.file.ContractQualificationRecovery.resolvingSource(paths, target).orElse(null), target.inputTransition());
            });
            source = pairing.source(); targetTransition = pairing.transition();
            if (source == null) { status = "NO_RESOLVING_SOURCE"; why = "target has no resolving contract decision"; }
            else if (failed != null && source.checks().runtimeIdentitySha256().equals(trace.runtimeIdentity)) {
                status = "PAIRED"; why = "";
            }
        } catch (IOException | RuntimeException invalid) { why = "cannot verify resolving decision: " + invalid.getMessage(); }
        return new ContinuationFailure(1, ContractQualificationChecks.FAMILY, status,
                source == null ? "" : source.ref(), source == null ? "" : source.sha256(), source == null ? "" : "approved",
                source == null ? "" : source.node().nodeInstanceId(), context.instance().nodeInstanceId(),
                source == null ? List.of() : source.checks().subjectRefs(), context.instance().inputRefs(), targetTransition,
                consumer, String.valueOf(context.definition().config().getOrDefault("required_contract_phase", "ROUTED_ACCEPTANCE")),
                code, trace.runtimeIdentity, failed, why);
    }
    private record Pairing(dev.dual.ledger.file.ContractQualificationRecovery.Source source, Map<String, Object> transition) {}

    void checkReadiness(Map<String, Object> actualObservations, boolean rejected) {
        // The same validator predicate is called on candidate and current bindings. Its operands,
        // including actual validity/dependency observations, distinguish those two applications.
        var rule = Rule.CANDIDATE_READINESS;
        checks.put(rule.id, new Check(rule.id, version, rejected ? Result.FAILED : Result.PASSED,
                "", List.of(), actualObservations, actualObservations.get("readiness") instanceof Map<?, ?> observed
                        && Boolean.TRUE.equals(observed.get("fully_observed"))));
    }
    byte[] readBytes(LedgerSession files, Path path) throws IOException {
        var bytes = files.readBytes(path);
        var absolute = path.toAbsolutePath().normalize();
        observedFiles.put(absolute, new InputReference("FILE", root.relativize(absolute).toString(), TaskContractArtifactWriter.sha256(bytes), "", -1));
        return bytes;
    }
    <T> T read(LedgerSession files, Path path, Class<T> type) throws IOException {
        return TaskContractArtifactWriter.mapper().readValue(readBytes(files, path), type);
    }
    boolean exists(LedgerSession files, Path path) throws IOException {
        var exists = files.exists(path);
        if (!exists) {
            var absolute = path.toAbsolutePath().normalize();
            observedFiles.put(absolute, new InputReference("MISSING", root.relativize(absolute).toString(), "", "", -1));
        }
        return exists;
    }
    InputReference route(Path path, String line, String eventId, long sequence) {
        return new InputReference("ROUTE_EVENT", root.relativize(path.toAbsolutePath().normalize()).toString(),
                TaskContractArtifactWriter.sha256(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)), eventId, sequence);
    }
    boolean eventCheck(Rule rule, boolean rejected, Map<String, Object> values, InputReference entry) {
        checks.put(rule.id + ":" + entry.eventId(), new Check(rule.id, version, rejected ? Result.FAILED : Result.PASSED, "", List.of(entry), values, true));
        return rejected;
    }
    Decision decision(String gate, String source, List<String> subjects) {
        return new Decision(1, ContractQualificationChecks.FAMILY, Coverage.COVERED, gate, source, subjects,
                runtimeIdentity, List.copyOf(checks.values()));
    }
}
