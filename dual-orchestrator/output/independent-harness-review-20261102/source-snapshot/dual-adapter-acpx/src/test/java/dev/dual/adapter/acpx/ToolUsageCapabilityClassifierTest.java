package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolUsageCapabilityClassifierTest {
    @Test
    void classifiesDirectReadsAndCatalogDeclaredProviders() {
        var usage = new AcpxToolUsage(
                1,
                3,
                1,
                0,
                2,
                0,
                Map.of("Read", 1, "Bash", 2),
                List.of("Read", "Bash"),
                List.of("README.md"),
                List.of(),
                List.of("rtk grep issueWorkflow src", "codegraph query Symbol"),
                List.of("rtk", "codegraph"));
        var catalog = catalog(List.of(
                capability("direct_file_read", "Read"),
                capability("token_filtered_shell", "rtk"),
                capability("symbol_index", "codegraph")));

        var result = ToolUsageCapabilityClassifier.classify(usage, catalog);

        assertEquals(List.of("direct_file_read", "token_filtered_shell", "symbol_index"), result);
        assertEquals(
                List.of(
                        Map.of("kind", "direct_file_read", "provider", "Read", "source", "tool_id"),
                        Map.of("kind", "token_filtered_shell", "provider", "rtk", "source", "command_executable"),
                        Map.of("kind", "symbol_index", "provider", "codegraph", "source", "command_executable")
                ),
                ToolUsageCapabilityClassifier.classifyUsages(usage, catalog));
    }

    @Test
    void usesCatalogProviderNamesRatherThanHardcodedToolNames() {
        var usage = new AcpxToolUsage(
                1,
                1,
                0,
                0,
                1,
                0,
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("symbol-router lookup PatientTimeline"),
                List.of("symbol-router"));
        var catalog = catalog(List.of(capability("symbol_index", "symbol-router")));

        assertEquals(List.of("symbol_index"), ToolUsageCapabilityClassifier.classify(usage, catalog));
    }

    @Test
    void ignoresCommandsWhenProviderIsNotDeclaredInCatalog() {
        var usage = new AcpxToolUsage(
                1,
                1,
                0,
                0,
                1,
                0,
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("codegraph query Symbol"),
                List.of("codegraph"));
        var catalog = catalog(List.of(capability("token_filtered_shell", "rtk")));

        assertEquals(List.of(), ToolUsageCapabilityClassifier.classify(usage, catalog));
    }

    @Test
    void ignoresProviderNamesThatOnlyAppearInCommandArguments() {
        var usage = new AcpxToolUsage(
                1,
                3,
                0,
                0,
                3,
                0,
                Map.of("Bash", 3),
                List.of("Bash"),
                List.of(),
                List.of(),
                List.of("grep codegraph docs", "echo rtk", "cat docs/gitnexus.md"),
                List.of("grep", "echo", "cat"));
        var catalog = catalog(List.of(
                capability("symbol_index", "codegraph"),
                capability("token_filtered_shell", "rtk"),
                capability("repo_graph", "gitnexus")));

        assertEquals(List.of(), ToolUsageCapabilityClassifier.classify(usage, catalog));
    }

    @Test
    void matchesNormalizedCommandExecutable() {
        var usage = new AcpxToolUsage(
                1,
                2,
                0,
                0,
                2,
                0,
                Map.of("Bash", 2),
                List.of("Bash"),
                List.of(),
                List.of(),
                List.of("/usr/local/bin/rtk grep x", "./codegraph query Symbol"),
                List.of("rtk", "codegraph"));
        var catalog = catalog(List.of(
                capability("token_filtered_shell", "rtk"),
                capability("symbol_index", "codegraph")));

        assertEquals(List.of("token_filtered_shell", "symbol_index"), ToolUsageCapabilityClassifier.classify(usage, catalog));
    }

    @Test
    void grepAndGlobDoNotCountAsDirectFileRead() {
        var usage = new AcpxToolUsage(
                1,
                2,
                2,
                0,
                0,
                0,
                Map.of("Grep", 1, "Glob", 1),
                List.of("Grep", "Glob"),
                List.of("src", "docs"),
                List.of(),
                List.of(),
                List.of());
        var catalog = catalog(List.of(capability("direct_file_read", "Read")));

        assertEquals(List.of(), ToolUsageCapabilityClassifier.classify(usage, catalog));
    }

    @Test
    void classifiedRtkReadDoesNotCountAsNativeReadTool() {
        var usage = new AcpxToolUsage(
                1,
                1,
                1,
                0,
                0,
                0,
                Map.of("Read", 1),
                List.of("Bash"),
                List.of("docs/product.md"),
                List.of(),
                List.of("rtk read docs/product.md"),
                List.of("rtk"));
        var catalog = catalog(List.of(
                capability("direct_file_read", "Read"),
                capability("token_filtered_shell", "rtk")));

        assertEquals(List.of("token_filtered_shell"), ToolUsageCapabilityClassifier.classify(usage, catalog));
    }

    private static Map<String, Object> catalog(List<Map<String, Object>> capabilities) {
        return Map.of("capabilities", capabilities);
    }

    private static Map<String, Object> capability(String kind, String provider) {
        return Map.of("kind", kind, "provider", provider);
    }
}
