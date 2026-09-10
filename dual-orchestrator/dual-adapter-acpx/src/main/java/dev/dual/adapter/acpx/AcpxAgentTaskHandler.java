package dev.dual.adapter.acpx;

import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.execution.AdapterSignal;
import dev.dual.core.execution.AgentExecutionTimelineEventKind;
import dev.dual.runtime.handler.AgentExecutionAttemptReconciler;
import dev.dual.runtime.handler.AgentExecutionAttemptTimeoutPolicy;
import dev.dual.runtime.handler.AgentRunGovernanceContext;
import dev.dual.runtime.handler.AgentResultPromptContract;
import dev.dual.runtime.handler.ContractRegistry;
import dev.dual.runtime.handler.TaskHandler;
import dev.dual.runtime.handler.TaskContractRevisionContextResolver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AcpxAgentTaskHandler implements TaskHandler {
    private final AcpxAgentResultAcquirer resultAcquirer;
    private final boolean prepareOnly;
    private final AgentPromptRenderer promptRenderer;
    private final AgentRunMetadataWriter metadataWriter;
    private final AcpxUsageReader usageReader;
    private final TaskContractRevisionContextResolver revisionContextResolver =
            new TaskContractRevisionContextResolver();
    // Closes a started execution that never reached the completion finalizer. This is the same
    // closer used by workflow recovery, so both paths replay the same durable execution facts.
    private final AgentExecutionAttemptReconciler attemptReconciler =
            new AgentExecutionAttemptReconciler(new AgentExecutionAttemptTimeoutPolicy().timeout());
    private final PromptManifestWriter promptManifestWriter;
    private final SessionCompactionPolicy sessionCompactionPolicy = new SessionCompactionPolicy();
    private final SessionCompactionSeedWriter sessionCompactionSeedWriter = new SessionCompactionSeedWriter();

    public AcpxAgentTaskHandler(AcpxAgentTransport transport, boolean prepareOnly) {
        this(transport, prepareOnly, new AcpxUsageReader());
    }

    AcpxAgentTaskHandler(AcpxAgentTransport transport, boolean prepareOnly, AcpxUsageReader usageReader) {
        this(transport, prepareOnly, usageReader, new PromptManifestWriter());
    }

    AcpxAgentTaskHandler(AcpxAgentTransport transport, boolean prepareOnly, AcpxUsageReader usageReader,
                         PromptManifestWriter promptManifestWriter) {
        if (transport == null) {
            throw new IllegalArgumentException("transport is required");
        }
        if (usageReader == null) {
            throw new IllegalArgumentException("usageReader is required");
        }
        this.resultAcquirer = new AcpxAgentResultAcquirer(transport);
        this.prepareOnly = prepareOnly;
        this.promptRenderer = new AgentPromptRenderer();
        this.metadataWriter = new AgentRunMetadataWriter();
        this.usageReader = usageReader;
        this.promptManifestWriter = java.util.Objects.requireNonNull(promptManifestWriter);
    }

    @Override
    public String type() {
        return "acpx.agent_run";
    }

    @Override
    public NodeResult execute(TaskContext context) throws Exception {
        var agent = configText(context, "agent");
        if (!"claude".equals(agent) && !"codex".equals(agent)) {
            throw new IllegalArgumentException("unsupported agent: " + agent);
        }
        var configuredSession = configText(context, "session");
        var promptTemplate = configText(context, "prompt_template");
        var runId = "run-" + Instant.now().toEpochMilli();
        var e1Enabled = "claude".equals(agent) && hasNamedPermissionProfile(context);
        var emissionContext = proposalEmissionContext(context);
        if (emissionContext.failed() != null) {
            return emissionContext.failed();
        }
        var agentResultContract = agentResultContractPrompt(
                context, runId, emissionContext.context());
        var sessionScopeConfigValue = context.definition().config().get("session_scope");
        var nodeConfigScope = sessionScopeConfigValue instanceof String text ? text.trim() : "";
        var sessionDecision = e1Enabled
                ? SessionScopePolicy.occurrenceBound(
                        configuredSession,
                        context.workstreamId(),
                        context.instance().stageId(),
                        context.instance().iterationId(),
                        runId)
                : SessionScopePolicy.resolve(
                        agent,
                        configuredSession,
                        context.workstreamId(),
                        context.instance().stageId(),
                        context.instance().iterationId(),
                        runId,
                        nodeConfigScope.isBlank() ? null : nodeConfigScope);
        var compactionDecision = sessionCompactionPolicy.decide(
                agent,
                context.definition().id(),
                context.workstreamId(),
                runId,
                configuredSession,
                sessionDecision,
                context.workspaceRoot()
        );
        if (e1Enabled && compactionDecision.enabled()) {
            compactionDecision = new SessionCompactionPolicy.Decision(
                    compactionDecision.mode(),
                    true,
                    sessionDecision.sessionName(),
                    sessionDecision.segmentKey(),
                    compactionDecision.seedKind(),
                    compactionDecision.reason() + "; occurrence-bound session retained"
            );
        }
        var session = compactionDecision.enabled() ? compactionDecision.sessionName() : sessionDecision.sessionName();
        var sessionMeta = SessionMeta.from(
                sessionDecision,
                context.instance().stageId(),
                context.instance().iterationId()
        );
        var runDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId())
                .resolve("runs")
                .resolve(runId);
        var promptPath = runDir.resolve("prompts").resolve(promptTemplate + ".md");
        var outputPath = runDir.resolve("outputs").resolve(promptTemplate + ".json");
        var transcriptPath = runDir.resolve("outputs").resolve(promptTemplate + ".transcript.log");
        var stderrPath = runDir.resolve("outputs").resolve(promptTemplate + ".stderr.log");
        var initialOutputPath = runDir.resolve("outputs").resolve(promptTemplate + ".attempt-1.json");
        var correctionPromptPath = runDir.resolve("prompts").resolve(promptTemplate + ".contract-correction.md");
        var correctionOutputPath = runDir.resolve("outputs").resolve(promptTemplate + ".contract-correction.json");
        var correctionTranscriptPath = runDir.resolve("outputs").resolve(promptTemplate + ".contract-correction.transcript.log");
        var correctionStderrPath = runDir.resolve("outputs").resolve(promptTemplate + ".contract-correction.stderr.log");
        Files.createDirectories(promptPath.getParent());
        Files.createDirectories(outputPath.getParent());
        var seedPath = sessionCompactionSeedWriter.write(
                context.workspaceRoot(),
                context.workstreamId(),
                runDir,
                compactionDecision
        ).orElse(null);
        var inputRefs = new java.util.ArrayList<>(context.instance().inputRefs());
        AgentRunGovernanceContext.PreparedContext governanceContext;
        try {
            governanceContext = AgentRunGovernanceContext.ensureQualified(context, runId);
            inputRefs.add(governanceContext.contextRef());
        } catch (AgentRunGovernanceContext.QualificationException failure) {
            return new NodeResult(
                    AgentRunGovernanceContext.QUALIFICATION_FAILURE_OUTCOME,
                    NodeStatus.FAILED,
                    List.of(),
                    Map.of("error", failure.getMessage()),
                    failure.getMessage());
        } catch (AgentRunGovernanceContext.AuthoredContextException failure) {
            return new NodeResult("REQUIRED_AGENT_EVIDENCE_MISSING", NodeStatus.FAILED, List.of(),
                    Map.of("failure_code", "AUTHORED_CONTEXT_INVALID", "error", failure.getMessage()), failure.getMessage());
        }
        if (seedPath != null) {
            inputRefs.add(context.workspaceRoot().relativize(seedPath).toString());
        }
        var compactionMeta = SessionCompactionMeta.from(compactionDecision, context.workspaceRoot(), seedPath);
        var effectivePermissionProfile = permissionProfile(context);
        if (e1Enabled && effectivePermissionProfile.approveAll()) {
            throw new IllegalStateException(
                    "NETWORK_AUTHORITY_NOT_CONFIGURED: approveAll cannot be combined with E1 policy");
        }
        var networkGrant = e1Enabled && !prepareOnly
                ? new dev.dual.ledger.file.ExecutionNetworkGrantStore().claimIfPresent(
                        new dev.dual.ledger.file.WorkstreamPaths(context.workspaceRoot().toRealPath(),
                                new dev.dual.core.id.DualId(context.workstreamId())), context.instance().nodeInstanceId(), runId)
                : null;
        var executionPolicy = e1Enabled
                ? ClaudeExecutionPolicy.write(
                        context.workspaceRoot(),
                        context.workstreamId(),
                        context.instance().nodeInstanceId(),
                        runId,
                        runDir,
                        effectivePermissionProfile, networkGrant)
                : null;
        var rendered = withExternalResearchGuidance(promptRenderer.renderWithSections(
                context.workspaceRoot(),
                context.workstreamId(),
                runId,
                context.definition().id(),
                promptTemplate,
                inputRefs,
                agentResultContract,
                governanceContext.trustedTaskContractPayload().orElse(null)
        ), effectivePermissionProfile);
        var authored = governanceContext.authoredContext().orElse(null);
        try {
            if (authored != null) {
                var body = authored.render(context.workspaceRoot());
                var sections = new ArrayList<>(rendered.sections());
                sections.add(new AgentPromptRenderer.PromptSection(
                        dev.dual.runtime.handler.AuthoredExecutionContext.SECTION_NAME, "typed_authored_context",
                        context.instance().nodeInstanceId(), body, "required approved and revision inputs", true));
                rendered = new AgentPromptRenderer.RenderedPrompt(rendered.content() + "\n\n" + body, List.copyOf(sections));
                agentResultContract += "\n\n" + body;
            }
        } catch (java.io.IOException | IllegalArgumentException failure) {
            return new NodeResult("REQUIRED_AGENT_EVIDENCE_MISSING", NodeStatus.FAILED, List.of(),
                    Map.of("failure_code", "AUTHORED_CONTEXT_INVALID", "error", failure.getMessage()), failure.getMessage());
        }
        var collectionInstruction = dev.dual.runtime.handler.EvidenceCollection.promptInstruction(
                context.workspaceRoot(), context.workstreamId(), context.instance().nodeInstanceId());
        if (collectionInstruction.isPresent()) {
            var instruction = collectionInstruction.orElseThrow();
            var sections = new ArrayList<>(rendered.sections());
            sections.add(new AgentPromptRenderer.PromptSection("Evidence collection boundary", "human_collection",
                    context.instance().nodeInstanceId(), instruction, "source-preserving evidence collection", true));
            rendered = new AgentPromptRenderer.RenderedPrompt(rendered.content() + "\n\n" + instruction, List.copyOf(sections));
            agentResultContract += "\n\n" + instruction;
        }
        // Storage failures retain the ordinary adapter IO path, not evidence-violation semantics.
        Files.writeString(promptPath, rendered.content(), StandardCharsets.UTF_8);
        promptManifestWriter.write(
                PromptManifestWriter.manifestPathFor(promptPath), context.workstreamId(), runId,
                context.definition().id(), agent, promptTemplate, promptPath, rendered.sections(), authored);
        try {
            if (authored != null) {
                dev.dual.runtime.handler.AuthoredExecutionContext.verifyManifest(context.workspaceRoot(),
                        PromptManifestWriter.manifestPathFor(promptPath), authored);
            }
        } catch (java.io.IOException | IllegalArgumentException failure) {
            return new NodeResult("REQUIRED_AGENT_EVIDENCE_MISSING", NodeStatus.FAILED, List.of(),
                    Map.of("failure_code", "AUTHORED_CONTEXT_INVALID", "error", failure.getMessage()), failure.getMessage());
        }

        if (prepareOnly) {
            metadataWriter.writePrepared(
                    context.workspaceRoot(),
                    context.workstreamId(),
                    context.definition().id(),
                    agent,
                    session,
                    promptTemplate,
                    runId,
                    runDir,
                    promptPath,
                    outputPath,
                    transcriptPath,
                    stderrPath,
                    sessionMeta,
                    compactionMeta,
                    executionPolicy
            );
            return NodeResult.completed("completed", List.of(
                    context.workspaceRoot().relativize(promptPath).toString(),
                    governanceContext.repositoryFactBinding().ref()), Map.of(
                    "run_id", runId,
                    "run_metadata", context.workspaceRoot().relativize(runDir.resolve("run.json")).toString(),
                    "agent", agent,
                    "session", session,
                    "repository_fact_binding_ref", governanceContext.repositoryFactBinding().ref(),
                    "repository_fact_binding_sha256", governanceContext.repositoryFactBinding().sha256(),
                    "prepared", true
            ));
        }

        metadataWriter.writePrepared(
                context.workspaceRoot(),
                context.workstreamId(),
                context.definition().id(),
                agent,
                session,
                promptTemplate,
                runId,
                runDir,
                promptPath,
                outputPath,
                transcriptPath,
                stderrPath,
                sessionMeta,
                compactionMeta,
                executionPolicy
        );
        var request = new AcpxRunRequest(
                context.workspaceRoot(),
                session,
                promptPath,
                transcriptPath,
                stderrPath,
                effectivePermissionProfile,
                event -> metadataWriter.appendExecutionEvent(runDir.resolve("run.json"), event),
                executionPolicy
        );
        var correctionRequest = new AcpxRunRequest(
                context.workspaceRoot(),
                session,
                correctionPromptPath,
                correctionTranscriptPath,
                correctionStderrPath,
                AcpxPermissionProfile.contractCorrection(),
                event -> metadataWriter.appendExecutionEvent(runDir.resolve("run.json"), event),
                executionPolicy
        );
        var authoritySnapshot = ChangeBoundaryAuthoritySnapshot.capture(
                context.workspaceRoot(),
                context.workstreamId(),
                request.permissionProfile().approveAll()
        );
        try {
            var acquisition = resultAcquirer.acquire(
                    agent,
                    request,
                    correctionRequest,
                    agentResultContract,
                    context.workstreamId(),
                    runId,
                    ContractRegistry.hasContract(context.definition().id())
                            ? ContractRegistry.forNode(context.definition().id())
                            : null
            );
            var finalResult = acquisition.finalResult();
            var runTimeline = new ArrayList<>(acquisition.executionTimeline());
            var initialUsage = usageFor(agent, acquisition.initialResult(), promptPath, stderrPath);
            var correctionUsage = acquisition.correctionAttempted()
                    ? usageFor(agent, acquisition.correctionResult(), correctionPromptPath, correctionStderrPath)
                    : null;
            var usage = acquisition.correctionAttempted()
                    ? AcpxUsage.aggregatePromptOccurrences(initialUsage, correctionUsage)
                    : initialUsage;
            var finalArtifact = acquisition.finalCandidate();
            if (acquisition.correctionAttempted()) {
                Files.writeString(
                        initialOutputPath,
                        acquisition.initialCandidate(),
                        StandardCharsets.UTF_8
                );
                Files.writeString(correctionOutputPath, finalArtifact, StandardCharsets.UTF_8);
            }
            if (finalResult.promptCompletion().completed()) {
                Files.writeString(outputPath, finalArtifact, StandardCharsets.UTF_8);
                appendTimelineEvent(
                        runTimeline,
                        finalResult.startedAt(),
                        AgentExecutionTimelineEventKind.ARTIFACT_EXTRACTED,
                        "adapter"
                );
            }
            var effectiveTranscriptPath = acquisition.correctionAttempted() ? correctionTranscriptPath : transcriptPath;
            var effectiveStderrPath = acquisition.correctionAttempted() ? correctionStderrPath : stderrPath;
            var outputComplete = finalResult.promptCompletion().completed();
            var acquisitionDocument = acquisition.toDocument(
                    context.workspaceRoot(),
                    acquisition.correctionAttempted() ? initialOutputPath : outputPath,
                    correctionOutputPath,
                    initialUsage,
                    correctionUsage
            );
            metadataWriter.writeCompleted(
                    context.workspaceRoot(),
                    context.workstreamId(),
                    context.definition().id(),
                    agent,
                    session,
                    promptTemplate,
                    runId,
                    runDir,
                    promptPath,
                    outputPath,
                    effectiveStderrPath,
                    transcriptPath,
                    finalResult,
                    runTimeline,
                    usage,
                    sessionMeta,
                    compactionMeta,
                    acquisitionDocument,
                    governanceContext.repositoryFactBinding(),
                    governanceContext,
                    executionPolicy
            );
            return authoritySnapshot.enforce(NodeResult.completed(
                    "completed",
                    evidenceOutputRefs(
                            context,
                            outputPath,
                            transcriptPath,
                            stderrPath,
                            governanceContext.repositoryFactBinding().ref()),
                    resultFacts(
                            context,
                            runDir,
                            transcriptPath,
                            finalResult,
                            agent,
                            session,
                            runId,
                            outputComplete,
                            governanceContext.repositoryFactBinding()
                    )
            ));
        } catch (Exception failure) {
            // If the transport persisted evidence and completion writing failed, replay that
            // evidence to a typed terminal disposition before propagating the handler failure.
            try {
                closeInFlightExecutionIfStarted(runDir.resolve("run.json"));
            } catch (Exception reconciliationFailure) {
                failure.addSuppressed(reconciliationFailure);
            }
            var guarded = authoritySnapshot.enforce(new NodeResult(
                    "AGENT_HANDLER_FAILED",
                    NodeStatus.FAILED,
                    List.of(),
                    Map.of(),
                    failure.getMessage()
            ));
            if (Boolean.TRUE.equals(guarded.result().get("change_boundary_authority_tampered"))) {
                return guarded;
            }
            throw failure;
        }
    }

    private void closeInFlightExecutionIfStarted(Path runJson) throws Exception {
        // A run that never started a process is correctly left untouched. If durable closure
        // itself fails, attach that failure to the original exception instead of hiding a
        // second ledger contradiction.
        if (runJson == null || !Files.isRegularFile(runJson)) {
            return;
        }
        attemptReconciler.reconcile(runJson);
    }

    private record ChangeBoundaryAuthoritySnapshot(
            Path workspaceRoot,
            Map<String, String> fingerprints
    ) {
        static ChangeBoundaryAuthoritySnapshot capture(
                Path workspaceRoot,
                String workstreamId,
                boolean protect
        ) throws Exception {
            var root = workspaceRoot.toAbsolutePath().normalize();
            if (!protect) {
                return new ChangeBoundaryAuthoritySnapshot(root, Map.of());
            }
            var workstreamDir = root.resolve(".agent/workstreams").resolve(workstreamId).normalize();
            var protectedPaths = new ArrayList<Path>();
            protectedPaths.add(workstreamDir.resolve("artifacts/task-contract.json"));
            protectedPaths.add(workstreamDir.resolve("workstream.json"));
            var workspacesDir = workstreamDir.resolve("workspaces");
            protectedPaths.add(workspacesDir);
            if (Files.isDirectory(workspacesDir)) {
                try (var files = Files.list(workspacesDir)) {
                    files.filter(Files::isRegularFile)
                            .sorted(Comparator.comparing(Path::toString))
                            .forEach(protectedPaths::add);
                }
            }
            var fingerprints = new LinkedHashMap<String, String>();
            for (var path : protectedPaths) {
                fingerprints.put(root.relativize(path).toString(), fingerprint(path));
            }
            return new ChangeBoundaryAuthoritySnapshot(root, Map.copyOf(fingerprints));
        }

        NodeResult enforce(NodeResult result) {
            if (fingerprints.isEmpty()) {
                return result;
            }
            var changed = fingerprints.entrySet().stream()
                    .filter(entry -> !entry.getValue().equals(currentFingerprint(workspaceRoot.resolve(entry.getKey()))))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            if (changed.isEmpty()) {
                return result;
            }
            var facts = new LinkedHashMap<String, Object>(result.result());
            facts.put("change_boundary_authority_tampered", true);
            facts.put("changed_control_facts", changed);
            return new NodeResult(
                    "CHANGE_BOUNDARY_AUTHORITY_TAMPERED",
                    NodeStatus.FAILED,
                    result.outputRefs(),
                    facts,
                    "implementation agent modified protected change-boundary facts: " + String.join(", ", changed)
            );
        }

        private static String fingerprint(Path path) throws Exception {
            if (Files.isDirectory(path)) {
                var digest = MessageDigest.getInstance("SHA-256");
                try (var files = Files.walk(path)) {
                    for (var file : files.filter(Files::isRegularFile)
                            .sorted(Comparator.comparing(Path::toString))
                            .toList()) {
                        digest.update(path.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                        digest.update(Files.readAllBytes(file));
                    }
                }
                return HexFormat.of().formatHex(digest.digest());
            }
            if (!Files.isRegularFile(path)) {
                return "<missing>";
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        }

        private static String currentFingerprint(Path path) {
            try {
                return fingerprint(path);
            } catch (Exception ignored) {
                return "<unreadable>";
            }
        }
    }

    private AcpxUsage usageFor(String agent, AcpxRunResult result, java.nio.file.Path promptPath, java.nio.file.Path stderrPath) {
        return result.usage() != null
                ? result.usage()
                : usageReader.read(agent, promptPath, stderrPath).orElse(null);
    }

    private static String agentResultContractPrompt(
            TaskContext context,
            String runId,
            AgentResultPromptContract.ProposalEmissionContext emissionContext
    ) {
        if (!ContractRegistry.hasContract(context.definition().id())) {
            return "";
        }
        var contract = ContractRegistry.forNode(context.definition().id());
        var decisionExample = contract.allowedDecisions().size() == 1
                ? contract.allowedDecisions().iterator().next()
                : "<one allowed decision>";
        return AgentResultPromptContract.render(
                context.workstreamId(),
                runId,
                context.definition().id(),
                decisionExample,
                contract.allowedDecisions(),
                emissionContext
        );
    }

    private ProposalEmissionResolution proposalEmissionContext(TaskContext context) {
        if (!"intake.claude_brainstorm".equals(context.definition().id())) {
            return new ProposalEmissionResolution(
                    AgentResultPromptContract.ProposalEmissionContext.notApplicable(), null);
        }
        var resolved = revisionContextResolver.resolveForIteration(
                context.workspaceRoot(), context.workstreamId(), context.instance().iterationId());
        if (!resolved.valid()) {
            return new ProposalEmissionResolution(null, new NodeResult(
                    "TASK_CONTRACT_REVISION_CONTEXT_INVALID",
                    NodeStatus.FAILED,
                    List.of(),
                    Map.of("errors", resolved.errors()),
                    "task contract revision context could not be resolved"));
        }
        return new ProposalEmissionResolution(
                AgentResultPromptContract.proposalEmissionContext(resolved.context()), null);
    }

    private record ProposalEmissionResolution(
            AgentResultPromptContract.ProposalEmissionContext context,
            NodeResult failed
    ) {}

    private static void appendTimelineEvent(
            List<AcpxExecutionEvent> timeline,
            Instant startedAt,
            AgentExecutionTimelineEventKind kind,
            String source
    ) {
        timeline.add(AcpxExecutionEvent.now(kind, source, timeline.size() + 1L, startedAt));
    }

    private static String configText(TaskContext context, String key) {
        var value = context.definition().config().get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("config " + key + " is required for " + context.definition().id());
        }
        return text;
    }

    private static boolean approveAll(TaskContext context) {
        var value = context.definition().config().get("approve_all");
        return Boolean.TRUE.equals(value);
    }

    private static AcpxPermissionProfile permissionProfile(TaskContext context) {
        var value = context.definition().config().get("permission_profile");
        var profile = value instanceof String text ? text.trim() : "";
        // Named profiles are the permission authority. approve_all remains only as
        // compatibility for old definitions that do not declare a profile.
        var resolved = AcpxPermissionProfile.fromName(profile, profile.isBlank() && approveAll(context));
        return hasApprovedPublicWebSearch(context) ? resolved.withAutoApprovedTool("WebSearch") : resolved;
    }

    private static boolean hasNamedPermissionProfile(TaskContext context) {
        var value = context.definition().config().get("permission_profile");
        return value instanceof String text && !text.isBlank();
    }

    private static boolean hasApprovedPublicWebSearch(TaskContext context) {
        var value = context.instance().result().get("permission_approval");
        if (value == null) {
            return false;
        }
        if (!(value instanceof Map<?, ?> approval)
                || !Integer.valueOf(1).equals(approval.get("schema_version"))
                || !"PUBLIC_WEBSEARCH".equals(text(approval.get("type")))
                || !"WebSearch".equals(text(approval.get("tool_name")))
                || !"fetch".equals(text(approval.get("tool_kind")))
                || !context.workstreamId().equals(text(approval.get("workstream_id")))
                || !context.instance().nodeInstanceId().equals(text(approval.get("node_instance_id")))
                || !Boolean.TRUE.equals(approval.get("public_repository_attested"))
                || text(approval.get("source_run_ref")).isBlank()
                || text(approval.get("source_tool_call_id")).isBlank()
                || !text(approval.get("source_query_sha256")).matches("[0-9a-f]{64}")
                || text(approval.get("note")).isBlank()
                || text(approval.get("approved_at")).isBlank()) {
            throw new IllegalArgumentException("invalid public WebSearch permission approval for "
                    + context.instance().nodeInstanceId());
        }
        return true;
    }

    private static AgentPromptRenderer.RenderedPrompt withExternalResearchGuidance(
            AgentPromptRenderer.RenderedPrompt rendered,
            AcpxPermissionProfile profile
    ) {
        var webSearchApproved = profile.autoApprovedTools().contains("WebSearch");
        var guidance = """
                ## External Research Discipline

                - Use repository facts and supplied context first.
                - WebSearch is %s for this occurrence. Use it only for public package, version, or documentation facts.
                - Never use WebFetch or arbitrary URL retrieval. Do not place secrets, source contents, credentials, or private identifiers in a search query.
                - If the needed public fact cannot be obtained under the active permission policy, state the evidence gap instead of bypassing the policy.
                """.formatted(webSearchApproved ? "operator-approved" : "not auto-approved; request human approval if necessary").strip();
        var marker = "\n## Output Contract\n";
        var markerIndex = rendered.content().lastIndexOf(marker);
        if (markerIndex < 0) {
            throw new IllegalStateException("agent prompt is missing the Output Contract boundary");
        }
        var content = rendered.content().substring(0, markerIndex).stripTrailing()
                + "\n\n" + guidance + "\n"
                + rendered.content().substring(markerIndex);
        var sections = new java.util.ArrayList<>(rendered.sections());
        sections.add(new AgentPromptRenderer.PromptSection(
                "external_research_discipline",
                "permission_policy_projection",
                profile.name(),
                guidance,
                "guide provider behavior without replacing adapter enforcement",
                true
        ));
        return new AgentPromptRenderer.RenderedPrompt(content, List.copyOf(sections));
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static List<String> evidenceOutputRefs(
            TaskContext context,
            java.nio.file.Path outputPath,
            java.nio.file.Path transcriptPath,
            java.nio.file.Path stderrPath,
            String repositoryFactBindingRef
    ) {
        var refs = new java.util.ArrayList<String>();
        refs.add(context.workspaceRoot().relativize(stderrPath).toString());
        refs.add(context.workspaceRoot().relativize(transcriptPath).toString());
        if (Files.exists(outputPath)) {
            refs.add(context.workspaceRoot().relativize(outputPath).toString());
        }
        if (repositoryFactBindingRef != null && !repositoryFactBindingRef.isBlank()) {
            refs.add(repositoryFactBindingRef);
        }
        return List.copyOf(refs);
    }

    private static Map<String, Object> resultFacts(
            TaskContext context,
            java.nio.file.Path runDir,
            java.nio.file.Path transcriptPath,
            AcpxRunResult result,
            String agent,
            String session,
            String runId,
            boolean outputComplete,
            dev.dual.runtime.handler.RepositoryFactBinding.Verified repositoryFactBinding
    ) {
        var facts = new LinkedHashMap<String, Object>();
        facts.put("run_id", runId);
        facts.put("run_metadata", context.workspaceRoot().relativize(runDir.resolve("run.json")).toString());
        facts.put("agent", agent);
        facts.put("session", session);
        facts.put("exit_code", result.exitCode());
        facts.put("transcript_ref", context.workspaceRoot().relativize(transcriptPath).toString());
        facts.put("output_complete", outputComplete);
        facts.put("repository_fact_binding_ref", repositoryFactBinding.ref());
        facts.put("repository_fact_binding_sha256", repositoryFactBinding.sha256());
        facts.put("process_usability", result.processResult().usability().name());
        facts.put("adapter_signals", result.adapterSignals().stream()
                .map(AcpxAgentTaskHandler::adapterSignalFact)
                .toList());
        return facts;
    }

    private static Map<String, Object> adapterSignalFact(AdapterSignal signal) {
        var fact = new LinkedHashMap<String, Object>();
        fact.put("type", signal.type().name());
        fact.put("message", signal.message());
        fact.put("observedSequence", signal.observedSequence());
        fact.put("source", signal.source());
        fact.put("sessionId", signal.sessionId());
        fact.put("superseded", signal.superseded());
        return Map.copyOf(fact);
    }

    private static String relative(Path root, Path path) {
        return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }
}
