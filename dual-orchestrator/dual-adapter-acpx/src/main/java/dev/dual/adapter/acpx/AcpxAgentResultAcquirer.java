package dev.dual.adapter.acpx;

import dev.dual.runtime.handler.AgentResultEnvelopeParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Acquires one strict agent-result candidate, with at most one same-session contract correction. */
final class AcpxAgentResultAcquirer {
    private final AcpxAgentTransport transport;
    private final AgentResultEnvelopeParser parser;
    private final AcpxFailureClassifier failureClassifier;

    AcpxAgentResultAcquirer(AcpxAgentTransport transport) {
        this(transport, new AgentResultEnvelopeParser(), new AcpxFailureClassifier());
    }

    AcpxAgentResultAcquirer(
            AcpxAgentTransport transport,
            AgentResultEnvelopeParser parser,
            AcpxFailureClassifier failureClassifier
    ) {
        this.transport = transport;
        this.parser = parser;
        this.failureClassifier = failureClassifier;
    }

    Acquisition acquire(
            String agent,
            AcpxRunRequest initialRequest,
            AcpxRunRequest correctionRequest,
            String correctionPrompt
    ) throws IOException, InterruptedException {
        var initialResult = executeInitial(agent, initialRequest);
        var initialParse = parser.parse(initialResult.promptCompletion().finalAssistantMessage());
        if (!mayCorrect(initialRequest, initialResult, initialParse, correctionPrompt)) {
            return new Acquisition(initialResult, initialParse, null, null, initialRequest, null);
        }

        Files.createDirectories(correctionRequest.promptPath().getParent());
        Files.writeString(correctionRequest.promptPath(), correctionPrompt, StandardCharsets.UTF_8);
        var correctionResult = transport.runAgentResultCorrection(agent, correctionRequest);
        var correctionParse = parser.parse(correctionResult.promptCompletion().finalAssistantMessage());
        return new Acquisition(
                initialResult,
                initialParse,
                correctionResult,
                correctionParse,
                initialRequest,
                correctionRequest
        );
    }

    private AcpxRunResult executeInitial(String agent, AcpxRunRequest request) throws IOException, InterruptedException {
        return switch (agent) {
            case "claude" -> transport.runClaudeDesign(request);
            case "codex" -> transport.runCodexDesignReview(request);
            default -> throw new IllegalArgumentException("unsupported agent: " + agent);
        };
    }

    private boolean mayCorrect(
            AcpxRunRequest request,
            AcpxRunResult result,
            AgentResultEnvelopeParser.ParseResult parsed,
            String correctionPrompt
    ) {
        if (correctionPrompt == null || correctionPrompt.isBlank()
                || result.exitCode() != 0
                || !result.promptCompletion().completed()
                || !"AGENT_RESULT_PARSE_FAILED".equals(parsed.errorCode())) {
            return false;
        }
        var failure = failureClassifier.classify(request.stdoutPath(), request.stderrPath(), result.exitCode());
        return !failure.signalPresent();
    }

    record Acquisition(
            AcpxRunResult initialResult,
            AgentResultEnvelopeParser.ParseResult initialParse,
            AcpxRunResult correctionResult,
            AgentResultEnvelopeParser.ParseResult correctionParse,
            AcpxRunRequest initialRequest,
            AcpxRunRequest correctionRequest
    ) {
        boolean correctionAttempted() {
            return correctionResult != null;
        }

        AcpxRunResult finalResult() {
            return correctionAttempted() ? correctionResult : initialResult;
        }

        AcpxRunResult executionResult() {
            if (!correctionAttempted()) {
                return initialResult;
            }
            var timeline = new java.util.ArrayList<>(initialResult.executionTimeline());
            timeline.addAll(correctionResult.executionTimeline());
            return new AcpxRunResult(
                    correctionResult.exitCode(),
                    initialResult.startedAt(),
                    correctionResult.finishedAt(),
                    correctionResult.command(),
                    timeline,
                    correctionResult.usage(),
                    correctionResult.promptCompletion()
            );
        }

        AgentResultEnvelopeParser.ParseResult finalParse() {
            return correctionAttempted() ? correctionParse : initialParse;
        }

        AgentResultAcquisitionDocument toDocument(
                java.nio.file.Path workspaceRoot,
                java.nio.file.Path initialOutputPath,
                java.nio.file.Path correctionOutputPath,
                AcpxUsage initialUsage,
                AcpxUsage correctionUsage
        ) {
            return new AgentResultAcquisitionDocument(
                    1,
                    correctionAttempted(),
                    1,
                    correctionAttempted() ? 2 : 1,
                    correctionAttempted() ? java.util.List.of(
                            occurrence(workspaceRoot, 1, initialRequest, initialResult, initialParse,
                                    initialOutputPath, initialUsage),
                            occurrence(workspaceRoot, 2, correctionRequest, correctionResult, correctionParse,
                                    correctionOutputPath, correctionUsage))
                    : java.util.List.of(
                            occurrence(workspaceRoot, 1, initialRequest, initialResult, initialParse,
                                    initialOutputPath, initialUsage))
            );
        }

        private static AgentResultOccurrenceDocument occurrence(
                java.nio.file.Path workspaceRoot,
                int occurrence,
                AcpxRunRequest request,
                AcpxRunResult result,
                AgentResultEnvelopeParser.ParseResult parsed,
                java.nio.file.Path outputPath,
                AcpxUsage usage
        ) {
            return new AgentResultOccurrenceDocument(
                    occurrence,
                    request.sessionName(),
                    result.promptCompletion().requestId(),
                    relative(workspaceRoot, request.promptPath()),
                    relative(workspaceRoot, outputPath),
                    relative(workspaceRoot, request.stdoutPath()),
                    relative(workspaceRoot, request.stderrPath()),
                    result.startedAt(),
                    result.finishedAt(),
                    result.durationMs(),
                    result.exitCode(),
                    result.promptCompletion().stopReason(),
                    parsed.valid(),
                    parsed.errorCode(),
                    usage
            );
        }

        private static String relative(java.nio.file.Path root, java.nio.file.Path path) {
            return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
        }
    }
}
