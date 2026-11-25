package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * P0 session 生命周期止损：读取一个 workstream 的 run ledger，对其用过的每个
 * (agent, session) 调用 {@code acpx <agent> sessions close}。
 *
 * <p>设计要点：</p>
 * <ul>
 *     <li>session 名直接来自各 {@code runs/<runId>/run.json} 的 {@code agent}+{@code session}
 *     字段——即 adapter 实际开过的 session，不靠反推命名规则。</li>
 *     <li>整个方法 <b>永不抛异常</b>：任何 run 读不出、acpx 不可用、close 失败都计入 failed，
 *     绝不影响 workflow 状态。</li>
 *     <li>只调 {@code acpx sessions close}，不删 {@code ~/.acpx/sessions} 文件。</li>
 *     <li>把清理结果追加写入 {@code session-cleanup.log}，方便追踪。</li>
 * </ul>
 *
 * <p>本类只做 lifecycle cleanup，不解决 token 雪球。</p>
 */
public final class AcpxSessionLifecycle implements WorkstreamSessionCloser {

    private static final Set<String> SUPPORTED_AGENTS = Set.of("claude", "codex");

    private final AcpxAgentTransport transport;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AcpxSessionLifecycle() {
        this(new AcpxAgentTransport());
    }

    public AcpxSessionLifecycle(AcpxAgentTransport transport) {
        this.transport = transport;
    }

    @Override
    public CloseSummary closeWorkstreamSessions(Path workspaceRoot, String workstreamId) {
        var targets = collectSessionTargets(workspaceRoot, workstreamId);
        int closed = 0;
        int failed = 0;
        for (SessionTarget target : targets) {
            try {
                if (transport.closeSession(target.agent, workspaceRoot, target.session)) {
                    closed++;
                } else {
                    failed++;
                }
            } catch (RuntimeException ignored) {
                failed++;
            }
        }
        writeDiagnostic(workspaceRoot, workstreamId, targets, closed, failed);
        return new CloseSummary(workstreamId, targets.size(), closed, failed);
    }

    private List<SessionTarget> collectSessionTargets(Path workspaceRoot, String workstreamId) {
        var runsDir = workspaceRoot.resolve(".agent").resolve("workstreams").resolve(workstreamId).resolve("runs");
        var targets = new LinkedHashSet<SessionTarget>();
        if (!Files.isDirectory(runsDir)) {
            return List.of();
        }
        try (var stream = Files.list(runsDir)) {
            for (var runDir : stream.filter(Files::isDirectory).toList()) {
                var runJson = runDir.resolve("run.json");
                if (!Files.isRegularFile(runJson)) {
                    continue;
                }
                readTarget(runJson).ifPresent(targets::add);
            }
        } catch (IOException ignored) {
            // runs dir unreadable: nothing to close
        }
        return new ArrayList<>(targets);
    }

    private java.util.Optional<SessionTarget> readTarget(Path runJson) {
        try {
            JsonNode node = objectMapper.readTree(runJson.toFile());
            var agent = text(node.get("agent"));
            var session = text(node.get("session"));
            if (SUPPORTED_AGENTS.contains(agent) && !session.isBlank()) {
                return java.util.Optional.of(new SessionTarget(agent, session));
            }
        } catch (IOException ignored) {
            // skip unreadable run.json
        }
        return java.util.Optional.empty();
    }

    private void writeDiagnostic(Path workspaceRoot, String workstreamId,
                                 List<SessionTarget> targets, int closed, int failed) {
        var logPath = workspaceRoot.resolve(".agent").resolve("workstreams")
                .resolve(workstreamId).resolve("session-cleanup.log");
        try {
            Files.createDirectories(logPath.getParent());
            var builder = new StringBuilder()
                    .append("session-cleanup workstream=").append(workstreamId)
                    .append(" distinct=").append(targets.size())
                    .append(" closed=").append(closed)
                    .append(" failed=").append(failed)
                    .append(System.lineSeparator());
            for (var target : targets) {
                builder.append("  ").append(target.agent).append(' ').append(target.session)
                        .append(System.lineSeparator());
            }
            Files.writeString(logPath, builder.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // diagnostic is best-effort
        }
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("").trim();
    }

    record SessionTarget(String agent, String session) {
    }
}
