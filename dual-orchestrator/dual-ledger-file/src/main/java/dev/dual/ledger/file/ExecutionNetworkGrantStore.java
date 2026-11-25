package dev.dual.ledger.file;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Operator authority for one execution; callers hold the workstream lock. */
public final class ExecutionNetworkGrantStore {
    public static final String REF_KEY = "execution_network_grant_ref";
    private static final String SHA_KEY = "execution_network_grant_sha256";
    private final AtomicJsonWriter writer;
    private final boolean scoped;
    private final WorkflowFileLedger ledger;

    public ExecutionNetworkGrantStore(WorkflowFileLedger ledger) {
        this.ledger = java.util.Objects.requireNonNull(ledger);
        this.scoped = ledger.hasSession();
        this.writer = scoped ? new AtomicJsonWriter(ledger.files()) : new AtomicJsonWriter();
    }

    public record Grant(int schemaVersion, String workstreamId, String sourceNodeInstanceId,
                        String targetNodeDefinitionId, String definitionSha256, List<String> domains,
                        String note, String approvedAt, String state, String claimedNodeInstanceId,
                        String claimedRunId) {
        public Grant {
            domains = List.copyOf(domains == null ? List.of() : domains);
        }
    }

    public record Claim(String ref, String sha256, List<String> domains) {
        public Claim { domains = List.copyOf(domains); }
        public Map<String, Object> metadata() {
            return Map.of("grantRef", ref, "grantSha256", sha256, "allowedDomains", domains);
        }
    }

    public String approve(WorkstreamPaths paths, String target, List<String> domains, String note) throws IOException {
        if (!scoped) {
            try (var write = ledger.writeSession(paths, "network grant approval", UUID.randomUUID().toString())) {
                var result = new ExecutionNetworkGrantStore(write.ledger()).approve(paths, target, domains, note);
                write.commit();
                return result;
            }
        }
        var source = waitingSource(paths);
        var definition = ledger.loadDefinition(paths);
        var canonical = canonicalDomains(domains);
        if (note == null || note.isBlank()) throw new IOException("network approval note is required");
        if (!eligible(definition.node(target))) throw new IOException("target must use Claude implement profile");
        if (source.kind() == NodeKind.GATE) {
            if (!targets(definition, source).contains(target)) throw new IOException("target is not reachable before another gate");
        } else if (!source.nodeDefinitionId().equals(target)) {
            throw new IOException("a failed execution can authorize only its own retry");
        }
        if (source.inputTransition().containsKey(REF_KEY)) {
            var previous = read(paths, source.inputTransition().get(REF_KEY).toString());
            if (previous.state().equals("PENDING")) throw new IOException("cancel the pending network grant before replacing it");
        }
        var path = directory(paths).resolve("grant-" + UUID.randomUUID() + ".json");
        var ref = paths.workspaceRoot().relativize(path).toString();
        var grant = new Grant(1, paths.workstreamId().value(), source.nodeInstanceId(), target,
                sha(writer.readBytes(paths.workflowDefinitionJson())), canonical, note.trim(),
                Instant.now().toString(), "PENDING", "", "");
        writer.write(path, grant);
        var transition = new LinkedHashMap<>(source.inputTransition());
        transition.put(REF_KEY, ref);
        transition.put(SHA_KEY, sha(writer.objectMapper().writeValueAsBytes(grant)));
        writer.write(paths.nodeJson(source.nodeInstanceId()), new NodeInstanceDocument(
                source.schemaVersion(), source.nodeInstanceId(), source.nodeDefinitionId(), source.kind(),
                source.handlerType(), source.status(), source.stageId(), source.iterationId(), source.inputRefs(),
                transition, source.outputRefs(), source.result(), source.runRef(), source.latestExecutionId(),
                source.startedAt(), source.finishedAt()));
        return ref;
    }

    public void cancel(WorkstreamPaths paths) throws IOException {
        if (!scoped) {
            try (var write = ledger.writeSession(paths, "network grant cancellation", UUID.randomUUID().toString())) {
                new ExecutionNetworkGrantStore(write.ledger()).cancel(paths);
                write.commit();
                return;
            }
        }
        var source = waitingSource(paths);
        var value = source.inputTransition().get(REF_KEY);
        if (!(value instanceof String ref)) throw new IOException("no network grant on the current waiting node");
        var grant = read(paths, ref);
        if (!grant.state().equals("PENDING")) throw new IOException("only an unclaimed network grant can be cancelled");
        writer.write(resolve(paths, ref), withState(grant, "CANCELLED", "", ""));
    }

    public Map<String, Object> view(WorkstreamPaths paths) throws IOException {
        try { return ledger.inspect(paths, (snapshot, files) -> new ExecutionNetworkGrantStore(snapshot).validView(paths)); }
        catch (IOException invalid) {
            return Map.of("canApprove", false, "canCancel", false, "status", "INVALID", "reason", invalid.getMessage());
        }
    }

    private Map<String, Object> validView(WorkstreamPaths paths) throws IOException {
        var current = ledger.loadCurrent(paths);
        if (current.activeNodeInstanceIds().size() != 1) return Map.of("canApprove", false, "canCancel", false);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst());
        var result = new LinkedHashMap<String, Object>();
        var waiting = isWaiting(current, node);
        if (waiting) waitingSource(paths);
        var candidates = waiting ? targets(ledger.loadDefinition(paths), node) : List.<String>of();
        result.put("canApprove", !candidates.isEmpty());
        result.put("targets", candidates);
        result.put("canCancel", false);
        result.put("scope", "one_execution");
        var value = node.inputTransition().get(REF_KEY);
        if (value instanceof String ref) {
            var grant = read(paths, ref);
            result.put("grant", grant);
            result.put("grantRef", ref);
            var pending = grant.state().equals("PENDING");
            result.put("canApprove", !candidates.isEmpty() && !pending);
            result.put("canCancel", waiting && pending);
        }
        return Map.copyOf(result);
    }

    /** The atomic claim deliberately burns the grant even if subsequent startup fails. */
    public Claim claimIfPresent(WorkstreamPaths paths, String nodeId, String runId) throws IOException {
        if (!scoped) {
            try (var write = ledger.writeSession(paths, "network grant optional claim", UUID.randomUUID().toString())) {
                var result = new ExecutionNetworkGrantStore(write.ledger()).claimIfPresent(paths, nodeId, runId);
                write.commit();
                return result;
            }
        }
        if (!writer.exists(paths.nodeJson(nodeId))) return null;
        if (!ledger.loadNode(paths, nodeId).inputTransition().containsKey(REF_KEY)) return null;
        return claim(paths, nodeId, runId);
    }

    public Claim claim(WorkstreamPaths paths, String nodeId, String runId) throws IOException {
        if (!scoped) {
            try (var write = ledger.writeSession(paths, "network grant claim", UUID.randomUUID().toString())) {
                var result = new ExecutionNetworkGrantStore(write.ledger()).claim(paths, nodeId, runId);
                write.commit();
                return result;
            }
        }
        var current = ledger.loadCurrent(paths);
        if (current.status() != WorkflowStatus.RUNNING || !current.activeNodeInstanceIds().contains(nodeId)) {
            throw new IOException("network grant requires the current running execution");
        }
        var node = ledger.loadNode(paths, nodeId);
        var value = node.inputTransition().get(REF_KEY);
        if (value == null) return null;
        if (!(value instanceof String ref)) throw new IOException("invalid network grant reference");
        var grant = read(paths, ref);
        if (!grant.targetNodeDefinitionId().equals(node.nodeDefinitionId())) return null;
        if (!eligible(ledger.loadDefinition(paths).node(node.nodeDefinitionId()))) throw new IOException("network grant target capability mismatch");
        if (node.status() != NodeStatus.RUNNING) throw new IOException("network grant target is not running");
        if (!grant.state().equals("PENDING")) return null;
        if (runId == null || !runId.matches("[A-Za-z0-9._-]+")) throw new IOException("invalid network grant run id");
        var source = ledger.loadNode(paths, grant.sourceNodeInstanceId());
        if (!ref.equals(source.inputTransition().get(REF_KEY))) throw new IOException("network grant was superseded");
        if (source.kind() == NodeKind.GATE) {
            if (source.status() != NodeStatus.COMPLETED
                    || !List.of("approve", "approved", "revise").contains(source.result().get("human_action"))) {
                throw new IOException("network grant does not replace the business gate decision");
            }
        } else if (!source.nodeInstanceId().equals(nodeId)) {
            throw new IOException("network retry grant cannot cross node instances");
        }
        var path = resolve(paths, ref);
        writer.write(path, withState(grant, "CLAIMED", nodeId, runId));
        return new Claim(ref, sha(writer.readBytes(path)), grant.domains());
    }

    public static Map<String, Object> forward(AtomicJsonWriter reader, WorkstreamPaths paths, NodeInstanceDocument source,
                                               NodeDefinition next, Map<String, Object> attributes) throws IOException {
        var result = new LinkedHashMap<>(attributes);
        result.remove(REF_KEY);
        var value = source.inputTransition().get(REF_KEY);
        if (value instanceof String ref && next.kind() != NodeKind.GATE) {
            var grant = readDocument(reader, paths, ref);
            var origin = reader.read(paths.nodeJson(grant.sourceNodeInstanceId()), NodeInstanceDocument.class);
            var originalSha = sha(reader.objectMapper().writeValueAsBytes(withState(grant, "PENDING", "", "")));
            if (!ref.equals(origin.inputTransition().get(REF_KEY)) || !originalSha.equals(origin.inputTransition().get(SHA_KEY)))
                throw new IOException("network grant approval binding mismatch");
            if (grant.state().equals("PENDING") && !source.nodeDefinitionId().equals(grant.targetNodeDefinitionId())) {
                result.put(REF_KEY, ref);
            }
        }
        return Map.copyOf(result);
    }

    public static void verifyClaim(WorkstreamPaths paths, Claim claim, String nodeId, String runId) throws IOException {
        // Historical execution evidence is bound by its recorded SHA, not today's source pointer.
        var grant = readDocument(new AtomicJsonWriter(), paths, claim.ref());
        if (!grant.state().equals("CLAIMED") || !nodeId.equals(grant.claimedNodeInstanceId())
                || !runId.equals(grant.claimedRunId()) || !claim.domains().equals(grant.domains())
                || !claim.sha256().equals(sha(Files.readAllBytes(resolve(paths, claim.ref()))))) {
            throw new IOException("network grant claim mismatch");
        }
    }

    private NodeInstanceDocument waitingSource(WorkstreamPaths paths) throws IOException {
        var current = ledger.loadCurrent(paths);
        if (current.activeNodeInstanceIds().size() != 1) throw new IOException("network approval requires one active waiting node");
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst());
        if (!isWaiting(current, node)) throw new IOException("network approval requires a human gate or failed execution");
        if (node.kind() == NodeKind.GATE) {
            var open = false;
            for (var gateId : current.openGateIds()) {
                var gate = writer.read(paths.gateJson(gateId), GateDocument.class);
                open |= gate.nodeInstanceId().equals(node.nodeInstanceId()) && gate.status().equals("open");
            }
            if (!open) throw new IOException("network approval requires a matching open gate");
        }
        return node;
    }

    private static boolean isWaiting(WorkflowCurrentDocument current, NodeInstanceDocument node) {
        return current.status() == WorkflowStatus.WAITING_FOR_HUMAN && node.kind() == NodeKind.GATE && node.status() == NodeStatus.WAITING
                || current.status() == WorkflowStatus.WAITING_FOR_RECOVERY && node.kind() == NodeKind.TASK
                && (node.status() == NodeStatus.FAILED || node.status() == NodeStatus.BLOCKED);
    }

    private static boolean eligible(NodeDefinition node) {
        return node.handlerType().equals("acpx.agent_run") && "claude".equals(node.config().get("agent"))
                && "implement".equals(node.config().get("permission_profile"));
    }

    private static List<String> targets(WorkflowDefinition definition, NodeInstanceDocument source) {
        if (source.kind() != NodeKind.GATE) return eligible(definition.node(source.nodeDefinitionId())) ? List.of(source.nodeDefinitionId()) : List.of();
        var queue = new ArrayDeque<String>();
        definition.edgesFrom(source.nodeDefinitionId()).stream()
                .filter(e -> List.of("approve", "approved", "revise").contains(e.on())).forEach(e -> queue.add(e.to()));
        var seen = new HashSet<String>();
        var targets = new java.util.TreeSet<String>();
        while (!queue.isEmpty()) {
            var id = queue.removeFirst();
            if (!seen.add(id)) continue;
            var node = definition.node(id);
            if (node.kind() == NodeKind.GATE) continue;
            if (eligible(node)) targets.add(id);
            definition.edgesFrom(id).forEach(e -> queue.add(e.to()));
        }
        return List.copyOf(targets);
    }

    private Grant read(WorkstreamPaths paths, String ref) throws IOException {
        var grant = readDocument(writer, paths, ref);
        var source = ledger.loadNode(paths, grant.sourceNodeInstanceId());
        var originalSha = sha(writer.objectMapper().writeValueAsBytes(withState(grant, "PENDING", "", "")));
        if (!ref.equals(source.inputTransition().get(REF_KEY)) || !originalSha.equals(source.inputTransition().get(SHA_KEY))) {
            throw new IOException("network grant approval binding mismatch");
        }
        return grant;
    }

    private static Grant readDocument(AtomicJsonWriter writer, WorkstreamPaths paths, String ref) throws IOException {
        var grant = writer.read(resolve(paths, ref), Grant.class);
        if (grant == null || grant.schemaVersion() != 1 || !paths.workstreamId().value().equals(grant.workstreamId())
                || !sha(writer.readBytes(paths.workflowDefinitionJson())).equals(grant.definitionSha256())
                || grant.state() == null || !List.of("PENDING", "CLAIMED", "CANCELLED").contains(grant.state())
                || grant.targetNodeDefinitionId() == null || !grant.targetNodeDefinitionId().matches("[A-Za-z0-9._-]+")
                || grant.sourceNodeInstanceId() == null || !grant.sourceNodeInstanceId().matches("[A-Za-z0-9._-]+")
                || grant.note() == null || grant.note().isBlank() || grant.approvedAt() == null || grant.approvedAt().isBlank()
                || !canonicalDomains(grant.domains()).equals(grant.domains())) throw new IOException("invalid execution network grant");
        return grant;
    }

    public static List<String> canonicalDomains(List<String> domains) throws IOException {
        if (domains == null || domains.isEmpty() || domains.size() > 32) throw new IOException("provide 1-32 exact public DNS names");
        var result = new java.util.TreeSet<String>();
        for (var domain : domains) {
            if (domain == null || domain.length() > 253 || !domain.equals(domain.toLowerCase(java.util.Locale.ROOT))
                    || !domain.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")
                    || domain.matches("[0-9.]+") || domain.endsWith(".localhost") || domain.endsWith(".local")
                    || java.util.Arrays.stream(domain.split("\\.")).anyMatch(label -> label.length() > 63)) {
                throw new IOException("network domain must be an exact canonical DNS name without URL, wildcard, IP or local suffix");
            }
            result.add(domain);
        }
        return List.copyOf(result);
    }

    private static Grant withState(Grant g, String state, String node, String run) {
        return new Grant(g.schemaVersion(), g.workstreamId(), g.sourceNodeInstanceId(), g.targetNodeDefinitionId(),
                g.definitionSha256(), g.domains(), g.note(), g.approvedAt(), state, node, run);
    }

    private static Path directory(WorkstreamPaths paths) { return paths.workstreamDir().resolve("execution-authority/network-grants"); }
    private static Path resolve(WorkstreamPaths paths, String ref) throws IOException {
        var path = paths.workspaceRoot().resolve(ref).normalize();
        if (!path.getParent().equals(directory(paths).normalize())
                || !path.getFileName().toString().matches("grant-[0-9a-f-]{36}\\.json")
                || !path.toRealPath().equals(path.toAbsolutePath())) throw new IOException("invalid network grant path");
        return path;
    }
    private static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
