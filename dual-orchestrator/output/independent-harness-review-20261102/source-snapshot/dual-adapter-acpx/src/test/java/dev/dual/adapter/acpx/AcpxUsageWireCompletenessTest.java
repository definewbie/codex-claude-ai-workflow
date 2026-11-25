package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcpxUsageWireCompletenessTest {
    @Test void partialTokenObjectIsNotMeasuredUsage() {
        assertNull(AcpxStreamWireLine.parse("{\"id\":3,\"result\":{\"stopReason\":\"end_turn\",\"usage\":{\"totalTokens\":100}}}").result().usage());
    }
}
