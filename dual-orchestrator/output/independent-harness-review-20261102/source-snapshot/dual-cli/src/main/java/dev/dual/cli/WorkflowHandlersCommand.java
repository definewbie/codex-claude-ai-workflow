package dev.dual.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Component
@Command(
        name = "handlers",
        mixinStandardHelpOptions = true,
        description = "List workflow handler types built into this CLI."
)
public final class WorkflowHandlersCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        CliSupportedHandlers.TYPES.stream()
                .sorted()
                .forEach(System.out::println);
        return 0;
    }
}
