package dev.dual.ledger.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * One short, explicitly owned workstream read/write session. Planning is memory-only;
 * replay applies fixed bytes and never invokes domain handlers or external activities.
 */
public final class LedgerSession implements AutoCloseable {
    private final WorkstreamPaths paths;
    private final Path root;
    private final WorkstreamLock lock;
    private final AtomicJsonWriter writer;
    private final Map<String, String> runtimeIdentity;
    private final boolean writable;
    private final String operation;
    private final String requestId;
    private final FaultPoint faultPoint;
    private final Map<Path, byte[]> originals = new LinkedHashMap<>();
    private final Map<Path, byte[]> replacements = new LinkedHashMap<>();
    private final Map<Path, byte[]> appends = new LinkedHashMap<>();
    private boolean closed;
    private boolean committed;
    private boolean appliedMutation;

    @FunctionalInterface
    interface FaultPoint { void at(String point, Path target) throws IOException; }

    LedgerSession(WorkstreamPaths paths, AtomicJsonWriter writer, Map<String, String> identity,
                  boolean writable, boolean initialize, String operation, String requestId,
                  FaultPoint faultPoint) throws IOException {
        if (identity == null || identity.isEmpty()) throw new IllegalArgumentException("frozen runtime identity required");
        if (operation == null || operation.isBlank() || requestId == null || requestId.isBlank())
            throw new IllegalArgumentException("operation and request identity required");
        this.paths = paths;
        this.writer = writer;
        this.runtimeIdentity = Map.copyOf(identity);
        this.writable = writable;
        this.operation = operation;
        this.requestId = requestId;
        this.faultPoint = faultPoint;
        Files.createDirectories(paths.workstreamDir());
        this.root = paths.workstreamDir().toRealPath();
        this.lock = WorkstreamLock.acquire(root,
                LockOwner.currentProcess(paths.workstreamId().value(), operation));
        try {
            // Both the existing instance and the prepared write must belong to this runtime.
            // Checking only the manifest would allow writes into an unrelated/replaced instance.
            var workstream = root.resolve("workstream.json");
            if (Files.exists(workstream, LinkOption.NOFOLLOW_LINKS)) validateWorkstreamIdentity(workstream);
            var pending = pending();
            if (pending != null) {
                if (!writable) throw new PendingCommitException(pending.getFileName().toString());
                var mutation = readMutation(pending);
                validateIdentity(mutation.runtimeIdentity());
                apply(pending, mutation);
            }
            if (Files.isRegularFile(workstream, LinkOption.NOFOLLOW_LINKS)) {
                validateWorkstreamIdentity(workstream);
                if (initialize) throw new IOException("ledger already initialized");
            } else if (!initialize) {
                throw invalid("missing workstream runtime identity");
            }
            if (writable) verifyAtomicReplacement();
        } catch (IOException | RuntimeException error) {
            try { lock.close(); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    public WorkstreamPaths paths() { return paths; }
    boolean appliedMutation() { return appliedMutation; }

    public byte[] readBytes(Path path) throws IOException {
        requireOpen();
        var canonical = checked(path);
        byte[] bytes;
        if (replacements.containsKey(canonical)) bytes = replacements.get(canonical);
        else {
            bytes = original(canonical);
            if (appends.containsKey(canonical)) bytes = concat(bytes, appends.get(canonical));
        }
        if (bytes == null) throw new java.nio.file.NoSuchFileException(canonical.toString());
        return bytes.clone();
    }

    public String readString(Path path) throws IOException { return new String(readBytes(path), StandardCharsets.UTF_8); }
    public List<String> readLines(Path path) throws IOException { return readString(path).lines().toList(); }
    public <T> T read(Path path, Class<T> type) throws IOException { return writer.objectMapper().readValue(readBytes(path), type); }
    public <T> T read(Path path, TypeReference<T> type) throws IOException { return writer.objectMapper().readValue(readBytes(path), type); }
    public JsonNode readTree(Path path) throws IOException { return writer.objectMapper().readTree(readBytes(path)); }

    public boolean exists(Path path) throws IOException {
        requireOpen();
        var canonical = checked(path);
        return replacements.containsKey(canonical) || appends.containsKey(canonical)
                || Files.exists(canonical, LinkOption.NOFOLLOW_LINKS);
    }

    /** Includes planned children so a later domain calculation sees the same planned state. */
    public List<Path> list(Path directory) throws IOException {
        requireOpen();
        var canonical = checked(directory);
        var children = new java.util.TreeSet<Path>();
        if (Files.isDirectory(canonical, LinkOption.NOFOLLOW_LINKS)) {
            try (var stream = Files.list(canonical)) { stream.forEach(children::add); }
        }
        Stream.concat(replacements.keySet().stream(), appends.keySet().stream())
                .filter(p -> canonical.equals(p.getParent())).forEach(children::add);
        return List.copyOf(children);
    }

    public void write(Path path, Object value) throws IOException { writeBytes(path, writer.objectMapper().writeValueAsBytes(value)); }

    public void writeBytes(Path path, byte[] bytes) throws IOException {
        requireWritable();
        var canonical = writablePath(path);
        if (appends.containsKey(canonical)) throw invalid("cannot replace an append target");
        var before = original(canonical);
        if ((relative(canonical).startsWith("artifacts/contracts/")
                || relative(canonical).startsWith("artifacts/verification-readiness/"))
                && before != null && !Arrays.equals(before, bytes))
            throw invalid("immutable occurrence artifact already contains different bytes");
        replacements.put(canonical, bytes.clone());
    }

    /** Each append contains complete JSONL records. Event identity is checked against planned and committed bytes. */
    public void appendEvents(Path path, byte[] bytes) throws IOException {
        requireWritable();
        var canonical = checked(path);
        if (!canonical.equals(root.resolve("events.jsonl")) && !canonical.equals(root.resolve("timeline.jsonl")))
            throw invalid("unsupported append target");
        if (replacements.containsKey(canonical)) throw invalid("cannot append to a replacement target");
        var existing = concat(original(canonical), appends.get(canonical));
        var known = records(existing);
        var added = new java.io.ByteArrayOutputStream();
        for (var entry : records(bytes).entrySet()) {
            var previous = known.putIfAbsent(entry.getKey(), entry.getValue());
            if (previous != null && !Arrays.equals(previous, entry.getValue()))
                throw invalid("event id has different bytes: " + entry.getKey());
            if (previous == null) added.writeBytes(entry.getValue());
        }
        if (added.size() > 0) appends.put(canonical, concat(appends.get(canonical), added.toByteArray()));
    }

    /** Returning from this method proves this live invocation completed its local commit only. */
    public void commit() throws IOException {
        requireWritable();
        if (replacements.isEmpty() && appends.isEmpty()) { committed = true; return; }
        // Every mutation, including a claim with no domain event, advances the same current read identity.
        var current = readTree(paths.workflowCurrentJson());
        if (!(current instanceof com.fasterxml.jackson.databind.node.ObjectNode object)) throw invalid("missing current document");
        object.put("commitSequence", Math.addExact(baseSequence(), 1));
        write(paths.workflowCurrentJson(), object);
        var targets = new ArrayList<Target>();
        for (var entry : replacements.entrySet()) {
            targets.add(new Target(relative(entry.getKey()), sha(originals.get(entry.getKey())),
                    entry.getValue(), sha(entry.getValue())));
        }
        var tails = new ArrayList<Append>();
        for (var entry : appends.entrySet()) {
            var before = originals.get(entry.getKey());
            tails.add(new Append(relative(entry.getKey()), before == null ? 0 : before.length, sha(concat(before, null)),
                    entry.getValue(), sha(concat(before, entry.getValue()))));
        }
        if (targets.isEmpty() && tails.isEmpty()) { committed = true; return; }
        var id = UUID.randomUUID().toString();
        var mutation = new Mutation(1, id, paths.workstreamId().value(), runtimeIdentity,
                operation, requestId, baseSequence(), List.copyOf(targets), List.copyOf(tails));
        var pending = root.resolve("pending").resolve(id + ".json");
        validateMutation(pending, mutation);
        // Detect changes made outside this session before making the plan authoritative.
        validateStates(mutation);
        faultPoint.at("before_manifest", pending);
        writer.write(pending, mutation);
        faultPoint.at("after_manifest", pending);
        apply(pending, mutation);
        committed = true;
    }

    private long baseSequence() throws IOException {
        var path = checked(paths.workflowCurrentJson());
        var before = original(path);
        if (before == null) return 0;
        return commitSequence(before);
    }

    private long commitSequence(byte[] bytes) throws IOException {
        var sequence = writer.objectMapper().readTree(bytes).path("commitSequence");
        if (!sequence.isIntegralNumber() || !sequence.canConvertToLong() || sequence.asLong() < 1)
            throw invalid("current commit sequence is missing or invalid");
        return sequence.asLong();
    }

    private void apply(Path pending, Mutation mutation) throws IOException {
        validateMutation(pending, mutation);
        validateStates(mutation);
        // current/instance are applied last; all readers remain excluded by the session lock/pending guard.
        var targets = new ArrayList<>(mutation.targets());
        targets.sort(java.util.Comparator.comparingInt(t -> t.path().startsWith("workflow/")
                && !t.path().equals("workflow/definition.json") ? 1 : 0));
        for (var target : targets) if (!currentIndex(target)) applyTarget(target);
        for (var tail : mutation.appends()) {
            var path = checked(root.resolve(tail.path()));
            var current = bytesOrAbsent(path);
            validateAppend(path, tail, current);
            faultPoint.at("before_append", path);
            if (!sha(concat(current, null)).equals(tail.afterSha())) {
                // Only the manifest-bound uncommitted tail is repaired. The committed prefix is preserved.
                var prefix = Arrays.copyOf(concat(current, null), Math.toIntExact(tail.prefixLength()));
                writer.writeBytes(path, concat(prefix, tail.bytes()));
            }
            faultPoint.at("after_append", path);
        }
        for (var target : targets) if (currentIndex(target)) applyTarget(target);
        for (var target : mutation.targets())
            if (!sha(bytesOrAbsent(checked(root.resolve(target.path())))).equals(target.afterSha()))
                throw invalid("after-state verification failed: " + target.path());
        for (var tail : mutation.appends())
            if (!sha(concat(bytesOrAbsent(checked(root.resolve(tail.path()))), null)).equals(tail.afterSha()))
                throw invalid("append verification failed: " + tail.path());
        faultPoint.at("before_pending_delete", pending);
        Files.delete(pending);
        appliedMutation = true;
        faultPoint.at("after_pending_delete", pending);
    }

    private static boolean currentIndex(Target target) {
        return target.path().equals("workflow/current.json") || target.path().equals("workflow/instance.json");
    }

    private void applyTarget(Target target) throws IOException {
        var path = writablePath(root.resolve(target.path()));
        validateTarget(path, target);
        faultPoint.at("before_replace", path);
        if (!sha(bytesOrAbsent(path)).equals(target.afterSha())) writer.writeBytes(path, target.afterBytes());
        faultPoint.at("after_replace", path);
    }

    private void validateStates(Mutation mutation) throws IOException {
        var currentPath = checked(paths.workflowCurrentJson());
        var current = bytesOrAbsent(currentPath);
        var plannedCurrent = mutation.targets().stream().filter(t -> t.path().equals("workflow/current.json")).findFirst();
        boolean isPreparedAfter = plannedCurrent.isPresent() && sha(current).equals(plannedCurrent.get().afterSha());
        if (!isPreparedAfter) {
            long observed = 0;
            if (current != null) {
                observed = commitSequence(current);
            }
            if (observed != mutation.baseSequence()) throw invalid("base sequence changed");
        }
        for (var target : mutation.targets()) validateTarget(checked(root.resolve(target.path())), target);
        for (var tail : mutation.appends()) {
            var path = checked(root.resolve(tail.path()));
            validateAppend(path, tail, bytesOrAbsent(path));
        }
    }

    private void validateTarget(Path path, Target target) throws IOException {
        var observed = sha(bytesOrAbsent(path));
        if (!observed.equals(target.beforeSha()) && !observed.equals(target.afterSha()))
            throw invalid("target is neither before nor after: " + relative(path));
    }

    private void validateAppend(Path path, Append tail, byte[] currentOrAbsent) throws IOException {
        var current = concat(currentOrAbsent, null);
        if (tail.prefixLength() < 0 || tail.prefixLength() > current.length || tail.prefixLength() > Integer.MAX_VALUE)
            throw invalid("append prefix length changed: " + relative(path));
        int length = (int) tail.prefixLength();
        if (!sha(Arrays.copyOf(current, length)).equals(tail.prefixSha()))
            throw invalid("committed append prefix changed: " + relative(path));
        var observedTail = Arrays.copyOfRange(current, length, current.length);
        if (observedTail.length > tail.bytes().length
                || !Arrays.equals(observedTail, Arrays.copyOf(tail.bytes(), observedTail.length)))
            throw invalid("uncommitted tail is not a prefix of prepared events");
        if (!sha(concat(Arrays.copyOf(current, length), tail.bytes())).equals(tail.afterSha()))
            throw invalid("append after checksum mismatch");
    }

    private Mutation readMutation(Path pending) throws IOException {
        try { return writer.objectMapper().readValue(Files.readAllBytes(pending), Mutation.class); }
        catch (IOException | RuntimeException e) { throw new IOException("INVALID_LOCAL_COMMIT: malformed manifest", e); }
    }

    private void validateMutation(Path pending, Mutation mutation) throws IOException {
        if (mutation.schemaVersion() != 1 || mutation.id() == null
                || !pending.getFileName().toString().equals(mutation.id() + ".json")
                || !mutation.id().matches("[0-9a-f-]{36}")
                || !paths.workstreamId().value().equals(mutation.workstreamId())
                || mutation.operation() == null || mutation.operation().isBlank()
                || mutation.requestId() == null || mutation.requestId().isBlank()
                || mutation.baseSequence() < 0 || mutation.targets() == null || mutation.appends() == null)
            throw invalid("invalid manifest identity");
        validateIdentity(mutation.runtimeIdentity());
        var names = new java.util.HashSet<String>();
        for (var target : mutation.targets()) {
            if (target == null || target.path() == null || !names.add(target.path())
                    || target.afterBytes() == null || !sha(target.afterBytes()).equals(target.afterSha())
                    || target.beforeSha() == null || !(target.beforeSha().equals("ABSENT") || target.beforeSha().matches("[0-9a-f]{64}")))
                throw invalid("invalid replacement/checksum");
            if (!relative(writablePath(root.resolve(target.path()))).equals(target.path()))
                throw invalid("noncanonical manifest target");
            if (target.path().equals("workstream.json"))
                validateIdentity(writer.objectMapper().convertValue(
                        writer.objectMapper().readTree(target.afterBytes()).get("frozenRuntimeIdentity"),
                        new TypeReference<Map<String, String>>() {}));
        }
        for (var tail : mutation.appends()) {
            if (tail == null || !("events.jsonl".equals(tail.path()) || "timeline.jsonl".equals(tail.path()))
                    || !names.add(tail.path()) || tail.bytes() == null || tail.prefixLength() < 0)
                throw invalid("invalid append target");
            records(tail.bytes());
        }
    }

    private void validateIdentity(Map<String, String> identity) throws IOException {
        if (!runtimeIdentity.equals(identity)) throw invalid("frozen runtime identity mismatch or absent");
    }

    private void validateWorkstreamIdentity(Path path) throws IOException {
        validateIdentity(writer.objectMapper().convertValue(
                writer.objectMapper().readTree(bytesOrAbsent(path)).get("frozenRuntimeIdentity"),
                new TypeReference<Map<String, String>>() {}));
    }

    private Path pending() throws IOException {
        var directory = checked(root.resolve("pending"));
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return null;
        try (var stream = Files.list(directory)) {
            var files = stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList();
            if (files.size() > 1) throw invalid("multiple pending commits");
            return files.isEmpty() ? null : checked(files.getFirst());
        }
    }

    private void verifyAtomicReplacement() throws IOException {
        var probe = Files.createTempFile(root, ".atomic-probe-", ".tmp");
        try { writer.writeBytes(probe, new byte[]{1}); }
        finally { Files.deleteIfExists(probe); }
    }

    private byte[] original(Path path) throws IOException {
        if (!originals.containsKey(path)) originals.put(path, bytesOrAbsent(path));
        return originals.get(path);
    }

    private byte[] bytesOrAbsent(Path path) throws IOException {
        checked(path);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw invalid("not a regular document: " + path);
        return Files.readAllBytes(path);
    }

    private Path checked(Path path) throws IOException {
        var absolute = path.toAbsolutePath().normalize();
        // Account for a workspace spelled through a canonical alias without permitting link escapes within it.
        var lexicalRoot = paths.workstreamDir().toAbsolutePath().normalize();
        if (absolute.startsWith(lexicalRoot)) absolute = root.resolve(lexicalRoot.relativize(absolute));
        if (!absolute.startsWith(root)) throw invalid("path outside this workstream");
        for (var part = absolute; part != null && part.startsWith(root); part = part.getParent())
            if (Files.isSymbolicLink(part)) throw invalid("symbolic link in control path");
        return absolute;
    }

    private Path writablePath(Path path) throws IOException {
        var canonical = checked(path);
        var name = relative(canonical);
        if (!(name.matches("(workstream|settings)\\.json") || name.equals("requirement.md")
                || name.matches("workflow/(definition|instance|current)\\.json")
                || name.matches("current/(stage|iteration|checkpoint)\\.json")
                || name.matches("(nodes|gates|iterations|workspaces)/[^/]+\\.json")
                || name.matches("checkpoints/[^/]+\\.json")
                || name.matches("executions/(workflow|nodes)/[^/]+\\.json")
                || name.matches("queue/agent-tasks/[^/]+\\.json")
                || name.matches("stages/[^/]+/(stage\\.json|iterations/[^/]+/(iteration|checkpoint)\\.json)")
                || name.matches("domain/(goal-satisfaction|completion-audit)\\.json")
                || java.util.Set.of("delivery/merge-plan.json", "delivery/merge.patch", "delivery/changed-files.txt", "delivery/excluded-files.txt").contains(name)
                || name.matches("artifacts/(contracts/[^/]+/)?[^/]+\\.(json|md)")
                || name.matches("artifacts/verification-readiness/[A-Za-z0-9._-]{1,80}\\.json")
                || name.matches("authority/[^/]+\\.json")
                || name.matches("execution-authority/network-grants/grant-[A-Za-z0-9-]+\\.json")))
            throw invalid("not a registered ledger write target: " + name);
        return canonical;
    }

    private String relative(Path path) { return root.relativize(path).toString().replace('\\', '/'); }

    private Map<String, byte[]> records(byte[] bytes) throws IOException {
        var result = new LinkedHashMap<String, byte[]>();
        if (bytes.length == 0) return result;
        if (bytes[bytes.length - 1] != '\n') throw invalid("incomplete planned/committed event record");
        int start = 0;
        for (int i = 0; i < bytes.length; i++) if (bytes[i] == '\n') {
            var line = Arrays.copyOfRange(bytes, start, i + 1);
            var event = writer.objectMapper().readTree(line);
            var id = event == null ? "" : event.path("event_id").asText();
            if (id.isBlank() || result.putIfAbsent(id, line) != null) throw invalid("missing/duplicate event id");
            start = i + 1;
        }
        return result;
    }

    static String sha(byte[] bytes) {
        if (bytes == null) return "ABSENT";
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static byte[] concat(byte[] first, byte[] second) {
        first = first == null ? new byte[0] : first;
        second = second == null ? new byte[0] : second;
        var result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static IOException invalid(String reason) { return new IOException("INVALID_LOCAL_COMMIT: " + reason); }
    private void requireOpen() { if (closed) throw new IllegalStateException("ledger session closed"); }
    private void requireWritable() {
        requireOpen();
        if (!writable || committed) throw new IllegalStateException("no writable session");
    }
    @Override public void close() throws IOException { if (!closed) { closed = true; lock.close(); } }

    record Target(String path, String beforeSha, byte[] afterBytes, String afterSha) {}
    record Append(String path, long prefixLength, String prefixSha, byte[] bytes, String afterSha) {}
    record Mutation(int schemaVersion, String id, String workstreamId, Map<String, String> runtimeIdentity,
                    String operation, String requestId, long baseSequence, List<Target> targets, List<Append> appends) {}

    public static final class PendingCommitException extends IOException {
        public PendingCommitException(String id) { super("PENDING_LOCAL_COMMIT: " + id); }
    }
}
