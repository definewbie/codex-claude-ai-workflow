package dev.dual.runtime.handler;

import dev.dual.core.contract.TaskContract;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Applies one lineage-bound, finite revision patch to a verified proposal. */
final class TaskContractProposalRevisionMerger {
    MergeResult merge(
            TaskContractRevisionContextResolver.Context context,
            AgentResultEnvelope.TaskContractRevisionPatchResult patch,
            Path workspaceRoot
    ) {
        var errors = new ArrayList<String>();
        if (context == null || !context.revision()
                || context.previousProposal() == null || context.previousReference() == null) {
            return MergeResult.failure("REVISION_CONTEXT_REQUIRED");
        }
        if (!context.previousReference().proposalId().equals(patch.baseProposalId())) {
            errors.add("REVISION_BASE_PROPOSAL_ID_MISMATCH");
        }
        if (!context.previousReference().sha256().equals(patch.baseProposalSha256())) {
            errors.add("REVISION_BASE_PROPOSAL_SHA256_MISMATCH");
        }

        var expectedObligations = new LinkedHashSet<String>();
        for (var obligation : context.obligations()) {
            if (obligation != null && obligation.id() != null && !obligation.id().isBlank()) {
                expectedObligations.add(obligation.id().trim());
            }
        }
        var actualObligations = new LinkedHashSet<String>();
        for (var obligationPatch : safe(patch.obligationPatches())) {
            if (obligationPatch == null || obligationPatch.reviewObligationId() == null) {
                continue;
            }
            var obligationId = obligationPatch.reviewObligationId().trim();
            if (!actualObligations.add(obligationId)) {
                errors.add("DUPLICATE_REVISION_OBLIGATION: " + obligationId);
            }
        }
        errors.addAll(TaskContractRevisionPatchTargetValidator.validate(patch));
        for (var expected : expectedObligations) {
            if (!actualObligations.contains(expected)) {
                errors.add("MISSING_REVISION_OBLIGATION: " + expected);
            }
        }
        for (var actual : actualObligations) {
            if (!expectedObligations.contains(actual)) {
                errors.add("UNKNOWN_REVISION_OBLIGATION: " + actual);
            }
        }
        if (!errors.isEmpty()) {
            return new MergeResult(null, List.copyOf(errors));
        }

        var previous = context.previousProposal();
        var sections = sections(previous);
        var allIds = new LinkedHashSet<String>();
        sections.values().forEach(values -> values.forEach(clause -> allIds.add(clause.id())));
        for (var obligationPatch : patch.obligationPatches()) {
            for (var change : obligationPatch.clauseChanges()) {
                var values = sections.get(change.section());
                var index = indexOf(values, change.id());
                if (change.operation() == AgentResultEnvelope.ClauseChangeOperation.REPLACE) {
                    if (index < 0) {
                        errors.add("UNKNOWN_CLAUSE_REPLACE_TARGET: " + change.id());
                    } else {
                        values.set(index, new TaskContractProposal.ContractClause(change.id(), change.text()));
                    }
                } else if (change.operation() == AgentResultEnvelope.ClauseChangeOperation.ADD) {
                    if (allIds.contains(change.id())) {
                        errors.add("CLAUSE_ADD_TARGET_ALREADY_EXISTS: " + change.id());
                    } else {
                        values.add(new TaskContractProposal.ContractClause(change.id(), change.text()));
                        allIds.add(change.id());
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            return new MergeResult(null, List.copyOf(errors));
        }

        var scope = new ArrayList<>(List.copyOf(previous.scope().included()));
        var evidence = new ArrayList<>(List.copyOf(previous.evidenceRefs()));
        var responses = new ArrayList<TaskContractProposal.RevisionResponse>();
        var scopeResolver = new TaskContractScopeResolver();
        for (var obligationPatch : patch.obligationPatches()) {
            var scopeAdditions = safe(obligationPatch.scopeAdditions());
            if (!scopeAdditions.isEmpty()) {
                var resolvedScope = scopeResolver.resolve(scopeAdditions, workspaceRoot,
                        context.approvedScopeBaseline() == null ? null : context.originalBaseCommit());
                if (!resolvedScope.valid()) {
                    errors.addAll(resolvedScope.errors());
                    continue;
                }
                scope.addAll(resolvedScope.entries());
            }
            evidence.addAll(List.copyOf(obligationPatch.evidenceRefAdditions()));
            responses.add(new TaskContractProposal.RevisionResponse(
                    obligationPatch.reviewObligationId(),
                    List.copyOf(obligationPatch.resolvedByClauseIds())));
        }
        if (!errors.isEmpty()) {
            return new MergeResult(null, List.copyOf(errors));
        }
        var merged = new TaskContractProposal(
                "task_contract_proposal",
                TaskContractProposal.SCHEMA_VERSION,
                previous.objective(),
                new TaskContractProposal.Scope(List.copyOf(scope), List.copyOf(previous.scope().excluded())),
                List.copyOf(sections.get(AgentResultEnvelope.ClauseSection.ACCEPTANCE_CRITERIA)),
                List.copyOf(sections.get(AgentResultEnvelope.ClauseSection.TEST_REQUIREMENTS)),
                List.copyOf(sections.get(AgentResultEnvelope.ClauseSection.CONSTRAINTS)),
                List.copyOf(sections.get(AgentResultEnvelope.ClauseSection.NON_GOALS)),
                List.copyOf(sections.get(AgentResultEnvelope.ClauseSection.RISK_AREAS)),
                List.copyOf(evidence),
                List.copyOf(previous.openQuestions()),
                List.copyOf(responses));
        return new MergeResult(merged, List.of());
    }

    private static Map<AgentResultEnvelope.ClauseSection, ArrayList<TaskContractProposal.ContractClause>> sections(
            TaskContractProposal proposal
    ) {
        var result = new LinkedHashMap<AgentResultEnvelope.ClauseSection,
                ArrayList<TaskContractProposal.ContractClause>>();
        result.put(AgentResultEnvelope.ClauseSection.ACCEPTANCE_CRITERIA,
                new ArrayList<>(List.copyOf(proposal.acceptanceCriteria())));
        result.put(AgentResultEnvelope.ClauseSection.TEST_REQUIREMENTS,
                new ArrayList<>(List.copyOf(proposal.testRequirements())));
        result.put(AgentResultEnvelope.ClauseSection.CONSTRAINTS,
                new ArrayList<>(List.copyOf(proposal.constraints())));
        result.put(AgentResultEnvelope.ClauseSection.NON_GOALS,
                new ArrayList<>(List.copyOf(proposal.nonGoals())));
        result.put(AgentResultEnvelope.ClauseSection.RISK_AREAS,
                new ArrayList<>(List.copyOf(proposal.riskAreas())));
        return result;
    }

    private static int indexOf(List<TaskContractProposal.ContractClause> clauses, String id) {
        for (int index = 0; index < clauses.size(); index++) {
            if (clauses.get(index).id().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    record MergeResult(TaskContractProposal proposal, List<String> errors) {
        static MergeResult failure(String error) {
            return new MergeResult(null, List.of(error));
        }

        boolean valid() {
            return proposal != null && errors.isEmpty();
        }
    }
}
