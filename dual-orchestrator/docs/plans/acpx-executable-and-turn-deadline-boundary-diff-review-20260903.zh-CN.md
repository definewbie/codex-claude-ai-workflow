# acpx Executable 与 Turn Deadline Checkpoint 独立 Diff Review

## Findings

P0：无。P1：无。

- P2-1：Codex sibling 已有显式反例；无 E1 binding 的 Claude 与 build-info 不可用场景主要由代码条件和 mismatch 用例覆盖，直接测试仍可加强。
- P2-2：总 turn deadline 与 progress watchdog 由不同字段和控制流拥有，但缺少 `total=1200 / progress=1` 的同用例组合反证。
- P2-3：deadline classification 使用既有 5 秒容差，是基于 elapsed time 的 adapter 推断，不是 acpx typed timeout；600 秒配置下阈值从约 595 秒开始。它覆盖 captured 604/608 秒样本，但不得描述成精确到秒的 provider fact。
- P2-4：build-info 校验与后续 session/prompt 启动之间仍有本地 executable replacement TOCTOU；当前单 operator、受信安装模型下不阻塞，但不是 executable bytes 的事务绑定。

## 控制流核实

1. E1-bound Claude initial 与 correction 均在 session/provider prompt 前执行相同 binding 和 reviewed identity 校验；错误 identity 的 invocation 只有 `build-info --json`。
2. Codex 与无 E1 binding 的 sibling 跳过该检查，原 session/prompt 路径不变。
3. process usability 顺序为 completion、deadline、exit code；完整 completion 保留既有 exit 语义，无 completion 且到达 deadline 时 exit 0 为 `TIMED_OUT`。
4. core finalizer 在缺失 AgentResult 之前消费 `TIMED_OUT`，最终主因为 `TIMEOUT / process_timed_out`。
5. `DUAL_ACPX_COMMAND_TIMEOUT_SECONDS` 只生成 acpx `--timeout`；progress watchdog 仍由既有 progress timeout 与 `AgentExecutionHealth` 拥有。
6. Doctor 与 runtime 共享 `ReviewedAcpxBuildIdentity`，无第二 build-identity owner。

## 独立验证

- focused：108/0/0/0
- full reactor：1311/0/0/0，`BUILD SUCCESS`
- `git diff --check`：clean
- 未调用 provider，未修改 target 或 OKX workstream

## 判定

- Boundary Review：PASS
- 选择性提交：ALLOWED
- 成熟度：reviewed identity 与 deadline classification `ENFORCED`；OKX 1200 秒恢复 `NOT PROVEN`

提交必须排除未跟踪的 `docs/testing/verification-gates/20260903-okx-s2-recovery-and-terminal-completion.md`。下一步仅为选择性提交、fresh package、当前节点一次 recovery；同类失败再次出现即停止。
