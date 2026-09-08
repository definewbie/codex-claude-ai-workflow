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
        return new TaskContract("task-ws", 1, Instant.now(), "Generic utility", new TaskContract.Scope(entries, List.of()),
                List.of("Works", "Preserves data"), List.of("Run tests"), List.of("Stay in scope"),
                List.of(), List.of(), List.of(), List.of(), ContractStatus.READY_FOR_REVIEW, 1,
                new TaskContract.Source("REQUIREMENT.md", "test"));
    }
}
