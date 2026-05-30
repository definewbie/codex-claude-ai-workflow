package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureClassifyRegistryTest {

    @Test
    void registryHasAcceptanceClassifyFailure() {
        assertTrue(ContractRegistry.hasContract("acceptance.classify_failure"));
    }

    @Test
    void classifyFailureContractHasCorrectAllowedDecisions() {
        var contract = ContractRegistry.forNode("acceptance.classify_failure");
        var allowed = contract.allowedDecisions();
        // 6 REJECT_* outcomes
        assertTrue(allowed.contains("REJECT_CONTRACT_GAP"));
        assertTrue(allowed.contains("REJECT_CONTRACT_AMBIGUITY"));
        assertTrue(allowed.contains("REJECT_IMPLEMENTATION_BUG"));
        assertTrue(allowed.contains("REJECT_CONTEXT_INSUFFICIENT"));
        assertTrue(allowed.contains("REJECT_TOOL_LIMITATION"));
        assertTrue(allowed.contains("REJECT_REVIEW_NOISE"));
        // 6 NOTED_* outcomes
        assertTrue(allowed.contains("NOTED_CONTRACT_GAP"));
        assertTrue(allowed.contains("NOTED_CONTRACT_AMBIGUITY"));
        assertTrue(allowed.contains("NOTED_IMPLEMENTATION_BUG"));
        assertTrue(allowed.contains("NOTED_CONTEXT_INSUFFICIENT"));
        assertTrue(allowed.contains("NOTED_TOOL_LIMITATION"));
        assertTrue(allowed.contains("NOTED_REVIEW_NOISE"));
        assertEquals(12, allowed.size());
    }

    @Test
    void classifyFailureContractRequiresDecisionAndSummary() {
        var contract = ContractRegistry.forNode("acceptance.classify_failure");
        assertTrue(contract.requiredKeys().contains("decision"));
        assertTrue(contract.requiredKeys().contains("summary"));
    }

    @Test
    void classifyFailureContractHasOptionalKeys() {
        var contract = ContractRegistry.forNode("acceptance.classify_failure");
        assertTrue(contract.optionalKeys().contains("category"));
        assertTrue(contract.optionalKeys().contains("reasoning"));
        assertTrue(contract.optionalKeys().contains("matched_rule"));
        assertTrue(contract.optionalKeys().contains("classification_ref"));
    }

    @Test
    void eventTypesHasFailureClassified() {
        assertEquals("failure_classified", ContractEventTypes.FAILURE_CLASSIFIED);
    }
}
