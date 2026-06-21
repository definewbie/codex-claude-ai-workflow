package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Local operator index for projects and workstream entry points.
 *
 * This registry is intentionally not a workflow source of truth. It only helps
 * CLI, autorun, and the local UI find known repository roots and effective
 * workdirs. Workflow status, gates, recovery, and artifacts remain owned by the
 * per-workstream ledger.
 */
public final class OperatorWorkspaceRegistry {
    public static final String REGISTRY_PATH_PROPERTY = "dual.operator.registry.path";
    public static final String REGISTRY_PATH_ENV = "DUAL_OPERATOR_REGISTRY";

    private final AtomicJsonWriter jsonWriter;
    private final ObjectMapper objectMapper;
    private final Path registryPath;

    public OperatorWorkspaceRegistry() {
        this(defaultRegistryPath());
    }

    public OperatorWorkspaceRegistry(Path registryPath) {
        this.jsonWriter = new AtomicJsonWriter();
        this.objectMapper = jsonWriter.objectMapper();
        this.registryPath = registryPath.toAbsolutePath().normalize();
    }

    public synchronized OperatorWorkspaceRegistryDocument read() throws IOException {
        if (!Files.isRegularFile(registryPath)) {
            return OperatorWorkspaceRegistryDocument.empty();
        }
        return objectMapper.readValue(registryPath.toFile(), OperatorWorkspaceRegistryDocument.class)
                .normalized();
    }

    public synchronized OperatorWorkspaceRegistryDocument registerProject(Path repoRoot) throws IOException {
        var normalizedRepoRoot = normalizeRepositoryRoot(repoRoot);
        var document = read();
        var project = projectEntry(normalizedRepoRoot, List.of(), Instant.now());
        var projects = upsertProject(document.projects(), project);
        var next = new OperatorWorkspaceRegistryDocument(
                1,
                project.projectId(),
                sortedProjects(projects)
        );
        write(next);
        return next;
    }

    public synchronized OperatorWorkspaceRegistryDocument registerWorkstream(
            Path originalRepoRoot,
            Path effectiveWorkdir,
            String workstreamId,
            String title,
            String status,
            Instant updatedAt
    ) throws IOException {
        var normalizedRepoRoot = normalizeRepositoryRoot(originalRepoRoot);
        var normalizedEffectiveWorkdir = effectiveWorkdir.toAbsolutePath().normalize();
        var document = read();
        var now = updatedAt == null ? Instant.now() : updatedAt;
        var projectId = projectId(normalizedRepoRoot);
        var existingProject = document.projects().stream()
                .filter(project -> project.projectId().equals(projectId))
                .findFirst();
        var existingWorkstreams = existingProject
                .map(OperatorWorkspaceProject::workstreams)
                .orElse(List.of());
        var workstream = new OperatorWorkspaceWorkstream(
                workstreamId,
                normalizedEffectiveWorkdir.toString(),
                nullToBlank(title),
                nullToBlank(status),
                now
        );
        var project = projectEntry(normalizedRepoRoot, upsertWorkstream(existingWorkstreams, workstream), now);
        var next = new OperatorWorkspaceRegistryDocument(
                1,
                project.projectId(),
                sortedProjects(upsertProject(document.projects(), project))
        );
        write(next);
        return next;
    }

    public synchronized OperatorWorkspaceRegistryDocument registerFromLedger(Path effectiveWorkdir, String workstreamId) throws IOException {
        var root = effectiveWorkdir.toAbsolutePath().normalize();
        var status = new FileLedgerStore().loadStatus(root, workstreamId);
        return registerWorkstream(
                Path.of(status.workspace().originalWorkdir()),
                root,
                workstreamId,
                status.workstream().title(),
                status.workstream().status(),
                status.workstream().updatedAt()
        );
    }

    public Path registryPath() {
        return registryPath;
    }

    public static Path defaultAgentRunsDir(Path repoRoot) {
        var root = repoRoot.toAbsolutePath().normalize();
        var parent = root.getParent();
        var name = root.getFileName() == null ? "repo" : root.getFileName().toString();
        return (parent == null ? Path.of(".") : parent).resolve(name + "-agent-runs").toAbsolutePath().normalize();
    }

    private void write(OperatorWorkspaceRegistryDocument document) throws IOException {
        jsonWriter.write(registryPath, document.normalized());
    }

    private static OperatorWorkspaceProject projectEntry(Path repoRoot, List<OperatorWorkspaceWorkstream> workstreams, Instant lastOpenedAt) {
        var name = repoRoot.getFileName() == null ? repoRoot.toString() : repoRoot.getFileName().toString();
        return new OperatorWorkspaceProject(
                projectId(repoRoot),
                name,
                repoRoot.toString(),
                defaultAgentRunsDir(repoRoot).toString(),
                lastOpenedAt,
                sortedWorkstreams(workstreams)
        );
    }

    private static List<OperatorWorkspaceProject> upsertProject(
            List<OperatorWorkspaceProject> projects,
            OperatorWorkspaceProject next
    ) {
        var result = new ArrayList<OperatorWorkspaceProject>();
        var replaced = false;
        for (var project : projects) {
            if (project.projectId().equals(next.projectId())) {
                var mergedWorkstreams = next.workstreams().isEmpty()
                        ? project.workstreams()
                        : next.workstreams();
                result.add(new OperatorWorkspaceProject(
                        next.projectId(),
                        next.name(),
                        next.repoRoot(),
                        next.agentRunsDir(),
                        next.lastOpenedAt(),
                        mergedWorkstreams
                ));
                replaced = true;
            } else {
                result.add(project);
            }
        }
        if (!replaced) {
            result.add(next);
        }
        return result;
    }

    private static List<OperatorWorkspaceWorkstream> upsertWorkstream(
            List<OperatorWorkspaceWorkstream> workstreams,
            OperatorWorkspaceWorkstream next
    ) {
        var result = new ArrayList<OperatorWorkspaceWorkstream>();
        var replaced = false;
        for (var workstream : workstreams) {
            if (workstream.workstreamId().equals(next.workstreamId())) {
                result.add(next);
                replaced = true;
            } else {
                result.add(workstream);
            }
        }
        if (!replaced) {
            result.add(next);
        }
        return sortedWorkstreams(result);
    }

    private static List<OperatorWorkspaceProject> sortedProjects(List<OperatorWorkspaceProject> projects) {
        return projects.stream()
                .map(OperatorWorkspaceProject::normalized)
                .sorted(Comparator
                        .comparing(OperatorWorkspaceProject::lastOpenedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(OperatorWorkspaceProject::name))
                .toList();
    }

    private static List<OperatorWorkspaceWorkstream> sortedWorkstreams(List<OperatorWorkspaceWorkstream> workstreams) {
        return workstreams.stream()
                .map(OperatorWorkspaceWorkstream::normalized)
                .sorted(Comparator
                        .comparing(OperatorWorkspaceWorkstream::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(OperatorWorkspaceWorkstream::workstreamId))
                .toList();
    }

    private static Path normalizeRepositoryRoot(Path repoRoot) {
        var root = repoRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("repository root is not a directory: " + root);
        }
        if (!Files.exists(root.resolve(".git"))) {
            throw new IllegalArgumentException("repository root does not look like a git repository: " + root);
        }
        return root;
    }

    private static String projectId(Path repoRoot) {
        var name = repoRoot.getFileName() == null ? "repo" : slug(repoRoot.getFileName().toString());
        return name + "-" + shortHash(repoRoot.toString());
    }

    private static String slug(String value) {
        var slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        return slug.isBlank() ? "repo" : slug;
    }

    private static String shortHash(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }

    private static Path defaultRegistryPath() {
        var property = System.getProperty(REGISTRY_PATH_PROPERTY);
        if (property != null && !property.isBlank()) {
            return Path.of(property);
        }
        var env = System.getenv(REGISTRY_PATH_ENV);
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        return Path.of(System.getProperty("user.home"), ".dual", "operator-workspaces.json");
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
