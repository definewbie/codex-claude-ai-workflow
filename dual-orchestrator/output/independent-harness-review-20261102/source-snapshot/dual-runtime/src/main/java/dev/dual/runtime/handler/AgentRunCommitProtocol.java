package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeResult;
import dev.dual.ledger.file.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fixed run records and their binding to the existing node-execution claim. No external replay. */
public final class AgentRunCommitProtocol {
    private static final AtomicJsonWriter WRITER = new AtomicJsonWriter();
    private AgentRunCommitProtocol() {}

    public record Prepared(NodeExecutionDocument execution, Map<String, String> reference, Path path) {}

    /** Program facts share the run writer and cannot replace a prior observation or a completed outcome. */
    static dev.dual.core.contract.TaskContract.Reference recordObservation(Path root, String workstream,
                                                                          Prepared prepared, Object observation) throws IOException {
        var run = prepared.path().getParent().getParent();
        var path = run.resolve("outputs/verification-observations.json");
        try (var ignored = lock(run, workstream)) {
            if (!hash(Files.readAllBytes(prepared.path())).equals(prepared.reference().get("sha256"))
                    || !Files.isRegularFile(intentPath(prepared.path())))
                throw new IOException("observation lacks its fixed preparation and execution intent");
            if (Files.exists(completionPath(prepared.path())) || Files.exists(run.resolve("outputs/execution-outcome.json")))
                throw new IOException("completed execution cannot acquire new observations");
            immutable(path, observation);
            return new dev.dual.core.contract.TaskContract.Reference(reference(root, path), hash(Files.readAllBytes(path)));
        }
    }

    public static Prepared prepare(TaskHandler.TaskContext context, Path runDirectory, List<Path> inputs,
                                   Map<String, Object> binding) throws IOException {
        var paths = paths(context);
        NodeExecutionDocument execution;
        try (var read = context.ledger().readSession(paths)) {
            var node = read.ledger().loadNode(paths, context.instance().nodeInstanceId());
            execution = read.ledger().latestNodeExecution(paths, node).orElseThrow(() -> new IOException("run has no execution claim"));
            read.ledger().verifyExecutionClaim(paths, execution);
        }
        var run = runDirectory.toAbsolutePath().normalize();
        if (!run.getParent().equals(paths.runsDir().toAbsolutePath().normalize()))
            throw new IOException("run directory is outside this workstream");
        var inputHashes = new java.util.TreeMap<String, String>();
        for (var input : inputs) inputHashes.put(reference(paths.workspaceRoot(), input), hash(Files.readAllBytes(input)));
        for (var ref : context.instance().inputRefs()) {
            var path = inside(paths.workspaceRoot(), ref);
            inputHashes.put(ref, hash(Files.readAllBytes(path)));
        }
        var document = new LinkedHashMap<String, Object>();
        document.put("schema", "dual.run.prepared.v1");
        document.put("workstream", context.workstreamId());
        document.put("node", execution.nodeInstanceId());
        document.put("task", execution.agentTaskId());
        document.put("execution", execution.nodeExecutionId());
        document.put("claim", execution.result().get("claim"));
        document.put("runtime_identity", context.ledger().frozenRuntimeIdentity());
        document.put("inputs", inputHashes);
        document.put("binding", binding);
        var path = run.resolve("outputs").resolve(execution.nodeExecutionId() + "-prepared.json");
        Map<String, String> ref;
        try (var ignored = lock(run, context.workstreamId())) {
            immutable(path, document);
            ref = Map.of("ref", reference(paths.workspaceRoot(), path), "sha256", hash(Files.readAllBytes(path)));
        }
        // Never hold R while acquiring L. Orphan R01 bytes have no execution authority.
        try (var write = context.ledger().writeSession(paths, "bind prepared run", execution.nodeExecutionId())) {
            write.ledger().bindPreparedRun(paths, execution, ref);
            write.commit();
        }
        return new Prepared(execution, ref, path);
    }

    /** Repeat qualification against the current claim and fixed contract, without producing new inputs. */
    public static void qualifyDispatch(TaskHandler.TaskContext context, Prepared prepared) throws IOException {
        try {
            VerificationEvidence.verifyAcceptanceInputs(context);
        } catch (IOException invalid) {
            throw new AgentRunGovernanceContext.QualificationException("VERIFICATION_EVIDENCE_INVALID", invalid.getMessage());
        }
        var paths = paths(context);
        context.ledger().inspect(paths, (scoped, files) -> {
            scoped.verifyExecutionClaim(paths, prepared.execution());
            var fixed = WRITER.readTree(prepared.path());
            if (!hash(Files.readAllBytes(prepared.path())).equals(prepared.reference().get("sha256")))
                throw new IOException("prepared identity changed");
            verifyInputs(paths.workspaceRoot(), fixed.path("inputs"));
            ContractRevisionBinding.verifyDispatch(new TaskHandler.TaskContext(context.workspaceRoot(),
                    context.workstreamId(),context.definition(),context.instance(),scoped),fixed);
            if (TaskContractProjectionVerifier.requiresContract(context.definition())) {
                var resolution = TaskContractProjectionVerifier.resolve(scoped, paths.workspaceRoot(), context.workstreamId(),
                        TaskContractProjectionVerifier.requiredPhase(context.definition()));
                if (resolution.failure() != null)
                    throw AgentRunGovernanceContext.qualificationFailure(new TaskHandler.TaskContext(context.workspaceRoot(),
                            context.workstreamId(), context.definition(), context.instance(), scoped),
                            resolution.failure(), "AgentRunCommitProtocol.qualifyDispatch");
                var contract = resolution.qualified().orElseThrow(() -> new IOException("approved contract is absent"));
                if (!contract.contractRef().equals(fixed.path("binding").path("contract_ref").asText())
                        || !contract.sha256().equals(fixed.path("binding").path("contract_sha256").asText()))
                    throw new IOException("qualified contract differs from prepared contract");
            }
            return null;
        });
    }

    /** Only a live executor may create the first durable intent. Existing intent never grants another send. */
    public static void beginExternalCall(TaskHandler.TaskContext context, Prepared prepared) throws IOException {
        var paths = paths(context);
        try (var read = context.ledger().readSession(paths)) {
            var execution = read.ledger().verifyExecutionClaim(paths, prepared.execution());
            if (!prepared.reference().equals(execution.result().get("prepared_run")))
                throw new IOException("prepared run is not the current claim binding");
        }
        var run = prepared.path().getParent().getParent();
        try (var ignored = lock(run, context.workstreamId())) {
            var bytes = Files.readAllBytes(prepared.path());
            if (!hash(bytes).equals(prepared.reference().get("sha256"))) throw new IOException("prepared run bytes changed");
            var document = WRITER.objectMapper().readTree(bytes);
            verifyInputs(paths.workspaceRoot(), document.path("inputs"));
            var owner = document.path("claim");
            var actual = ProcessHandle.current();
            if (actual.pid() != owner.path("executor_pid").asLong()
                    || !actual.info().startInstant().orElseThrow().toString().equals(owner.path("executor_started_at").asText()))
                throw new IOException("preparation belongs to another executor process");
            var intent = intentPath(prepared.path());
            if (Files.exists(run.resolve("outputs").resolve(prepared.execution().nodeExecutionId() + "-input-rejection.json")))
                throw new IOException("fixed input rejection cannot acquire external intent");
            if (Files.exists(intent)) throw new IOException("external intent already exists; no automatic resend");
            immutable(intent, Map.of("schema", "dual.run.intent.v1", "prepared", prepared.reference()));
        }
    }

    /** Each native command has a durable intent; telemetry alone never grants dispatch. */
    public static void commandEvent(TaskHandler.TaskContext context, Prepared prepared, String kind,
                                    Map<String, Object> details) throws IOException {
        if (!kind.equals("command_intent") && !kind.equals("command_result")) return;
        var id = String.valueOf(details.get("command_id"));
        if (!id.matches("[a-f0-9-]{36}")) throw new IOException("native command identity missing");
        var cleanup = Boolean.TRUE.equals(details.get("cleanup"));
        if (kind.equals("command_intent") && !cleanup) qualifyDispatch(context, prepared);
        var run = prepared.path().getParent().getParent();
        try (var ignored = lock(run, context.workstreamId())) {
            if (!hash(Files.readAllBytes(prepared.path())).equals(prepared.reference().get("sha256")))
                throw new IOException("prepared bytes changed before native command");
            if (kind.equals("command_intent")) {
                var fixed = WRITER.readTree(prepared.path());
                verifyNativeCommand(fixed.path("binding"), details);
                if (!cleanup) {
                    var process = ProcessHandle.current();
                    var owner = fixed.path("claim");
                    if (Files.exists(releasePath(prepared.path())) || Files.exists(completionPath(prepared.path()))
                            || Files.exists(prepared.path().getParent().resolve("execution-outcome.json"))
                            || process.pid() != owner.path("executor_pid").asLong()
                            || !process.info().startInstant().orElseThrow().toString().equals(owner.path("executor_started_at").asText()))
                        throw new IOException("recorded external intent does not grant a new executor send authority");
                }
            }
            if (!Files.isRegularFile(intentPath(prepared.path()))) throw new IOException("native command lacks attempt intent");
            var prefix = prepared.execution().nodeExecutionId() + "-command-" + id;
            var intent = run.resolve("outputs").resolve(prefix + "-intent.json");
            if (kind.equals("command_intent") && Files.exists(intent)) throw new IOException("native command intent cannot be sent twice");
            if (kind.equals("command_result") && !Files.isRegularFile(intent)) throw new IOException("native command result lacks intent");
            long ordinal;
            if (kind.equals("command_intent")) {
                ordinal = 1;
                try (var entries = Files.list(run.resolve("outputs"))) {
                    for (var entry : entries.filter(p -> p.getFileName().toString().startsWith(prepared.execution().nodeExecutionId() + "-command-")
                            && p.getFileName().toString().endsWith("-intent.json")).toList())
                        ordinal = Math.max(ordinal, WRITER.readTree(entry).path("ordinal").asLong() + 1);
                }
            } else {
                var declared = WRITER.readTree(intent);
                var actual = WRITER.objectMapper().valueToTree(details).deepCopy();
                ((com.fasterxml.jackson.databind.node.ObjectNode) actual).remove("observation");
                if (!actual.equals(declared.path("details"))) throw new IOException("native command result identity differs from intent");
                ordinal = declared.path("ordinal").asLong();
            }
            immutable(run.resolve("outputs").resolve(prefix + (kind.equals("command_intent") ? "-intent.json" : "-result.json")),
                    Map.of("schema", "dual.run.command.v1", "prepared", prepared.reference(), "kind", kind, "ordinal", ordinal, "details", details));
        }
    }

    private static void verifyNativeCommand(JsonNode binding, Map<String, Object> details) throws IOException {
        var expected = binding.path("native_invocation");
        var actual = WRITER.objectMapper().valueToTree(details.get("invocation"));
        if (!expected.isObject() || !expected.path("schema").asText().equals("dual.acpx.invocation.v1")
                || !expected.equals(actual) || !expected.path("session").asText().equals(details.get("session")))
            throw new IOException("native invocation differs from R01; do not borrow the old session or retry an unknown call");
        var command = WRITER.objectMapper().valueToTree(details.get("command"));
        if (!command.isArray() || command.isEmpty() || !command.path(0).equals(expected.path("executable")))
            throw new IOException("native command executable differs from R01");
        if (Boolean.TRUE.equals(details.get("cleanup"))) {
            var executable = expected.path("executable").asText();
            var agent = expected.path("agent").asText();
            var session = expected.path("session").asText();
            var allowed = switch (String.valueOf(details.get("purpose"))) {
                case "session_cancel" -> List.of(executable, agent, "cancel", "-s", session);
                case "session_close" -> List.of(executable, agent, "sessions", "close", session);
                default -> List.of();
            };
            if (allowed.isEmpty() || !WRITER.objectMapper().valueToTree(allowed).equals(command))
                throw new IOException("cleanup cannot grant native prompt authority");
        } else {
            var options = WRITER.objectMapper().valueToTree(details.get("request_options"));
            var allowed = binding.path("allowed_request_options");
            if (!options.isArray() || !allowed.isArray() || java.util.stream.StreamSupport.stream(allowed.spliterator(), false)
                    .noneMatch(options::equals)) throw new IOException("native permission configuration differs from R01");
        }
    }

    /** A stopped Java invocation is distinct from native session quiescence. It only releases its local claim. */
    public static void releaseExecutor(WorkflowFileLedger ledger, WorkstreamPaths paths, NodeExecutionDocument expected) throws IOException {
        NodeExecutionDocument actual;
        try (var read = ledger.readSession(paths)) {
            actual = read.files().read(paths.nodeExecutionJson(expected.nodeExecutionId()), NodeExecutionDocument.class);
        }
        if (!hasPrepared(actual)) return;
        var prepared = preparedPath(paths, actual);
        var owner = WRITER.readTree(prepared).path("claim");
        var process = ProcessHandle.current();
        if (owner.path("executor_pid").asLong() != process.pid()
                || !owner.path("executor_started_at").asText().equals(process.info().startInstant().orElseThrow().toString()))
            throw new IOException("only the original executor may release its invocation");
        try (var ignored = lock(prepared.getParent().getParent(), paths.workstreamId().value())) {
            immutable(releasePath(prepared), Map.of("schema", "dual.run.executor-release.v1", "prepared", actual.result().get("prepared_run"),
                    "execution", actual.nodeExecutionId(), "claim", actual.result().get("claim")));
        }
    }

    public static boolean executorOwnsInvocation(WorkstreamPaths paths, NodeExecutionDocument execution) throws IOException {
        var prepared = preparedPath(paths, execution);
        if (Files.exists(releasePath(prepared))) {
            var release = WRITER.readTree(releasePath(prepared));
            if (!release.path("schema").asText().equals("dual.run.executor-release.v1")
                    || !release.path("execution").asText().equals(execution.nodeExecutionId())
                    || !release.path("prepared").equals(WRITER.objectMapper().valueToTree(execution.result().get("prepared_run")))
                    || !release.path("claim").equals(WRITER.objectMapper().valueToTree(execution.result().get("claim"))))
                throw new IOException("invalid executor release");
            return false;
        }
        return executorAlive(execution);
    }

    /** R03 fixes the validated completion, actual run observations and every published output identity. */
    public static NodeResult complete(Path root, WorkflowFileLedger ledger, WorkstreamPaths paths,
                                      NodeExecutionDocument expected, NodeResult result) throws IOException {
        if (AgentExecutionResultFacts.inputPreparationFailed(result)) {
            if (!result.result().containsKey("continuation_failure")) return result;
            NodeExecutionDocument claim;
            try (var read = ledger.readSession(paths)) { claim = read.ledger().verifyExecutionClaim(paths, expected); }
            var diagnostic = ContractQualificationChecks.continuation(result.result().get("continuation_failure"));
            if (!diagnostic.targetNodeInstanceId().equals(claim.nodeInstanceId())
                    || !diagnostic.runtimeIdentitySha256().equals(ContractQualificationChecks.identity(ledger.frozenRuntimeIdentity())))
                throw new IOException("preparation diagnostic belongs to a different target/runtime");
            var completion = inputRejectionPath(paths, claim);
            var run = completion.getParent().getParent();
            try (var ignored = lock(run, paths.workstreamId().value())) {
                if (hasPrepared(claim) && Files.exists(intentPath(preparedPath(paths, claim))))
                    throw new IOException("preparation rejection cannot assert absence after an external intent");
                immutable(completion, Map.of("schema", "dual.run.input-rejection.v1", "execution", claim.nodeExecutionId(),
                        "node", claim.nodeInstanceId(), "task", claim.agentTaskId(), "claim", claim.result().get("claim"),
                        "runtime_identity", ledger.frozenRuntimeIdentity(), "result", result));
                return withCompletion(root, result, completion);
            }
        }
        final Map<String, String> preparedRef;
        try (var read = ledger.readSession(paths)) {
            // Preserve actual completion even after cancellation; only the L03 consumer grants routing.
            var execution = read.files().read(paths.nodeExecutionJson(expected.nodeExecutionId()), NodeExecutionDocument.class);
            if (!execution.nodeInstanceId().equals(expected.nodeInstanceId())
                    || !execution.agentTaskId().equals(expected.agentTaskId())) throw new IOException("foreign execution");
            if (!(execution.result().get("prepared_run") instanceof Map<?, ?> value)) {
                if (AgentExecutionResultFacts.inputPreparationFailed(result)
                        || !"acpx.agent_run".equals(execution.handlerType())
                        || Boolean.TRUE.equals(result.result().get("prepared"))) return result;
                throw new IOException("external result has no prepared claim binding");
            }
            preparedRef = stringReference(value);
        }
        var preparedPath = inside(root, preparedRef.get("ref"));
        var run = preparedPath.getParent().getParent();
        try (var ignored = lock(run, paths.workstreamId().value())) {
            if (!hash(Files.readAllBytes(preparedPath)).equals(preparedRef.get("sha256"))) throw new IOException("prepared run changed");
            if (!Files.isRegularFile(intentPath(preparedPath))) throw new IOException("external result has no durable intent");
            var input = WRITER.readTree(preparedPath);
            if (!expected.nodeExecutionId().equals(input.path("execution").asText())) throw new IOException("foreign execution result");
            var outputHashes = new java.util.TreeMap<String, String>();
            for (var ref : result.outputRefs()) outputHashes.put(ref, hash(Files.readAllBytes(inside(root, ref))));
            var metadata = WRITER.readTree(run.resolve("outputs/execution-outcome.json")).path("run_observations");
            if (metadata.path("processResult").isMissingNode() || metadata.path("executionOutcome").isMissingNode())
                throw new IOException("run lacks typed process and execution outcome");
            if (!WRITER.objectMapper().readTree(WRITER.objectMapper().writeValueAsBytes(result.result().get("agent_execution_outcome")))
                    .equals(metadata.path("executionOutcome"))) throw new IOException("result contradicts the fixed outcome");
            var completion = completionPath(preparedPath);
            immutable(completion, Map.of("schema", "dual.run.completion.v1", "prepared", preparedRef,
                    "execution", expected.nodeExecutionId(), "result", result,
                    "output_hashes", outputHashes, "run_observations", metadata));
            return withCompletion(root, result, completion);
        }
    }

    /** L03 consumer reads the fixed record; mutable run.json is not a completion authority. */
    public static NodeResult consume(WorkflowFileLedger scoped, WorkstreamPaths paths,
                                     NodeExecutionDocument expected) throws IOException {
        var execution = scoped.verifyExecutionClaim(paths, expected);
        if (hasInputRejection(paths, execution)) return readCompletion(paths, execution);
        verifyInputs(paths.workspaceRoot(), WRITER.readTree(preparedPath(paths, execution)).path("inputs"));
        return readCompletion(paths, execution);
    }

    public static NodeResult readCompletion(WorkstreamPaths paths, NodeExecutionDocument execution) throws IOException {
        if (hasInputRejection(paths, execution)) {
            var path = inputRejectionPath(paths, execution);
            if (hasPrepared(execution) && Files.exists(intentPath(preparedPath(paths, execution))))
                throw new IOException("input rejection conflicts with external intent; absence of execution is not proven");
            var record = WRITER.readTree(path);
            if (!"dual.run.input-rejection.v1".equals(record.path("schema").asText())
                    || !execution.nodeExecutionId().equals(record.path("execution").asText())
                    || !execution.nodeInstanceId().equals(record.path("node").asText())
                    || !execution.agentTaskId().equals(record.path("task").asText())
                    || !WRITER.objectMapper().valueToTree(execution.result().get("claim")).equals(record.path("claim")))
                throw new IOException("input rejection belongs to a different execution claim");
            var result = WRITER.objectMapper().treeToValue(record.path("result"), NodeResult.class);
            if (!AgentExecutionResultFacts.inputPreparationFailed(result) || !result.result().containsKey("continuation_failure"))
                throw new IOException("input rejection lacks its closed machine-owned diagnostic");
            var diagnostic = ContractQualificationChecks.continuation(result.result().get("continuation_failure"));
            if (!diagnostic.targetNodeInstanceId().equals(execution.nodeInstanceId())
                    || !diagnostic.runtimeIdentitySha256().equals(ContractQualificationChecks.identity(
                            WRITER.objectMapper().convertValue(record.path("runtime_identity"), Map.class))))
                throw new IOException("input rejection runtime/target identity differs");
            return withCompletion(paths.workspaceRoot(), result, path);
        }
        if (!(execution.result().get("prepared_run") instanceof Map<?, ?> value)) throw new IOException("missing prepared binding");
        var ref = stringReference(value);
        var prepared = inside(paths.workspaceRoot(), ref.get("ref"));
        if (!hash(Files.readAllBytes(prepared)).equals(ref.get("sha256"))) throw new IOException("prepared bytes changed");
        verifyInputs(paths.workspaceRoot(), WRITER.readTree(prepared).path("inputs"));
        var completion = completionPath(prepared);
        var doc = WRITER.readTree(completion);
        if (!"dual.run.completion.v1".equals(doc.path("schema").asText())
                || !execution.nodeExecutionId().equals(doc.path("execution").asText())
                || !WRITER.objectMapper().valueToTree(ref).equals(doc.path("prepared")))
            throw new IOException("completion identity mismatch");
        verifyInputs(paths.workspaceRoot(), doc.path("output_hashes"));
        var result = WRITER.objectMapper().treeToValue(doc.path("result"), NodeResult.class);
        return withCompletion(paths.workspaceRoot(), result, completion);
    }

    public static Prepared boundPreparation(WorkstreamPaths paths, NodeExecutionDocument execution) throws IOException {
        var path = preparedPath(paths, execution);
        return new Prepared(execution, stringReference((Map<?, ?>) execution.result().get("prepared_run")), path);
    }

    static void verifyPreparedInputs(Path root, com.fasterxml.jackson.databind.JsonNode prepared) throws IOException {
        verifyInputs(root,prepared.path("inputs"));
    }

    public static boolean hasPrepared(NodeExecutionDocument execution) { return execution.result().containsKey("prepared_run"); }

    public static boolean hasInputRejection(WorkstreamPaths paths, NodeExecutionDocument execution) throws IOException {
        return Files.isRegularFile(inputRejectionPath(paths, execution));
    }
    private static Path inputRejectionPath(WorkstreamPaths paths, NodeExecutionDocument execution) throws IOException {
        var run = hasPrepared(execution) ? preparedPath(paths, execution).getParent().getParent()
                : paths.runsDir().resolve(execution.nodeExecutionId());
        return run.resolve("outputs").resolve(execution.nodeExecutionId() + "-input-rejection.json");
    }

    public enum Observation { PREPARED, EXTERNAL_UNKNOWN, OUTCOME_RECORDED, COMPLETED }

    public static Observation observe(WorkstreamPaths paths, NodeExecutionDocument execution) throws IOException {
        if (hasInputRejection(paths, execution)) return Observation.COMPLETED;
        var prepared = preparedPath(paths, execution);
        if (Files.isRegularFile(completionPath(prepared))) return Observation.COMPLETED;
        if (Files.isRegularFile(prepared.getParent().resolve("execution-outcome.json"))) return Observation.OUTCOME_RECORDED;
        return Files.exists(intentPath(prepared)) ? Observation.EXTERNAL_UNKNOWN : Observation.PREPARED;
    }

    public static NodeResult recordedCandidate(WorkstreamPaths paths, NodeExecutionDocument execution) throws IOException {
        var prepared = preparedPath(paths, execution);
        var record = WRITER.readTree(prepared.getParent().resolve("execution-outcome.json"));
        if (!"dual.run.outcome.v1".equals(record.path("schema").asText())) throw new IOException("unknown outcome schema");
        verifyInputs(paths.workspaceRoot(), record.path("output_hashes"));
        var candidate = WRITER.objectMapper().treeToValue(record.path("candidate"), NodeResult.class);
        var metadataRef = candidate.result().get("run_metadata");
        if (!(metadataRef instanceof String ref) || !inside(paths.workspaceRoot(), ref).equals(prepared.getParent().getParent().resolve("run.json")))
            throw new IOException("candidate does not belong to prepared run");
        return candidate;
    }

    public static boolean executorAlive(NodeExecutionDocument execution) throws IOException {
        if (!(execution.result().get("claim") instanceof Map<?, ?> claim)
                || !(claim.get("executor_pid") instanceof Number pid)
                || !(claim.get("executor_started_at") instanceof String start)) throw new IOException("missing executor identity");
        var process = ProcessHandle.of(pid.longValue());
        return process.isPresent() && process.get().isAlive()
                && process.get().info().startInstant().orElseThrow(() -> new IOException("executor start identity unavailable"))
                    .toString().equals(start);
    }

    private static Path preparedPath(WorkstreamPaths paths, NodeExecutionDocument execution) throws IOException {
        if (!(execution.result().get("prepared_run") instanceof Map<?, ?> value)) throw new IOException("missing prepared binding");
        var ref = stringReference(value);
        var path = inside(paths.workspaceRoot(), ref.get("ref"));
        if (!hash(Files.readAllBytes(path)).equals(ref.get("sha256"))) throw new IOException("prepared record changed");
        var prepared = WRITER.readTree(path);
        if (!"dual.run.prepared.v1".equals(prepared.path("schema").asText())
                || !execution.nodeExecutionId().equals(prepared.path("execution").asText())
                || !execution.nodeInstanceId().equals(prepared.path("node").asText())
                || !execution.agentTaskId().equals(prepared.path("task").asText())) throw new IOException("foreign preparation");
        return path;
    }

    static void recordPublication(Path root, NodeResult candidate, ArtifactContractValidator.Publication publication) throws IOException {
        if (!(candidate.result().get("run_metadata") instanceof String ref)) throw new IOException("publication has no program run identity");
        var run = inside(root, ref).getParent();
        try (var ignored = lock(run, publication.workstream())) {
            immutable(run.resolve("outputs/publication-plan.json"), Map.of("schema", "dual.run.publication.v1", "publication", publication));
        }
    }

    static ArtifactContractValidator.Publication recordedPublication(Path root, WorkstreamPaths paths,
                                                                      NodeExecutionDocument execution) throws IOException {
        var prepared = preparedPath(paths, execution);
        var outcome = WRITER.readTree(prepared.getParent().resolve("execution-outcome.json"));
        var ref = outcome.path("publication");
        if (ref.isMissingNode()) return null;
        var path = inside(root, ref.path("ref").asText());
        if (!path.equals(prepared.getParent().resolve("publication-plan.json"))
                || !hash(Files.readAllBytes(path)).equals(ref.path("sha256").asText())) throw new IOException("fixed publication changed");
        var plan = WRITER.readTree(path);
        if (!plan.path("schema").asText().equals("dual.run.publication.v1")) throw new IOException("publication schema mismatch");
        var publication = WRITER.objectMapper().treeToValue(plan.path("publication"), ArtifactContractValidator.Publication.class);
        if (!publication.workstream().equals(paths.workstreamId().value()) || !publication.node().equals(execution.nodeInstanceId()))
            throw new IOException("publication identity mismatch");
        return publication;
    }

    static dev.dual.core.execution.AgentExecutionOutcome recordedOutcome(Path root, Path runJson, NodeResult candidate) throws IOException {
        var path = runJson.getParent().resolve("outputs/execution-outcome.json");
        if (!Files.exists(path)) return null;
        var record = WRITER.readTree(path);
        if (!"dual.run.outcome.v1".equals(record.path("schema").asText())
                || !WRITER.objectMapper().readTree(WRITER.objectMapper().writeValueAsBytes(candidate)).equals(record.path("candidate")))
            throw new IOException("candidate differs from the fixed execution decision");
        verifyInputs(root, record.path("output_hashes"));
        return WRITER.objectMapper().treeToValue(record.path("outcome"), dev.dual.core.execution.AgentExecutionOutcome.class);
    }

    static void recordOutcome(Path root, Path runJson, NodeResult candidate,
                              dev.dual.core.execution.AgentExecutionOutcome outcome, JsonNode metadata) throws IOException {
        var run = runJson.getParent();
        var hashes = new java.util.TreeMap<String, String>();
        for (var ref : candidate.outputRefs()) hashes.put(ref, hash(Files.readAllBytes(inside(root, ref))));
        try (var ignored = lock(run, metadata.path("workstreamId").asText("unknown"))) {
            var record = new LinkedHashMap<String, Object>(Map.of("schema", "dual.run.outcome.v1",
                    "candidate", candidate, "outcome", outcome, "output_hashes", hashes, "run_observations", metadata));
            var publication = run.resolve("outputs/publication-plan.json");
            if (Files.isRegularFile(publication)) record.put("publication", Map.of("ref", reference(root, publication), "sha256", hash(Files.readAllBytes(publication))));
            immutable(run.resolve("outputs/execution-outcome.json"), record);
        }
    }

    private static NodeResult withCompletion(Path root, NodeResult result, Path completion) throws IOException {
        var facts = new LinkedHashMap<String, Object>(result.result());
        facts.put("completion_ref", reference(root, completion));
        facts.put("completion_sha256", hash(Files.readAllBytes(completion)));
        return new NodeResult(result.outcome(), result.status(), result.outputRefs(), facts, result.errorMessage());
    }

    private static void verifyInputs(Path root, JsonNode inputs) throws IOException {
        if (!inputs.isObject()) throw new IOException("missing content identities");
        var fields = inputs.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (!field.getValue().isTextual() || !hash(Files.readAllBytes(inside(root, field.getKey()))).equals(field.getValue().asText()))
                throw new IOException("run input/output changed: " + field.getKey());
        }
    }

    private static Map<String, String> stringReference(Map<?, ?> value) throws IOException {
        if (!(value.get("ref") instanceof String ref) || !(value.get("sha256") instanceof String sha)
                || !sha.matches("[a-f0-9]{64}")) throw new IOException("invalid fixed record reference");
        return Map.of("ref", ref, "sha256", sha);
    }

    static void immutable(Path path, Object document) throws IOException {
        var bytes = WRITER.objectMapper().writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsBytes(document);
        if (Files.exists(path)) {
            if (!java.util.Arrays.equals(bytes, Files.readAllBytes(path))) throw new IOException("different bytes at immutable run record: " + path);
            return;
        }
        WRITER.writeBytes(path, bytes);
    }

    private static Path releasePath(Path prepared) { return prepared.resolveSibling(prepared.getFileName().toString().replace("-prepared.json", "-released.json")); }
    private static Path intentPath(Path prepared) { return prepared.resolveSibling(prepared.getFileName().toString().replace("-prepared.json", "-intent.json")); }
    private static Path completionPath(Path prepared) { return prepared.resolveSibling(prepared.getFileName().toString().replace("-prepared.json", "-completion.json")); }
    static WorkstreamPaths paths(TaskHandler.TaskContext context) { return new WorkstreamPaths(context.workspaceRoot(), new DualId(context.workstreamId())); }
    static WorkstreamLock lock(Path run, String workstream) throws IOException { return WorkstreamLock.acquire(run, LockOwner.currentProcess(workstream, "run commit")); }
    static Path inside(Path root, String ref) throws IOException {
        var base = root.toAbsolutePath().normalize();
        var path = base.resolve(ref).normalize();
        if (Path.of(ref).isAbsolute() || !path.startsWith(base)) throw new IOException("run reference escapes workspace");
        if (Files.exists(path) && !path.toRealPath().startsWith(base.toRealPath())) throw new IOException("run reference escapes through symlink");
        return path;
    }
    static String reference(Path root, Path path) throws IOException {
        var ref = root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
        inside(root, ref);
        return ref;
    }
    static String hash(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
