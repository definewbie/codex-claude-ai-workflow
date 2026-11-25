package dev.dual.adapter.acpx;

import java.nio.file.Path;

/**
 * 关闭一个 workstream 持有的 acpx provider session（P0 session 生命周期止损）。
 *
 * <p>实现必须：</p>
 * <ul>
 *     <li><b>永不抛异常</b>——session 清理是 best-effort，绝不能影响 workflow 主状态。</li>
 *     <li>只调用 {@code acpx <agent> sessions close}，不删除磁盘上的 session 文件。</li>
 *     <li>幂等：对已关闭的 session 再次关闭应为 no-op。</li>
 * </ul>
 *
 * <p>这层只做 session lifecycle cleanup，<b>不</b>解决 token 雪球（留 P1/P2 A/B）。</p>
 */
public interface WorkstreamSessionCloser {

    CloseSummary closeWorkstreamSessions(Path workspaceRoot, String workstreamId);

    /** 测试/回退用的 no-op 实现，不发起任何 acpx 调用。 */
    static WorkstreamSessionCloser noop() {
        return (root, workstreamId) -> new CloseSummary(workstreamId, 0, 0, 0);
    }

    /**
     * @param workstreamId     被清理的 workstream
     * @param distinctSessions 从 run ledger 读出的不同 (agent, session) 数
     * @param closed           成功 close 的数量
     * @param failed           close 失败（含异常）的数量
     */
    record CloseSummary(String workstreamId, int distinctSessions, int closed, int failed) {
    }
}
