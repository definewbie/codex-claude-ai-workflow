package dev.dual.cli;

import dev.dual.runtime.lifecycle.WorkflowDefinitionLoader;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "validate", description = "Validate a workflow definition JSON file.", mixinStandardHelpOptions = true)
public final class WorkflowValidateCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Workflow definition JSON file.")
    Path workflowPath;

    @Override
    public Integer call() throws Exception {
        var path = workflowPath.toAbsolutePath().normalize();
        var definition = new WorkflowDefinitionLoader().load(path);
        System.out.println("Workflow definition is valid.");
        System.out.println("Path: " + path);
        System.out.println("Workflow: " + definition.workflowId() + "@" + definition.version());
        System.out.println("Entry: " + definition.entryNodeId());
        System.out.println("Nodes: " + definition.nodes().size());
        System.out.println("Edges: " + definition.edges().size());
        System.out.println("Handlers: " + definition.nodes().stream()
                .map(node -> node.handlerType())
                .distinct()
                .sorted()
                .toList());
        return 0;
    }
}
