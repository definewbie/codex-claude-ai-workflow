# 白皮书对应说明

> Status: current narrative doc
> Last updated: 2026-06-04

这份文档说明 `dual-orchestrator` 如何对应“AI 时代研发效能”这条主线。

一句话版本：这个项目不是为了证明某个编码 Agent 能多写几行代码，而是想给 AI 辅助研发补上一层本地、可审计、可恢复的生命周期编排，让一个真实软件任务可以穿过需求澄清、设计审查、实现、自测、独立验收、修复、交付和 handoff，而不是靠人在 Claude / Codex / 终端之间反复复制粘贴。

## 背景

日常使用 AI 写代码时，真正贵的地方往往不是某一次改代码，而是过程断裂：

- Claude Code 做了设计或实现，但结果需要复制给 Codex / ChatGPT 审视。
- Codex 给了 review 意见，人再复制回 Claude Code。
- 长任务过程中会遇到 context window、rate limit、reconnect、会话重开。
- 自测、diff、日志、验收结论散落在不同终端和聊天窗口里。
- 最后很难回答一个朴素问题：这个任务到底为什么算完成了？

白皮书里的核心判断是：AI 时代的研发效能，不只来自模型能力，也来自流程层的杠杆。`dual-orchestrator` 是这个判断的一份开源实现雏形。

## 核心对应关系

| 白皮书观点 | dual-orchestrator 中的实现 |
| --- | --- |
| AI 应参与完整交付生命周期，而不是只负责生成代码。 | lifecycle workflow 覆盖 intake、design、plan、implementation、self-test、acceptance、repair、delivery、handoff。 |
| 不同 Agent 应承担不同角色。 | Claude 是 developer / architect；Codex 是 reviewer / verifier / user proxy。 |
| 人应该在关键卡点介入，而不是全程盯着终端。 | Human gate 会生成 review packet，并暂停等待 approve / revise / complete。 |
| 长任务需要可持久化的过程记忆。 | `.agent/workstreams/<id>/` 文件账本保存 events、runs、prompts、outputs、artifacts、reports、timeline。 |
| AI 输出需要证据链，而不是只看一段总结。 | Git evidence、自测报告、Codex acceptance、contract sidecar、final report、benchmark summary 都会落盘。 |
| 安全执行环境是默认要求。 | 默认 Git worktree 隔离，原始仓库保持干净。 |
| Agent 工具应该可替换。 | `acpx` 是统一通信边界，未来可以接 Claude Code、Codex、Cursor 或其他 agent runtime。 |
| 系统应该从失败里沉淀改进，但不能过早规则化。 | Failure classification 和 calibration candidate 会记录可提升点，但不会自动修改模板或规则。 |

## 当前 MVP 证据

Java 版 MVP 已经在真实项目里跑通过主生命周期：

- Case：`cancer-clinic-feature-slice`
- Variant：`p6`
- 结果：`COMPLETED`
- Codex acceptance 路径：`REJECT -> failure classification -> Claude repair -> ACCEPT_WITH_NOTES`
- 安全结果：原始仓库保持干净，改动发生在自动生成的 worktree 中。
- 产物：final report、delivery package、handoff JSON、next-stage prompt、timeline、run metadata、benchmark summary。

这说明 MVP 主生命周期已经功能性闭环。它还不是生产级产品。

## 产品形态

当前有三个操作入口：

- `dualctl`：主 CLI，用于 start、run、review、recovery、summary、trace、delivery。
- `dual-server`：本地 HTTP API，为 dashboard 和外部自动化提供入口。
- Operator panel：很薄的浏览器页面，用来读 status、timeline、review packet、contract、failure，并触发少量动作。

长任务追踪的低门槛入口是：

```bash
dualctl trace --workdir <effective_workdir> <workstream_id>
```

它会在一屏里展示当前状态、当前节点、open gate、Codex final acceptance、repair loop、最近 timeline、关键 artifacts 和下一步命令。

## 当前差距

项目仍然是 MVP，主要缺口是：

- UI 还是 operator panel，不是完整 dashboard。
- 实时进度仍是轮询式，尚未做 SSE / WebSocket。
- 一些 runtime failure 仍需要人工判断，自动恢复还不完整。
- 真实 case benchmark 数量还少，不能宣称广泛可靠。
- 分发方式还是本地 install script，Homebrew / Docker / native image 还没做。
- 权限、角色、多用户安全还不是生产级。

## 后续方向

后续工作应该继续贴近白皮书主线：

1. 让真实长任务更容易观察、恢复和复盘。
2. 让 review packet 和 artifact 变成更清楚的 operator experience。
3. 扩充 real-case benchmark，让项目有可比较的运行证据。
4. calibration 继续保持人审，不要过早把一次失败沉淀成硬规则。

暂时不应该把主要精力花在边界 fixture、装饰性 UI 或泛集成上。只有当这些工作能直接提高完整生命周期的可运行性、可追踪性或可信度时，才值得进入主线。
