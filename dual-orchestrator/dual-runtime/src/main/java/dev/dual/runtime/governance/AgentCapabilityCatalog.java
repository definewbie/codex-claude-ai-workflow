package dev.dual.runtime.governance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Typed projection of exploration capabilities available to an agent run.
 *
 * This is not a prompt policy and not workflow truth. It gives the
 * orchestrator a structured way to say what exploration surfaces are available
 * before an agent chooses how to inspect the repository.
 */
public final class AgentCapabilityCatalog {
    private final String workstreamId;
    private final String nodeDefinitionId;
    private final List<Map<String, Object>> capabilities;

    private AgentCapabilityCatalog(String workstreamId, String nodeDefinitionId, List<Map<String, Object>> capabilities) {
        this.workstreamId = text(workstreamId);
        this.nodeDefinitionId = text(nodeDefinitionId);
        this.capabilities = List.copyOf(capabilities);
    }

    public static AgentCapabilityCatalog detect(String workstreamId, String nodeDefinitionId) {
        return fromAvailableCommands(workstreamId, nodeDefinitionId, availableCommands());
    }

    public static AgentCapabilityCatalog detect(
            String workstreamId,
            String nodeDefinitionId,
            Map<String, Object> nodeConfig
    ) {
        var permission = CapabilityPermission.fromNodeConfig(nodeConfig);
        return fromAvailableCommands(
                workstreamId,
                nodeDefinitionId,
                availableCommands(),
                permission.directReadAllowed(),
                permission.shellAllowed()
        );
    }

    public static AgentCapabilityCatalog fromAvailableCommands(
            String workstreamId,
            String nodeDefinitionId,
            Collection<String> availableCommands
    ) {
        return fromAvailableCommands(workstreamId, nodeDefinitionId, availableCommands, true, true);
    }

    public static AgentCapabilityCatalog fromAvailableCommands(
            String workstreamId,
            String nodeDefinitionId,
            Collection<String> availableCommands,
            boolean directReadAllowed,
            boolean shellAllowed
    ) {
        var commands = new LinkedHashSet<String>();
        if (availableCommands != null) {
            for (var command : availableCommands) {
                var value = text(command).toLowerCase(Locale.ROOT);
                if (!value.isBlank()) {
                    commands.add(value);
                }
            }
        }

        var capabilities = new ArrayList<Map<String, Object>>();
        if (directReadAllowed) {
            capabilities.add(capability("direct_file_read", "Read", "Direct file content retrieval tool."));
        }
        if (shellAllowed && allowsTokenFilteredShell(nodeDefinitionId) && commands.contains("rtk")) {
            capabilities.add(capability("token_filtered_shell", "rtk", "Token-filtered shell command surface."));
        }
        if (shellAllowed && commands.contains("codegraph")) {
            capabilities.add(capability("symbol_index", "codegraph", "Symbol-level repository index."));
        } else if (shellAllowed && commands.contains("gitnexus")) {
            capabilities.add(capability("symbol_index", "gitnexus", "Symbol-level repository index."));
        }
        return new AgentCapabilityCatalog(workstreamId, nodeDefinitionId, capabilities);
    }

    public boolean hasKind(String kind) {
        var expected = text(kind);
        return capabilities.stream().anyMatch(item -> expected.equals(text(item.get("kind"))));
    }

    public List<String> capabilityKinds() {
        return capabilities.stream()
                .map(item -> text(item.get("kind")))
                .filter(value -> !value.isBlank())
                .toList();
    }

    public Map<String, Object> toDocument() {
        var document = new LinkedHashMap<String, Object>();
        document.put("schemaVersion", 1);
        document.put("artifactType", "agent_capability_catalog");
        document.put("workstreamId", workstreamId);
        document.put("nodeDefinitionId", nodeDefinitionId);
        document.put("capabilities", capabilities);
        return document;
    }

    private static Map<String, Object> capability(String kind, String provider, String description) {
        var item = new LinkedHashMap<String, Object>();
        item.put("kind", kind);
        item.put("provider", provider);
        item.put("description", description);
        return item;
    }

    private static boolean allowsTokenFilteredShell(String nodeDefinitionId) {
        return !"intake.claude_brainstorm".equals(text(nodeDefinitionId));
    }

    private static Set<String> availableCommands() {
        var result = new LinkedHashSet<String>();
        for (var command : List.of("rtk", "codegraph", "gitnexus")) {
            if (commandAvailable(command)) {
                result.add(command);
            }
        }
        return result;
    }

    private static boolean commandAvailable(String command) {
        var path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return false;
        }
        for (var dir : path.split(java.io.File.pathSeparator)) {
            if (Files.isExecutable(Path.of(dir).resolve(command))) {
                return true;
            }
        }
        return false;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record CapabilityPermission(boolean directReadAllowed, boolean shellAllowed) {
        static CapabilityPermission fromNodeConfig(Map<String, Object> nodeConfig) {
            if (nodeConfig != null && Boolean.TRUE.equals(nodeConfig.get("approve_all"))) {
                return new CapabilityPermission(true, true);
            }
            var profile = text(nodeConfig == null ? null : nodeConfig.get("permission_profile"));
            return switch (profile) {
                case "contract_discovery" -> new CapabilityPermission(true, false);
                case "observe", "review" -> new CapabilityPermission(true, false);
                case "implement" -> new CapabilityPermission(true, true);
                default -> new CapabilityPermission(true, false);
            };
        }
    }
}
