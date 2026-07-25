package dev.dual.runtime.handler;

import dev.dual.core.contract.TaskContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
                List.of("Declared files are handled", "Unsafe paths are rejected"),
                List.of("Run contract tests"),
                List.of("Keep workspace containment"),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static TaskContract.ScopeEntry entry(String path, TaskContract.ScopeOperation operation) {
        return new TaskContract.ScopeEntry(path, operation);
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
