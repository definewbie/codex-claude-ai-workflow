# dual-orchestrator Future Features

> Last updated: 2026-05-29

## P0 — 验证（阻塞后续所有工作）

- [ ] T0.1 全生命周期 smoke：`start → delivery` 端到端真实跑通（需要 Codex quota）
- [ ] 验证 auto-retry 端到端：Thread.sleep + 指数退避在真实 acpx failure 下验证（依赖 T0.1）

## P1 — 技术债

- [x] T2.3 CheckpointDocument (319行) 单元测试
- [x] T2.3 CodexReviewControl (105行) 单元测试
- [ ] Budget overflow 降级测试：验证 DIFF_STAT / DECISION_ONLY 渲染在 budget 真正溢出时触发
- [ ] Profile override 单元测试：验证 `maxSnippetChars(context, budget)` 返回 profile override > node config > 默认值

## P2 — 完善性

- [ ] T2.2 Prompt regression：用真实 Agent 输出验证 prompt 模板变量一致性
- [ ] Token-level budgeting：context governance 从 char-level 改为 token 计算
- [ ] Adaptive compression：context 超长时自动压缩/摘要

## P3 — 产品化基础设施

- [ ] T3.1 实时进度：SSE 或 file watch 推送 workflow 状态变更
- [ ] T3.2 Metrics 收集：token 用量、延迟、成功率等指标采集
- [ ] T3.3 跨 workstream 协调：多 workstream 共享状态/依赖
- [ ] T4.1 Scheduler / API Server：HTTP API + 定时调度，脱离 CLI 手动推进
- [ ] T4.2 Web UI / Dashboard：可视化 workflow 状态、event 时间线、Agent 输出
- [ ] T4.3 Notification：Slack / 邮件 / Webhook 推送状态变更
- [ ] T4.4 安装脚本：`brew install` / `curl | sh` 等分发包

## P4 — Workflow Engine 增强

- [ ] 条件表达式：edge 上支持条件判断（如 `if config.x == y`）
- [ ] 并行节点：多个 Task 同时执行 + join 等待
- [ ] Schema 版本迁移：ledger 文件结构升级时自动迁移旧数据
