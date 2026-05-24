package dev.dual.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dual.runtime.lifecycle.LifecycleWorkflowDefinitionFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "print-default", description = "Print or write the built-in default workflow definition.", mixinStandardHelpOptions = true)
public final class WorkflowPrintDefaultCommand implements Callable<Integer> {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Option(names = "--output", description = "Output file. Defaults to stdout.")
    Path outputPath;

    @Override
    public Integer call() throws Exception {
        var definition = new LifecycleWorkflowDefinitionFactory().thinMainline();
        var json = objectMapper.writeValueAsString(definition) + System.lineSeparator();
        if (outputPath == null) {
            System.out.print(json);
            return 0;
        }
        var path = outputPath.toAbsolutePath().normalize();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, json);
        System.out.println("Default workflow written.");
        System.out.println("Path: " + path);
        System.out.println("Workflow: " + definition.workflowId() + "@" + definition.version());
        System.out.println("Nodes: " + definition.nodes().size());
        System.out.println("Edges: " + definition.edges().size());
        return 0;
    }
}
