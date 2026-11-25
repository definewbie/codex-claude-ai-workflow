package dev.dual.cli;

import dev.dual.runtime.delivery.DeliveryApplicationService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "resolve", description = "Record an explicit delivery resolution; never blindly repeat an unknown application.", mixinStandardHelpOptions = true)
public final class DeliveryResolveCommand implements Callable<Integer> {
    @Option(names = "--workdir") Path workdir = Path.of(".");
    @Option(names = "--decision", required = true) Path decision;
    @Parameters(index = "0") String workstreamId;

    @Override public Integer call() throws Exception {
        byte[] bytes;
        try (var input = Files.newInputStream(decision)) { bytes = input.readNBytes(64 * 1024 + 1); }
        var result = new DeliveryApplicationService(CliLedgerFactory.create()).resolve(workdir, workstreamId, bytes);
        System.out.println("Delivery: " + result.status());
        System.out.println("Reason: " + result.reasonCode() + " - " + result.reason());
        System.out.println("Stop basis: " + result.stopBasis());
        System.out.println("Next action: " + result.nextAction());
        result.nextCommands().forEach(System.out::println);
        return java.util.Set.of("BLOCKED", "BUSY", "PENDING", "INVALID").contains(result.status()) ? 1 : 0;
    }
}
