package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

/**
 * Migrates ledger JSON files from older schema versions to the current version.
 * Each migration step is version N → N+1, applied sequentially.
 */
final class LedgerMigrator {
    private static final int CURRENT_SCHEMA_VERSION = 2;

    static int currentVersion() { return CURRENT_SCHEMA_VERSION; }

    /**
     * Migrate a JSON document to the current schema version.
     * Returns the migrated JSON string.
     *
     * @param json       the raw JSON string to migrate
     * @param fromVersion the schema version of the input JSON
     * @return the migrated JSON string
     * @throws IOException if migration or JSON parsing fails
     */
    static String migrate(String json, int fromVersion) throws IOException {
        if (fromVersion <= 0) {
            throw new IOException("invalid schema version: " + fromVersion);
        }
        if (fromVersion > CURRENT_SCHEMA_VERSION) {
            throw new IOException("schema version " + fromVersion + " is newer than current version " + CURRENT_SCHEMA_VERSION + " — cannot downgrade");
        }
        if (fromVersion == CURRENT_SCHEMA_VERSION) {
            return json;
        }

        var mapper = new ObjectMapper();
        var node = mapper.readTree(json);

        // v1 → v2: add condition field to edges (null = unconditional)
        if (fromVersion < 2) {
            node = v1toV2((ObjectNode) node, mapper);
        }

        // Future migrations go here:
        // if (fromVersion < 3) { node = v2toV3(...); }

        // Update the schema version field
        ((ObjectNode) node).put("schemaVersion", CURRENT_SCHEMA_VERSION);
        return mapper.writeValueAsString(node);
    }

    /** v1 → v2: Edge definitions gained a condition field. */
    private static JsonNode v1toV2(ObjectNode root, ObjectMapper mapper) {
        if (root.has("edges") && root.get("edges").isArray()) {
            var edges = root.withArray("edges");
            for (var edge : edges) {
                if (edge.isObject() && !edge.has("condition")) {
                    ((ObjectNode) edge).putNull("condition");
                }
            }
        }
        return root;
    }
}
