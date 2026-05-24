package dev.dual.runtime.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dual.core.workflow.WorkflowDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WorkflowDefinitionLoader {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public WorkflowDefinition loadOrDefault(Path workflowPath) throws IOException {
        if (workflowPath == null) {
            return new LifecycleWorkflowDefinitionFactory().thinMainline();
        }
        return load(workflowPath);
    }

    public WorkflowDefinition load(Path workflowPath) throws IOException {
        if (!Files.exists(workflowPath)) {
            throw new IOException("workflow file not found: " + workflowPath);
        }
        try {
            return objectMapper.readValue(workflowPath.toFile(), WorkflowDefinition.class);
        } catch (RuntimeException e) {
            throw new IOException("invalid workflow definition: " + workflowPath + ": " + e.getMessage(), e);
        }
    }
}
