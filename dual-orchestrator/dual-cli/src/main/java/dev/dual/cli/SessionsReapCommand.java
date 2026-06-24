package dev.dual.cli;

import dev.dual.adapter.acpx.AcpxSessionLifecycle;
import dev.dual.adapter.acpx.WorkstreamSessionCloser;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.WorkstreamPaths;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * P0 session lifecycle 止损：扫描 workdir 下所有 workstream，对处于终态
 * （COMPLETED / FAILED）的，best-effort 关闭其残留 acpx session。
 *
 * <p>不做 daemon、不引入调度器；只处理 terminal workstream，不处理
 * RUNNING / WAITING_FOR_HUMAN / WAITING_FOR_RECOVERY。只调
 * {@code acpx sessions close}，不删 {@code ~/.acpx/sessions} 文件。</p>
 *
 * <p>本命令只做 lifecycle cleanup，<b>不</b>解决 token 雪球（留 P1/P2 A/B）。</p>
 */
@Component
@Command(
        name = "sessions-reap",
        description = "Close leftover acpx sessions for terminal (COMPLETED/FAILED) workstreams.",
        mixinStandardHelpOptions = true
)
public final class SessionsReapCommand implements Callable<Integer> {

    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Override
    public Integer call() throws Exception {
        return reap(new AcpxSessionLifecycle());
    }

    int reap(WorkstreamSessionCloser closer) throws Exception {
        var root = workdir.toAbsolutePath().normalize();
        var workstreamsDir = root.resolve(".agent").resolve("workstreams");
        if (!Files.isDirectory(workstreamsDir)) {
            System.out.println("No workstreams found at " + workstreamsDir);
            return 0;
        }
        var ledger = CliLedgerFactory.create();
        int reaped = 0;
        int skipped = 0;
        int statusFailed = 0;
        int closeFailed = 0;
        try (Stream<Path> stream = Files.list(workstreamsDir)) {
            var dirs = stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for (var dir : dirs) {
                var workstreamId = dir.getFileName().toString();
                WorkflowStatus status;
                try {
                    status = ledger.loadCurrent(new WorkstreamPaths(root, new DualId(workstreamId))).status();
                } catch (RuntimeException e) {
                    System.out.println("skip ws=" + workstreamId + " reason=unreadable_status");
                    statusFailed++;
                    continue;
                }
                if (status != WorkflowStatus.COMPLETED && status != WorkflowStatus.FAILED) {
                    System.out.println("skip ws=" + workstreamId + " status=" + status + " (not terminal)");
                    skipped++;
                    continue;
                }
                var summary = closer.closeWorkstreamSessions(root, workstreamId);
                closeFailed += summary.failed();
                System.out.println("reap ws=" + workstreamId + " status=" + status
                        + " distinct=" + summary.distinctSessions()
                        + " closed=" + summary.closed()
                        + " failed=" + summary.failed());
                reaped++;
            }
        }
        System.out.println("reaped=" + reaped + " skipped=" + skipped
                + " statusFailed=" + statusFailed + " closeFailed=" + closeFailed);
        return 0;
    }
}
