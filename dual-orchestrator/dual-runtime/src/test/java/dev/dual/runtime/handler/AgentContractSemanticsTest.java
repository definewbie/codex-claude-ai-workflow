package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for typed {@code reread_reasons} handling: structural validation of the
 * raw envelope field, and typed reading on {@link AgentOutputContractDocument}.
 */
class AgentContractSemanticsTest {
    private static final String ALLOWED = "exact current content is needed before editing";

    // --- validation of raw reread_reasons ---

    @Test
    void rereadReasonsAbsentOrEmptyIsValid() {
        assertTrue(AgentContractSemantics.validateRereadReasons(null).isEmpty());
        assertTrue(AgentContractSemantics.validateRereadReasons(List.of()).isEmpty());
    }

    @Test
    void rereadReasonsValidPasses() {
        var raw = List.of(Map.of(
                "path", "src/a.py",
                "reason", ALLOWED,
                "stage", "implement",
                "evidence", "editing transition map requires current code"));
        assertTrue(AgentContractSemantics.validateRereadReasons(raw).isEmpty());
    }

    @Test
    void rereadReasonsValidWithoutOptionalStageOrEvidence() {
        var raw = List.of(Map.of("path", "src/a.py", "reason", ALLOWED));
        assertTrue(AgentContractSemantics.validateRereadReasons(raw).isEmpty());
    }

    @Test
    void rereadReasonEmptyPathRejected() {
        var raw = List.of(Map.of("path", "   ", "reason", ALLOWED));
        var fail = AgentContractSemantics.validateRereadReasons(raw);
        assertTrue(fail.isPresent());
        assertTrue(fail.get().errorCode().contains("REREAD_REASON"), fail.get().errorCode());
    }

    @Test
    void rereadReasonDisallowedReasonRejected() {
        var raw = List.of(Map.of("path", "src/a.py", "reason", "because I felt like it"));
        assertTrue(AgentContractSemantics.validateRereadReasons(raw).isPresent());
    }

    @Test
    void rereadReasonBlankStageIfPresentRejected() {
        var raw = List.of(Map.of("path", "src/a.py", "reason", ALLOWED, "stage", "   "));
        assertTrue(AgentContractSemantics.validateRereadReasons(raw).isPresent());
    }

    @Test
    void rereadReasonBlankEvidenceIfPresentRejected() {
        var raw = List.of(Map.of("path", "src/a.py", "reason", ALLOWED, "evidence", "  "));
        assertTrue(AgentContractSemantics.validateRereadReasons(raw).isPresent());
    }

    @Test
    void rereadReasonEntryNotAMapIsRejected() {
        var raw = List.of("not-a-map");
        assertTrue(AgentContractSemantics.validateRereadReasons(raw).isPresent());
    }

    // --- typed reading on AgentOutputContractDocument ---

    @Test
    void documentReadsRereadReasonsTyped() {
        var map = new LinkedHashMap<String, Object>();
        map.put("node_id", "n");
        map.put("decision", "READY_FOR_REVIEW");
        map.put("summary", "ok");
        map.put("reread_reasons", List.of(Map.of(
                "path", "src/a.py", "reason", ALLOWED, "stage", "implement", "evidence", "editing map")));
        var doc = AgentOutputContractDocument.from(map);
        assertEquals(1, doc.rereadReasons().size());
        var reason = doc.rereadReasons().get(0);
        assertEquals("src/a.py", reason.path());
        assertEquals(ALLOWED, reason.reason());
        assertEquals("implement", reason.stage());
        assertEquals("editing map", reason.evidence());
    }

    @Test
    void documentRereadReasonsAbsentIsEmpty() {
        var doc = AgentOutputContractDocument.from(Map.of("node_id", "n", "decision", "READY_FOR_REVIEW", "summary", "ok"));
        assertTrue(doc.rereadReasons().isEmpty());
    }
}
