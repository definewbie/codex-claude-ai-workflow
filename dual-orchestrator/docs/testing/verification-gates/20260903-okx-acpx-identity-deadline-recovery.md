# OKX acpx Identity 与 Turn Deadline Recovery Gate

日期：2026-09-03  
状态：`PASS`

## Verification Gate

- **Target**：只恢复 `node-implement-claude_implement-049` 一次，验证 reviewed acpx 在 provider 前被强制确认、总 turn deadline 实际为 1200 秒，并取得合法 AgentResultEnvelope 继续 workflow。
- **Source of truth**：`workflow/current.json`、新 `run.json` 的 command/processResult/executionOutcome、prompt completion、AgentResult contract，以及 reviewed acpx `build-info --json`。
- **Trigger precondition**：Dual HEAD=`3bb87f3583ceac312c24eb5a7d2a62767e08e8df`；fresh JAR SHA256=`86beadc7a473d2cdec9fccaad9bffb4231502ba4130b2f2d98a61c72862fa8a8`；Doctor `Result: OK`，唯一允许 warning 为既有 `git evidence - not generated yet`；reviewed acpx identity=`fd173f04 / 41ee5f00c95dc07fc113fbac171270204ca98c842e9fb6838df900f4109e8d2c / claude_execution_policy.v2`；current=`WAITING_FOR_RECOVERY / evt-000054`，唯一 active node=`node-implement-claude_implement-049`。
- **Target source identity**：target HEAD=`c84807946493a603c789ee40c6a4c615aa846c3b`；tracked binary diff SHA256=`34dfa2f329a6c0520b4035f0951dc32b9cc1e97d07066e4f080818ef124ae67d`；porcelain-v1-z SHA256=`ba17fd5ab225a3233ddd270df1fd5c56ffbf8f0185abe781a5201cdd12ec81a7`；51 个 untracked 文件的排序 path+content manifest SHA256=`0a6957d2e29afd310c91fdea0bbfebf9c9f7b68ab96f024c892c98933c1683f8`。这些值只冻结 recovery 启动前的候选；Agent 合法修改后的差异由新 run 和运行后测试判定。
- **Run/test baseline**：运行前共有 20 个 run 目录，排序路径 manifest SHA256=`df51bf55445b02440434af57d060230993615b69c7167b685f639d0fbcc51790`；`mvn test`=`44/0/0/0`；排序后的 `classname#testname` SHA256=`ed8e5f3a7a5d2a6413d553f87a605e9cd696057c920ad445936e18bead0f43c1`。
- **How precondition is checked before run**：运行前重新复算 current/node、source identity、run baseline、JAR SHA、reviewed build-info、Doctor 和 Maven baseline。先且只执行一次 `retry`；随后必须机械确认 current=`RUNNING / evt-000055`、open gates 为空、同一 active node=`READY`，且 `evt-000055` 是该 node 的 `node_retry_requested`。只有该核验通过，才执行一次 `run --max-steps 1`，并显式设置 `DUAL_ACPX_EXECUTABLE` 和 `DUAL_ACPX_COMMAND_TIMEOUT_SECONDS=1200`。
- **PASS**：运行后 run 目录总数恰为 21，且唯一新增 run 归属 `node-implement-claude_implement-049`，无其他节点新 run；新 run command 含精确 `--timeout 1200` 和 E1 policy binding；不存在 identity bootstrap failure；存在 `prompt_completed` 与合法 AgentResultEnvelope；typed execution outcome 不以 `AGENT_RESULT_MISSING` 结束；记录运行后的 current status、active node 与 last event。随后 operator 原样执行 `mvn test`，tests>=44、failures/errors/skipped 全为 0，且原 44 项 `classname#testname` 均仍存在。无论完成时间小于旧 600 秒阈值，还是介于 600 至 1200 秒，只要完整完成均可 PASS；证明对象是 1200 配置已生效与结果边界正确，不要求故意耗时。
- **FAIL**：reviewed identity/runtime bootstrap 失败；无 completion 且触达 1200 秒，此时必须机械表现为 `processResult.usability=TIMED_OUT`、execution outcome=`TIMEOUT`、primary reason=`process_timed_out`；再次出现 exit 0 + no completion + `AGENT_RESULT_MISSING` 主因；运行后产品测试退化；或 Agent 返回合法的产品阻塞。
- **INVALID**：JAR/HEAD/SHA、active node、reviewed executable 或 1200 秒配置任一未命中；执行了不同节点、第二样本或旧 JAR。
- **Stop-loss**：只允许一次 `node_retry_requested` 和一个当前节点新 run。同类 identity/timeout/output-incomplete 再现即停止，不再 retry，不改 prompt，不新增网络、权限或 recovery 机制。
- **Run artifact freshness**：新 run id/startedAt 晚于本 Gate；JAR 时间为 `2026-09-03T20:02:10+0800`；run command 与 E1 binding 来自本轮 fresh JAR。

## 唯一执行序列

```bash
java -jar /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar retry \
  --workdir /Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260829T122452Z-9BZG \
  ws-20260829T122452Z-9BZG

# 先核验 evt-000055、同一 active node READY、open gate 为空，再执行：
DUAL_ACPX_EXECUTABLE=/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/.tools/acpx-e1/bin/acpx \
DUAL_ACPX_COMMAND_TIMEOUT_SECONDS=1200 \
java -jar /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar run \
  --workdir /Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260829T122452Z-9BZG \
  --max-steps 1 \
  ws-20260829T122452Z-9BZG
```

本 Gate 不创建新样本、不修改产品范围、不运行 capability probe，也不声称一次 recovery 能证明全部复杂 real-case 能力。
旧 Gate `20260903-okx-s2-recovery-and-terminal-completion.md` 已被本 Gate 取代，仅保留为历史证据，不提供当前执行授权。

## 执行结果

- 唯一 recovery 按约束执行：`evt-000055=node_retry_requested`，随后只运行一次
  `run --max-steps 1`；run 目录由 20 增至 21，唯一新增 run 为
  `run-1788437649906`，归属 `node-implement-claude_implement-049`。
- 新 run command 含精确 `--timeout 1200`，并绑定
  `claude_execution_policy.v2`；reviewed acpx 身份预检通过，未出现
  `SESSION_BOOTSTRAP_FAILED` 或 `NETWORK_AUTHORITY_NOT_CONFIGURED`。
- 首次 provider 输出在 `790110ms` 后正常 exit 0，并记录
  `prompt_completed`；其 JSON 解析失败后，同一 run 的既有 correction 在
  `25794ms` 内完成。最终 `AgentResultEnvelope` 合法，decision=`IMPLEMENTED`，
  execution outcome=`SUCCESS/completed`，不存在 `AGENT_RESULT_MISSING`。
- 节点 049 已 `COMPLETED`，账本以 `evt-000056` 路由到
  `node-implement-claude_self_test_report-056`。`--max-steps 1` 在该节点执行前停止；
  当前 workflow 仍为 `RUNNING`，没有第二个 provider run。
- 运行后 target HEAD、tracked binary diff 与 porcelain-v1-z identity 均与启动前一致。
  operator 原样复跑 `mvn test`：`44/0/0/0`；排序后的 44 个
  `classname#testname` SHA256 仍为
  `ed8e5f3a7a5d2a6413d553f87a605e9cd696057c920ad445936e18bead0f43c1`，
  原测试集零缺失。

结论：`PASS`。本轮证明 reviewed executable 的启动前身份约束和显式 1200 秒 turn
deadline 已作用于真实 provider occurrence，并消除了旧 600 秒提前终止；它不证明后续
self-test、acceptance 或整个 OKX workflow 已完成。
