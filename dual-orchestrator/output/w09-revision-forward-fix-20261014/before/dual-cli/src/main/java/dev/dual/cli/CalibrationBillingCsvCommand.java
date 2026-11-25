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

    @Option(names = "--cost-csv", description = "Optional provider cost summary CSV export to compare against --csv token amount rows.")
    Path costCsv;

    @Override
    public Integer call() throws Exception {
        var root = workdir.toAbsolutePath().normalize();
        var csvPath = csv.toAbsolutePath().normalize();
        var pricing = ProviderPricingCatalog.load(root, OBJECT_MAPPER);
        var csvText = Files.readString(csvPath, StandardCharsets.UTF_8);
        var report = ProviderBillingCsvCalibration.parse(csvText, pricing);

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
        if (costCsv != null) {
            var costCsvPath = costCsv.toAbsolutePath().normalize();
            var paired = ProviderBillingCsvCalibration.compareUsageAndCost(
                    csvText,
                    Files.readString(costCsvPath, StandardCharsets.UTF_8),
                    pricing
            );
            System.out.println();
            System.out.println("Cost Summary CSV: " + costCsvPath);
            System.out.println("Usage exported total: " + paired.usageReport().exportedTotal());
            System.out.println("Cost summary exported total: " + paired.costReport().exportedTotal());
            System.out.println("Exported delta: " + paired.exportedDelta());
            System.out.println("Usage vs cost summary: " + paired.consistency());
            if ("matches_cost_summary".equals(paired.consistency())) {
                System.out.println("Provider amount/cost files are consistent.");
            }
        }
        return 0;
    }
}
