package dev.dual.server;

import dev.dual.ledger.file.OperatorWorkspaceProject;
import dev.dual.ledger.file.OperatorWorkspaceRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WorkspaceRegistryService {
    private final OperatorWorkspaceRegistry registry;
    private final WorkstreamQueryService workstreams;

    @Autowired
    public WorkspaceRegistryService(WorkstreamQueryService workstreams) {
        this(new OperatorWorkspaceRegistry(), workstreams);
    }

    WorkspaceRegistryService(OperatorWorkspaceRegistry registry, WorkstreamQueryService workstreams) {
        this.registry = registry;
        this.workstreams = workstreams;
    }

    public Map<String, Object> list() throws IOException {
        var document = registry.read();
        var response = new LinkedHashMap<String, Object>();
        response.put("schemaVersion", document.schemaVersion());
        response.put("registryPath", registry.registryPath().toString());
        response.put("lastProjectId", document.lastProjectId());
        response.put("projects", document.projects().stream().map(this::projectView).toList());
        return response;
    }

    public Map<String, Object> register(Path repoRoot) throws IOException {
        registry.registerProject(repoRoot);
        return list();
    }

    private Map<String, Object> projectView(OperatorWorkspaceProject project) {
        var item = new LinkedHashMap<String, Object>();
        item.put("projectId", project.projectId());
        item.put("name", project.name());
        item.put("repoRoot", project.repoRoot());
        item.put("agentRunsDir", project.agentRunsDir());
        item.put("lastOpenedAt", project.lastOpenedAt());
        item.put("registeredWorkstreams", project.workstreams());
        try {
            var live = workstreams.list(Path.of(project.repoRoot()));
            var liveWorkstreams = live.get("workstreams");
            item.put("workstreams", liveWorkstreams);
            item.put("workstreamCount", liveWorkstreams instanceof java.util.List<?> list ? list.size() : 0);
            item.put("available", true);
        } catch (Exception e) {
            item.put("workstreams", project.workstreams());
            item.put("workstreamCount", project.workstreams().size());
            item.put("available", false);
            item.put("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        return item;
    }
}
