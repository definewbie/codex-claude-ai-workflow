package dev.dual.ledger.file;

import java.util.List;

/** An advisory request, never an executable action or authorization. */
public record HumanActionRequest(String requestId, String gateNodeInstanceId, List<String> sourceRefs,
                                 String expectedResult, String whyExistingActionsInsufficient) {
    public HumanActionRequest {
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,80}"))
            throw new IllegalArgumentException("requestId must be 1-80 letters, digits, underscores or hyphens");
        require(gateNodeInstanceId, "gateNodeInstanceId", 200);
        require(expectedResult, "expectedResult", 4000);
        require(whyExistingActionsInsufficient, "whyExistingActionsInsufficient", 4000);
        if (sourceRefs == null) throw new IllegalArgumentException("sourceRefs is required");
        sourceRefs = List.copyOf(sourceRefs);
    }

    private static void require(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max)
            throw new IllegalArgumentException(name + " is required and must not exceed " + max + " characters");
    }
}
