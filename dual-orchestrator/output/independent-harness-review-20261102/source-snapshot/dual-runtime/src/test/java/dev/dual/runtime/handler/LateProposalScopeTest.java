package dev.dual.runtime.handler;

import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LateProposalScopeTest {
    @TempDir Path root;
    private final TaskContractProposalValidator validator = new TaskContractProposalValidator();

    @Test
    void reviewedExistingCandidatesRemainCreateWithoutAuthorizingTheContract() throws Exception {
        var base = baseline();
        var resolver = new TaskContractScopeResolver();
        for (var path : List.of("docs/guide.md", "src/Tool.java", "service.py")) {
            Files.createDirectories(root.resolve(path).getParent());
            Files.writeString(root.resolve(path), "candidate\n");
            var occurrence = additionEvidence(path);
            assertEquals(List.of(new TaskContract.ScopeEntry(path, TaskContract.ScopeOperation.CREATE)),
                    resolver.reviewedCreateCandidates(root, base, occurrence));
            assertFalse(resolver.resolve(List.of(new AgentResultEnvelope.ProposalScopePath(path)), root, base).valid());
            assertFalse(validator.validate(proposal(path, TaskContract.ScopeOperation.CREATE), root).isEmpty(),
                    "being a review candidate does not approve an existing CREATE");
        }
    }

    @Test
    void candidateConsumptionRechecksEvidenceAndBase() throws Exception {
        var base = baseline();
        Files.writeString(root.resolve("extra.py"), "candidate\n");
        var occurrence = additionEvidence("extra.py");
        var resolver = new TaskContractScopeResolver();
        git("add", "extra.py");
        git("commit", "-m", "later tree");
        assertThrows(java.io.IOException.class, () -> resolver.reviewedCreateCandidates(
                root, git("rev-parse", "HEAD").trim(), occurrence));
        assertEquals(TaskContract.ScopeOperation.CREATE,
                resolver.reviewedCreateCandidates(root, base, occurrence).getFirst().operation());
        Files.writeString(root.resolve(occurrence.ref()), "{}\n");
        assertThrows(java.io.IOException.class, () -> resolver.reviewedCreateCandidates(root, base, occurrence));
        Files.delete(root.resolve(occurrence.ref()));
        assertThrows(java.io.IOException.class, () -> resolver.reviewedCreateCandidates(root, base, occurrence));
    }

    @Test
    void reviewCandidatesRejectProtectedMissingDirectoryAndSymlinkPaths() throws Exception {
        var base = baseline();
        Files.createDirectories(root.resolve("folder"));
        Files.writeString(root.resolve("ordinary.py"), "candidate\n");
        Files.createSymbolicLink(root.resolve("alias.py"), Path.of("ordinary.py"));
        Files.createSymbolicLink(root.resolve("alias-dir"), Path.of("folder"));
        Files.writeString(root.resolve("folder/nested.py"), "candidate\n");
        for (var path : List.of("README.md", "missing.py", "folder", "alias.py", "alias-dir/nested.py",
                ".agent/private.json", ".git/config", "../outside.py")) {
            var occurrence = additionEvidence(path);
            assertThrows(java.io.IOException.class, () -> new TaskContractScopeResolver()
                    .reviewedCreateCandidates(root, base, occurrence), path);
        }
    }

    @Test
    void reviewCandidatesRejectOtherOperationsAndInconsistentEvidence() throws Exception {
        var base = baseline();
        Files.writeString(root.resolve("extra.py"), "candidate\n");
        for (var operation : ChangeScopeEvaluation.ActualOperation.values()) {
            var actual = new ChangeScopeEvaluation.ActualChange("extra.py", operation, null, "?");
            var violation = new ChangeScopeEvaluation.Violation("UNEXPECTED_ACTUAL_CHANGE", "extra.py",
                    null, ChangeScopeEvaluation.ActualOperation.CREATE, null);
            var evaluation = new ChangeScopeEvaluation(1, ChangeScopeEvaluation.Status.FAIL,
                    operation == ChangeScopeEvaluation.ActualOperation.CREATE ? List.of() : List.of(actual),
                    List.of(), List.of(), List.of(violation), null);
            var occurrence = evidence(evaluation);
            assertThrows(java.io.IOException.class, () -> new TaskContractScopeResolver()
                    .reviewedCreateCandidates(root, base, occurrence));
        }
    }

    @Test
    void candidateSetCannotGrowFromLaterWorkspaceChangesOrMixedViolations() throws Exception {
        var base = baseline();
        Files.writeString(root.resolve("extra.py"), "candidate\n");
        var occurrence = additionEvidence("extra.py");
        Files.writeString(root.resolve("later.py"), "not in evidence\n");
        var resolver = new TaskContractScopeResolver();
        assertEquals(List.of("extra.py"), resolver.reviewedCreateCandidates(root, base, occurrence)
                .stream().map(TaskContract.ScopeEntry::path).toList());
        var valid = occurrence.document().evaluation();
        for (var violation : List.of(
                new ChangeScopeEvaluation.Violation("UNEXPECTED_ACTUAL_CHANGE", "extra.py", null,
                        ChangeScopeEvaluation.ActualOperation.MODIFY, null),
                new ChangeScopeEvaluation.Violation("OPERATION_MISMATCH", "extra.py",
                        TaskContract.ScopeOperation.MODIFY, ChangeScopeEvaluation.ActualOperation.CREATE, null),
                new ChangeScopeEvaluation.Violation("UNEXPECTED_ACTUAL_CHANGE", "extra.py", null,
                        ChangeScopeEvaluation.ActualOperation.CREATE, "previous.py"),
                valid.violations().getFirst())) {
            var mixed = evidence(new ChangeScopeEvaluation(1, ChangeScopeEvaluation.Status.FAIL,
                    valid.actualChanges(), List.of(), List.of(),
                    List.of(valid.violations().getFirst(), violation), null));
            assertThrows(java.io.IOException.class, () -> resolver.reviewedCreateCandidates(root, base, mixed));
        }
        assertThrows(java.io.IOException.class, () -> resolver.reviewedCreateCandidates(root, "missing", occurrence));
        assertThrows(java.io.IOException.class, () -> resolver.reviewedCreateCandidates(root, base, null));
    }

    private ChangeScopeEvaluationArtifact.Verified additionEvidence(String path) throws Exception {
        return evidence(new ChangeScopeEvaluation(1, ChangeScopeEvaluation.Status.FAIL,
                List.of(new ChangeScopeEvaluation.ActualChange(path, ChangeScopeEvaluation.ActualOperation.CREATE,
                        null, "A")), List.of(), List.of(),
                List.of(new ChangeScopeEvaluation.Violation("UNEXPECTED_ACTUAL_CHANGE", path, null,
                        ChangeScopeEvaluation.ActualOperation.CREATE, null)), null));
    }

    @Test
    void scopeReviewRejectsMissingFieldsWithoutThrowing() throws Exception {
        var base = baseline();
        var approved = proposal("approved.py", TaskContract.ScopeOperation.CREATE);
        var malformed = new TaskContractProposal("task_contract_proposal", TaskContractProposal.SCHEMA_VERSION,
                null, new TaskContractProposal.Scope(null, null), null, null, null, null, null, null, null, null);
        var candidates = List.of(new TaskContract.ScopeEntry("extra.py", TaskContract.ScopeOperation.CREATE));
        var errors = assertDoesNotThrow(() -> validator.validate(malformed, root, approved, base, candidates));
        assertTrue(errors.contains("scope review must preserve the approved contract outside candidate additions"));
        assertTrue(errors.contains("scope.included must be present (GOAL may propose no files)"));
    }

    private ChangeScopeEvaluationArtifact.Verified evidence(ChangeScopeEvaluation evaluation) throws Exception {
        var reference = ChangeScopeEvaluationArtifact.publish(root, "ws", "node-validation", evaluation);
        return ChangeScopeEvaluationArtifact.verify(root, "ws", "node-validation", reference.ref(), reference.sha256());
    }

    @Test
    void inheritedCreateRemainsCreateAgainstTheOriginalGitBase() throws Exception {
        var base = baseline();
        for (var path : List.of("docs/guide.md", "src/Tool.java", "service.py")) {
            var approved = proposal(path, TaskContract.ScopeOperation.CREATE);
            Files.createDirectories(root.resolve(path).getParent());
            Files.writeString(root.resolve(path), "implemented\n");
            assertFalse(validator.validate(approved, root).isEmpty(), "initial proposal cannot adopt existing CREATE");
            assertTrue(validator.validate(approved, root, approved, base).isEmpty());
            git("add", path);
        }
        var changes = ChangeScopeEvaluator.parseNameStatus(git("diff", "--cached", "--name-status", "-z", base));
        var entries = List.of("docs/guide.md", "src/Tool.java", "service.py").stream()
                .map(path -> new TaskContract.ScopeEntry(path, TaskContract.ScopeOperation.CREATE)).toList();
        assertEquals(ChangeScopeEvaluation.Status.PASS, ChangeScopeEvaluator.evaluate(contract(entries), changes).status());
        var relabelled = entries.stream().map(entry -> new TaskContract.ScopeEntry(
                entry.path(), TaskContract.ScopeOperation.MODIFY)).toList();
        assertEquals(ChangeScopeEvaluation.Status.FAIL, ChangeScopeEvaluator.evaluate(contract(relabelled), changes).status());
        assertFalse(validator.validate(proposal("service.py", TaskContract.ScopeOperation.MODIFY),
                root, proposal("service.py", TaskContract.ScopeOperation.CREATE), base).isEmpty());
    }

    @Test
    void unapprovedExistingAdditionCannotInheritAnEarlierDraftsCreate() throws Exception {
        var base = baseline();
        var approved = proposal("approved.py", TaskContract.ScopeOperation.CREATE);
        Files.writeString(root.resolve("unapproved.py"), "outside approved scope\n");
        assertFalse(validator.validate(proposal("unapproved.py", TaskContract.ScopeOperation.CREATE),
                root, approved, base).isEmpty());
        assertFalse(validator.validate(proposal("unapproved.py", TaskContract.ScopeOperation.MODIFY),
                root, approved, base).isEmpty());
        var resolver = new TaskContractScopeResolver();
        assertFalse(resolver.resolve(List.of(new AgentResultEnvelope.ProposalScopePath("unapproved.py")),
                root, base).valid());
        assertTrue(resolver.resolve(List.of(new AgentResultEnvelope.ProposalScopePath("README.md")),
                root, base).valid(), "existing original files remain valid additions");
        assertTrue(resolver.resolve(List.of(new AgentResultEnvelope.ProposalScopePath("future.py")),
                root, base).valid(), "uncreated additions remain valid CREATE proposals");
        assertFalse(validator.validate(approved, root, approved, "missing").isEmpty());
    }

    @Test
    void inheritedCreateDoesNotExemptDirectoriesOrNonCanonicalPaths() throws Exception {
        var base = baseline();
        Files.createDirectory(root.resolve("folder"));
        for (var path : List.of("folder", "../outside", ".agent/private.json", "./README.md")) {
            var approved = proposal(path, TaskContract.ScopeOperation.CREATE);
            assertFalse(validator.validate(approved, root, approved, base).isEmpty(), path);
        }
    }

    private String baseline() throws Exception {
        git("init");
        git("config", "user.name", "Test");
        git("config", "user.email", "test@example.invalid");
        Files.writeString(root.resolve("README.md"), "baseline\n");
        git("add", "README.md");
        git("commit", "-m", "baseline");
        return git("rev-parse", "HEAD").trim();
    }

    private String git(String... args) throws Exception {
        var command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }

    private static TaskContractProposal proposal(String path, TaskContract.ScopeOperation operation) {
        return new TaskContractProposal("task_contract_proposal", TaskContractProposal.SCHEMA_VERSION,
                "Generic utility", new TaskContractProposal.Scope(List.of(new TaskContract.ScopeEntry(path, operation)), List.of()),
                List.of(new TaskContractProposal.ContractClause("AC-1", "Works"),
                        new TaskContractProposal.ContractClause("AC-2", "Preserves data")),
                List.of(new TaskContractProposal.ContractClause("T-1", "Run tests")),
                List.of(new TaskContractProposal.ContractClause("C-1", "Stay in scope")),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static TaskContract contract(List<TaskContract.ScopeEntry> entries) {
        return new TaskContract(2,
                dev.dual.core.contract.TaskContract.Phase.EXECUTION,
                1,
                "task-ws",
                1,
                Instant.now(),
                "Generic utility",
                new TaskContract.Scope(entries, List.of()),
                dev.dual.runtime.RuntimeTestLedger.clauses("AC-", List.of("Works", "Preserves data")),
                dev.dual.runtime.RuntimeTestLedger.clauses("TR-", List.of("Run tests")),
                List.of("Stay in scope"),
                List.of(),
                List.of(),
                java.util.List.of(),
                List.of(),
                List.of(),
                ContractStatus.READY_FOR_REVIEW,
                1,
                new TaskContract.Source("REQUIREMENT.md", "test", null, null),
                null,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of());
    }
}
