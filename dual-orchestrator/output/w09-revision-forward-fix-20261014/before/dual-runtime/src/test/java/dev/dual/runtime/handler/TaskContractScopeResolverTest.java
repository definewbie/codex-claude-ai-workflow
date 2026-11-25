package dev.dual.runtime.handler;

import dev.dual.core.contract.TaskContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskContractScopeResolverTest {
    @TempDir
    Path workspace;

    private final TaskContractScopeResolver resolver = new TaskContractScopeResolver();

    @Test
    void resolvesOnlyDeclaredPathsFromTheEffectiveWorktree() throws Exception {
        Files.writeString(workspace.resolve("REQUIREMENT.md"), "requirement");

        var resolution = resolver.resolve(List.of(
                new AgentResultEnvelope.ProposalScopePath("REQUIREMENT.md"),
                new AgentResultEnvelope.ProposalScopePath("README.md")), workspace);

        assertTrue(resolution.valid(), resolution.errors().toString());
        assertEquals(List.of(
                new TaskContract.ScopeEntry("REQUIREMENT.md", TaskContract.ScopeOperation.MODIFY),
                new TaskContract.ScopeEntry("README.md", TaskContract.ScopeOperation.CREATE)),
                resolution.entries());
    }

    @Test
    void invalidDuplicateAndDirectoryPathsFailClosed() throws Exception {
        Files.createDirectories(workspace.resolve("src"));

        var resolution = resolver.resolve(List.of(
                new AgentResultEnvelope.ProposalScopePath("../outside.java"),
                new AgentResultEnvelope.ProposalScopePath("./inside.java"),
                new AgentResultEnvelope.ProposalScopePath("src/"),
                new AgentResultEnvelope.ProposalScopePath("src"),
                new AgentResultEnvelope.ProposalScopePath("README.md"),
                new AgentResultEnvelope.ProposalScopePath("README.md")), workspace);

        assertFalse(resolution.valid());
        assertTrue(resolution.errors().stream().anyMatch(error -> error.contains("canonical")));
        assertTrue(resolution.errors().stream().anyMatch(error -> error.contains("directory")));
        assertTrue(resolution.errors().stream().anyMatch(error -> error.contains("duplicate")));
    }

    @Test
    void durableValidatorStillRejectsTamperedResolvedOperation() throws Exception {
        var proposal = new TaskContractProposal(
                "task_contract_proposal", TaskContractProposal.SCHEMA_VERSION, "Create README",
                new TaskContractProposal.Scope(List.of(
                        new TaskContract.ScopeEntry("README.md", TaskContract.ScopeOperation.MODIFY)), List.of()),
                List.of(
                        new TaskContractProposal.ContractClause("AC-1", "README exists"),
                        new TaskContractProposal.ContractClause("AC-2", "Content is correct")),
                List.of(new TaskContractProposal.ContractClause("TR-1", "Inspect README")),
                List.of(new TaskContractProposal.ContractClause("C-1", "Keep scope bounded")),
                List.of(), List.of(), List.of(), List.of(), List.of());

        var errors = new TaskContractProposalValidator().validate(proposal, workspace);

        assertTrue(errors.contains("scope.included MODIFY path does not exist: README.md"), errors.toString());
    }
}
