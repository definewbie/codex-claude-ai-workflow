package dev.dual.runtime.handler;

import dev.dual.core.contract.TaskContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskContractProposalValidatorTest {
    @TempDir
    Path workspace;

    private final TaskContractProposalValidator validator = new TaskContractProposalValidator();

    @Test
    void acceptsProjectNeutralCreateAndModifyPaths() throws Exception {
        Files.writeString(workspace.resolve("Dockerfile"), "FROM scratch\n");
        Files.createDirectories(workspace.resolve("cmd/server"));
        Files.writeString(workspace.resolve("cmd/server/main.go"), "package main\n");

        var proposal = proposal(List.of(
                entry("Dockerfile", TaskContract.ScopeOperation.MODIFY),
                entry("cmd/server/main.go", TaskContract.ScopeOperation.MODIFY),
                entry("src/lib.rs", TaskContract.ScopeOperation.CREATE)
        ));

        assertTrue(validator.validate(proposal, workspace).isEmpty());
    }

    @Test
    void rejectsEscapingAndInternalPathsBeforeWorkspaceEvidenceChecks() {
        var proposal = proposal(List.of(
                entry("../outside.rs", TaskContract.ScopeOperation.CREATE),
                entry(".agent/workstreams/ws/run.json", TaskContract.ScopeOperation.CREATE)
        ));

        var errors = validator.validate(proposal, workspace);

        assertTrue(errors.stream().allMatch(error -> error.contains("canonical relative file path")), errors.toString());
    }

    private static TaskContractProposal proposal(List<TaskContract.ScopeEntry> included) {
        return proposal(included, List.of());
    }

    private static TaskContractProposal proposal(
            List<TaskContract.ScopeEntry> included,
            List<String> excluded
    ) {
        return new TaskContractProposal(
                "task_contract_proposal",
                TaskContractProposal.SCHEMA_VERSION,
                "Keep path validation project-neutral.",
                new TaskContractProposal.Scope(included, excluded),
                clauses("AC", "Declared files are handled", "Unsafe paths are rejected"),
                clauses("TEST", "Run contract tests"),
                clauses("CONSTRAINT", "Keep workspace containment"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static TaskContract.ScopeEntry entry(String path, TaskContract.ScopeOperation operation) {
        return new TaskContract.ScopeEntry(path, operation);
    }

    private static List<TaskContractProposal.ContractClause> clauses(String prefix, String... values) {
        var clauses = new java.util.ArrayList<TaskContractProposal.ContractClause>();
        for (int index = 0; index < values.length; index++) {
            clauses.add(new TaskContractProposal.ContractClause(prefix + "-" + (index + 1), values[index]));
        }
        return List.copyOf(clauses);
    }

    @Test
    void rejectsDuplicateClauseIdsAcrossSections() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "# fixture\n");
        var proposal = new TaskContractProposal(
                "task_contract_proposal",
                TaskContractProposal.SCHEMA_VERSION,
                "Keep clauses traceable.",
                new TaskContractProposal.Scope(
                        List.of(entry("README.md", TaskContract.ScopeOperation.MODIFY)), List.of()),
                List.of(
                        new TaskContractProposal.ContractClause("CLAUSE-1", "First criterion"),
                        new TaskContractProposal.ContractClause("CLAUSE-2", "Second criterion")),
                List.of(new TaskContractProposal.ContractClause("CLAUSE-1", "Run tests")),
                clauses("CONSTRAINT", "Keep scope bounded"),
                List.of(), List.of(), List.of(), List.of(), List.of());

        var errors = validator.validate(proposal, workspace);

        assertTrue(errors.stream().anyMatch(error -> error.contains("clause id is duplicated: CLAUSE-1")),
                errors.toString());
    }

    @Test
    void revisionCoverageRequiresEveryObligation() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "# fixture\n");
        var proposal = new TaskContractProposal(
                "task_contract_proposal",
                TaskContractProposal.SCHEMA_VERSION,
                "Resolve the rejected review.",
                new TaskContractProposal.Scope(
                        List.of(entry("README.md", TaskContract.ScopeOperation.MODIFY)), List.of()),
                clauses("AC", "First criterion", "Second criterion"),
                clauses("TEST", "Run tests"),
                clauses("CONSTRAINT", "Keep scope bounded"),
                List.of(), List.of(), List.of(), List.of(),
                List.of(new TaskContractProposal.RevisionResponse("OB-1", List.of("AC-1"))));
        var obligations = List.of(
                new AgentResultEnvelope.ReviewObligation("OB-1", "Add the missing behavior"),
                new AgentResultEnvelope.ReviewObligation("OB-2", "Preserve its evidence"));

        var errors = validator.validateRevisionCoverage(proposal, obligations);

        assertTrue(errors.contains("revision_responses does not cover obligation: OB-2"), errors.toString());
        assertTrue(validator.validateInitialIteration(proposal).stream()
                .anyMatch(error -> error.contains("must be empty")));
    }

    @Test
    void revisionResponsesRejectUnknownObligationsAndClauseTargets() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "# fixture\n");
        var proposal = new TaskContractProposal(
                "task_contract_proposal",
                TaskContractProposal.SCHEMA_VERSION,
                "Reject invented mappings.",
                new TaskContractProposal.Scope(
                        List.of(entry("README.md", TaskContract.ScopeOperation.MODIFY)), List.of()),
                clauses("AC", "First criterion", "Second criterion"),
                clauses("TEST", "Run tests"),
                clauses("CONSTRAINT", "Keep scope bounded"),
                List.of(), List.of(), List.of(), List.of(),
                List.of(new TaskContractProposal.RevisionResponse("OB-X", List.of("AC-404"))));

        var shapeErrors = validator.validate(proposal, workspace);
        var coverageErrors = validator.validateRevisionCoverage(
                proposal, List.of(new AgentResultEnvelope.ReviewObligation("OB-1", "Real gap")));

        assertTrue(shapeErrors.stream().anyMatch(error -> error.contains("unknown clause id: AC-404")),
                shapeErrors.toString());
        assertTrue(coverageErrors.contains("revision_responses refers to unknown obligation: OB-X"),
                coverageErrors.toString());
        assertTrue(coverageErrors.contains("revision_responses does not cover obligation: OB-1"),
                coverageErrors.toString());
    }

    @Test
    void capturedC2RegressionCannotPassAsAnUndeclaredRevision() {
        var previous = revisionProposal(
                List.of(clause("AC-1", "Registration verifies the submitted identity.")),
                List.of(clause("TEST-1", "Test registration.")),
                List.of(clause("C-2", "Preserve the local part of an email address.")),
                List.of(),
                List.of(),
                List.of());
        var current = revisionProposal(
                List.of(clause("AC-1", "Registration atomically verifies the submitted identity.")),
                previous.testRequirements(),
                List.of(clause("C-2", "Lowercase the complete email address.")),
                previous.nonGoals(),
                previous.riskAreas(),
                List.of(new TaskContractProposal.RevisionResponse("CO-5", List.of("AC-1"))));

        var errors = validator.validateRevisionDelta(previous, current);

        assertEquals(List.of("UNDECLARED_CONTRACT_CLAUSE_CHANGE: C-2"),
                errors.stream().map(TaskContractProposalValidator.RevisionDeltaError::message).toList());
    }

    @Test
    void declaredChangesAdditionsAndSupportingClausesAreAcceptedIndependentOfOrder() {
        var previous = revisionProposal(
                List.of(clause("AC-1", "Old behavior"), clause("AC-2", "Supporting behavior")),
                List.of(clause("TEST-1", "Run tests")),
                List.of(clause("C-1", "Keep scope bounded")),
                List.of(),
                List.of(),
                List.of());
        var current = revisionProposal(
                List.of(clause("AC-2", "Supporting behavior"), clause("AC-1", "New behavior")),
                previous.testRequirements(),
                previous.constraints(),
                List.of(),
                List.of(clause("RISK-1", "New declared risk")),
                List.of(new TaskContractProposal.RevisionResponse(
                        "CO-1", List.of("AC-1", "AC-2", "RISK-1"))));

        assertTrue(validator.validateRevisionDelta(previous, current).isEmpty());
    }

    @Test
    void sectionMovesAndDeletionsRemainMaterialRevisionFacts() {
        var previous = revisionProposal(
                List.of(clause("AC-1", "Behavior"), clause("AC-2", "Must remain")),
                List.of(clause("TEST-1", "Run tests")),
                List.of(clause("C-1", "Boundary")),
                List.of(),
                List.of(),
                List.of());
        var current = revisionProposal(
                List.of(clause("AC-2", "Must remain")),
                previous.testRequirements(),
                List.of(clause("C-1", "Boundary"), clause("AC-1", "Behavior")),
                List.of(),
                List.of(),
                List.of(new TaskContractProposal.RevisionResponse("CO-1", List.of("AC-1"))));

        var moved = validator.validateRevisionDelta(previous, current);
        assertTrue(moved.isEmpty(), moved.toString());

        var deleted = revisionProposal(
                List.of(clause("AC-1", "Behavior")),
                previous.testRequirements(),
                previous.constraints(),
                List.of(),
                List.of(),
                List.of(new TaskContractProposal.RevisionResponse("CO-1", List.of("AC-1"))));
        assertTrue(validator.validateRevisionDelta(previous, deleted).stream()
                .anyMatch(error -> "CONTRACT_CLAUSE_DELETION_NOT_ALLOWED".equals(error.code())
                        && "AC-2".equals(error.targetId())));
    }

    private static TaskContractProposal revisionProposal(
            List<TaskContractProposal.ContractClause> acceptance,
            List<TaskContractProposal.ContractClause> tests,
            List<TaskContractProposal.ContractClause> constraints,
            List<TaskContractProposal.ContractClause> nonGoals,
            List<TaskContractProposal.ContractClause> risks,
            List<TaskContractProposal.RevisionResponse> responses
    ) {
        return new TaskContractProposal(
                "task_contract_proposal",
                TaskContractProposal.SCHEMA_VERSION,
                "Stable revision objective",
                new TaskContractProposal.Scope(
                        List.of(entry("README.md", TaskContract.ScopeOperation.MODIFY)), List.of()),
                acceptance,
                tests,
                constraints,
                nonGoals,
                risks,
                List.of(),
                List.of(),
                responses);
    }

    private static TaskContractProposal.ContractClause clause(String id, String text) {
        return new TaskContractProposal.ContractClause(id, text);
    }

    @Test
    void existingDirectoryModifyIsRejected() throws Exception {
        Files.createDirectories(workspace.resolve("src/pkg"));
        var validator = new TaskContractProposalValidator();
        var errors = validator.validate(
                proposal(List.of(entry("src/pkg", TaskContract.ScopeOperation.MODIFY))),
                workspace);
        assertFalse(errors.isEmpty(),
                "MODIFY on an existing directory must be rejected (exact file path only)");
        assertTrue(errors.stream().anyMatch(e -> e.contains("directory")),
                "error message must mention directory: " + errors);
    }

    @Test
    void existingDirectoryCreateIsRejected() throws Exception {
        Files.createDirectories(workspace.resolve("src/existing"));
        var validator = new TaskContractProposalValidator();
        var errors = validator.validate(
                proposal(List.of(entry("src/existing", TaskContract.ScopeOperation.CREATE))),
                workspace);
        assertFalse(errors.isEmpty(),
                "CREATE on an existing directory must be rejected");
    }

    @Test
    void nonCanonicalProposalPathIsRejected() throws Exception {
        var validator = new TaskContractProposalValidator();
        var errors = validator.validate(
                proposal(List.of(entry("./src/a.py", TaskContract.ScopeOperation.CREATE))),
                workspace);
        assertFalse(errors.isEmpty(),
                "non-canonical path (leading ./) must be rejected in proposal validator");
    }

    @Test
    void trailingSlashRejected() throws Exception {
        var validator = new TaskContractProposalValidator();
        var errors = validator.validate(
                proposal(List.of(entry("src/a.py/", TaskContract.ScopeOperation.CREATE))),
                workspace);
        assertFalse(errors.isEmpty(),
                "trailing slash must be rejected");
    }

    @Test
    void caseSensitivePathsNotDuplicateInProposal() throws Exception {
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/Foo.java"), "class Foo {}\n");
        Files.writeString(workspace.resolve("src/foo.java"), "class foo {}\n");
        var validator = new TaskContractProposalValidator();
        var errors = validator.validate(
                proposal(List.of(
                        entry("src/Foo.java", TaskContract.ScopeOperation.MODIFY),
                        entry("src/foo.java", TaskContract.ScopeOperation.MODIFY)
                )),
                workspace);
        assertTrue(errors.stream().noneMatch(e -> e.contains("duplicate")),
                "case-sensitive paths must not be duplicates: " + errors);
    }

    @Test
    void caseSensitivePathDoesNotOverlapDifferentlyCasedExclusion() throws Exception {
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/Foo.java"), "class Foo {}\n");

        var errors = validator.validate(
                proposal(
                        List.of(entry("src/Foo.java", TaskContract.ScopeOperation.MODIFY)),
                        List.of("src/foo.java")),
                workspace);

        assertTrue(errors.stream().noneMatch(error -> error.contains("overlaps")),
                "case-sensitive paths must not overlap differently cased exclusions: " + errors);
    }

    @Test
    void surroundingWhitespaceIsRejectedBeforeWorkspaceResolution() {
        var errors = validator.validate(
                proposal(List.of(
                        entry(" src/a.py", TaskContract.ScopeOperation.CREATE),
                        entry("src/b.py ", TaskContract.ScopeOperation.CREATE)
                )),
                workspace);

        assertTrue(errors.stream().filter(error -> error.contains("canonical relative file path")).count() == 2,
                "both leading and trailing whitespace must be rejected: " + errors);
    }
}
