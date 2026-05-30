package dev.dual.ledger.file;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerMigratorTest {

    @Test
    void sameVersion_noMigration() throws Exception {
        var json = """
                {"schemaVersion": 2, "workflowId": "test", "version": 1, "entryNodeId": "a", "nodes": [], "edges": []}
                """;
        var result = LedgerMigrator.migrate(json, 2);
        // Same version → returned as-is (preserves original formatting)
        assertTrue(result.contains("workflowId"));
        assertTrue(result.contains("test"));
    }

    @Test
    void futureVersion_throws() {
        var json = """
                {"schemaVersion": 99}
                """;
        var ex = assertThrows(IOException.class, () -> LedgerMigrator.migrate(json, 99));
        assertTrue(ex.getMessage().contains("newer than current version"));
    }

    @Test
    void invalidVersion_throws() {
        var json = """
                {"schemaVersion": 0}
                """;
        assertThrows(IOException.class, () -> LedgerMigrator.migrate(json, 0));
    }

    @Test
    void v1toV2_addsConditionFieldToEdges() throws Exception {
        var json = """
                {"schemaVersion":1,"workflowId":"test","version":1,"entryNodeId":"a","nodes":[],"edges":[{"from":"a","on":"DONE","to":"b"}]}
                """;
        var result = LedgerMigrator.migrate(json, 1);
        assertTrue(result.contains("condition"), "v1 edges should get condition field");
        assertTrue(result.contains("schemaVersion"), "result should contain schemaVersion");
        // Schema version should be updated to 2
        assertTrue(result.contains("2"), "schema version should be updated to 2");
    }

    @Test
    void currentVersion_returnsExpectedValue() {
        assertTrue(LedgerMigrator.currentVersion() >= 2);
    }
}
