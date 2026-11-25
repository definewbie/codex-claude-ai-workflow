package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AcpxExecutionFactOwnershipBoundaryTest {
    private static final Path PRODUCTION_PACKAGE = Path.of("src/main/java/dev/dual/adapter/acpx");

    @Test
    void deletedPostHocControlOwnersCannotReturn() throws Exception {
        assertFalse(Files.exists(PRODUCTION_PACKAGE.resolve("AcpxFailureClassifier.java")));
        assertFalse(Files.exists(PRODUCTION_PACKAGE.resolve("AcpxSessionStreamFailureEvidenceReader.java")));

        var production = productionSource();
        assertFalse(production.contains("new AcpxFailureClassifier("));
        assertFalse(production.contains("failureClassifier.classify("));
        assertFalse(production.contains("transportFailureResult("));
        assertFalse(production.contains("interruptedOutputFailure("));
        assertFalse(production.contains("readStringIfExists("));
    }

    @Test
    void currentRunProcessResultsAreConstructedOnlyByTransport() throws Exception {
        assertEquals(
                Set.of("AcpxAgentTransport.java"),
                filesContaining("new ProcessResult(")
        );
    }

    @Test
    void wireGrammarHasOnlyLiveControlAndUsageOwners() throws Exception {
        assertEquals(
                Set.of("AgentLifecycleEventReader.java", "AcpxUsageReader.java"),
                filesContaining("AcpxStreamWireLine.parse(")
        );
    }

    private static Set<String> filesContaining(String marker) throws Exception {
        try (var files = Files.list(PRODUCTION_PACKAGE)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> contains(path, marker))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    private static String productionSource() throws Exception {
        var source = new StringBuilder();
        try (var files = Files.list(PRODUCTION_PACKAGE)) {
            for (var path : files
                    .filter(candidate -> candidate.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                source.append(Files.readString(path)).append('\n');
            }
        }
        return source.toString();
    }

    private static boolean contains(Path path, String marker) {
        try {
            return Files.readString(path).contains(marker);
        } catch (Exception failure) {
            throw new IllegalStateException("failed to inspect production source " + path, failure);
        }
    }
}
