package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dual.core.workflow.NodeResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HumanReviewGateHandler implements GateHandler {
    private static final int MAX_INPUT_SNIPPET_CHARS = 4000;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public String type() {
        return "human.review";
    }

    @Override
    public NodeResult openOrInspect(GateContext context) throws Exception {
        var allowedActions = allowedActions(context);
        var packetRef = reviewPacketRef(context, ".md");
        var packetJsonRef = reviewPacketRef(context, ".json");
        writeReviewPacket(context, allowedActions, packetRef, packetJsonRef);

        return NodeResult.waiting("open", Map.of(
                "allowed_actions", allowedActions,
                "review_packet", packetRef,
                "review_packet_json", packetJsonRef
        ));
    }

    @SuppressWarnings("unchecked")
    private List<String> allowedActions(GateContext context) {
        var configured = context.definition().config().getOrDefault("allowed_actions", List.of("approve", "revise"));
        if (configured instanceof List<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        return List.of("approve", "revise");
    }

    private String reviewPacketRef(GateContext context, String extension) {
        return ".agent/workstreams/" + context.workstreamId()
                + "/reviews/" + context.instance().nodeInstanceId() + extension;
    }

    private void writeReviewPacket(
            GateContext context,
            List<String> allowedActions,
            String packetRef,
            String packetJsonRef
    ) throws Exception {
        var packetPath = context.workspaceRoot().resolve(packetRef);
        var packetJsonPath = context.workspaceRoot().resolve(packetJsonRef);
        Files.createDirectories(packetPath.getParent());

        var packet = new LinkedHashMap<String, Object>();
        packet.put("schemaVersion", 1);
        packet.put("workstreamId", context.workstreamId());
        packet.put("gateNodeInstanceId", context.instance().nodeInstanceId());
        packet.put("gateNodeDefinitionId", context.instance().nodeDefinitionId());
        packet.put("stageId", context.instance().stageId());
        packet.put("iterationId", context.instance().iterationId());
        packet.put("allowedActions", allowedActions);
        packet.put("inputRefs", context.instance().inputRefs());
        packet.put("reviewPacketPath", packetRef);
        packet.put("createdAt", Instant.now().toString());

        objectMapper.writeValue(packetJsonPath.toFile(), packet);
        Files.writeString(packetPath, renderMarkdown(context, allowedActions), StandardCharsets.UTF_8);
    }

    private String renderMarkdown(GateContext context, List<String> allowedActions) throws Exception {
        var builder = new StringBuilder();
        builder.append("# Human Review Packet\n\n");
        builder.append("- Workstream: ").append(context.workstreamId()).append("\n");
        builder.append("- Gate: ").append(context.instance().nodeDefinitionId()).append("\n");
        builder.append("- Gate Instance: ").append(context.instance().nodeInstanceId()).append("\n");
        builder.append("- Stage: ").append(context.instance().stageId()).append("\n");
        builder.append("- Iteration: ").append(context.instance().iterationId()).append("\n");
        builder.append("- Allowed Actions: ").append(String.join(", ", allowedActions)).append("\n\n");

        builder.append("## Review Focus\n\n");
        builder.append("- Read the referenced artifacts before approving.\n");
        builder.append("- Use `approve` only when the current gate is good enough to continue.\n");
        builder.append("- Use `revise` when the agent should address concrete feedback first.\n\n");

        builder.append("## Input Artifacts\n\n");
        if (context.instance().inputRefs().isEmpty()) {
            builder.append("No input artifacts were attached to this gate.\n");
        } else {
            for (var inputRef : context.instance().inputRefs()) {
                builder.append("### ").append(inputRef).append("\n\n");
                builder.append("```markdown\n");
                builder.append(readSnippet(context.workspaceRoot(), inputRef));
                builder.append("\n```\n\n");
            }
        }
        return builder.toString();
    }

    private String readSnippet(Path workspaceRoot, String ref) throws Exception {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var path = root.resolve(ref).normalize();
        if (!path.startsWith(root)) {
            return "(artifact outside workspace)";
        }
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return "(artifact not found)";
        }
        var content = Files.readString(path, StandardCharsets.UTF_8);
        if (content.length() <= MAX_INPUT_SNIPPET_CHARS) {
            return content;
        }
        return content.substring(0, MAX_INPUT_SNIPPET_CHARS)
                + "\n\n... truncated for review packet ...";
    }
}
