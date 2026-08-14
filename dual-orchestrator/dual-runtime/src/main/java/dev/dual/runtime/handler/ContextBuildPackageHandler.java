package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ContextBuildPackageHandler implements TaskHandler {
    private static final int MAX_SNIPPET_CHARS = 4_000;
    private static final int MAX_RECENT_AGENT_OUTPUTS = 8;
    private static final TokenEstimator TOKEN_ESTIMATOR = TokenEstimator.defaultEstimator();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final List<String> FULL_SECTIONS = List.of(
            "requirement",
            "input_artifacts",
            "iteration_history",
            "human_feedback",
            "recent_agent_outputs",
            "working_memory",
            "review_evidence",
            "git_evidence",
            "ledger_pointers",
            "task_contract"
    );
    private static final List<String> MEMORY_SECTIONS = List.of("working_memory", "review_evidence");

    @Override
    public String type() {
        return "context.build_package";
    }

    @Override
    public NodeResult execute(TaskContext context) throws Exception {
        var workstreamDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId());
        var contextDir = workstreamDir.resolve("context");
        Files.createDirectories(contextDir);

        var packagePath = contextDir.resolve(context.instance().nodeInstanceId() + ".md");
        var profileName = profile(context);
        var hasSectionOverride = context.definition().config().get("include_sections") instanceof List<?>;
        var requiredInputArtifact = configText(context, "required_input_artifact", "");
        if (!requiredInputArtifact.isBlank() && !"task_contract_proposal".equals(requiredInputArtifact)) {
            return new NodeResult(
                    "failed",
                    NodeStatus.FAILED,
                    List.of(),
                    Map.of("error", "unsupported required input artifact: " + requiredInputArtifact),
                    null);
        }
        var reviewSubject = resolveRequiredReviewSubject(context);
        var architectureReviewSubject = resolveRequiredArchitectureReviewSubject(context);
        if (requiresTaskContractProposal(context) && reviewSubject.isEmpty()) {
            return new NodeResult(
                    "failed",
                    NodeStatus.FAILED,
                    List.of(),
                    Map.of("error", "verified task-contract proposal reference is required for product review"),
                    null);
        }
        if (requiresArchitectureReviewSubject(context) && architectureReviewSubject.isEmpty()) {
            return new NodeResult(
                    "REQUIRED_AGENT_EVIDENCE_MISSING",
                    NodeStatus.FAILED,
                    List.of(),
                    Map.of(
                            "failure_code", "REQUIRED_AGENT_EVIDENCE_MISSING",
                            "error", "validated design review subject is required for architecture review"),
                    null);
        }
        var missingAgentEvidence = missingRequiredAgentEvidence(context);
        if (missingAgentEvidence.isPresent()) {
            return new NodeResult(
                    "REQUIRED_AGENT_EVIDENCE_MISSING",
                    NodeStatus.FAILED,
                    List.of(),
                    Map.of(
                            "failure_code", "REQUIRED_AGENT_EVIDENCE_MISSING",
                            "required_agent_evidence", missingAgentEvidence.get().toMap(),
                            "error", "required validated agent evidence is missing: "
                                    + missingAgentEvidence.get().description()),
                    null);
        }

        if (ContextProfiles.hasProfile(profileName) && !hasSectionOverride) {
            return executeGoverned(context, workstreamDir, packagePath, profileName,
                    reviewSubject, architectureReviewSubject);
        }

        var requestedSections = effectiveSections(context);
        var manifest = requestedSections.stream().anyMatch(ContextBuildPackageHandler::requiresEvidenceManifest)
                ? EvidenceManifest.rebuild(context.workspaceRoot(), workstreamDir, context.workstreamId(),
                        context.instance().inputRefs(), OBJECT_MAPPER)
                : null;
        var manifestPath = manifest == null ? null : manifest.write(workstreamDir, OBJECT_MAPPER);
        var governance = governanceEnabled()
                ? RepoScopeGovernance.build(context.workspaceRoot(), workstreamDir, context, OBJECT_MAPPER)
                : null;
        var markdown = renderPackage(context, workstreamDir, manifest, governance,
                reviewSubject, architectureReviewSubject);
        Files.writeString(packagePath, markdown, StandardCharsets.UTF_8);
        var requiredTypedSubjectTokens = requiredTypedSubjectTokens(reviewSubject, architectureReviewSubject);
        var metadataPath = writeContextPackageMetadata(
                context,
                packagePath,
                profileName,
                null,
                tokenCount(markdown),
                List.of(),
                manifest,
                manifestPath,
                memoryEnabled(),
                disabledMemorySections(sections(context), requestedSections),
                governance,
                requiredTypedSubjectTokens
        );

        return buildLegacyResult(context, packagePath, metadataPath, requestedSections);
    }

    private static NodeResult buildLegacyResult(TaskContext context, Path packagePath) {
        return buildLegacyResult(context, packagePath, null, effectiveSections(context));
    }

    private static NodeResult buildLegacyResult(TaskContext context, Path packagePath, Path metadataPath) {
        return buildLegacyResult(context, packagePath, metadataPath, effectiveSections(context));
    }

    private static NodeResult buildLegacyResult(
            TaskContext context,
            Path packagePath,
            Path metadataPath,
            List<String> includedSections
    ) {
        var packageRef = toWorkspaceRef(context.workspaceRoot(), packagePath);
        var result = new LinkedHashMap<String, Object>();
        result.put("context_package", packageRef);
        if (metadataPath != null) {
            result.put("context_package_metadata", toWorkspaceRef(context.workspaceRoot(), metadataPath));
        }
        result.put("target_node", configText(context, "target_node", ""));
        result.put("profile", profile(context));
        result.put("sections", includedSections);
        result.put("input_ref_count", context.instance().inputRefs().size());
        result.put("generated_at", Instant.now().toString());
        var outputRefs = new ArrayList<String>();
        outputRefs.add(packageRef);
        for (var inputRef : context.instance().inputRefs()) {
            if (TaskContractProposalReference.readVerified(
                    context.workspaceRoot(), context.workstreamId(), inputRef, OBJECT_MAPPER).isPresent()) {
                outputRefs.add(inputRef);
            }
        }
        return NodeResult.completed("completed", List.copyOf(outputRefs), result);
    }

    private static NodeResult executeGoverned(
            TaskContext context,
            Path workstreamDir,
            Path packagePath,
            String profileName,
            Optional<TaskContractProposalReference.ResolvedProposal> reviewSubject,
            Optional<AgentEvidenceIndex.Entry> architectureReviewSubject
    ) throws IOException {
        var rawBudget = ContextProfiles.budgetFor(profileName);
        var budget = effectiveBudget(rawBudget);
        var handoff = loadHandoff(workstreamDir);
        var manifest = budget.sections().stream().anyMatch(req -> requiresEvidenceManifest(req.section()))
                ? EvidenceManifest.rebuild(context.workspaceRoot(), workstreamDir, context.workstreamId(),
                        context.instance().inputRefs(), OBJECT_MAPPER)
                : null;
        var manifestPath = manifest == null ? null : manifest.write(workstreamDir, OBJECT_MAPPER);
        var governance = governanceEnabled()
                ? RepoScopeGovernance.build(context.workspaceRoot(), workstreamDir, context, OBJECT_MAPPER)
                : null;

        // Phase 2: Resolve section availability
        var resolved = new ArrayList<ResolvedSection>();
        for (var req : budget.sections()) {
            var available = checkAvailability(req, context, workstreamDir);
            if (req.missingPolicy() == MissingPolicy.FAIL && !available) {
                return new NodeResult("failed", NodeStatus.FAILED, List.of(), Map.of("error", "required section '" + req.section() + "' data not found"), null);
            }
            resolved.add(new ResolvedSection(req, available));
        }

        // Phase 3: Plan budgets - priority order, softBudget allocation
        var sorted = new ArrayList<>(resolved);
        sorted.sort(java.util.Comparator.comparingInt((ResolvedSection rs) -> rs.req.priority().ordinal()));
        int allocated = 0;
        for (var rs : sorted) {
            if (!rs.available) continue;
            if (allocated + rs.req.softBudget() > budget.totalBudget()) {
                rs.degraded = true;
                if (rs.req.overflowPolicy() == OverflowPolicy.DROP_OPTIONAL) {
                    rs.dropped = true;
                } else if (rs.req.overflowPolicy() == OverflowPolicy.FAIL) {
                    return new NodeResult("failed", NodeStatus.FAILED, List.of(), Map.of("error", "budget exceeded at section '" + rs.req.section() + "'"), null);
                }
            } else {
                allocated += rs.req.softBudget();
            }
        }

        // Phase 4: Render sections with degradation
        var builder = new StringBuilder();
        appendGovernedHeader(builder, context, budget, sorted);
        ContextStateEvidenceBoundaryRenderer.append(builder, context, workstreamDir, OBJECT_MAPPER);
        RepoScopeGovernance.appendMarkdownGuidance(builder, governance, context.workspaceRoot(), compactGovernance(budget));

        for (var rs : sorted) {
            if (rs.dropped || !rs.available) {
                if (rs.req.missingPolicy() == MissingPolicy.PLACEHOLDER) {
                    builder.append("## ").append(sectionTitle(rs.req.section())).append("\n\n");
                    builder.append("(not available)\n\n");
                }
                continue;
            }
            var section = renderGovernedSectionOrFailure(rs, context, workstreamDir, handoff, manifest, budget);
            if (section.failure() != null) {
                return section.failure();
            }
            builder.append(section.content());
        }

        // Phase 5: Validate total budget (token-based)
        if (tokenCount(builder.toString()) > budget.totalBudget()) {
            for (int i = sorted.size() - 1; i >= 0; i--) {
                if (tokenCount(builder.toString()) <= budget.totalBudget()) break;
                var rs = sorted.get(i);
                if (!rs.available || rs.dropped) continue;
                if (rs.req.overflowPolicy() == OverflowPolicy.FAIL) continue;
                rs.dropped = true;
            }
            builder.setLength(0);
            appendGovernedHeader(builder, context, budget, sorted);
            ContextStateEvidenceBoundaryRenderer.append(builder, context, workstreamDir, OBJECT_MAPPER);
            RepoScopeGovernance.appendMarkdownGuidance(builder, governance, context.workspaceRoot(), compactGovernance(budget));
            for (var rs : sorted) {
                if (rs.dropped || !rs.available) {
                    if (rs.req.missingPolicy() == MissingPolicy.PLACEHOLDER) {
                        builder.append("## ").append(sectionTitle(rs.req.section())).append("\n\n");
                        builder.append("(not available)\n\n");
                    }
                    continue;
                }
                var section = renderGovernedSectionOrFailure(rs, context, workstreamDir, handoff, manifest, budget);
                if (section.failure() != null) {
                    return section.failure();
                }
                builder.append(section.content());
            }
            if (tokenCount(builder.toString()) > budget.totalBudget()) {
                return new NodeResult("failed", NodeStatus.FAILED, List.of(),
                        Map.of("error", "budget exceeded: " + tokenCount(builder.toString()) + " > " + budget.totalBudget() + " tokens"), null);
            }
        }

        var requiredTypedSubjectTokens = new LinkedHashMap<String, Integer>();
        if (reviewSubject.isPresent()) {
            var start = builder.length();
            ContextPackageSectionRenderer.appendRequiredReviewSubject(builder, reviewSubject.get());
            requiredTypedSubjectTokens.put("required_review_subject", tokenCount(builder.substring(start)));
        }
        if (architectureReviewSubject.isPresent()) {
            var start = builder.length();
            ContextPackageSectionRenderer.appendRequiredArchitectureReviewSubject(
                    builder, architectureReviewSubject.get());
            requiredTypedSubjectTokens.put(
                    "required_architecture_review_subject", tokenCount(builder.substring(start)));
        }
        // Required typed evidence is never dropped; report its cost instead of hiding it behind the soft budget.
        builder.replace(0, builder.indexOf("\n## Context Governance"),
                rebuildGovernedHeader(context, budget, sorted, requiredTypedSubjectTokens));
        var markdown = builder.toString();
        Files.writeString(packagePath, markdown, StandardCharsets.UTF_8);
        var declaredSections = budget.sections().stream()
                .map(SectionRequirement::section)
                .toList();
        var metadataPath = writeContextPackageMetadata(
                context,
                packagePath,
                profileName,
                budget,
                tokenCount(markdown),
                sorted,
                manifest,
                manifestPath,
                memoryEnabled(),
                disabledMemorySections(rawBudget.sections().stream().map(SectionRequirement::section).toList(),
                        budget.sections().stream().map(SectionRequirement::section).toList()),
                governance,
                requiredTypedSubjectTokens
        );
        return buildLegacyResult(context, packagePath, metadataPath, declaredSections);
    }

    /**
     * Writes the structured context governance sidecar.
     *
     * The markdown package remains the agent-facing artifact. The sidecar is
     * the machine-facing evidence boundary used by trace, metrics, and later
     * budget audits; callers should not parse the markdown table for lifecycle
     * decisions.
     */
    private static Path writeContextPackageMetadata(
            TaskContext context,
            Path packagePath,
            String profileName,
            ContextBudget budget,
            int estimatedTokens,
            List<ResolvedSection> sections
    ) throws IOException {
        return writeContextPackageMetadata(context, packagePath, profileName, budget, estimatedTokens, sections, null, null);
    }

    private static Path writeContextPackageMetadata(
            TaskContext context,
            Path packagePath,
            String profileName,
            ContextBudget budget,
            int estimatedTokens,
            List<ResolvedSection> sections,
            EvidenceManifest manifest,
            Path manifestPath
    ) throws IOException {
        return writeContextPackageMetadata(context, packagePath, profileName, budget, estimatedTokens, sections,
                manifest, manifestPath, memoryEnabled(), List.of());
    }

    private static Path writeContextPackageMetadata(
            TaskContext context,
            Path packagePath,
            String profileName,
            ContextBudget budget,
            int estimatedTokens,
            List<ResolvedSection> sections,
            EvidenceManifest manifest,
            Path manifestPath,
            boolean memoryEnabled,
            List<String> memoryDisabledSections
    ) throws IOException {
        return writeContextPackageMetadata(context, packagePath, profileName, budget, estimatedTokens, sections,
                manifest, manifestPath, memoryEnabled, memoryDisabledSections, null, Map.of());
    }

    private static Path writeContextPackageMetadata(
            TaskContext context,
            Path packagePath,
            String profileName,
            ContextBudget budget,
            int estimatedTokens,
            List<ResolvedSection> sections,
            EvidenceManifest manifest,
            Path manifestPath,
            boolean memoryEnabled,
            List<String> memoryDisabledSections,
            RepoScopeGovernance.Documents governance,
            Map<String, Integer> requiredTypedSubjectTokens
    ) throws IOException {
        var metadataPath = packagePath.resolveSibling(stripMarkdownExtension(packagePath.getFileName().toString()) + ".json");
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("schemaVersion", 1);
        metadata.put("workstreamId", context.workstreamId());
        metadata.put("builderNode", context.definition().id());
        metadata.put("targetNode", configText(context, "target_node", ""));
        metadata.put("profile", profileName);
        metadata.put("contextPackage", toWorkspaceRef(context.workspaceRoot(), packagePath));
        metadata.put("estimatedTokens", estimatedTokens);
        metadata.put("tokenBudget", budget == null ? 0 : budget.totalBudget());
        metadata.put("withinBudget", budget == null || estimatedTokens <= budget.totalBudget());
        metadata.put("memoryEnabled", memoryEnabled);
        metadata.put("memoryDisabledSections", memoryDisabledSections);
        metadata.put("contextGovernanceEnabled", governance != null);
        metadata.put("requiredTypedSubjectTokens", Map.copyOf(requiredTypedSubjectTokens));
        metadata.put("generatedAt", Instant.now().toString());
        metadata.put("sections", sections.stream()
                .map(ContextBuildPackageHandler::sectionMetadata)
                .toList());
        if (manifest != null && manifestPath != null) {
            metadata.put("evidenceManifest", toWorkspaceRef(context.workspaceRoot(), manifestPath));
            metadata.put("evidenceRefCount", manifest.sourceRefs().size());
            metadata.put("evidenceRefs", manifest.sourceRefs());
            metadata.put("workingMemoryEvidenceCount", manifest.stageHandoffs().size());
            metadata.put("reviewEvidenceCount", manifest.reviewItems().size());
        }
        if (governance != null) {
            metadata.put("repoMap", toWorkspaceRef(context.workspaceRoot(), governance.repoMapPath()));
            metadata.put("fileRelevancePlan", toWorkspaceRef(context.workspaceRoot(), governance.fileRelevancePlanPath()));
            metadata.put("stageScopePolicy", toWorkspaceRef(context.workspaceRoot(), governance.stageScopePolicyPath()));
            metadata.put("contextGovernanceReport", toWorkspaceRef(context.workspaceRoot(), governance.governanceReportPath()));
            metadata.put("workstreamMemory", toWorkspaceRef(context.workspaceRoot(), governance.workstreamMemoryPath()));
            metadata.put("workingSet", toWorkspaceRef(context.workspaceRoot(), governance.workingSetPath()));
            metadata.put("fileSummaries", toWorkspaceRef(context.workspaceRoot(), governance.fileSummariesPath()));
            metadata.put("readReuseContract", toWorkspaceRef(context.workspaceRoot(), governance.readReuseContractPath()));
            metadata.put("agentCapabilityCatalog", toWorkspaceRef(context.workspaceRoot(), governance.agentCapabilityCatalogPath()));
        }
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(metadataPath.toFile(), metadata);
        return metadataPath;
    }

    private static Map<String, Object> sectionMetadata(ResolvedSection rs) {
        var section = new LinkedHashMap<String, Object>();
        section.put("name", rs.req.section());
        section.put("available", rs.available);
        section.put("degraded", rs.degraded);
        section.put("dropped", rs.dropped);
        section.put("priority", rs.req.priority().name());
        section.put("overflowPolicy", rs.req.overflowPolicy().name());
        section.put("softBudgetTokens", rs.req.softBudget());
        section.put("originalTokens", rs.originalChars);
        section.put("renderedTokens", rs.renderedChars);
        section.put("degradation", rs.degraded ? rs.req.degradeTo().strategy().name() : "FULL");
        return section;
    }

    private static String stripMarkdownExtension(String filename) {
        return filename.endsWith(".md") ? filename.substring(0, filename.length() - 3) : filename;
    }

    private static boolean checkAvailability(SectionRequirement req, TaskContext context, Path workstreamDir) {
        return switch (req.section()) {
            case "requirement" -> Files.exists(workstreamDir.resolve("requirement.md"));
            case "input_artifacts" -> !context.instance().inputRefs().isEmpty();
            case "iteration_history" -> Files.exists(workstreamDir.resolve("iterations"));
            case "human_feedback" -> Files.exists(workstreamDir.resolve("nodes"));
            case "recent_agent_outputs" -> Files.exists(workstreamDir.resolve("runs"));
            case "git_evidence" -> Files.exists(workstreamDir.resolve("artifacts/git-evidence"));
            case "ledger_pointers" -> Files.exists(workstreamDir.resolve("events.jsonl"));
            case "task_contract" -> Files.exists(workstreamDir.resolve("artifacts/task-contract.json"));
            case "working_memory", "review_evidence" -> Files.exists(workstreamDir.resolve("memory/workstream-memory.json"));
            default -> false;
        };
    }

    private static void appendGovernedHeader(StringBuilder builder, TaskContext context,
                                              ContextBudget budget, List<ResolvedSection> sections) {
        builder.append("# Context Package\n\n");
        builder.append("- Workstream: ").append(context.workstreamId()).append("\n");
        builder.append("- Builder Node: ").append(context.definition().id()).append("\n");
        builder.append("- Target Node: ").append(configText(context, "target_node", "(not specified)")).append("\n");
        builder.append("- Generated At: ").append(Instant.now()).append("\n");
        builder.append("- Profile: ").append(profile(context)).append("\n");
        builder.append("- Total: -- / ").append(formatTokens(budget.totalBudget())).append(" tokens budget\n\n");

        builder.append("## Context Governance\n\n");
        builder.append("| Section | Level | Degraded | Original | Current |\n");
        builder.append("|---|---|---|---|---|\n");
        for (var rs : sections) {
            var level = rs.degraded ? rs.req.degradeTo().strategy().name().toLowerCase() : "full";
            var degraded = rs.degraded ? "yes" : "no";
            if (!rs.available) {
                if (rs.req.missingPolicy() == MissingPolicy.PLACEHOLDER) degraded = "-";
                else degraded = "skip";
            }
            var original = rs.available ? formatTokens(rs.originalChars) : "-";
            var current = rs.available ? formatTokens(rs.renderedChars) : "-";
            builder.append("| ").append(rs.req.section()).append(" | ").append(level)
                   .append(" | ").append(degraded).append(" | ").append(original)
                   .append(" | ").append(current).append(" |\n");
        }
        builder.append("\nDegraded sections have full data at their original paths listed below.\n\n");
    }

    private static String rebuildGovernedHeader(
            TaskContext context,
            ContextBudget budget,
            List<ResolvedSection> sections,
            Map<String, Integer> requiredTypedSubjectTokens
    ) {
        var sb = new StringBuilder();
        int total = 0;
        for (var rs : sections) total += rs.renderedChars;
        total += requiredTypedSubjectTokens.values().stream().mapToInt(Integer::intValue).sum();

        sb.append("# Context Package\n\n");
        sb.append("- Workstream: ").append(context.workstreamId()).append("\n");
        sb.append("- Builder Node: ").append(context.definition().id()).append("\n");
        sb.append("- Target Node: ").append(configText(context, "target_node", "(not specified)")).append("\n");
        sb.append("- Generated At: ").append(Instant.now()).append("\n");
        sb.append("- Profile: ").append(profile(context)).append("\n");
        sb.append("- Total: ").append(formatTokens(total)).append(" / ").append(formatTokens(budget.totalBudget())).append(" tokens\n\n");

        sb.append("## Context Governance\n\n");
        sb.append("| Section | Level | Degraded | Original | Current |\n");
        sb.append("|---|---|---|---|---|\n");
        for (var rs : sections) {
            var level = rs.degraded ? rs.req.degradeTo().strategy().name().toLowerCase() : "full";
            var degraded = rs.degraded ? "yes" : "no";
            if (!rs.available) {
                degraded = rs.req.missingPolicy() == MissingPolicy.PLACEHOLDER ? "-" : "skip";
            }
            sb.append("| ").append(rs.req.section()).append(" | ").append(level)
              .append(" | ").append(degraded).append(" | ").append(formatTokens(rs.originalChars))
              .append(" | ").append(formatTokens(rs.renderedChars)).append(" |\n");
        }
        for (var subject : requiredTypedSubjectTokens.entrySet()) {
            sb.append("| ").append(subject.getKey()).append(" | full | no | ")
                    .append(formatTokens(subject.getValue()))
                    .append(" | ").append(formatTokens(subject.getValue())).append(" |\n");
        }
        sb.append("\nDegraded sections have full data at their original paths listed below.\n\n");
        return sb.toString();
    }

    private static RenderedSection renderGovernedSectionOrFailure(
            ResolvedSection rs,
            TaskContext context,
            Path workstreamDir,
            StageHandoffDocument handoff,
            EvidenceManifest manifest,
            ContextBudget budget
    ) throws IOException {
        var section = new StringBuilder();
        appendGovernedSection(section, rs, context, workstreamDir, handoff, manifest, budget);
        var full = section.toString();
        rs.originalChars = tokenCount(full);

        if (rs.originalChars <= rs.req.softBudget()) {
            rs.renderedChars = rs.originalChars;
            return RenderedSection.content(full);
        }
        if (rs.req.degradeTo().strategy() == Degradation.ADAPTIVE) {
            rs.degraded = true;
            var capped = capSectionToBudget(rs.req.section(), full, rs.req.softBudget(), workstreamDir);
            rs.renderedChars = tokenCount(capped);
            return RenderedSection.content(capped);
        }
        if (rs.req.overflowPolicy() == OverflowPolicy.FAIL) {
            var error = "section '" + rs.req.section() + "' exceeded soft budget: "
                    + rs.originalChars + " > " + rs.req.softBudget() + " tokens";
            return RenderedSection.failure(new NodeResult("failed", NodeStatus.FAILED, List.of(), Map.of("error", error), null));
        }

        rs.degraded = true;
        if (rs.req.degradeTo().strategy() == Degradation.DECISION_ONLY) {
            section.setLength(0);
            appendGovernedSection(section, rs, context, workstreamDir, handoff, manifest, budget);
            var decisions = section.toString();
            rs.renderedChars = tokenCount(decisions);
            if (rs.renderedChars <= rs.req.softBudget()) {
                return RenderedSection.content(decisions);
            }
            var capped = capSectionToBudget(rs.req.section(), decisions, rs.req.softBudget(), workstreamDir);
            rs.renderedChars = tokenCount(capped);
            return RenderedSection.content(capped);
        }
        var capped = capSectionToBudget(rs.req.section(), full, rs.req.softBudget(), workstreamDir);
        rs.renderedChars = tokenCount(capped);
        return RenderedSection.content(capped);
    }

    private static void appendGovernedSection(StringBuilder builder, ResolvedSection rs,
                                               TaskContext context, Path workstreamDir,
                                               StageHandoffDocument handoff,
                                               EvidenceManifest manifest,
                                               ContextBudget budget) throws IOException {
        builder.append("## ").append(sectionTitle(rs.req.section())).append("\n\n");
        var strategy = rs.degraded ? rs.req.degradeTo().strategy() : Degradation.FULL;
        var maxItems = rs.degraded ? rs.req.degradeTo().maxItems() : 0;

        switch (rs.req.section()) {
            case "requirement" -> {
                var content = readOrDefault(workstreamDir.resolve("requirement.md"), "(requirement.md not found)");
                // ADAPTIVE: compress if content exceeds softBudget tokens, regardless of degraded flag
                if (rs.req.degradeTo().strategy() == Degradation.ADAPTIVE
                        && tokenCount(content) > rs.req.softBudget()) {
                    builder.append(snippetToTokens(content, rs.req.softBudget(), workstreamDir.resolve("requirement.md")));
                } else {
                    builder.append(content);
                }
                builder.append("\n\n");
            }
            case "input_artifacts" -> {
                if (strategy == Degradation.SUMMARY) {
                    ContextPackageSectionRenderer.appendInputArtifactsSummary(builder, context);
                } else {
                    ContextPackageSectionRenderer.appendInputArtifacts(builder, context, maxSnippetChars(context, budget));
                }
            }
            case "iteration_history" -> {
                if (strategy == Degradation.DECISION_ONLY) {
                    ContextPackageSectionRenderer.appendIterationHistoryDecisions(builder, workstreamDir, maxItems > 0 ? maxItems : 5);
                } else {
                    ContextPackageSectionRenderer.appendIterationHistory(builder, workstreamDir);
                }
                ContextPackageSectionRenderer.appendCompletedWork(builder, handoff);
                ContextPackageSectionRenderer.appendImportantDecisions(builder, handoff);
            }
            case "human_feedback" -> {
                ContextPackageSectionRenderer.appendHumanFeedbackLimited(builder, workstreamDir, maxItems > 0 ? maxItems : 3);
                ContextPackageSectionRenderer.appendOpenRisks(builder, handoff);
                ContextPackageSectionRenderer.appendRecommendedNextStage(builder, handoff);
            }
            case "recent_agent_outputs" -> {
                ContextPackageSectionRenderer.appendRecentAgentOutputsLimited(
                        builder, context, maxItems > 0 ? maxItems : 2);
            }
            case "working_memory" -> {
                ContextPackageSectionRenderer.appendWorkingMemory(builder, workstreamDir, context.workspaceRoot());
            }
            case "review_evidence" -> {
                ContextPackageSectionRenderer.appendReviewEvidenceBrief(builder, workstreamDir, context.workspaceRoot());
            }
            case "git_evidence" -> {
                if (strategy == Degradation.DIFF_STAT) {
                    ContextPackageSectionRenderer.appendGitEvidenceDiffStat(builder, workstreamDir, handoff);
                } else {
                    ContextPackageSectionRenderer.appendGitEvidence(builder, workstreamDir, maxSnippetChars(context, budget));
                }
            }
            case "ledger_pointers" -> {
                builder.append("- Events: ").append(toDisplayRef(workstreamDir.resolve("events.jsonl"))).append("\n");
                builder.append("- Workflow current: ").append(toDisplayRef(workstreamDir.resolve("workflow/current.json"))).append("\n");
                builder.append("- Node state directory: ").append(toDisplayRef(workstreamDir.resolve("nodes"))).append("\n");
                builder.append("- Agent runs directory: ").append(toDisplayRef(workstreamDir.resolve("runs"))).append("\n\n");
            }
            case "task_contract" -> {
                var contractPath = workstreamDir.resolve("artifacts/task-contract.json");
                if (Files.exists(contractPath)) {
                    ContextPackageSectionRenderer.appendTaskContract(builder, contractPath);
                    ContextPackageSectionRenderer.appendContractResolution(builder, workstreamDir.resolve("artifacts/contract-resolution.json"));
                } else {
                    builder.append("(task-contract.json not found)\n\n");
                }
            }
            default -> builder.append("(unknown section)\n\n");
        }
    }

    private static String sectionTitle(String section) {
        return switch (section) {
            case "requirement" -> "User Requirement";
            case "input_artifacts" -> "Input Artifacts";
            case "iteration_history" -> "Iteration History";
            case "human_feedback" -> "Human Feedback";
            case "recent_agent_outputs" -> "Recent Agent Outputs";
            case "working_memory" -> "Working Memory";
            case "review_evidence" -> "Review Evidence Brief";
            case "git_evidence" -> "Git Evidence";
            case "ledger_pointers" -> "Ledger Pointers";
            case "task_contract" -> "TaskContract";
            default -> section;
        };
    }

    private static int tokenCount(String text) {
        return TOKEN_ESTIMATOR.estimate(text);
    }

    private static String formatTokens(int count) {
        if (count <= 0) return "0";
        if (count >= 1_000) return String.format("%.1fk", count / 1_000.0);
        return String.valueOf(count);
    }

    private static final class ResolvedSection {
        final SectionRequirement req;
        final boolean available;
        boolean degraded;
        boolean dropped;
        int originalChars;
        int renderedChars;

        ResolvedSection(SectionRequirement req, boolean available) {
            this.req = req;
            this.available = available;
        }
    }

    private static boolean requiresEvidenceManifest(String section) {
        return "working_memory".equals(section) || "review_evidence".equals(section);
    }

    private static String renderPackage(
            TaskContext context,
            Path workstreamDir,
            EvidenceManifest manifest,
            RepoScopeGovernance.Documents governance,
            Optional<TaskContractProposalReference.ResolvedProposal> reviewSubject,
            Optional<AgentEvidenceIndex.Entry> architectureReviewSubject
    ) throws IOException {
        var targetNode = configText(context, "target_node", "(not specified)");
        var purpose = configText(context, "purpose", "Prepare focused context for the next agent step.");
        var profile = profile(context);
        var sections = effectiveSections(context);
        var maxSnippetChars = maxSnippetChars(context);
        var maxRecentAgentOutputs = maxRecentAgentOutputs(context);
        var requirement = readOrDefault(workstreamDir.resolve("requirement.md"), "(requirement.md not found)");
        var builder = new StringBuilder();
        builder.append("# Context Package\n\n");
        builder.append("- Workstream: ").append(context.workstreamId()).append("\n");
        builder.append("- Builder Node: ").append(context.definition().id()).append("\n");
        builder.append("- Target Node: ").append(targetNode).append("\n");
        builder.append("- Generated At: ").append(Instant.now()).append("\n");
        builder.append("- Purpose: ").append(purpose).append("\n\n");

        builder.append("## Context Contract\n\n");
        builder.append("- Profile: ").append(profile).append("\n");
        builder.append("- Included Sections: ").append(String.join(", ", sections)).append("\n");
        builder.append("- Max Snippet Chars: ").append(maxSnippetChars).append("\n");
        builder.append("- Max Recent Agent Outputs: ").append(maxRecentAgentOutputs).append("\n\n");
        ContextStateEvidenceBoundaryRenderer.append(builder, context, workstreamDir, OBJECT_MAPPER);
        RepoScopeGovernance.appendMarkdownGuidance(builder, governance, context.workspaceRoot());

        if (sections.contains("requirement")) {
            builder.append("## User Requirement\n\n");
            builder.append(requirement).append("\n\n");
        }

        if (sections.contains("input_artifacts")) {
            builder.append("## Input Artifacts\n\n");
            ContextPackageSectionRenderer.appendInputArtifacts(builder, context, maxSnippetChars);
        }

        if (sections.contains("iteration_history")) {
            builder.append("## Iteration History\n\n");
            ContextPackageSectionRenderer.appendIterationHistory(builder, workstreamDir);
        }

        if (sections.contains("human_feedback")) {
            builder.append("## Human Feedback\n\n");
            ContextPackageSectionRenderer.appendHumanFeedback(builder, workstreamDir);
        }

        if (sections.contains("recent_agent_outputs")) {
            builder.append("## Recent Agent Outputs\n\n");
            ContextPackageSectionRenderer.appendRecentAgentOutputs(
                    builder, context, maxRecentAgentOutputs);
        }

        if (sections.contains("working_memory")) {
            builder.append("## Working Memory\n\n");
            ContextPackageSectionRenderer.appendWorkingMemory(builder, workstreamDir, context.workspaceRoot());
        }

        if (sections.contains("review_evidence")) {
            builder.append("## Review Evidence Brief\n\n");
            ContextPackageSectionRenderer.appendReviewEvidenceBrief(builder, workstreamDir, context.workspaceRoot());
        }

        if (sections.contains("git_evidence")) {
            builder.append("## Git Evidence\n\n");
            ContextPackageSectionRenderer.appendGitEvidence(builder, workstreamDir, maxSnippetChars);
        }

        if (sections.contains("ledger_pointers")) {
            builder.append("## Ledger Pointers\n\n");
            builder.append("- Events: ").append(toDisplayRef(workstreamDir.resolve("events.jsonl"))).append("\n");
            builder.append("- Workflow current: ").append(toDisplayRef(workstreamDir.resolve("workflow/current.json"))).append("\n");
            builder.append("- Node state directory: ").append(toDisplayRef(workstreamDir.resolve("nodes"))).append("\n");
            builder.append("- Agent runs directory: ").append(toDisplayRef(workstreamDir.resolve("runs"))).append("\n");
        }
        if (reviewSubject.isPresent()) {
            ContextPackageSectionRenderer.appendRequiredReviewSubject(builder, reviewSubject.get());
        }
        if (architectureReviewSubject.isPresent()) {
            ContextPackageSectionRenderer.appendRequiredArchitectureReviewSubject(
                    builder, architectureReviewSubject.get());
        }
        return builder.toString();
    }

    private static Optional<TaskContractProposalReference.ResolvedProposal> resolveRequiredReviewSubject(
            TaskContext context
    ) {
        if (!requiresTaskContractProposal(context)) {
            return Optional.empty();
        }
        return TaskContractProposalReference.resolveProposalFromRefs(
                context.workspaceRoot(),
                context.workstreamId(),
                context.instance().inputRefs(),
                OBJECT_MAPPER);
    }

    private static boolean requiresTaskContractProposal(TaskContext context) {
        return "task_contract_proposal".equals(configText(context, "required_input_artifact", ""));
    }

    private static Optional<AgentEvidenceIndex.Entry> resolveRequiredArchitectureReviewSubject(
            TaskContext context
    ) {
        if (!requiresArchitectureReviewSubject(context)) {
            return Optional.empty();
        }
        return AgentEvidenceIndex.fromRefs(
                        context.workspaceRoot(), context.workstreamId(), context.instance().inputRefs(), OBJECT_MAPPER)
                .recent(Math.max(1, context.instance().inputRefs().size()))
                .stream()
                .filter(entry -> "design.claude_design".equals(entry.document().nodeId()))
                .findFirst();
    }

    private static boolean requiresArchitectureReviewSubject(TaskContext context) {
        return "architecture_review".equals(profile(context))
                && "design.codex_arch_review".equals(configText(context, "target_node", ""));
    }

    private static Map<String, Integer> requiredTypedSubjectTokens(
            Optional<TaskContractProposalReference.ResolvedProposal> reviewSubject,
            Optional<AgentEvidenceIndex.Entry> architectureReviewSubject
    ) throws IOException {
        var tokens = new LinkedHashMap<String, Integer>();
        if (reviewSubject.isPresent()) {
            var rendered = new StringBuilder();
            ContextPackageSectionRenderer.appendRequiredReviewSubject(rendered, reviewSubject.get());
            tokens.put("required_review_subject", tokenCount(rendered.toString()));
        }
        if (architectureReviewSubject.isPresent()) {
            var rendered = new StringBuilder();
            ContextPackageSectionRenderer.appendRequiredArchitectureReviewSubject(
                    rendered, architectureReviewSubject.get());
            tokens.put("required_architecture_review_subject", tokenCount(rendered.toString()));
        }
        return Map.copyOf(tokens);
    }

    private static Optional<RequiredAgentEvidence> missingRequiredAgentEvidence(TaskContext context) {
        var configured = context.definition().config().get("required_agent_evidence");
        if (configured == null) {
            return Optional.empty();
        }
        var required = RequiredAgentEvidence.from(configured);
        var evidence = AgentEvidenceIndex.fromRefs(
                context.workspaceRoot(), context.workstreamId(), context.instance().inputRefs(), OBJECT_MAPPER);
        return evidence.contains(required.nodeId(), required.sourceArtifactType(), required.decision())
                ? Optional.empty()
                : Optional.of(required);
    }

    private record RequiredAgentEvidence(String nodeId, String sourceArtifactType, String decision) {
        static RequiredAgentEvidence from(Object configured) {
            if (!(configured instanceof Map<?, ?> values)) {
                throw new IllegalArgumentException("required_agent_evidence must be an object");
            }
            var requirement = new RequiredAgentEvidence(
                    requiredText(values, "node_id"),
                    requiredText(values, "source_artifact_type"),
                    requiredText(values, "decision"));
            return requirement;
        }

        String description() {
            return nodeId + "/" + sourceArtifactType + "/" + decision;
        }

        Map<String, Object> toMap() {
            return Map.of(
                    "node_id", nodeId,
                    "source_artifact_type", sourceArtifactType,
                    "decision", decision);
        }

        private static String requiredText(Map<?, ?> values, String key) {
            var value = values.get(key);
            if (!(value instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException("required_agent_evidence." + key + " is required");
            }
            return text;
        }
    }

    private static String snippetToTokens(String content, int maxTokens, Path path) {
        var tokens = tokenCount(content);
        if (tokens <= maxTokens) return content.stripTrailing();
        // Estimate char position from token budget (4 chars/token ratio)
        var estimatedChars = Math.min(content.length(), maxTokens * 4);
        var truncated = content.substring(0, estimatedChars).stripTrailing();
        return truncated + "\n\n[adaptively compressed to ~" + maxTokens + " tokens; full data at " + toDisplayRef(path) + "]";
    }

    private static String capSectionToBudget(String section, String content, int maxTokens, Path workstreamDir) {
        var heading = "## " + sectionTitle(section) + "\n\n";
        var marker = "\n\n[adaptively compressed; section degraded to stay within ~" + maxTokens
                + " tokens; full source remains in ledger under " + toDisplayRef(workstreamDir) + "]\n\n";
        var bodyBudget = Math.max(100, maxTokens - tokenCount(heading) - tokenCount(marker));
        var maxChars = Math.min(content.length(), bodyBudget * 4);
        var body = content.substring(0, maxChars).stripTrailing();
        if (body.startsWith(heading)) {
            return body + marker;
        }
        return heading + body + marker;
    }

    private record RenderedSection(String content, NodeResult failure) {
        static RenderedSection content(String content) {
            return new RenderedSection(content, null);
        }

        static RenderedSection failure(NodeResult failure) {
            return new RenderedSection("", failure);
        }
    }

    private static String readOrDefault(Path path, String fallback) throws IOException {
        if (!Files.exists(path)) {
            return fallback;
        }
        return Files.readString(path, StandardCharsets.UTF_8).stripTrailing();
    }

    private static String configText(TaskContext context, String key, String fallback) {
        var value = context.definition().config().get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return fallback;
    }

    private static String profile(TaskContext context) {
        return configText(context, "profile", "full");
    }

    private static List<String> sections(TaskContext context) {
        var override = context.definition().config().get("include_sections");
        if (override instanceof List<?> values && !values.isEmpty()) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(section -> FULL_SECTIONS.contains(section))
                    .distinct()
                    .toList();
        }
        return switch (profile(context)) {
            case "product_review" -> List.of(
                    "requirement",
                    "input_artifacts",
                    "recent_agent_outputs",
                    "ledger_pointers"
            );
            case "architecture_review" -> List.of(
                    "requirement",
                    "input_artifacts",
                    "iteration_history",
                    "human_feedback",
                    "recent_agent_outputs",
                    "ledger_pointers"
            );
            case "plan_review" -> List.of(
                    "requirement",
                    "input_artifacts",
                    "iteration_history",
                    "human_feedback",
                    "recent_agent_outputs",
                    "ledger_pointers"
            );
            case "implementation" -> List.of(
                    "requirement",
                    "input_artifacts",
                    "iteration_history",
                    "human_feedback",
                    "recent_agent_outputs",
                    "ledger_pointers"
            );
            case "acceptance" -> List.of(
                    "requirement",
                    "input_artifacts",
                    "iteration_history",
                    "human_feedback",
                    "recent_agent_outputs",
                    "git_evidence",
                    "task_contract",
                    "ledger_pointers"
            );
            default -> FULL_SECTIONS;
        };
    }

    private static List<String> effectiveSections(TaskContext context) {
        return filterMemorySections(sections(context));
    }

    private static ContextBudget effectiveBudget(ContextBudget budget) {
        var filtered = budget.sections().stream()
                .filter(req -> memoryEnabled() || !MEMORY_SECTIONS.contains(req.section()))
                .toList();
        if (filtered.size() == budget.sections().size()) {
            return budget;
        }
        return new ContextBudget(budget.totalBudget(), filtered, budget.configOverrides());
    }

    private static List<String> filterMemorySections(List<String> sections) {
        if (memoryEnabled()) {
            return sections;
        }
        return sections.stream()
                .filter(section -> !MEMORY_SECTIONS.contains(section))
                .toList();
    }

    private static List<String> disabledMemorySections(List<String> rawSections, List<String> effectiveSections) {
        if (memoryEnabled()) {
            return List.of();
        }
        return rawSections.stream()
                .filter(MEMORY_SECTIONS::contains)
                .filter(section -> !effectiveSections.contains(section))
                .distinct()
                .toList();
    }

    private static boolean memoryEnabled() {
        var property = System.getProperty("dual.context.memory.enabled");
        if (property != null && !property.isBlank()) {
            return !isFalse(property);
        }
        var env = System.getenv("DUAL_CONTEXT_MEMORY_ENABLED");
        if (env != null && !env.isBlank()) {
            return !isFalse(env);
        }
        return true;
    }

    private static boolean governanceEnabled() {
        var property = System.getProperty("dual.context.governance.enabled");
        if (property != null && !property.isBlank()) {
            return !isFalse(property);
        }
        var env = System.getenv("DUAL_CONTEXT_GOVERNANCE_ENABLED");
        if (env != null && !env.isBlank()) {
            return !isFalse(env);
        }
        return true;
    }

    private static boolean isFalse(String value) {
        var normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("false") || normalized.equals("0") || normalized.equals("no") || normalized.equals("off");
    }

    private static int maxSnippetChars(TaskContext context) {
        return positiveIntConfig(context, "max_snippet_chars", MAX_SNIPPET_CHARS, Map.of());
    }

    private static int maxSnippetChars(TaskContext context, ContextBudget budget) {
        return positiveIntConfig(context, "max_snippet_chars", MAX_SNIPPET_CHARS, budget.configOverrides());
    }

    private static int maxRecentAgentOutputs(TaskContext context) {
        return positiveIntConfig(context, "max_recent_agent_outputs", MAX_RECENT_AGENT_OUTPUTS, Map.of());
    }

    private static boolean compactGovernance(ContextBudget budget) {
        return Boolean.TRUE.equals(budget.configOverrides().get("compact_governance"));
    }

    private static int positiveIntConfig(TaskContext context, String key, int fallback, Map<String, Object> overrides) {
        var override = overrides.get(key);
        if (override instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        if (override instanceof String text && text.matches("\\d+")) {
            return Integer.parseInt(text);
        }
        var value = context.definition().config().get(key);
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        if (value instanceof String text && text.matches("\\d+")) {
            return Integer.parseInt(text);
        }
        return fallback;
    }

    private static String toWorkspaceRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private static String toDisplayRef(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static StageHandoffDocument loadHandoff(Path workstreamDir) {
        var path = workstreamDir.resolve("artifacts/next-stage/handoff.json");
        if (!Files.exists(path)) return emptyHandoff();
        return StageHandoffDocument.read(path, OBJECT_MAPPER).orElseGet(ContextBuildPackageHandler::emptyHandoff);
    }

    private static StageHandoffDocument emptyHandoff() {
        return new StageHandoffDocument(1, "", "", "", "", List.of(), List.of(), List.of(), List.of(), "", "", "", "");
    }
}
