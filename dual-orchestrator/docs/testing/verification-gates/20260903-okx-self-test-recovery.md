# OKX Self-Test Recovery Gate

日期：2026-09-03  
状态：`READY AFTER CHECKPOINT / PROVIDER NOT RUN`

## Verification Gate

- **Target**：只执行当前 `node-implement-claude_self_test_report-056` 一次，确认它在 implement local-binding 环境中复跑并结构化报告完整产品测试；不修改产品或 adapter。
- **Source of truth**：`workflow/current.json`、唯一新增 `run.json`、self-test contract sidecar、node 056 result、Surefire XML。
- **Trigger precondition**：Dual HEAD 为 `1d72da0` 后代；fresh JAR SHA256=`86beadc7a473d2cdec9fccaad9bffb4231502ba4130b2f2d98a61c72862fa8a8`；reviewed acpx identity=`fd173f04 / 41ee5f00c95dc07fc113fbac171270204ca98c842e9fb6838df900f4109e8d2c / claude_execution_policy.v2`；current=`RUNNING / evt-000056`，唯一 active node 056=`READY`，open gates 为空，run 目录数=21。
- **How precondition is checked before run**：复算上述 identity 与 current/node；确认 target HEAD=`c84807946493a603c789ee40c6a4c615aa846c3b`、tracked binary diff SHA256=`34dfa2f329a6c0520b4035f0951dc32b9cc1e97d07066e4f080818ef124ae67d`、porcelain-v1-z SHA256=`ba17fd5ab225a3233ddd270df1fd5c56ffbf8f0185abe781a5201cdd12ec81a7`、51 个 untracked 文件的排序 path+content manifest SHA256=`0a6957d2e29afd310c91fdea0bbfebf9c9f7b68ab96f024c892c98933c1683f8`；21 个 run basename 排序 manifest SHA256=`9daa8da387caeb6a0704a459c665f7dceb5753a7877b42c8a86e0d3c04fbd714`；operator `mvn test`=`44/0/0/0` 且 testcase manifest SHA256=`ed8e5f3a7a5d2a6413d553f87a605e9cd696057c920ad445936e18bead0f43c1`。
- **PASS**：只新增一个归属 node 056 的 run；command 含 `--timeout 1200` 与 E1 binding；存在 `prompt_completed`；self-test contract 合法且 outcome=`READY`。用于验收的全部 Surefire XML mtime 必须晚于新 run `startedAt`，并从这些 XML 重新聚合 tests>=44、failures/errors/skipped=0，原 44 项 testcase 全部存在；不得以 contract 自由文本或旧 XML 代替。node 056 `COMPLETED` 并只路由到既有 deterministic `implement.self_test_decision`；该后继必须为唯一 active `READY` 节点且没有 run。target HEAD、tracked diff、porcelain 和 untracked path+content identity 必须全部不变。
- **FAIL**：self-test 为 `INSUFFICIENT_EVIDENCE/BLOCKED`；产品测试失败或原 44 项缺失；identity/bootstrap/timeout/AgentResult 失败；模型修改产品文件；出现第二个 provider run。
- **INVALID**：前置 identity、current node、JAR、reviewed executable 或 1200 秒配置未命中；执行了其他节点或第二样本。
- **Stop-loss**：只执行一次 `run --max-steps 1`。任一 FAIL/INVALID 即停止，不 retry、不 revise、不修改 prompt、网络、权限、workflow 或 adapter。
- **Run artifact freshness**：先提交本 Gate 并冻结 checkpoint SHA；新 run id/startedAt 必须晚于该 commit，且 run command 和 policy binding 来自上述 fresh JAR。

## 唯一执行命令

```bash
DUAL_ACPX_EXECUTABLE=/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/.tools/acpx-e1/bin/acpx \
DUAL_ACPX_COMMAND_TIMEOUT_SECONDS=1200 \
java -jar /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar run \
  --workdir /Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260829T122452Z-9BZG \
  --max-steps 1 \
  ws-20260829T122452Z-9BZG
```

本 Gate 只证明 self-test 节点恢复，不证明 acceptance、delivery 或整个 real-case 已完成。
命令因 `--max-steps=1` 达到步数上限而返回 exit code 1 本身不构成 FAIL，最终判定只看 ledger、run artifact 与上述产品测试证据。
