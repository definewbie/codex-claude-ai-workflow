package dev.dual.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.provider.ProviderBillingCsvCalibration;
import dev.dual.core.provider.ProviderPricingCatalog;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(
        name = "billing-csv",
        description = "Compare a provider billing CSV export with the configured provider pricing catalog.",
        mixinStandardHelpOptions = true
)
public final class CalibrationBillingCsvCommand implements Callable<Integer> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Option(names = "--workdir", description = "Workspace directory used to load .agent/provider-pricing.json. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--csv", required = true, description = "Provider billing CSV export to calibrate.")
    Path csv;

    @Override
    public Integer call() throws Exception {
        var root = workdir.toAbsolutePath().normalize();
        var csvPath = csv.toAbsolutePath().normalize();
        var pricing = ProviderPricingCatalog.load(root, OBJECT_MAPPER);
        var report = ProviderBillingCsvCalibration.parse(
                Files.readString(csvPath, StandardCharsets.UTF_8),
                pricing
        );

        System.out.println("Billing CSV Calibration");
        System.out.println("CSV: " + csvPath);
        System.out.println("Workspace: " + root);
        System.out.println("Rows: " + report.rows().size());
        System.out.println("Exported total: " + report.exportedTotal());
        System.out.println("Dual calculated total: " + report.calculatedTotal());
        System.out.println("Consistency: " + report.consistency());
        System.out.println();
        System.out.println("Provider\tModel\tCurrency\tExported\tDualCalculated\tConsistency");
        for (var row : report.rows()) {
            System.out.println(row.providerId() + "\t"
                    + row.model() + "\t"
                    + row.currency() + "\t"
                    + row.exportedCost() + "\t"
                    + row.calculatedCost() + "\t"
                    + row.consistency());
        }
        return 0;
    }
}
