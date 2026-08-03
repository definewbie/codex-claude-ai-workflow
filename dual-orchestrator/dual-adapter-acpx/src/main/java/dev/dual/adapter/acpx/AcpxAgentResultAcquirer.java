package dev.dual.adapter.acpx;

import dev.dual.runtime.handler.AgentResultEnvelopeParser;
import dev.dual.runtime.handler.AgentResultEnvelopeValidator;
import dev.dual.runtime.handler.NodeOutputContract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Acquires one strict agent-result candidate, with at most one same-session contract correction. */
final class AcpxAgentResultAcquirer {
    private final AcpxAgentTransport transport;
    private final AgentResultEnvelopeParser parser;
    private final AgentResultEnvelopeValidator validator;

    AcpxAgentResultAcquirer(AcpxAgentTransport transport) {
        this(transport, new AgentResultEnvelopeParser(), new AgentResultEnvelopeValidator());
    }

    AcpxAgentResultAcquirer(
            AcpxAgentTransport transport,
            AgentResultEnvelopeParser parser,
            AgentResultEnvelopeValidator validator
    ) {
        this.transport = transport;
        this.parser = parser;
        this.validator = validator;
    }

    Acquisition acquire(
            String agent,
            AcpxRunRequest initialRequest,
            AcpxRunRequest correctionRequest,
            String correctionPrompt,
            String expectedWorkstreamId,
            String expectedRunId,
            NodeOutputContract contract
    ) throws IOException, InterruptedException {
        var initialResult = executeInitial(agent, initialRequest);
        var initialParse = parser.parse(initialResult.promptCompletion().finalAssistantMessage());
        var initialValidation = validate(initialParse, expectedWorkstreamId, expectedRunId, contract);
        var correctionReason = correctionReason(initialParse, initialValidation);
        var correctionDisposition = correctionDisposition(
                initialResult,
                correctionReason,
                correctionPrompt
        );
        if (correctionDisposition != CorrectionDisposition.ATTEMPTED) {
            return new Acquisition(
                    initialResult,
                    initialParse,
                    initialValidation,
                    null,
                    null,
                    null,
                    initialRequest,
                    null,
                    correctionReason,
                    correctionDisposition
            );
        }

        Files.createDirectories(correctionRequest.promptPath().getParent());
        Files.writeString(
                correctionRequest.promptPath(),
                renderCorrectionPrompt(correctionPrompt, correctionReason, initialParse, initialValidation),
                StandardCharsets.UTF_8
        );
        var correctionResult = transport.runAgentResultCorrection(agent, correctionRequest);
        var correctionParse = parser.parse(correctionResult.promptCompletion().finalAssistantMessage());
        var correctionValidation = validateCorrection(
                correctionReason,
                initialParse,
                initialValidation,
                correctionParse,
                expectedWorkstreamId,
                expectedRunId,
                contract
        );
        return new Acquisition(
                initialResult,
                initialParse,
                initialValidation,
                correctionResult,
                correctionParse,
                correctionValidation,
                initialRequest,
                correctionRequest,
                correctionReason,
                CorrectionDisposition.ATTEMPTED
        );
    }

    private AcpxRunResult executeInitial(String agent, AcpxRunRequest request) throws IOException, InterruptedException {
        return switch (agent) {
            case "claude" -> transport.runClaudeDesign(request);
            case "codex" -> transport.runCodexDesignReview(request);
            default -> throw new IllegalArgumentException("unsupported agent: " + agent);
        };
    }

    private CorrectionDisposition correctionDisposition(
            AcpxRunResult result,
            CorrectionReason correctionReason,
            String correctionPrompt
    ) {
        if (result.exitCode() != 0) {
            return CorrectionDisposition.SKIPPED_PROCESS_NON_ZERO;
        }
        if (!result.promptCompletion().completed()) {
            return CorrectionDisposition.SKIPPED_NOT_COMPLETED;
        }
        if (result.activeAdapterSignals().stream().anyMatch(signal -> signal.type().blocksSuccess())) {
            return CorrectionDisposition.SKIPPED_PROVIDER_FAILURE;
        }
        if (correctionReason == CorrectionReason.NONE) {
            return CorrectionDisposition.NOT_NEEDED;
        }
        if (correctionPrompt == null || correctionPrompt.isBlank()) {
            return CorrectionDisposition.SKIPPED_CORRECTION_UNAVAILABLE;
        }
        return CorrectionDisposition.ATTEMPTED;
    }

    private AgentResultEnvelopeValidator.ValidationResult validate(
            AgentResultEnvelopeParser.ParseResult parsed,
            String expectedWorkstreamId,
            String expectedRunId,
            NodeOutputContract contract
    ) {
        if (!parsed.valid() || contract == null) {
            return null;
        }
        return validator.validate(parsed.envelope(), expectedWorkstreamId, expectedRunId, contract);
    }

    private AgentResultEnvelopeValidator.ValidationResult validateCorrection(
            CorrectionReason reason,
            AgentResultEnvelopeParser.ParseResult initialParse,
            AgentResultEnvelopeValidator.ValidationResult initialValidation,
            AgentResultEnvelopeParser.ParseResult correctionParse,
            String expectedWorkstreamId,
            String expectedRunId,
            NodeOutputContract contract
    ) {
        if (!correctionParse.valid() || contract == null) {
            return null;
        }
        if (reason != CorrectionReason.SCHEMA_FAILURE) {
            return validator.validate(
                    correctionParse.envelope(),
                    expectedWorkstreamId,
                    expectedRunId,
                    contract
            );
        }
        return validator.validateCorrection(
                initialParse.envelope(),
                correctionParse.envelope(),
                expectedWorkstreamId,
                expectedRunId,
                contract,
                initialValidation
        );
    }

    private static CorrectionReason correctionReason(
            AgentResultEnvelopeParser.ParseResult parsed,
            AgentResultEnvelopeValidator.ValidationResult validation
    ) {
        if ("AGENT_RESULT_PARSE_FAILED".equals(parsed.errorCode())) {
            return CorrectionReason.PARSE_FAILURE;
        }
        if (parsed.valid() && validation != null && validation.schemaCorrectionAllowed()) {
            return CorrectionReason.SCHEMA_FAILURE;
        }
        return CorrectionReason.NONE;
    }

    private static String renderCorrectionPrompt(
            String contractPrompt,
            CorrectionReason reason,
            AgentResultEnvelopeParser.ParseResult parsed,
            AgentResultEnvelopeValidator.ValidationResult validation
    ) {
        if (reason == CorrectionReason.PARSE_FAILURE) {
            return """
                    Typed correction reason: %s
                    Syntax diagnostic:
                    %s

                    %s

                    %s
                    """.formatted(
                    reason,
                    renderParseDiagnostic(parsed.diagnostic()),
                    parseCorrectionInstruction(parsed.diagnostic()),
                    contractPrompt
            ).stripTrailing();
        }
        var evidence = reason == CorrectionReason.SCHEMA_FAILURE && validation != null
                ? validation.errors()
                : java.util.List.of(parsed.errorCode());
        return """
                Typed correction reason: %s
                Validation errors: %s

                %s
                """.formatted(reason, evidence, contractPrompt).stripTrailing();
    }

    private static String renderParseDiagnostic(AgentResultEnvelopeParser.ParseDiagnostic diagnostic) {
        if (diagnostic == null) {
            return "- kind: INVALID_JSON";
        }
        var location = diagnostic.line() > 0 && diagnostic.column() > 0
                ? "\n- line: " + diagnostic.line() + "\n- column: " + diagnostic.column()
                : "";
        return "- kind: " + diagnostic.kind() + location;
    }

    private static String parseCorrectionInstruction(AgentResultEnvelopeParser.ParseDiagnostic diagnostic) {
        if (diagnostic != null
                && diagnostic.kind() == AgentResultEnvelopeParser.ParseFailureKind.UNEXPECTED_END_OF_INPUT) {
            return """
                    The previous response ended before the root JSON object was closed.
                    Re-emit the complete AgentResultEnvelope and verify that every object and array is closed before ending the response.
                    """.stripTrailing();
        }
        return "Re-emit the complete AgentResultEnvelope as one strictly valid JSON object.";
    }

    enum CorrectionReason {
        NONE,
        PARSE_FAILURE,
        SCHEMA_FAILURE
    }

    enum CorrectionDisposition {
        NOT_NEEDED,
        ATTEMPTED,
        SKIPPED_PROCESS_NON_ZERO,
        SKIPPED_PROVIDER_FAILURE,
        SKIPPED_NOT_COMPLETED,
        SKIPPED_CORRECTION_UNAVAILABLE
    }

    record Acquisition(
            AcpxRunResult initialResult,
            AgentResultEnvelopeParser.ParseResult initialParse,
            AgentResultEnvelopeValidator.ValidationResult initialValidation,
            AcpxRunResult correctionResult,
            AgentResultEnvelopeParser.ParseResult correctionParse,
            AgentResultEnvelopeValidator.ValidationResult correctionValidation,
            AcpxRunRequest initialRequest,
            AcpxRunRequest correctionRequest,
            CorrectionReason correctionReason,
            CorrectionDisposition correctionDisposition
    ) {
        boolean correctionAttempted() {
            return correctionResult != null;
        }

        AcpxRunResult finalResult() {
            return correctionAttempted() ? correctionResult : initialResult;
        }

        java.util.List<AcpxExecutionEvent> executionTimeline() {
            var timeline = new java.util.ArrayList<>(initialResult.executionTimeline());
            if (correctionAttempted()) {
                timeline.addAll(correctionResult.executionTimeline());
            }
            return java.util.List.copyOf(timeline);
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
                    correctionReason.name(),
                    correctionDisposition,
                    correctionAttempted() ? java.util.List.of(
                            occurrence(workspaceRoot, 1, initialRequest, initialResult, initialParse, initialValidation,
                                    initialOutputPath, initialUsage),
                            occurrence(workspaceRoot, 2, correctionRequest, correctionResult, correctionParse,
                                    correctionValidation,
                                    correctionOutputPath, correctionUsage))
                    : java.util.List.of(
                            occurrence(workspaceRoot, 1, initialRequest, initialResult, initialParse, initialValidation,
                                    initialOutputPath, initialUsage))
            );
        }

        private static AgentResultOccurrenceDocument occurrence(
                java.nio.file.Path workspaceRoot,
                int occurrence,
                AcpxRunRequest request,
                AcpxRunResult result,
                AgentResultEnvelopeParser.ParseResult parsed,
                AgentResultEnvelopeValidator.ValidationResult validation,
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
                    validation != null && validation.valid(),
                    validation == null ? "NOT_EVALUATED" : validation.correctionDisposition().name(),
                    validation == null ? java.util.List.of() : validation.errors(),
                    usage,
                    result.processResult(),
                    result.adapterSignals()
            );
        }

        private static String relative(java.nio.file.Path root, java.nio.file.Path path) {
            return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
        }
    }
}
