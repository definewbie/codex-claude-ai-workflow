package dev.dual.adapter.acpx;

import dev.dual.runtime.handler.AgentResultEnvelopeParser;
import dev.dual.runtime.handler.AgentResultEnvelopeValidator;
import dev.dual.runtime.handler.NodeOutputContract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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
            String contractPrompt,
            String expectedWorkstreamId,
            String expectedRunId,
            NodeOutputContract contract
    ) throws IOException, InterruptedException {
        var initialResult = executeInitial(agent, initialRequest);
        var initialPresentation = presentationCandidate(
                initialResult.promptCompletion().finalAssistantMessage());
        var initialParse = parser.parse(initialPresentation.candidate());
        var initialValidation = validate(initialParse, expectedWorkstreamId, expectedRunId, contract);
        var correctionReason = correctionReason(initialParse, initialValidation);
        var correctionDisposition = correctionDisposition(
                initialResult,
                correctionReason,
                contractPrompt
        );
        if (correctionDisposition != CorrectionDisposition.ATTEMPTED) {
            return new Acquisition(
                    initialResult,
                    initialPresentation,
                    initialParse,
                    initialValidation,
                    null,
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
                renderCorrectionPrompt(contractPrompt, correctionReason, initialParse, initialValidation),
                StandardCharsets.UTF_8
        );
        var correctionResult = transport.runAgentResultCorrection(agent, correctionRequest);
        var correctionPresentation = presentationCandidate(
                correctionResult.promptCompletion().finalAssistantMessage());
        var correctionParse = parser.parse(correctionPresentation.candidate());
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
                initialPresentation,
                initialParse,
                initialValidation,
                correctionResult,
                correctionPresentation,
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
            String contractPrompt
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
        if (contractPrompt == null || contractPrompt.isBlank()) {
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
        var correctionPolicy = """
                Your preceding final assistant message failed strict AgentResultEnvelope validation.
                Preserve its semantic decision, evidence, and conclusion using only fields declared by the contract.
                Remove every unsupported key. Do not preserve invalid object structure.
                Do not re-run the task, use tools, explain, or use Markdown.
                This is the only contract-correction attempt. Return exactly the JSON object required below.
                """.stripTrailing();
        if (reason == CorrectionReason.PARSE_FAILURE) {
            return """
                    Typed correction reason: %s
                    Syntax diagnostic:
                    %s

                    %s

                    %s

                    %s
                    """.formatted(
                    reason,
                    renderParseDiagnostic(parsed.diagnostic()),
                    parseCorrectionInstruction(parsed.diagnostic()),
                    correctionPolicy,
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

                %s
                """.formatted(reason, evidence, correctionPolicy, contractPrompt).stripTrailing();
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

    enum PresentationDisposition {
        NOT_NEEDED,
        SINGLE_JSON_FENCE_REMOVED,
        SINGLE_JSON_FENCE_OPENING_REMOVED,
        SINGLE_FENCED_JSON_EXTRACTED,
        REJECTED_AMBIGUOUS_PRESENTATION
    }

    record PresentationCandidate(
            String raw,
            String candidate,
            PresentationDisposition disposition,
            String rawSha256,
            String candidateSha256
    ) {
    }

    private static PresentationCandidate presentationCandidate(String rawAssistantMessage) {
        var raw = rawAssistantMessage == null ? "" : rawAssistantMessage;
        var stripped = raw.strip();
        if (!stripped.contains("```")) {
            return presentationCandidate(raw, raw, PresentationDisposition.NOT_NEEDED);
        }

        var candidate = singleJsonFencePayload(stripped);
        if (candidate == null) {
            return presentationCandidate(raw, raw, PresentationDisposition.REJECTED_AMBIGUOUS_PRESENTATION);
        }
        var disposition = candidate.openingOnly()
                ? PresentationDisposition.SINGLE_JSON_FENCE_OPENING_REMOVED
                : candidate.exactPresentation()
                        ? PresentationDisposition.SINGLE_JSON_FENCE_REMOVED
                        : PresentationDisposition.SINGLE_FENCED_JSON_EXTRACTED;
        return presentationCandidate(raw, candidate.payload(), disposition);
    }

    private static JsonFencePayload singleJsonFencePayload(String stripped) {
        var openingOnly = exactJsonFenceOpeningPayload(stripped);
        if (openingOnly != null) {
            return openingOnly;
        }
        var opening = findFenceLine(stripped, "```json", 0);
        if (opening < 0 || findFenceLine(stripped, "```json", opening + 1) >= 0) {
            return null;
        }
        var openingEnd = lineEnd(stripped, opening);
        if (openingEnd < 0) {
            return null;
        }
        var payloadStart = afterLineBreak(stripped, openingEnd);
        var closing = findFenceLine(stripped, "```", payloadStart);
        if (closing < 0 || findFenceLine(stripped, "```", closing + 1) >= 0) {
            return null;
        }
        var payloadEnd = beforeLineBreak(stripped, closing);
        if (payloadEnd < payloadStart) {
            return null;
        }
        var payload = stripped.substring(payloadStart, payloadEnd);
        if (payload.isBlank() || payload.contains("```")) {
            return null;
        }
        var closingEnd = lineEndOrInputEnd(stripped, closing);
        var prefix = stripped.substring(0, opening);
        var suffix = stripped.substring(closingEnd);
        return new JsonFencePayload(payload, prefix.isBlank() && suffix.isBlank());
    }

    private static JsonFencePayload exactJsonFenceOpeningPayload(String stripped) {
        var opening = "```json";
        if (!stripped.startsWith(opening)) {
            return null;
        }
        var openingEnd = lineEnd(stripped, 0);
        if (openingEnd < 0) {
            return null;
        }
        var payload = stripped.substring(afterLineBreak(stripped, openingEnd));
        if (payload.isBlank() || payload.contains("```")) {
            return null;
        }
        return new JsonFencePayload(payload, true, true);
    }

    private static int findFenceLine(String value, String marker, int fromIndex) {
        var index = value.indexOf(marker, fromIndex);
        while (index >= 0) {
            var startsLine = index == 0 || value.charAt(index - 1) == '\n';
            var end = index + marker.length();
            var endsLine = end == value.length() || value.charAt(end) == '\n' || value.charAt(end) == '\r';
            if (startsLine && endsLine) {
                return index;
            }
            index = value.indexOf(marker, index + 1);
        }
        return -1;
    }

    private static int lineEnd(String value, int lineStart) {
        var newline = value.indexOf('\n', lineStart);
        return newline < 0 ? -1 : newline;
    }

    private static int lineEndOrInputEnd(String value, int lineStart) {
        var newline = value.indexOf('\n', lineStart);
        return newline < 0 ? value.length() : newline + 1;
    }

    private static int afterLineBreak(String value, int newline) {
        return newline + 1;
    }

    private static int beforeLineBreak(String value, int lineStart) {
        if (lineStart == 0) {
            return 0;
        }
        var newline = lineStart - 1;
        return newline > 0 && value.charAt(newline - 1) == '\r' ? newline - 1 : newline;
    }

    private record JsonFencePayload(String payload, boolean exactPresentation, boolean openingOnly) {
        private JsonFencePayload(String payload, boolean exactPresentation) {
            this(payload, exactPresentation, false);
        }
    }

    private static PresentationCandidate presentationCandidate(
            String raw,
            String candidate,
            PresentationDisposition disposition
    ) {
        return new PresentationCandidate(raw, candidate, disposition, sha256(raw), sha256(candidate));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    record Acquisition(
            AcpxRunResult initialResult,
            PresentationCandidate initialPresentation,
            AgentResultEnvelopeParser.ParseResult initialParse,
            AgentResultEnvelopeValidator.ValidationResult initialValidation,
            AcpxRunResult correctionResult,
            PresentationCandidate correctionPresentation,
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

        String initialCandidate() {
            return initialPresentation.candidate();
        }

        String finalCandidate() {
            return correctionAttempted() ? correctionPresentation.candidate() : initialPresentation.candidate();
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
                    2,
                    correctionAttempted(),
                    1,
                    correctionAttempted() ? 2 : 1,
                    correctionReason.name(),
                    correctionDisposition,
                    correctionAttempted() ? java.util.List.of(
                            occurrence(workspaceRoot, 1, initialRequest, initialResult, initialPresentation,
                                    initialParse, initialValidation, initialOutputPath, initialUsage),
                            occurrence(workspaceRoot, 2, correctionRequest, correctionResult, correctionPresentation,
                                    correctionParse,
                                    correctionValidation,
                                    correctionOutputPath, correctionUsage))
                    : java.util.List.of(
                            occurrence(workspaceRoot, 1, initialRequest, initialResult, initialPresentation,
                                    initialParse, initialValidation, initialOutputPath, initialUsage))
            );
        }

        private static AgentResultOccurrenceDocument occurrence(
                java.nio.file.Path workspaceRoot,
                int occurrence,
                AcpxRunRequest request,
                AcpxRunResult result,
                PresentationCandidate presentation,
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
                    presentation.disposition().name(),
                    presentation.rawSha256(),
                    presentation.candidateSha256(),
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
