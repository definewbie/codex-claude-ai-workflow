package dev.dual.runtime.governance;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCapabilityCatalogTest {
    @Test
    void intakeContractDiscoveryAdvertisesDirectReadButNotShellCapability() {
        var catalog = AgentCapabilityCatalog.fromAvailableCommands(
                "ws-001",
                "intake.claude_brainstorm",
                Set.of("rtk", "codegraph"),
                true,
                false
        );

        var document = catalog.toDocument();

        assertEquals("agent_capability_catalog", document.get("artifactType"));
        assertEquals(false, catalog.hasKind("token_filtered_shell"));
        assertEquals(false, catalog.hasKind("symbol_index"));
        assertEquals(true, catalog.hasKind("direct_file_read"));
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

    @Test
    void observeProfileDoesNotAdvertiseShellBackedCapabilities() {
        var catalog = AgentCapabilityCatalog.detect(
                "ws-001",
                "design.claude_design",
                Map.of("permission_profile", "observe")
        );

        assertTrue(catalog.hasKind("direct_file_read"));
        assertEquals(false, catalog.hasKind("token_filtered_shell"));
        assertEquals(false, catalog.hasKind("symbol_index"));
    }

    @Test
    void implementProfileMayAdvertiseShellBackedCapabilitiesWhenInstalled() {
        var catalog = AgentCapabilityCatalog.fromAvailableCommands(
                "ws-001",
                "implement.claude_implement",
                Set.of("rtk", "codegraph"),
                true,
                true
        );

        assertTrue(catalog.hasKind("direct_file_read"));
        assertTrue(catalog.hasKind("token_filtered_shell"));
        assertTrue(catalog.hasKind("symbol_index"));
    }

    @Test
    void namedProfileRemainsAuthoritativeWhenLegacyApproveAllIsAlsoPresent() {
        var catalog = AgentCapabilityCatalog.detect(
                "ws-001",
                "design.codex_arch_review",
                Map.of("permission_profile", "review", "approve_all", true)
        );

        assertTrue(catalog.hasKind("direct_file_read"));
        assertEquals(false, catalog.hasKind("token_filtered_shell"));
        assertEquals(false, catalog.hasKind("symbol_index"));
    }
}
