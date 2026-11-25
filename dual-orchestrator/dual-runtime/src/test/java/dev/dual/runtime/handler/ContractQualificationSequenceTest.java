package dev.dual.runtime.handler;

import dev.dual.core.workflow.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.nio.file.Files;
import dev.dual.ledger.file.*;
import dev.dual.ledger.file.ContractQualificationChecks.*;
import dev.dual.core.workflow.*;
import static org.junit.jupiter.api.Assertions.*;

class ContractQualificationSequenceTest {
    @TempDir Path root;

    @Test void realContractApprovalRecordsActualChecksAndNormalContinuationStillCompletes() throws Exception {
        var flow = new PhasedContractContinuationTest.Flow(root);
        flow.untilPause(); flow.recordReadiness();
        ReviewGateActions.resolve(flow.base.ledger, flow.base.paths, "revise", "Bind the current test environment");
        flow.untilPause();
        var id = flow.base.ledger.loadCurrent(flow.base.paths).activeNodeInstanceIds().getFirst();
        var gate = flow.base.ledger.loadOpenGateForNode(flow.base.paths, id);
        ReviewGateActions.resolve(flow.base.ledger, flow.base.paths, "approve", "Approve the exact scope");
        var decision = flow.base.ledger.loadNode(flow.base.paths, id);
        assertInstanceOf(Map.class, decision.result().get("decision_checks"), "actual approval must carry its checker observations");
        var savedGate = flow.base.ledger.inspect(flow.base.paths, (scoped, files) ->
                files.read(flow.base.paths.gateJson(gate.gateId()), dev.dual.ledger.file.GateDocument.class));
        assertEquals(decision.result().get("decision_checks"), savedGate.result().get("decision_checks"));
        flow.untilPause();
        assertEquals(WorkflowStatus.COMPLETED, flow.base.ledger.loadCurrent(flow.base.paths).status());
        assertEquals(1, java.util.Collections.frequency(flow.calls, "implement.claude_implement"));
    }

    private PhasedContractContinuationTest.Flow approved() throws Exception {
        var flow = new PhasedContractContinuationTest.Flow(root);
        flow.untilPause(); flow.recordReadiness();
        ReviewGateActions.resolve(flow.base.ledger, flow.base.paths, "revise", "Bind current verification inputs");
        flow.untilPause();
        ReviewGateActions.resolve(flow.base.ledger, flow.base.paths, "approve", "Approve exact execution");
        return flow;
    }
    private NodeInstanceDocument active(PhasedContractContinuationTest.Flow flow) throws Exception {
        return flow.base.ledger.loadNode(flow.base.paths, flow.base.ledger.loadCurrent(flow.base.paths).activeNodeInstanceIds().getFirst());
    }
    private RecoveryAssessment recovery(PhasedContractContinuationTest.Flow flow) throws Exception {
        return RecoveryAssessment.from(active(flow), root, flow.base.ws);
    }

    @Test void actualDependencyDriftIsPairedFixedAndRestoredSameInputsCanContinue() throws Exception {
        var flow = approved();
        var original = Files.readAllBytes(root.resolve("test-tool.lock"));
        Files.writeString(root.resolve("test-tool.lock"), "changed actual dependency");
        flow.untilPause();
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, flow.base.ledger.loadCurrent(flow.base.paths).status());
        var failed = active(flow);
        assertEquals(VerificationReadiness.REQUIRED, failed.result().get("failure_code"));
        assertEquals("qualification_input_drift", recovery(flow).classification(), recovery(flow).toString());
        assertFalse(recovery(flow).retrySafe());
        assertEquals(0, java.util.Collections.frequency(flow.calls, "implement.claude_implement"));
        var execution = flow.base.ledger.latestNodeExecution(flow.base.paths, failed).orElseThrow();
        var fixed = AgentRunCommitProtocol.readCompletion(flow.base.paths, execution);
        assertEquals(failed.result().get("continuation_failure"), fixed.result().get("continuation_failure"));
        assertTrue(Files.isRegularFile(root.resolve((String) fixed.result().get("completion_ref"))));
        Files.write(root.resolve("test-tool.lock"), original);
        flow.base.ledger.retryActiveNode(flow.base.paths, "Restored exact dependency bytes");
        flow.untilPause();
        assertEquals(WorkflowStatus.COMPLETED, flow.base.ledger.loadCurrent(flow.base.paths).status());
        assertEquals(1, java.util.Collections.frequency(flow.calls, "implement.claude_implement"));
    }

    @Test void deferredCompletedCompilerConditionIsActuallyRejectedAndItsRemovalAllowsContinuation() throws Exception {
        var flow = approved();
        var contract = TaskContractProjectionVerifier.resolve(flow.base.ledger, root, flow.base.ws).qualified().orElseThrow();
        var original = flow.base.ledger.loadNode(flow.base.paths, contract.compileNodeInstanceId());
        // Test-owned fault: an unapproved completed compiler occurrence appears after approval.
        // The approved check snapshot is produced by the real approval path, not fabricated here.
        var extra = new NodeInstanceDocument(original.schemaVersion(), "node-unapproved-extra", original.nodeDefinitionId(), original.kind(),
                original.handlerType(), original.status(), original.stageId(), original.iterationId(), original.inputRefs(), original.inputTransition(),
                original.outputRefs(), original.result(), original.runRef(), original.latestExecutionId(), original.startedAt(), java.time.Instant.now());
        var extraPath = flow.base.paths.nodeJson(extra.nodeInstanceId());
        try (var write = flow.base.ledger.writeSession(flow.base.paths, "test unapproved compiler", "fault")) {
            write.files().write(extraPath, extra); write.commit();
        }
        flow.untilPause();
        assertEquals("UNAPPROVED_RECOMPILE", active(flow).result().get("failure_code"));
        assertEquals("qualification_consumer_prerequisite", recovery(flow).classification(), recovery(flow).toString());
        assertFalse(recovery(flow).retrySafe());
        Files.delete(extraPath); // remove only the test-injected invalid occurrence; no production recovery claim.
        flow.base.ledger.retryActiveNode(flow.base.paths, "Test fault removed; same approval and inputs");
        flow.untilPause();
        assertEquals(WorkflowStatus.COMPLETED, flow.base.ledger.loadCurrent(flow.base.paths).status());
    }

    @Test void wrongTestCheckerWithIdenticalObservedInputsTriggersContradictionNotNewAuthority() throws Exception {
        var flow = approved();
        flow.testQualification = context -> {
            try {
                var qualified = TaskContractProjectionVerifier.resolve(context.ledger(), root, context.workstreamId()).qualified().orElseThrow();
                var trace = new ContractQualificationTrace(context.ledger(), flow.base.root);
                var paths = flow.base.paths;
                var failed = context.ledger().inspect(paths, (ledger, files) -> {
                    var compiler = ledger.loadNode(paths, qualified.compileNodeInstanceId());
                    var bytes = trace.readBytes(files, flow.base.root.resolve(qualified.contractRef()));
                    var resolution = trace.readBytes(files, flow.base.root.resolve(qualified.resolutionRef()));
                    // Deliberately wrong TEST predicate; measures real inputs through the same collector.
                    // This proves the diagnostic mechanism, not a defect in the production checker.
                    trace.rejects(ContractQualificationTrace.Rule.EXECUTION_HASH, true, ContractQualificationChecks.values(
                            "contractSha", TaskContractArtifactWriter.sha256(bytes), "resolutionSha", TaskContractArtifactWriter.sha256(resolution),
                            "expectedContractSha", compiler.result().get("task_contract_sha256"),
                            "expectedResolutionSha", compiler.result().get("contract_resolution_sha256")),
                            flow.base.root.resolve(qualified.contractRef()), flow.base.root.resolve(qualified.resolutionRef()));
                    return trace.failure(ContractQualificationTrace.Rule.EXECUTION_HASH, "HASH_MISMATCH", "test checker rejects identical inputs");
                });
                return AgentRunGovernanceContext.qualificationFailure(context, failed, "test erroneous checker").result();
            } catch (Exception failure) { throw new RuntimeException(failure); }
        };
        flow.untilPause();
        assertEquals("qualification_contract_contradiction", recovery(flow).classification(), recovery(flow).toString());
        assertEquals("HASH_MISMATCH", active(flow).result().get("failure_code"));
        assertFalse(recovery(flow).retrySafe());
        assertEquals(0, java.util.Collections.frequency(flow.calls, "implement.claude_implement"));
        flow.testQualification = null;
        flow.base.ledger.retryActiveNode(flow.base.paths, "Test checker restored; same approved inputs");
        flow.untilPause();
        assertEquals(WorkflowStatus.COMPLETED, flow.base.ledger.loadCurrent(flow.base.paths).status());
    }

    @Test void directDecisionIsExplicitlyUninvokedAndCannotBorrowTheRuntimeChecks() throws Exception {
        var flow = new PhasedContractContinuationTest.Flow(root);
        flow.untilPause(); flow.recordReadiness();
        ReviewGateActions.resolve(flow.base.ledger, flow.base.paths, "revise", "Bind readiness"); flow.untilPause();
        var id = active(flow).nodeInstanceId();
        flow.base.ledger.resolveOpenGate(flow.base.paths, flow.base.ledger.loadDefinition(flow.base.paths), "approve", "internal unobserved entry");
        var decision = ContractQualificationChecks.decision(flow.base.ledger.loadNode(flow.base.paths, id).result().get("decision_checks"));
        assertEquals(Coverage.NOT_INVOKED, decision.familyCoverage()); assertTrue(decision.checks().isEmpty());
        Files.writeString(flow.base.root.resolve("test-tool.lock"), "changed environment"); flow.untilPause();
        assertEquals("qualification_diagnostic_unknown", recovery(flow).classification());
        assertEquals(VerificationReadiness.REQUIRED, active(flow).result().get("failure_code"));
        assertFalse(recovery(flow).retrySafe());
    }

    @Test void missingPreviouslyReadContractIsObservedDriftAndExactRestorationIsLegal() throws Exception {
        var flow = approved();
        var contract = TaskContractProjectionVerifier.resolve(flow.base.ledger, flow.base.root, flow.base.ws).qualified().orElseThrow();
        var path = flow.base.root.resolve(contract.contractRef()); var bytes = Files.readAllBytes(path); Files.delete(path);
        flow.untilPause();
        assertEquals("ARTIFACT_MISSING", active(flow).result().get("failure_code"));
        assertEquals("qualification_input_drift", recovery(flow).classification(), recovery(flow).toString());
        Files.write(path, bytes); flow.base.ledger.retryActiveNode(flow.base.paths, "Same immutable contract restored"); flow.untilPause();
        assertEquals(WorkflowStatus.COMPLETED, flow.base.ledger.loadCurrent(flow.base.paths).status());
    }

    @Test void normalEventAppendDoesNotEraseEntryIdentityAndChangedSelectedEntryIsDrift() throws Exception {
        var flow = approved();
        var decision = ContractQualificationRecovery.resolvingSource(flow.base.paths, active(flow)).orElseThrow();
        var entries = decision.checks().checks().stream().flatMap(c -> c.inputRefs().stream())
                .filter(r -> r.kind().equals("ROUTE_EVENT")).toList();
        assertFalse(entries.isEmpty());
        assertTrue(decision.checks().checks().stream().flatMap(c -> c.inputRefs().stream())
                .noneMatch(r -> r.kind().equals("FILE") && r.ref().endsWith("events.jsonl")));
        var actualSha = TaskContractArtifactWriter.sha256(flow.base.paths.eventsJsonl());
        flow.base.ledger.appendEvent(flow.base.paths, "test-unrelated-entry", "test_unrelated_append", Map.of("detail", "no qualification change"));
        assertNotEquals(actualSha, TaskContractArtifactWriter.sha256(flow.base.paths.eventsJsonl()));
        assertTrue(TaskContractProjectionVerifier.resolve(flow.base.ledger, flow.base.root, flow.base.ws).qualified().isPresent());
        var original = Files.readAllBytes(flow.base.paths.eventsJsonl()); var lines = Files.readAllLines(flow.base.paths.eventsJsonl());
        var json = new AtomicJsonWriter().objectMapper();
        for (int i = 0; i < lines.size(); i++) {
            var event = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(lines.get(i));
            if (event.path("payload").path("node_instance_id").asText().equals(decision.node().nodeInstanceId())
                    && event.path("event_type").asText().equals("node_routed")) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) event.get("payload")).put("source_node_instance_id", "");
                lines.set(i, json.writeValueAsString(event).replace("\n", ""));
            }
        }
        Files.writeString(flow.base.paths.eventsJsonl(), String.join("\n", lines)+"\n"); flow.untilPause();
        assertEquals("GATE_SOURCE_MISSING", active(flow).result().get("failure_code"));
        assertEquals("qualification_input_drift", recovery(flow).classification(), recovery(flow).toString());
        var originalLines = new String(original, java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        var currentLines = Files.readAllLines(flow.base.paths.eventsJsonl());
        for (int i = 0; i < currentLines.size(); i++) {
            var entry = json.readTree(currentLines.get(i));
            if (entry.path("payload").path("node_instance_id").asText().equals(decision.node().nodeInstanceId())
                    && entry.path("event_type").asText().equals("node_routed")) {
                var eventId = entry.path("event_id").asText();
                currentLines.set(i, originalLines.stream().filter(raw -> {
                    try { return json.readTree(raw).path("event_id").asText().equals(eventId); }
                    catch (java.io.IOException invalid) { throw new RuntimeException(invalid); }
                }).findFirst().orElseThrow());
            }
        }
        Files.writeString(flow.base.paths.eventsJsonl(), String.join("\n", currentLines)+"\n");
        flow.base.ledger.retryActiveNode(flow.base.paths, "Restore only the test-corrupted route entry"); flow.untilPause();
        assertEquals(WorkflowStatus.COMPLETED, flow.base.ledger.loadCurrent(flow.base.paths).status());
    }

    @Test void missingPairWrongTargetSameCodeOtherRuleAndTamperedDecisionCannotBorrowDiagnosis() throws Exception {
        var flow = approved(); Files.writeString(flow.base.root.resolve("test-tool.lock"), "different environment"); flow.untilPause();
        var node = active(flow);
        var pair = ContractQualificationChecks.continuation(node.result().get("continuation_failure"));
        assertEquals("qualification_input_drift", recovery(flow).classification());
        var json = new AtomicJsonWriter().objectMapper();
        for (var field : List.of("targetNodeInstanceId", "decisionSha256", "ruleId", "ruleVersion", "failureCode")) {
            var wrong = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(pair.toMap());
            if (field.equals("ruleId")) ((com.fasterxml.jackson.databind.node.ObjectNode) wrong.get("failedCheck"))
                    .put(field, ContractQualificationTrace.Rule.EXECUTION_IDENTITY.id);
            else if (field.equals("ruleVersion")) ((com.fasterxml.jackson.databind.node.ObjectNode) wrong.get("failedCheck")).put(field, "f".repeat(64));
            else wrong.put(field, field.equals("decisionSha256") ? "f".repeat(64) : "another-target");
            assertEquals("UNKNOWN", ContractQualificationRecovery.classify(node, flow.base.root, flow.base.ws,
                    ContractQualificationChecks.continuation(json.convertValue(wrong, Map.class))), field);
        }
        var without = new java.util.LinkedHashMap<String, Object>(node.result()); without.remove("continuation_failure");
        var missing = new NodeInstanceDocument(node.schemaVersion(), node.nodeInstanceId(), node.nodeDefinitionId(), node.kind(), node.handlerType(),
                node.status(), node.stageId(), node.iterationId(), node.inputRefs(), node.inputTransition(), node.outputRefs(), without,
                node.runRef(), node.latestExecutionId(), node.startedAt(), node.finishedAt());
        assertEquals("qualification_diagnostic_unknown", RecoveryAssessment.from(missing, flow.base.root, flow.base.ws).classification());
        var decisionPath = flow.base.root.resolve(pair.decisionRef()); var original = Files.readAllBytes(decisionPath);
        var tampered = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(original);
        tampered.put("runRef", "tampered"); json.writeValue(decisionPath.toFile(), tampered);
        assertEquals("UNKNOWN", ContractQualificationRecovery.classify(node, flow.base.root, flow.base.ws, pair));
        Files.write(decisionPath, original);
        assertEquals("INPUT_DRIFT", ContractQualificationRecovery.classify(node, flow.base.root, flow.base.ws, pair));
    }

    @Test void missingCoveredSnapshotCannotSilentlyBecomeAnUnobservedApproval() throws Exception {
        var flow = new PhasedContractContinuationTest.Flow(root);
        flow.untilPause(); flow.recordReadiness();
        ReviewGateActions.resolve(flow.base.ledger, flow.base.paths, "revise", "Bind current environment");
        flow.untilPause();
        var before = Files.readAllBytes(flow.base.paths.eventsJsonl());
        var node = active(flow);
        assertThrows(java.io.IOException.class, () -> flow.base.ledger.resolveOpenGate(flow.base.paths,
                flow.base.ledger.loadDefinition(flow.base.paths), "approve", "missing checks",
                (dev.dual.ledger.file.ContractQualificationChecks.Decision) null));
        assertArrayEquals(before, Files.readAllBytes(flow.base.paths.eventsJsonl()));
        assertEquals(node, active(flow));
        ReviewGateActions.resolve(flow.base.ledger, flow.base.paths, "approve", "Actual checks are available");
        flow.untilPause();
        assertEquals(WorkflowStatus.COMPLETED, flow.base.ledger.loadCurrent(flow.base.paths).status());
    }

    @Test void localTaskConsumersCarryTheOriginalCauseWithoutInventingANativeRun() throws Exception {
        var flow = approved();
        var node = active(flow);
        var definition = flow.base.ledger.loadDefinition(flow.base.paths).node(node.nodeDefinitionId());
        var context = new TaskHandler.TaskContext(root, flow.base.ws, definition,
                flow.base.ledger.loadCoreNode(flow.base.paths, node.nodeInstanceId()), flow.base.ledger);
        var contract = TaskContractProjectionVerifier.resolve(flow.base.ledger, root, flow.base.ws).qualified().orElseThrow();
        var path = root.resolve(contract.contractRef()); var bytes = Files.readAllBytes(path); Files.delete(path);
        // Direct sibling invocation proves value propagation, not that these test calls are real native executions.
        for (var handler : List.<TaskHandler>of(new ContextBuildPackageHandler(), new ArtifactValidateHandler(),
                new FailureClassifyHandler(), new ReportGenerateFinalHandler())) {
            var result = handler.execute(context);
            assertEquals(TaskContractProjectionVerifier.FAILURE_OUTCOME, result.outcome(), handler.type());
            assertEquals(NodeStatus.FAILED, result.status());
            assertEquals(TaskContractProjectionVerifier.FAILURE_OUTCOME, result.result().get("failure_code"));
            var pair = ContractQualificationChecks.continuation(result.result().get("continuation_failure"));
            assertEquals(result.result().get("reason"), pair.failureCode());
            assertFalse(result.result().containsKey("input_preparation_failure"));
            assertFalse(result.result().containsKey("completion_ref"));
            assertTrue(result.outputRefs().isEmpty());
            var failed = new NodeInstanceDocument(node.schemaVersion(), node.nodeInstanceId(), node.nodeDefinitionId(), node.kind(),
                    node.handlerType(), NodeStatus.FAILED, node.stageId(), node.iterationId(), node.inputRefs(), node.inputTransition(),
                    result.outputRefs(), result.result(), node.runRef(), node.latestExecutionId(), node.startedAt(), java.time.Instant.now());
            var assessment = RecoveryAssessment.from(failed, root, flow.base.ws);
            assertTrue(assessment.classification().startsWith("qualification_"), handler.type());
            assertEquals(result.result().get("reason"), assessment.contractDiagnostic().get("original_code"));
            assertFalse(assessment.retrySafe());
        }
        Files.write(path, bytes); flow.untilPause();
        assertEquals(WorkflowStatus.COMPLETED, flow.base.ledger.loadCurrent(flow.base.paths).status());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"before_manifest","after_manifest","before_replace","after_replace","before_append","after_append","before_pending_delete","after_pending_delete"})
    void approvalWritePrefixesReplayOnlyFixedChecksThenNormalExecutionCompletes(String point) throws Exception {
        var flow = new PhasedContractContinuationTest.Flow(root);
        flow.untilPause(); flow.recordReadiness();
        ReviewGateActions.resolve(flow.base.ledger, flow.base.paths, "revise", "Bind readiness"); flow.untilPause();
        var id = active(flow).nodeInstanceId();
        var interrupted = dev.dual.ledger.file.InterruptedTestLedger.at(point, flow.base.ledger.frozenRuntimeIdentity());
        assertThrows(java.io.IOException.class, () -> ReviewGateActions.resolve(interrupted, flow.base.paths, "approve", "approve same scope"));
        if (point.equals("before_manifest")) {
            assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, flow.base.ledger.loadCurrent(flow.base.paths).status());
            assertFalse(flow.base.ledger.loadNode(flow.base.paths, id).result().containsKey("decision_checks"));
            ReviewGateActions.resolve(flow.base.ledger, flow.base.paths, "approve", "approve same scope");
        } else cold(flow, false);
        assertEquals(Coverage.COVERED, ContractQualificationChecks.decision(flow.base.ledger.loadNode(flow.base.paths, id).result().get("decision_checks")).familyCoverage());
        var events = Files.readAllBytes(flow.base.paths.eventsJsonl()); cold(flow, false);
        assertArrayEquals(events, Files.readAllBytes(flow.base.paths.eventsJsonl()));
        flow.untilPause(); assertEquals(WorkflowStatus.COMPLETED, flow.base.ledger.loadCurrent(flow.base.paths).status());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"before_manifest","after_manifest","before_replace","after_replace","before_append","after_append","before_pending_delete","after_pending_delete"})
    void fixedPreparationRejectionSurvivesEveryLocalConsumptionPrefixInColdJvm(String point) throws Exception {
        var flow = approved(); var claim = claim(flow);
        Files.writeString(flow.base.root.resolve("test-tool.lock"), "different actual dependency");
        var context = context(flow);
        var rejection = assertThrows(AgentRunGovernanceContext.QualificationException.class,
                () -> AgentRunGovernanceContext.ensureQualified(context, "no-provider")).result();
        var fixed = AgentRunCommitProtocol.complete(flow.base.root, flow.base.ledger, flow.base.paths, claim, rejection);
        var record = Files.readAllBytes(flow.base.root.resolve((String) fixed.result().get("completion_ref")));
        var interrupted = dev.dual.ledger.file.InterruptedTestLedger.at(point, flow.base.ledger.frozenRuntimeIdentity());
        assertThrows(java.io.IOException.class, () -> dev.dual.runtime.RecordedExecutionRecovery.recover(interrupted, flow.base.paths, claim));
        cold(flow, true);
        assertArrayEquals(record, Files.readAllBytes(flow.base.root.resolve((String) fixed.result().get("completion_ref"))));
        assertEquals("qualification_input_drift", recovery(flow).classification());
        assertEquals(0, java.util.Collections.frequency(flow.calls, "implement.claude_implement"));
        var events = Files.readAllBytes(flow.base.paths.eventsJsonl()); cold(flow, true);
        assertArrayEquals(events, Files.readAllBytes(flow.base.paths.eventsJsonl()));
    }

    @Test void rejectionRecordWriteFailureOrDeletedPairCannotClaimCompleteFailure() throws Exception {
        var flow = approved(); var claim = claim(flow);
        Files.writeString(flow.base.root.resolve("test-tool.lock"), "different actual dependency");
        var context = context(flow);
        var rejection = assertThrows(AgentRunGovernanceContext.QualificationException.class,
                () -> AgentRunGovernanceContext.ensureQualified(context, "no-provider")).result();
        var record = flow.base.paths.runsDir().resolve(claim.nodeExecutionId()).resolve("outputs").resolve(claim.nodeExecutionId()+"-input-rejection.json");
        Files.createDirectories(record);
        assertThrows(java.io.IOException.class, () -> AgentRunCommitProtocol.complete(flow.base.root, flow.base.ledger, flow.base.paths, claim, rejection));
        assertEquals(NodeStatus.RUNNING, active(flow).status()); assertFalse(AgentRunCommitProtocol.hasInputRejection(flow.base.paths, claim));
        Files.delete(record);
        AgentRunCommitProtocol.complete(flow.base.root, flow.base.ledger, flow.base.paths, claim, rejection);
        var bytes = Files.readAllBytes(record); var json = new AtomicJsonWriter().objectMapper();
        var deleted = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(bytes);
        ((com.fasterxml.jackson.databind.node.ObjectNode) deleted.path("result").path("result")).remove("continuation_failure");
        json.writeValue(record.toFile(), deleted);
        assertThrows(java.io.IOException.class, () -> dev.dual.runtime.RecordedExecutionRecovery.recover(flow.base.ledger, flow.base.paths, claim));
        assertEquals(NodeStatus.RUNNING, active(flow).status());
        Files.write(record, bytes); cold(flow, true);
        assertEquals("qualification_input_drift", recovery(flow).classification());
    }

    private TaskHandler.TaskContext context(PhasedContractContinuationTest.Flow flow) throws Exception {
        var node = active(flow);
        return new TaskHandler.TaskContext(flow.base.root, flow.base.ws, flow.base.ledger.loadDefinition(flow.base.paths).node(node.nodeDefinitionId()),
                flow.base.ledger.loadCoreNode(flow.base.paths, node.nodeInstanceId()), flow.base.ledger);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void dispatchRecheckAfterR01PreservesDiagnosticButNeverClaimsNoInvocationAfterR02(boolean hasIntent) throws Exception {
        var flow = approved(); claim(flow); var context = context(flow);
        var qualified = TaskContractProjectionVerifier.resolve(flow.base.ledger, flow.base.root, flow.base.ws).qualified().orElseThrow();
        var run = flow.base.paths.runsDir().resolve("test-dispatch-recheck"); Files.createDirectories(run);
        var prompt = run.resolve("prompt.md"); Files.writeString(prompt, "approved input");
        var prepared = AgentRunCommitProtocol.prepare(context, run, List.of(prompt), Map.of(
                "contract_ref", qualified.contractRef(), "contract_sha256", qualified.sha256()));
        AgentRunCommitProtocol.qualifyDispatch(context, prepared); // lawful R01 can proceed.
        if (hasIntent) AgentRunCommitProtocol.beginExternalCall(context, prepared);
        var dependency = flow.base.root.resolve("test-tool.lock"); var original = Files.readAllBytes(dependency);
        Files.writeString(dependency, "changed after actual preparation");
        var rejection = assertThrows(AgentRunGovernanceContext.QualificationException.class,
                () -> AgentRunCommitProtocol.qualifyDispatch(context, prepared)).result();
        assertEquals("AgentRunCommitProtocol.qualifyDispatch", ContractQualificationChecks.continuation(
                rejection.result().get("continuation_failure")).consumer());
        var execution = flow.base.ledger.latestNodeExecution(flow.base.paths, active(flow)).orElseThrow();
        if (hasIntent) {
            assertThrows(java.io.IOException.class, () -> AgentRunCommitProtocol.complete(flow.base.root, flow.base.ledger,
                    flow.base.paths, execution, rejection));
            assertFalse(AgentRunCommitProtocol.hasInputRejection(flow.base.paths, execution));
            AgentRunCommitProtocol.releaseExecutor(flow.base.ledger, flow.base.paths, execution);
            dev.dual.runtime.RecordedExecutionRecovery.recover(flow.base.ledger, flow.base.paths, execution);
            assertEquals("external_result_unknown", recovery(flow).classification());
            assertFalse(recovery(flow).retrySafe());
        } else {
            AgentRunCommitProtocol.complete(flow.base.root, flow.base.ledger, flow.base.paths, execution, rejection);
            assertThrows(java.io.IOException.class, () -> AgentRunCommitProtocol.beginExternalCall(context, prepared),
                    "a fixed failure is not permission for a later live sender");
            cold(flow, true);
            assertEquals("qualification_input_drift", recovery(flow).classification(), recovery(flow).toString());
            Files.write(dependency, original);
            flow.base.ledger.retryActiveNode(flow.base.paths, "Restore same dependency; new qualified attempt");
            flow.untilPause();
            assertEquals(WorkflowStatus.COMPLETED, flow.base.ledger.loadCurrent(flow.base.paths).status());
        }
    }
    private NodeExecutionDocument claim(PhasedContractContinuationTest.Flow flow) throws Exception {
        NodeExecutionDocument claim;
        try (var write = flow.base.ledger.writeSession(flow.base.paths, "test claim", "claim")) {
            var scoped = write.ledger(); var current = scoped.loadCurrent(flow.base.paths);
            var node = scoped.loadNode(flow.base.paths, current.activeNodeInstanceIds().getFirst());
            var workflow = scoped.startWorkflowExecution(flow.base.paths, current, "test");
            var task = scoped.createAgentTask(flow.base.paths, workflow.workflowExecutionId(), scoped.loadDefinition(flow.base.paths).node(node.nodeDefinitionId()), node);
            claim = scoped.startNodeExecution(flow.base.paths, workflow.workflowExecutionId(), node, task, ProcessHandle.current().pid());
            scoped.markAgentTaskRunning(flow.base.paths, task, claim.nodeExecutionId()); scoped.markRunning(flow.base.paths, node.nodeInstanceId(), ProcessHandle.current().pid());
            write.commit();
        }
        return claim;
    }
    private void cold(PhasedContractContinuationTest.Flow flow, boolean recover) throws Exception {
        var log = flow.base.root.resolve("cold-qualification.log");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", System.getProperty("java.class.path"),
                ColdQualificationRecovery.class.getName(), flow.base.root.toString(), flow.base.ws, Boolean.toString(recover))
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, process.exitValue(), Files.readString(log));
    }
    public static final class ColdQualificationRecovery {
        public static void main(String[] args) throws Exception {
            var ledger = new WorkflowFileLedger(new dev.dual.core.flow.BuiltInFlowOrchestrationKernel(), Map.of("test_binary", "readiness-v2"));
            var paths = new WorkstreamPaths(Path.of(args[0]), new dev.dual.core.id.DualId(args[1]));
            try (var finish = ledger.writeSession(paths, "finish fixed local mutation", "cold")) { finish.commit(); }
            if (Boolean.parseBoolean(args[2])) {
                var active = dev.dual.runtime.RecordedExecutionRecovery.active(ledger, paths);
                if (active.isPresent()) dev.dual.runtime.RecordedExecutionRecovery.recover(ledger, paths, active.get());
                else if (ledger.loadCurrent(paths).status() != WorkflowStatus.WAITING_FOR_RECOVERY) throw new AssertionError("fixed failure not consumed");
            }
        }
    }
}
