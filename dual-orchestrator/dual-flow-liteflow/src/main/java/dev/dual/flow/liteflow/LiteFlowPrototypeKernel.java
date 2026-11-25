package dev.dual.flow.liteflow;

import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.core.FlowExecutorHolder;
import com.yomahub.liteflow.property.LiteflowConfig;
import dev.dual.core.flow.FlowAdvanceResult;
import dev.dual.core.flow.FlowExecutionSnapshot;
import dev.dual.core.flow.FlowOrchestrationKernel;
import dev.dual.core.flow.FlowSignal;
import dev.dual.core.flow.FlowValidationResult;
import dev.dual.core.workflow.WorkflowDefinition;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class LiteFlowPrototypeKernel implements FlowOrchestrationKernel, AutoCloseable {
    private final LiteFlowElCompiler compiler;
    private Path lastRuleFile;

    public LiteFlowPrototypeKernel() {
        this(new LiteFlowElCompiler());
    }

    public LiteFlowPrototypeKernel(LiteFlowElCompiler compiler) {
        if (compiler == null) {
            throw new IllegalArgumentException("LiteFlow compiler is required");
        }
        this.compiler = compiler;
    }

    @Override
    public FlowValidationResult validate(WorkflowDefinition definition) {
        if (definition == null) {
            return FlowValidationResult.invalid(List.of("workflow definition is required"));
        }
        return FlowValidationResult.ok();
    }

    @Override
    public FlowAdvanceResult advance(
            WorkflowDefinition definition,
            FlowExecutionSnapshot snapshot,
            FlowSignal signal
    ) {
        // LiteFlow's holder is JVM-global: per-kernel and per-workstream locks cannot protect it.
        synchronized (FlowExecutorHolder.class) {
            return advanceExclusive(definition, snapshot, signal);
        }
    }

    private FlowAdvanceResult advanceExclusive(
            WorkflowDefinition definition, FlowExecutionSnapshot snapshot, FlowSignal signal
    ) {
        var compiled = compiler.compile(definition);
        var chainName = compiled.chainName(signal.nodeDefinitionId(), signal.outcome());
        if (chainName == null) {
            return new FlowAdvanceResult(List.of(), List.of("liteflow.no_route"));
        }
        var context = new LiteFlowKernelContext(
                definition,
                snapshot,
                signal,
                compiled.candidateEdges(signal.nodeDefinitionId(), signal.outcome())
        );
        try {
            var flowExecutor = loadExecutor(compiled.xml());
            var response = flowExecutor.execute2Resp(chainName, null, context);
            if (!response.isSuccess()) {
                var cause = response.getCause();
                var message = cause == null ? response.getMessage() : cause.getMessage();
                throw new IllegalStateException("LiteFlow prototype advance failed: " + message, cause);
            }
            return context.advanceResult();
        } finally {
            FlowExecutorHolder.clean();
            deleteLastRuleFile();
        }
    }

    @Override
    public void close() {
        synchronized (FlowExecutorHolder.class) {
            FlowExecutorHolder.clean();
            deleteLastRuleFile();
        }
    }

    private FlowExecutor loadExecutor(String xml) {
        try {
            deleteLastRuleFile();
            lastRuleFile = Files.createTempFile("dual-liteflow-", ".xml");
            Files.writeString(lastRuleFile, xml, StandardCharsets.UTF_8);
            var config = new LiteflowConfig();
            config.setRuleSource(lastRuleFile.toString());
            config.setPrintBanner(false);
            config.setEnableLog(false);
            config.setPrintExecutionLog(false);
            FlowExecutorHolder.clean();
            return FlowExecutorHolder.loadInstance(config);
        } catch (Exception e) {
            throw new IllegalStateException("failed to load LiteFlow compiled workflow definition", e);
        }
    }

    private void deleteLastRuleFile() {
        if (lastRuleFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(lastRuleFile);
        } catch (Exception ignored) {
            // Best-effort cleanup for prototype-generated LiteFlow rules.
        } finally {
            lastRuleFile = null;
        }
    }
}
