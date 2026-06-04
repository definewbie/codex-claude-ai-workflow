# dual-orchestrator Roadmap

> Last updated: 2026-06-04

## P0 — 验证（阻塞后续所有工作）

- [x] T0.1 全生命周期 smoke：`start → delivery` 端到端真实跑通。
  - 2026-06-01：真实 acpx smoke 已覆盖 `thin-basic`、`p5-contract-happy-path`、`p6-reject-classify-path`。
  - 2026-06-01：`thin-basic` 在最新分支复跑通过，最终 `COMPLETED`，28 nodes / 11 agent runs / 5 gate rounds。
  - 2026-06-01：`p5-contract-happy-path` 在最新分支复跑通过，最终 `COMPLETED`，30 nodes / 11 agent runs / 6 gate rounds，TaskContract artifact 和 `contract_compilation_completed` event 均通过。
  - 2026-06-01：`p6-reject-classify-path` 在最新分支复跑为 `PASS-WARN`，最终 `COMPLETED`，30 nodes / 11 agent runs / 6 gate rounds；Codex acceptance 为 `ACCEPT`，所以 failure classification 分支未触发。
- [x] T0.2 p6 semantic repair real case：真实复杂仓库中触发 Codex `REJECT` 后能进入分类和修复闭环。
  - 2026-06-02：`cancer-clinic` p6 长 case 跑通，workstream `ws-20260602T121712Z-V7WM`，最终 `COMPLETED`。
  - 这次真实 run 触发了两轮 Codex acceptance `REJECT`，系统路由到 `acceptance.classify_failure`，再回到 Claude repair，第三次 acceptance 通过。
  - 原始仓库保持干净，改动发生在 worktree：`miniprogram/src/pages/treatment-event/index.vue | 4 ++++`。
- [ ] T0.3 deterministic p6 reject fixture：技术债。真实 case 已证明 repair 主线可工作；暂不为了一个边界分支引入 fake acpx / controlled fixture。只有当 CI 需要稳定、可重复覆盖 reject 分支时再实现。
- [ ] T0.4 验证 auto-retry 端到端：Thread.sleep + 指数退避在真实 acpx failure 下验证。

## P0 结论

主生命周期的基本目标已经达到：系统能在真实项目里完成需求澄清、contract、设计/计划 review、人工 gate、Claude 实现、自测、Codex 验收、失败分类、修复循环、final report、delivery artifacts 和下一阶段 handoff。

这不等于产品已经完成。下一阶段不再优先补边角易用性，而是把真实长 run 的可观测、人工 review 和可重复评估做稳。

## P1 — Contract-driven harness 闭环

- [x] CalibrationCandidate MVP：从 FailureArbiter 输出生成 contract template / context rule / regression test 候选。
  - 2026-06-01：`FailureClassifyHandler` 会为可校准分类写入 `calibration/candidates/<id>.json` 和 `.md`。`TOOL_LIMITATION` / `REVIEW_NOISE` 暂不生成候选，避免把一次性运行噪声沉淀为规则。
  - 2026-06-03：`dualctl calibration list/show/decide` 可查看候选并记录人工采纳/拒绝/延后判断；暂不自动提升到模板，避免过早规则引擎化。
  - 2026-06-04：`dualctl calibration plan` 可把候选转成 `calibration/apply-plans/<id>.json` 和 `.md`，沉淀建议目标、证据和人工处理动作；仍不自动修改模板、规则或测试。
- [x] Review packet artifact：为 human gate 生成稳定 review 包，后续 CLI/UI 共用。
  - 2026-06-01：`HumanReviewGateHandler` 会写入 `reviews/<gate_node_instance_id>.md` 和 `.json`，`dualctl review show` 可直接展示当前 gate packet。
  - 2026-06-04：review packet 增加 `operatorGuidance`，包含推荐动作、原因和建议检查项；operator panel 会在结构化卡片里展示。
- [x] Timeline projection：从 workflow current 和 nodes 生成稳定 `timeline.json`，作为 status/watch/UI 读模型。
  - 2026-06-01：`WorkflowFileLedger` 在初始化、路由、运行状态变化、retry/recovery 操作后自动更新 `timeline.json`；`dualctl timeline` 可展示节点概览。
- [x] Workstream summary：从 ledger 聚合真实 run 复盘视图。
  - 2026-06-04：`dualctl summary` 会聚合 timeline、推荐下一步动作、final report、recovery summary、delivery、calibration candidates 和 apply plans；`--write` 可写入 `reports/workstream-summary.md`。
- [x] Status workflow-first：`dualctl status` 默认以 workflow current 为准，旧 checkpoint/stage 快照仅在 `--show-legacy` 时显示。
- [x] Failure summary：失败时生成 `recovery/current-failure.md`，聚合失败原因、证据和建议动作。
  - 2026-06-01：普通 `FAILED` / `BLOCKED` 节点会由 ledger 写入当前失败摘要，包含 node、handler、原因、exit code、output refs 和 retry 提示。
  - 2026-06-01：分类失败路径会写入语义化失败摘要，并把分类 artifact / calibration candidate ref 关联进去。
  - 2026-06-01：`dualctl recovery show` 可直接展示当前失败摘要。
  - 2026-06-04：`dual-server` 增加 `/api/workstreams/{id}/recovery/current`，operator panel 在 stopped/recovery 状态展示当前失败摘要。

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
- [x] Trace CLI MVP：`dualctl trace` 一屏展示 status、当前节点、open gate、acceptance、repair loops、timeline、关键 artifacts 和下一步命令。
- [x] Local API Server MVP：`dual-server` 提供 HTTP API，暴露 workstream list、workflow status / timeline，并支持最小 action：resume、retry、review approve/revise。
- [ ] T3.1 实时进度：SSE 或 file watch 推送 workflow 状态变更。
- [ ] T3.2 Metrics 收集：token 用量、延迟、成功率等指标采集
- [ ] T3.3 跨 workstream 协调：多 workstream 共享状态/依赖
- [x] Scheduler tick MVP：`POST /api/scheduler/tick` 手动触发一次调度，只推进 `RUNNING` workstreams，遇到 human gate / recovery / failure / completed 会跳过。
- [x] T4.1 Background Scheduler MVP：默认关闭；配置 `dual.scheduler.enabled=true` 和 `dual.scheduler.workdirs` 后周期调用 tick，支持间隔、max workstreams、max steps 配置，并用运行中保护避免重入。
- [ ] T4.2 Web UI / Dashboard：可视化 workflow 状态、event 时间线、Agent 输出
- [ ] T4.3 Notification：Slack / 邮件 / Webhook 推送状态变更
- [x] T4.4 Local install script MVP：`scripts/install.sh` 构建并安装 `dualctl` / `dual-server` wrapper 到用户 prefix；Homebrew / Docker / native image 等正式分发后置。

## P5 — 主线后续优先级

下面三件事比继续打磨小按钮、小提示更接近长期目标：

1. **Real-case benchmark loop**：沉淀 3-5 个真实仓库任务，定期跑 p6，记录完成率、rejection repair 次数、耗时、失败类型和人工介入点。
   - 2026-06-03：`run-real-case.sh` 开始生成 `summary.json`，把 final status、gate 轮数、Agent run 次数、revision 次数、acceptance decision、artifact presence 和原始 repo clean 状态沉淀为机器可读数据。
   - 2026-06-03：`cancer-clinic-rejection-prone` 跑到 `COMPLETED` / `PASS-WARN`。本轮未触发 Codex `REJECT`，但暴露并修复 `contract.compile` 错读 Codex review 中引用 artifact 的可信来源 bug；同一 workstream retry 后完成全生命周期。
   - 2026-06-04：`real-case-benchmark-summary.sh` 聚合所有 `summary.json`，生成 `testing/real-case-benchmarks/benchmark-index.md`，让真实 run 从零散报告变成可比较的 benchmark set。
   - 2026-06-04：`cancer-clinic-doc-constraints-smoke` 跑到 `COMPLETED`，验证 acpx reconnect 完整 stdout 修复后真实生命周期仍可完成；前一个 `START_FAILED` 诊断 run 保留在 benchmark index 中作为失败样本。
   - 2026-06-04：benchmark index 增加 `PASS` / `PASS-WARN` / `FAIL` 轻量分级和 reason，帮助快速判断真实 run 健康度。
   - 2026-06-04：`run-real-case.sh` 的单次 `summary.json` 和 `report.md` 也写入同一套 benchmark grade，单次报告和聚合 index 口径一致。
   - 2026-06-04：`run-real-case.sh --update-benchmark-index` 可在真实 case 结束后自动刷新 benchmark index；默认关闭，避免临时调试 run 改动文档。
2. **Operator-grade review UX**：围绕 `review packet`、`timeline.json`、artifact sidecar、failure summary 做人审页面，让人能看清“该不该 approve”，而不是只多几个按钮。
   - 2026-06-04：review packet 和 operator panel 已显示推荐动作、原因和建议检查项；stopped/recovery 状态已显示当前失败摘要。
   - 2026-06-04：CLI 在 `run` / `resume` 停住时统一打印 `Next actions`，覆盖 human gate、recovery、completed 和 max-steps，降低真实操作时翻 ledger 的成本。
3. **Failure / calibration follow-through**：把真实 run 中的 failure classification、calibration candidate、handoff 做成可读、可评估、可提升的闭环。
   - 当前已支持 candidate CLI 审阅和状态决策；下一步才考虑从 accepted candidate 生成模板更新建议。
   - 2026-06-04：`dualctl recovery show` 在原始 failure summary 前增加 `Recovery Assessment`，提示分类、retry readiness、原因和建议命令，帮助真实运行中快速判断能否 retry。

当前 benchmark 入口：

```text
dual-orchestrator/docs/testing/real-case-benchmarks/
```

## P6 — Workflow Engine 增强

- [x] 条件表达式：`EdgeCondition` 接口 + `ConfigEqualsCondition` 实现，edge 上支持条件判断
- [x] 并行节点：fan-out edge routing 激活多个后续节点 + sequential multi-node resume
- [ ] Ledger schema migration：当前框架仍在快速演进，暂不承诺完整历史账本迁移；仅保留最小兼容能力，等 schema 稳定后再设计正式迁移策略。
