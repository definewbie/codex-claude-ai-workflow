package dev.dual.adapter.acpx;

import dev.dual.core.contract.TaskContract;
import dev.dual.ledger.file.*;
import dev.dual.runtime.RecordedExecutionRecovery;
import dev.dual.runtime.handler.AgentRunCommitProtocol;
import dev.dual.runtime.handler.TaskHandler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.*;

/** Stops the exact session bound in R01; never guesses a session from mutable telemetry. */
public final class AcpxExecutionRecovery {
    private final AcpxAgentTransport transport;
    public AcpxExecutionRecovery() { this(new AcpxAgentTransport()); }
    AcpxExecutionRecovery(AcpxAgentTransport transport) { this.transport = transport; }

    public List<TaskContract.Reference> stop(WorkflowFileLedger ledger, WorkstreamPaths paths) throws Exception {
        var execution = RecordedExecutionRecovery.active(ledger, paths).orElseThrow(() -> new IOException("no recorded active execution"));
        if (AgentRunCommitProtocol.executorOwnsInvocation(paths, execution))
            throw new IOException("the original invocation still owns execution; stop it before issuing a recovery stop");
        var prepared = AgentRunCommitProtocol.boundPreparation(paths, execution);
        dev.dual.runtime.handler.NativePermissionRequests.close(ledger, paths, execution.nodeExecutionId(), "recorded native recovery stop");
        var json = new AtomicJsonWriter().readTree(prepared.path());
        var run = json.path("binding").path("run");
        var runDir = prepared.path().getParent().getParent();
        var refs = new ArrayList<TaskContract.Reference>();
        var context = ledger.inspect(paths, (scoped, files) -> new TaskHandler.TaskContext(paths.workspaceRoot(), paths.workstreamId().value(),
                scoped.loadDefinition(paths).node(execution.nodeDefinitionId()), scoped.loadCoreNode(paths, execution.nodeInstanceId()), ledger));
        var request = new AcpxRunRequest(paths.workspaceRoot(), run.path("session").asText(), runDir.resolve("unused-stop-prompt"),
                runDir.resolve("recovery-stop.stdout"), runDir.resolve("recovery-stop.stderr"), AcpxPermissionProfile.contractCorrection(), event -> {
                    try {
                        AgentRunCommitProtocol.commandEvent(context, prepared, event.kind().wireName(), event.details());
                        if (event.kind() == dev.dual.core.execution.AgentExecutionTimelineEventKind.COMMAND_RESULT) {
                            var file = prepared.path().getParent().resolve(execution.nodeExecutionId() + "-command-"
                                    + event.details().get("command_id") + "-result.json");
                            refs.add(new TaskContract.Reference(paths.workspaceRoot().relativize(file).toString(),
                                    java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)))));
                        }
                    } catch (IOException failure) { throw new UncheckedIOException(failure); }
                    catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
                });
        if (!transport.stopRecordedSession(run.path("agent").asText(), request))
            throw new IOException("native stop not acknowledged; retain unknown state and recorded observations");
        return List.copyOf(refs);
    }
}
