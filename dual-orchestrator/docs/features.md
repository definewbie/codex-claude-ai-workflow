# dual-orchestrator Future Features

> Last updated: 2026-06-01

## P0 — 验证（阻塞后续所有工作）

- [x] T0.1 全生命周期 smoke：`start → delivery` 端到端真实跑通。
  - 2026-06-01：真实 acpx smoke 已覆盖 `thin-basic`、`p5-contract-happy-path`、`p6-reject-classify-path`。
  - 2026-06-01：`thin-basic` 在最新分支复跑通过，最终 `COMPLETED`，28 nodes / 11 agent runs / 5 gate rounds。
  - 2026-06-01：`p5-contract-happy-path` 在最新分支复跑通过，最终 `COMPLETED`，30 nodes / 11 agent runs / 6 gate rounds，TaskContract artifact 和 `contract_compilation_completed` event 均通过。
  - 2026-06-01：`p6-reject-classify-path` 在最新分支复跑为 `PASS-WARN`，最终 `COMPLETED`，30 nodes / 11 agent runs / 6 gate rounds；Codex acceptance 为 `ACCEPT`，所以 failure classification 分支未触发。
- [ ] T0.2 deterministic p6 reject fixture：技术债。真实 acpx smoke 中 Codex 不一定 REJECT，这条分支目前按边界条件处理；暂不引入 fake acpx / controlled fixture。等真实 case 或 smoke 暴露稳定需求时再实现。
  - 2026-06-01：真实 acpx smoke 仍保留 `PASS-WARN` 语义。分类路径一旦触发，脚本会检查 calibration candidate 和 `recovery/current-failure.md`。
- [ ] T0.3 验证 auto-retry 端到端：Thread.sleep + 指数退避在真实 acpx failure 下验证。

## P1 — Contract-driven harness 闭环

- [x] CalibrationCandidate MVP：从 FailureArbiter 输出生成 contract template / context rule / regression test 候选。
  - 2026-06-01：`FailureClassifyHandler` 会为可校准分类写入 `calibration/candidates/<id>.json` 和 `.md`。`TOOL_LIMITATION` / `REVIEW_NOISE` 暂不生成候选，避免把一次性运行噪声沉淀为规则。
- [x] Review packet artifact：为 human gate 生成稳定 review 包，后续 CLI/UI 共用。
  - 2026-06-01：`HumanReviewGateHandler` 会写入 `reviews/<gate_node_instance_id>.md` 和 `.json`，`dualctl review show` 可直接展示当前 gate packet。
- [x] Timeline projection：从 workflow current 和 nodes 生成稳定 `timeline.json`，作为 status/watch/UI 读模型。
  - 2026-06-01：`WorkflowFileLedger` 在初始化、路由、运行状态变化、retry/recovery 操作后自动更新 `timeline.json`；`dualctl timeline` 可展示节点概览。
- [x] Status workflow-first：`dualctl status` 默认以 workflow current 为准，旧 checkpoint/stage 快照仅在 `--show-legacy` 时显示。
- [x] Failure summary：失败时生成 `recovery/current-failure.md`，聚合失败原因、证据和建议动作。
  - 2026-06-01：普通 `FAILED` / `BLOCKED` 节点会由 ledger 写入当前失败摘要，包含 node、handler、原因、exit code、output refs 和 retry 提示。
  - 2026-06-01：分类失败路径会写入语义化失败摘要，并把分类 artifact / calibration candidate ref 关联进去。
  - 2026-06-01：`dualctl recovery show` 可直接展示当前失败摘要。

## P2 — 技术债

- [x] T2.3 CheckpointDocument (319行) 单元测试
- [x] T2.3 CodexReviewControl (105行) 单元测试
- [x] Budget overflow 降级测试：验证 DIFF_STAT / DECISION_ONLY 渲染在 budget 真正溢出时触发
- [x] Profile override 单元测试：验证 `maxSnippetChars(context, budget)` 返回 profile override > node config > 默认值

## P3 — 完善性

- [x] T2.2 Prompt regression：用真实 Agent 输出验证 prompt 模板变量一致性
- [x] Token-level budgeting：context governance 从 char-level 改为 token 计算
- [x] Adaptive compression：context 超长时自动压缩/摘要

## P4 — 产品化基础设施

- [x] Watch mode MVP：`dualctl watch` 基于 `timeline.json` 轮询展示 workflow 进度。
- [x] Local API Server MVP：`dual-server` 提供 HTTP API，暴露 workstream list、workflow status / timeline，并支持最小 action：resume、retry、review approve/revise；暂不包含 scheduler 或 UI。
- [ ] T3.1 实时进度：SSE 或 file watch 推送 workflow 状态变更。
- [ ] T3.2 Metrics 收集：token 用量、延迟、成功率等指标采集
- [ ] T3.3 跨 workstream 协调：多 workstream 共享状态/依赖
- [x] Scheduler tick MVP：`POST /api/scheduler/tick` 手动触发一次调度，只推进 `RUNNING` workstreams，遇到 human gate / recovery / failure / completed 会跳过。
- [ ] T4.1 Background Scheduler：后台自动扫描和推进 workflow，支持 workspace roots registry、间隔配置和运行中保护。
- [ ] T4.2 Web UI / Dashboard：可视化 workflow 状态、event 时间线、Agent 输出
- [ ] T4.3 Notification：Slack / 邮件 / Webhook 推送状态变更
- [ ] T4.4 安装脚本：`brew install` / `curl | sh` 等分发包

## P5 — Workflow Engine 增强

- [x] 条件表达式：`EdgeCondition` 接口 + `ConfigEqualsCondition` 实现，edge 上支持条件判断
- [x] 并行节点：fan-out edge routing 激活多个后续节点 + sequential multi-node resume
- [ ] Ledger schema migration：当前框架仍在快速演进，暂不承诺完整历史账本迁移；仅保留最小兼容能力，等 schema 稳定后再设计正式迁移策略。
