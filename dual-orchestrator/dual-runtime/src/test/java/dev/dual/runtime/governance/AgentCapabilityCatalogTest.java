package dev.dual.runtime.governance;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCapabilityCatalogTest {
    @Test
    void intakeContractDiscoveryDoesNotAdvertiseDirectReadOrShellCapability() {
        var catalog = AgentCapabilityCatalog.fromAvailableCommands(
                "ws-001",
                "intake.claude_brainstorm",
                Set.of("rtk", "codegraph")
        );

        var document = catalog.toDocument();

        assertEquals("agent_capability_catalog", document.get("artifactType"));
        assertEquals(false, catalog.hasKind("token_filtered_shell"));
        assertTrue(catalog.hasKind("symbol_index"));
        assertEquals(false, catalog.hasKind("direct_file_read"));
        assertTrue(document.toString().contains("codegraph"));
    }

    @Test
    void nonIntakeNodesKeepDirectFileReadButDoNotInventUnavailableSymbolIndex() {
        var catalog = AgentCapabilityCatalog.fromAvailableCommands(
                "ws-001",
                "implement.claude_implement",
                Set.of()
        );

        assertTrue(catalog.hasKind("direct_file_read"));
        assertEquals(false, catalog.hasKind("symbol_index"));
    }
}
