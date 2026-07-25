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
                        List.of(
                                entry("src/main/java/FooApi.java", TaskContract.ScopeOperation.MODIFY),
                                entry("src/main/java/FooService.java", TaskContract.ScopeOperation.CREATE)),
                        List.of("src/main/java/BarModule.java")
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
    void validateNeedsClarificationAllowsEmptyScopeIncluded() {
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
        assertTrue(errors.isEmpty(), "NEEDS_CLARIFICATION can carry incomplete scope while waiting for answers: " + errors);
    }

    @Test
    void validateReadyForReviewRequiresExecutableBoundariesAndTests() {
        var contract = new TaskContract(
                "tc-003",
                1,
                Instant.now(),
                "Implement feature",
                new TaskContract.Scope(
                        List.of(entry("../outside", TaskContract.ScopeOperation.MODIFY)),
                        List.of("../outside")),
                List.of("Only one criterion"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ContractStatus.READY_FOR_REVIEW,
                1,
                new TaskContract.Source("req-1", "agent-1")
        );

        var errors = TaskContractValidator.validate(contract);

        assertTrue(errors.stream().anyMatch(error -> error.contains("at least two acceptance criteria")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("testRequirements")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("constraints or nonGoals")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("not a canonical relative file path")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("overlaps")));
    }

    @Test
    void validateAcceptsProjectNeutralPathsAndRejectsInternalOrEscapingPaths() {
        assertTrue(TaskContractValidator.isCanonicalPath("src/lib.rs"));
        assertTrue(TaskContractValidator.isCanonicalPath("cmd/server/main.go"));
        assertTrue(TaskContractValidator.isCanonicalPath("Dockerfile"));
        assertTrue(TaskContractValidator.isCanonicalPath("docs/API contract.md"));

        assertFalse(TaskContractValidator.isCanonicalPath("/etc/passwd"));
        assertFalse(TaskContractValidator.isCanonicalPath("C:/Windows/System32"));
        assertFalse(TaskContractValidator.isCanonicalPath("."));
        assertFalse(TaskContractValidator.isCanonicalPath("../outside"));
        assertFalse(TaskContractValidator.isCanonicalPath("src/../../outside"));
        assertFalse(TaskContractValidator.isCanonicalPath(".agent/workstreams/ws/run.json"));
        assertFalse(TaskContractValidator.isCanonicalPath(".git/config"));
    }

    @Test
    void validateRejectsLedgerMetadataInContractFields() {
        var contract = sampleContract(
                ContractStatus.READY_FOR_REVIEW,
                List.of("Feature works", "Workstream: ws-noise"),
                List.of()
        );

        var errors = TaskContractValidator.validate(contract);

        assertTrue(errors.stream().anyMatch(error -> error.contains("ledger/context metadata")));
    }

    @Test
    void validateRequiresTypedScopeOperationAndRejectsLegacyNewPrefix() {
        var missingOperation = new TaskContract.ScopeEntry("src/NewFile.java", null);
        var legacyPrefix = entry("new:src/NewFile.java", TaskContract.ScopeOperation.CREATE);
        var contract = new TaskContract(
                "tc-004",
                1,
                Instant.now(),
                "Create a file",
                new TaskContract.Scope(List.of(missingOperation, legacyPrefix), List.of()),
                List.of("File exists", "Tests pass"),
                List.of("Run tests"),
                List.of("Keep scope bounded"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ContractStatus.READY_FOR_REVIEW,
                1,
                new TaskContract.Source("req-1", "agent-1")
        );

        var errors = TaskContractValidator.validate(contract);

        assertTrue(errors.stream().anyMatch(error -> error.contains("operation is required")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("legacy new: prefix")));
    }

    private static TaskContract.ScopeEntry entry(String path, TaskContract.ScopeOperation operation) {
        return new TaskContract.ScopeEntry(path, operation);
    }

    @Test
    void caseDifferentPathsAreNotDuplicates() {
        var contract = new TaskContract(
                "tc-1", 1, java.time.Instant.parse("2026-07-24T00:00:00Z"),
                "Case sensitivity test",
                new TaskContract.Scope(
                        List.of(
                                entry("src/Foo.java", TaskContract.ScopeOperation.MODIFY),
                                entry("src/foo.java", TaskContract.ScopeOperation.MODIFY)
                        ),
                        List.of()
                ),
                List.of("criterion"), List.of("test"),
                List.of(), List.of(),
                List.of(), List.of(), List.of(),
                ContractStatus.READY_FOR_REVIEW,
                1,
                new TaskContract.Source("req-1", "agent-1")
        );

        var errors = TaskContractValidator.validate(contract);
        assertFalse(errors.stream().anyMatch(e -> e.contains("duplicate")),
                "case-sensitive filesystems must treat src/Foo.java and src/foo.java as distinct: " + errors);
    }

    @Test
    void nonCanonicalPathsAreRejected() {
        assertFalse(TaskContractValidator.isCanonicalPath("./src/a.py"),
                "leading ./ must be rejected");
        assertFalse(TaskContractValidator.isCanonicalPath("src/./a.py"),
                "interleaved . segment must be rejected");
        assertFalse(TaskContractValidator.isCanonicalPath("src//a.py"),
                "double slash must be rejected");
        assertFalse(TaskContractValidator.isCanonicalPath("src/a/"),
                "trailing slash must be rejected");
        assertFalse(TaskContractValidator.isCanonicalPath(" src/a.py"),
                "leading whitespace must be rejected");
        assertFalse(TaskContractValidator.isCanonicalPath("src/a.py "),
                "trailing whitespace must be rejected");
        assertTrue(TaskContractValidator.isCanonicalPath("src/a.py"),
                "simple relative path accepted");
        assertTrue(TaskContractValidator.isCanonicalPath("Dockerfile"),
                "extensionless path accepted");
        assertTrue(TaskContractValidator.isCanonicalPath("docs/API contract.md"),
                "path with space in filename accepted");
    }

    @Test
    void extensionlessAndUnusualPathsAccepted() {
        assertTrue(TaskContractValidator.isCanonicalPath("Dockerfile"),
                "no-extension accepted");
        assertTrue(TaskContractValidator.isCanonicalPath("Makefile"),
                "no-extension accepted");
        assertTrue(TaskContractValidator.isCanonicalPath("src/go.mod"),
                "non-whitelisted extension accepted");
    }
}
