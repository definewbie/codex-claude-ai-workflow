package dev.dual.cli;

import dev.dual.runtime.lifecycle.WorkflowDefinitionLoader;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "validate", description = "Validate a workflow definition JSON file.", mixinStandardHelpOptions = true)
public final class WorkflowValidateCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Workflow definition JSON file.")
    Path workflowPath;

    @Option(names = "--allow-unknown-handlers", description = "Allow handler types not built into this CLI.")
    boolean allowUnknownHandlers;

    @Override
    public Integer call() throws Exception {
        var path = workflowPath.toAbsolutePath().normalize();
        var definition = new WorkflowDefinitionLoader().load(path);
        var handlers = definition.nodes().stream()
                .map(node -> node.handlerType())
                .distinct()
                .sorted()
                .toList();
        var unknownHandlers = handlers.stream()
                .filter(handler -> !CliSupportedHandlers.TYPES.contains(handler))
                .toList();
        System.out.println("Workflow definition is valid.");
        System.out.println("Path: " + path);
        System.out.println("Workflow: " + definition.workflowId() + "@" + definition.version());
        System.out.println("Entry: " + definition.entryNodeId());
        System.out.println("Nodes: " + definition.nodes().size());
        System.out.println("Edges: " + definition.edges().size());
        System.out.println("Handlers: " + handlers);
        if (!unknownHandlers.isEmpty()) {
            System.out.println("Unknown handlers: " + unknownHandlers);
            if (!allowUnknownHandlers) {
                System.out.println("Use --allow-unknown-handlers if these handlers are provided by an external runtime.");
                return 1;
            }
        }
        return 0;
    }
}
