package dev.dual.core.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static TaskContract sampleContract(ContractStatus status,
                                                List<String> acceptanceCriteria,
                                                List<String> humanQuestions) {
        return new TaskContract(
                "tc-001",
                1,
                Instant.parse("2026-05-30T12:00:00Z"),
                "Implement the foo feature",
                new TaskContract.Scope(
                        List.of("foo-api", "foo-service"),
                        List.of("bar-module")
                ),
                acceptanceCriteria,
                List.of("Unit tests with 80% coverage"),
                List.of("Must not change existing APIs"),
                List.of("Bar feature is out of scope"),
                List.of("Performance regression risk"),
                List.of(),
                humanQuestions,
                status,
                1,
                new TaskContract.Source("req-42", "agent-1")
        );
    }

    @Test
    void serializeDeserialize() throws Exception {
        TaskContract original = sampleContract(
                ContractStatus.READY_FOR_REVIEW,
                List.of("Foo API returns 200", "Tests pass"),
                List.of()
        );

        String json = MAPPER.writeValueAsString(original);
        TaskContract deserialized = MAPPER.readValue(json, TaskContract.class);

        assertEquals(original.id(), deserialized.id());
        assertEquals(original.version(), deserialized.version());
        assertEquals(original.createdAt(), deserialized.createdAt());
        assertEquals(original.objective(), deserialized.objective());
        assertEquals(original.acceptanceCriteria(), deserialized.acceptanceCriteria());
        assertEquals(original.status(), deserialized.status());
        assertEquals(original.scope().included(), deserialized.scope().included());
        assertEquals(original.scope().excluded(), deserialized.scope().excluded());
        assertEquals(original.source().rawRequestRef(), deserialized.source().rawRequestRef());
        assertEquals(original.compilationPass(), deserialized.compilationPass());

        // Verify snake_case JSON field names
        assertTrue(json.contains("\"created_at\""));
        assertTrue(json.contains("\"acceptance_criteria\""));
        assertTrue(json.contains("\"test_requirements\""));
        assertTrue(json.contains("\"non_goals\""));
        assertTrue(json.contains("\"risk_areas\""));
        assertTrue(json.contains("\"ambiguity_notes\""));
        assertTrue(json.contains("\"human_questions\""));
        assertTrue(json.contains("\"compilation_pass\""));
    }

    @Test
    void contractStatusValues() {
        ContractStatus[] values = ContractStatus.values();
        assertEquals(4, values.length);
        assertTrue(Arrays.asList(values).containsAll(List.of(
                ContractStatus.READY_FOR_REVIEW,
                ContractStatus.NEEDS_CLARIFICATION,
                ContractStatus.NEEDS_SCOPE_REDUCTION,
                ContractStatus.REJECTED
        )));
    }

    @Test
    void statusNotApprovedDraft() {
        for (ContractStatus status : ContractStatus.values()) {
            assertNotEquals("APPROVED_DRAFT", status.name(),
                    "APPROVED_DRAFT must not exist in ContractStatus");
        }
        assertThrows(IllegalArgumentException.class, () ->
                ContractStatus.valueOf("APPROVED_DRAFT"));
    }

    @Test
    void validateReadyForReviewRequiresAcceptanceCriteria() {
        TaskContract contract = sampleContract(
                ContractStatus.READY_FOR_REVIEW,
                List.of(),   // empty acceptance criteria
                List.of()
        );

        List<String> errors = TaskContractValidator.validate(contract);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("acceptanceCriteria")));
    }

    @Test
    void validateNeedsClarificationAllowsEmptyAcceptanceCriteria() {
        TaskContract contract = sampleContract(
                ContractStatus.NEEDS_CLARIFICATION,
                List.of(),   // empty AC is OK for NEEDS_CLARIFICATION
                List.of("What is the expected timeout?")
        );

        List<String> errors = TaskContractValidator.validate(contract);
        assertTrue(errors.isEmpty(), "NEEDS_CLARIFICATION with empty AC should have no errors: " + errors);
    }

    @Test
    void validateScopeIncludedExcludedMustExist() {
        // Scope with null included/excluded gets normalized to empty lists — that's OK
        TaskContract.Scope scope = new TaskContract.Scope(null, null);
        TaskContract contract = new TaskContract(
                "tc-002",
                1,
                Instant.now(),
                "Test objective",
                scope,
                List.of("AC1"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("Some question?"),
                ContractStatus.NEEDS_CLARIFICATION,
                1,
                new TaskContract.Source("req-1", "agent-1")
        );

        List<String> errors = TaskContractValidator.validate(contract);
        // scope is non-null so no scope error; included/excluded default to empty
        assertTrue(errors.stream().noneMatch(e -> e.contains("scope")));
    }
}
