package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.execution.AgentExecutionEvidence.ToolObservation;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeResult;
import dev.dual.ledger.file.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Program-owned execution observations and criterion evidence checks. Never executes a test or judges assertion prose. */
public final class VerificationEvidence {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    public static final String FACT = "verification_observations";
    private static final String FILE = "verification-observations.json";
    public record Baseline(TaskContract.Reference contract, EvidenceCollectionSource source) {}
    public record Observation(String schema, String workstream, String node, String execution, String run,
                              String agent, Map<String, String> prepared, Baseline baseline,
                              EvidenceCollectionSource afterSource, List<ToolObservation> tools, List<dev.dual.core.execution.AdapterSignal> permissionSignals) {}
    private VerificationEvidence() {}

    public static Baseline prepare(TaskHandler.TaskContext context) throws IOException {
        var qualified = TaskContractProjectionVerifier.resolve(context.ledger(), context.workspaceRoot(), context.workstreamId(),
                TaskContract.Phase.EXECUTION).qualified().orElseThrow(() -> new IOException("approved EXECUTION required for source capture"));
        return new Baseline(new TaskContract.Reference(qualified.contractRef(), qualified.sha256()),
                EvidenceCollectionSource.capture(context.workspaceRoot(), qualified.contract().scope().included().stream()
                        .map(TaskContract.ScopeEntry::path).toList()));
    }

    public static NodeResult record(TaskHandler.TaskContext context, AgentRunCommitProtocol.Prepared prepared,
                                    Baseline baseline, String agent, List<ToolObservation> observations, NodeResult result) throws IOException {
        if (baseline == null) return result;
        var runDir = prepared.path().getParent().getParent();
        var record = new Observation("dual.verification.observations.v1", context.workstreamId(),
                context.instance().nodeInstanceId(), prepared.execution().nodeExecutionId(), runDir.getFileName().toString(),
                agent, prepared.reference(), baseline,
                EvidenceCollectionSource.capture(context.workspaceRoot(), baseline.source().paths()), List.copyOf(observations), AgentRunMetadataFinalizer.resultAdapterSignals(result));
        var ref = AgentRunCommitProtocol.recordObservation(context.workspaceRoot(), context.workstreamId(), prepared, record);
        var facts = new LinkedHashMap<String, Object>(result.result()); facts.put(FACT, ref);
        var outputs = new ArrayList<>(result.outputRefs()); outputs.add(ref.ref());
        return new NodeResult(result.outcome(), result.status(), List.copyOf(outputs), facts, result.errorMessage());
    }

    static List<AgentResultEnvelope.VerificationCheck> checks(AgentResultEnvelope.AgentResult result) {
        return result instanceof AgentResultEnvelope.SelfTestResult self ? self.verificationChecks()
                : result instanceof AgentResultEnvelope.AcceptanceReviewResult acceptance ? acceptance.verificationChecks() : null;
    }

    /** Required routed evidence is current execution input, not a historical display. */
    static void verifyRequired(WorkflowFileLedger ledger, Path root, String ws, List<String> refs, Object configured) throws IOException {
        require(configured instanceof Map<?, ?>, "required evidence definition is missing");
        var required = (Map<?, ?>) configured;
        var matches = AgentEvidenceIndex.fromRefs(ledger, root, ws, refs, JSON).recent(refs.size()).stream()
                .map(AgentEvidenceIndex.Entry::document)
                .filter(d -> Objects.equals(required.get("node_id"), d.nodeId())
                        && Objects.equals(required.get("source_artifact_type"), d.sourceArtifactType())
                        && Objects.equals(required.get("decision"), d.decision()) && d.hasRequiredTypedPayload()).toList();
        require(matches.size() == 1, "exactly one routed required evidence occurrence is needed");
        verifyPublished(ledger, root, ws, matches.getFirst());
    }

    /** Recheck the completed context's actual inputs immediately before a verifier command. */
    static void verifyAcceptanceInputs(TaskHandler.TaskContext context) throws IOException {
        if (!"acceptance_review".equals(context.definition().config().get("expected_artifact"))) return;
        var root = context.workspaceRoot();
        var paths = new WorkstreamPaths(root, new DualId(context.workstreamId()));
        var prefix = ".agent/workstreams/" + context.workstreamId() + "/context/";
        var packages = context.instance().inputRefs().stream()
                .filter(r -> r.startsWith(prefix) && r.endsWith(".md") && !r.substring(prefix.length()).contains("/")).toList();
        require(packages.size() == 1, "acceptance requires one routed context occurrence");
        var ref = packages.getFirst();
        var id = ref.substring(prefix.length(), ref.length() - 3);
        var producer = context.ledger().loadNode(paths, id);
        require(producer.status() == dev.dual.core.workflow.NodeStatus.COMPLETED
                && "context.build_package".equals(producer.handlerType()) && producer.outputRefs().contains(ref)
                && producer.iterationId().equals(context.instance().iterationId()), "invalid acceptance context producer");
        var definition = context.ledger().loadDefinition(paths).node(producer.nodeDefinitionId());
        require("acceptance".equals(definition.config().get("profile"))
                && context.definition().id().equals(definition.config().get("target_node")), "wrong acceptance context target");
        verifyRequired(context.ledger(), root, context.workstreamId(), producer.inputRefs(), definition.config().get("required_agent_evidence"));
    }

    static void verifyPublished(WorkflowFileLedger ledger, Path root, String ws, AgentOutputContractDocument document) throws IOException {
        if (!("self_test_report".equals(document.sourceArtifactType()) && "SELF_TEST_COMPLETE".equals(document.decision())
                || "acceptance_review".equals(document.sourceArtifactType()) && accepted(document.decision()))) return;
        var paths = new WorkstreamPaths(root, new DualId(ws));
        var fixed = ledger.inspect(paths, (snapshot, files) -> {
            var node = snapshot.loadNode(paths, document.nodeInstanceId());
            var execution = snapshot.latestNodeExecution(paths, node).orElseThrow(() -> new IOException("verification lacks its execution"));
            var result = AgentRunCommitProtocol.readCompletion(paths, execution);
            require(result.outputRefs().contains(document.sourceOutputRef()), "verification source is not in the fixed completion");
            return result;
        });
        var parsed = new AgentResultEnvelopeParser().parse(Files.readString(root.resolve(document.sourceOutputRef())));
        require(parsed.valid() && document.decision().equals(parsed.envelope().result().decision()), "invalid published verification result");
        verify(ledger, root, ws, document.nodeInstanceId(), fixed, parsed.envelope().result());
    }

    static void verifyPublication(WorkflowFileLedger ledger, Path root, String ws, String node,
                                  NodeResult execution, TaskContract.Reference candidate) throws IOException {
        var parsed = new AgentResultEnvelopeParser().parse(new String(read(root, candidate), java.nio.charset.StandardCharsets.UTF_8));
        require(parsed.valid(), "fixed publication candidate is invalid");
        verify(ledger, root, ws, node, execution, parsed.envelope().result());
    }

    /** Called before publication, again by completion/lineage consumers for the same approved requirements. */
    static void verify(WorkflowFileLedger ledger, Path root, String ws, String node, NodeResult executionResult,
                       AgentResultEnvelope.AgentResult result) throws IOException {
        boolean success = result instanceof AgentResultEnvelope.SelfTestResult && "SELF_TEST_COMPLETE".equals(result.decision())
                || result instanceof AgentResultEnvelope.AcceptanceReviewResult && accepted(result.decision());
        if (!success) return;
        var resolution = TaskContractProjectionVerifier.resolve(ledger, root, ws, TaskContract.Phase.EXECUTION);
        var qualified = resolution.qualified().orElseThrow(() -> new IOException("verification requires the approved EXECUTION contract"));
        var checks = checks(result);
        require(checks != null && checks.size() == qualified.contract().verificationBindings().size(),
                "successful verification requires one evidence check for each approved criterion");
        var seen = new HashSet<String>();
        var observations = new LinkedHashMap<String, Observation>();
        for (var check : checks) {
            require(check != null && seen.add(check.criterionId()) && check.assertion() != null && !check.assertion().isBlank(),
                    "missing/duplicate criterion assertion");
            var binding = qualified.contract().verificationBindings().stream().filter(b -> b.criterionId().equals(check.criterionId()))
                    .findFirst().orElseThrow(() -> new IOException("unapproved verification criterion"));
            require(dev.dual.core.contract.TaskContractValidator.verificationEvidenceErrors(binding).isEmpty(), "invalid evidence requirements");
            require(check.materialRefs() != null && check.toolCallIds() != null, "evidence selectors must be explicit");
            if (binding.evidenceKind() == TaskContract.EvidenceKind.OPERATOR_DECLARATION_WITH_MATERIAL) {
                var refs = EvidenceCollection.requiredReferences(ledger, root, ws, node);
                require(!refs.isEmpty() && refs.getFirst().equals(check.executionRef()),
                        "operator assertion requires the bound, verified external submission");
                require(!check.materialRefs().isEmpty() && check.materialRefs().stream().allMatch(r -> r != null && refs.subList(1, refs.size()).contains(r.ref())),
                        "operator assertion must reference the submitted materials");
                require(check.toolCallIds().isEmpty(), "operator declaration cannot claim program tool calls");
            } else {
                var evidence = observations.get(check.executionRef());
                if (evidence == null) {
                    evidence = load(ledger, root, ws, node, executionResult, check.executionRef());
                    observations.put(check.executionRef(), evidence);
                }
                require(evidence.baseline().contract().equals(new TaskContract.Reference(qualified.contractRef(), qualified.sha256())),
                        "tool evidence belongs to a different approved contract");
                require(evidence.baseline().source().equals(evidence.afterSource()), "source changed during observed execution");
                // An empty selection means the fixed execution's complete tool-result set, so clients
                // need not copy opaque ACP IDs. It never means that no observed execution is required.
                var selected = check.toolCallIds().isEmpty() ? evidence.tools().stream()
                        .filter(ToolObservation::hasExecutionResult).map(ToolObservation::toolCallId).toList() : check.toolCallIds();
                require(!selected.isEmpty() && new HashSet<>(selected).size() == selected.size(),
                        "criterion must have actual tool results without duplicate identities");
                var selectedObservations = new ArrayList<ToolObservation>();
                for (var callId : selected) {
                    require(callId != null && !callId.isBlank(), "tool call identity is missing");
                    var matches = evidence.tools().stream().filter(t -> callId.equals(t.toolCallId())).toList();
                    require(matches.size() == 1 && matches.getFirst().hasExecutionResult(), "missing, ambiguous or incomplete tool call: " + callId);
                    selectedObservations.add(matches.getFirst());
                }
                verifySelectedExits(binding, selectedObservations);
            }
            for (var material : check.materialRefs()) read(root, material);
        }
        // One fresh source read per distinct baseline at this consumption point. Multiple
        // criteria sharing the same execution do not each launch another Git inventory.
        // Nothing is cached across preparation, dispatch, publication or recovery calls.
        for (var source : observations.values().stream().map(Observation::afterSource).distinct().toList())
            require(source.equals(EvidenceCollectionSource.capture(root, source.paths())), "source changed after observed execution");
    }

    public static void verifyExit(TaskContract.VerificationBinding binding, ToolObservation observation) throws IOException {
        if (!Boolean.TRUE.equals(binding.actualExitRequired())) return;
        require(observation.actualExitCode() != null && observation.actualExitSource() != null
                && !observation.actualExitSource().isBlank(), "required actual process exit is unavailable; native status is insufficient");
        require(binding.expectedExitCode() == null || binding.expectedExitCode().equals(observation.actualExitCode()),
                "actual process exit does not match approved requirement");
    }

    static void verifySelectedExits(TaskContract.VerificationBinding binding, List<ToolObservation> observations) throws IOException {
        if (!Boolean.TRUE.equals(binding.actualExitRequired())) return;
        // Reading a test report has no process exit. It may accompany a process result, but
        // cannot replace one or exempt an execution tool whose actual exit is unavailable.
        var executions = observations.stream().filter(t -> "execute".equals(t.kind()) || t.actualExitCode() != null).toList();
        require(!executions.isEmpty(), "required actual process exit is unavailable; native status is insufficient");
        for (var execution : executions) verifyExit(binding, execution);
    }

    static Observation load(WorkflowFileLedger ledger, Path root, String ws, String node, NodeResult result, String selector) throws IOException {
        require(selector != null && !selector.isBlank(), "execution_ref must be CURRENT or a fixed observation reference");
        TaskContract.Reference ref;
        boolean own = "CURRENT".equals(selector);
        if (own) {
            require(result.result().get(FACT) != null, "current execution has no program tool observation");
            ref = JSON.convertValue(result.result().get(FACT), TaskContract.Reference.class);
        } else {
            var path = root.resolve(selector).normalize();
            require(path.startsWith(root.toAbsolutePath().normalize()) && path.getFileName().toString().equals(FILE), "invalid tool observation selector");
            ref = new TaskContract.Reference(selector, TaskContractArtifactWriter.sha256(path));
        }
        require(ref.ref().startsWith(".agent/workstreams/" + ws + "/runs/") && ref.ref().endsWith("/outputs/" + FILE), "foreign observation reference");
        var observed = JSON.readValue(read(root, ref), Observation.class);
        require(observed != null && observed.prepared() != null && observed.prepared().get("ref") != null
                && observed.baseline() != null && observed.baseline().source() != null && observed.baseline().contract() != null
                && observed.afterSource() != null && observed.tools() != null && observed.tools().stream().noneMatch(Objects::isNull),
                "incomplete observation record");
        require("dual.verification.observations.v1".equals(observed.schema()) && ws.equals(observed.workstream()), "invalid observation schema/workstream");
        require(ref.ref().equals(".agent/workstreams/" + ws + "/runs/" + observed.run() + "/outputs/" + FILE)
                && observed.prepared().get("ref").equals(".agent/workstreams/" + ws + "/runs/" + observed.run()
                    + "/outputs/" + observed.execution() + "-prepared.json"), "observation run differs from its preparation");
        if (own) require(node.equals(observed.node()) && observed.run().equals(String.valueOf(result.result().get("run_id"))), "foreign current observation");
        var paths = new WorkstreamPaths(root, new DualId(ws));
        ledger.inspect(paths, (snapshot, files) -> {
            var execution = files.read(paths.nodeExecutionJson(observed.execution()), NodeExecutionDocument.class);
            require(execution.nodeInstanceId().equals(observed.node()) && observed.prepared().equals(execution.result().get("prepared_run")),
                    "tool observation lacks its actual prepared execution binding");
            var prepared = JSON.readTree(read(root, new TaskContract.Reference(observed.prepared().get("ref"), observed.prepared().get("sha256"))));
            require(prepared.path("binding").path("verification_source").equals(JSON.valueToTree(observed.baseline())), "source identity is not the prepared baseline");
            if (!own) {
                var completed = AgentRunCommitProtocol.readCompletion(paths, execution);
                require(completed.outputRefs().contains(ref.ref())
                        && JSON.valueToTree(ref).equals(JSON.valueToTree(completed.result().get(FACT))),
                        "observation was not fixed by its original execution completion");
            }
            return null;
        });
        return observed;
    }

    static byte[] read(Path root, TaskContract.Reference ref) throws IOException {
        require(ref != null && ref.ref() != null && ref.sha256() != null && ref.sha256().matches("[0-9a-f]{64}")
                && !Path.of(ref.ref()).isAbsolute() && !ref.ref().isBlank(), "invalid evidence reference");
        var file = root.toAbsolutePath().normalize().resolve(ref.ref());
        require(file.normalize().equals(file) && file.startsWith(root.toAbsolutePath().normalize()), "evidence path escapes workspace");
        require(file.toRealPath().equals(root.toRealPath().resolve(ref.ref())), "noncanonical evidence path");
        var bytes = Files.readAllBytes(file);
        require(ref.sha256().equals(TaskContractArtifactWriter.sha256(bytes)), "evidence bytes changed");
        return bytes;
    }
    private static boolean accepted(String decision) { return "ACCEPT".equals(decision) || "ACCEPT_WITH_NOTES".equals(decision); }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
}
