package dev.dual.core.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic schema examples; hashes here do not prove approval or tool execution. */
class PhasedContractTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    void approvedGoalCanDescribeAcceptanceWithoutGrantingProductWrites() throws Exception {
        var goal = parse(example("goal-contract.json"));
        assertEquals(java.util.List.of(), TaskContractValidator.validate(goal));
        assertTrue(goal.scope().included().isEmpty());
        var serialized = MAPPER.valueToTree(goal);
        assertEquals("A1", serialized.path("acceptance_criteria").get(0).path("id").asText());
        assertEquals("GOAL", serialized.path("phase").asText());
    }

    @Test
    void executionProposalBindsExactFilesAndEveryApprovedCriterion() throws Exception {
        var document = example("execution-contract.json");
        assertEquals(java.util.List.of(), TaskContractValidator.validate(parse(document)));
        document.withArray("verification_bindings").remove(1);
        assertFalse(TaskContractValidator.validate(parse(document)).isEmpty(),
                "an execution contract cannot silently lose the illegal-input acceptance exit");
    }

    @Test
    void goalCannotCarryImplementationScopeAndExecutionCannotBorrowEmptyGoalScope() throws Exception {
        var goal = example("goal-contract.json");
        var execution = example("execution-contract.json");
        goal.set("scope", execution.get("scope").deepCopy());
        execution.withObject("scope").putArray("included");
        assertFalse(TaskContractValidator.validate(parse(goal)).isEmpty());
        assertFalse(TaskContractValidator.validate(parse(execution)).isEmpty());
    }

    @Test
    void phaseAndSchemaCannotBeInferredFromOldFields() throws Exception {
        var goal = example("goal-contract.json");
        goal.remove("phase");
        assertThrows(Exception.class, () -> parse(goal));
        var old = example("execution-contract.json");
        old.remove("schema_version");
        assertThrows(Exception.class, () -> parse(old));
    }

    @Test
    void duplicateClauseIdsAndUnknownVerificationKindsAreNotAuthorized() throws Exception {
        var document = example("execution-contract.json");
        ((ObjectNode) document.withArray("acceptance_criteria").get(1)).put("id", "A1");
        assertFalse(TaskContractValidator.validate(parse(document)).isEmpty());
        var unsupported = example("execution-contract.json");
        ((ObjectNode) unsupported.withArray("verification_bindings").get(0))
                .put("evidence_kind", "MODEL_SAYS_PASSED");
        assertThrows(Exception.class, () -> parse(unsupported));
    }

    private static TaskContract parse(ObjectNode document) throws Exception {
        return MAPPER.treeToValue(document, TaskContract.class);
    }

    @Test
    void executionCannotTreatUnspecifiedExitEvidenceAsOptional() throws Exception {
        var document = example("execution-contract.json");
        for (var binding : document.withArray("verification_bindings"))
            ((ObjectNode) binding).remove("actual_exit_required");
        assertTrue(TaskContractValidator.validate(parse(document)).stream()
                .anyMatch(error -> error.contains("actual_exit_required")),
                "an execution approval must explicitly decide each criterion's exit evidence requirement");
    }

    @Test
    void approvedEvidenceCanRequireExitForOneCriterionWithoutImposingItOnAnother() throws Exception {
        var document = example("execution-contract.json");
        ((ObjectNode) document.withArray("verification_bindings").get(0))
                .put("actual_exit_required", true).put("expected_exit_code", 0);
        var contract = parse(document);
        assertTrue(TaskContractValidator.validate(contract).isEmpty());
        assertTrue(contract.verificationBindings().getFirst().actualExitRequired());
        assertEquals(0, contract.verificationBindings().getFirst().expectedExitCode());
        assertFalse(contract.verificationBindings().get(1).actualExitRequired());
        assertEquals(document.path("verification_bindings"), MAPPER.valueToTree(contract).path("verification_bindings"));
        ((ObjectNode) document.withArray("verification_bindings").get(0)).put("actual_exit_required", false);
        assertTrue(TaskContractValidator.validate(parse(document)).stream().anyMatch(s -> s.contains("expected_exit_code")));
    }

    private static ObjectNode example(String name) throws Exception {
        try (var input = PhasedContractTest.class.getResourceAsStream(name)) {
            assertNotNull(input);
            return (ObjectNode) MAPPER.readTree(input);
        }
    }
}
