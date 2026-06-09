# 设计演进笔记

这不是当前架构规格，而是记录 dual-orchestrator 背后的设计路径和取舍。后续可以作为文章素材。

## 起点

最初的问题很普通：人夹在 Claude Code 和 Codex 中间，来回复制需求、summary、review 意见、自测结果和 handoff prompt。

第一反应是自动化 copy/paste loop：

```text
Claude 写 -> Codex 审 -> Claude 修 -> Codex 验收
```

这有用，但太小。流程一长，真正的问题不是消息传递，而是 lifecycle control：

- 约定好的任务是什么？
- 哪个 Agent 可以改代码？
- 什么证据能证明 developer 自测完成？
- verifier 到底 reject 了什么？
- quota、context overflow、模型漂移之后从哪里恢复？
- 人怎么 review，而不是读完所有原始日志？

项目从 “dual Agent runner” 变成了 “engineering harness”。

## 第一课：Worktree 和 Ledger 比智能更先重要

Bash prototype 证明了两件无聊但关键的事：

1. 默认在隔离 Git worktree 中执行；
2. 每个 prompt、output、event、diff 和 result 都写入 ledger。

没有这两点，Agent automation 很难被信任。再好的模型也可能改错文件。再好的 review 也可能无法复盘。Ledger 不是日志附属品，而是系统记忆。

## 第二课：Reviewer 和 Developer 要分工

Claude 被当作 developer / architect。Codex 被当作外部 reviewer / verifier。这个分工不是为了强调模型品牌，而是为了明确责任：

- developer 可以改代码；
- verifier 质疑 scope、evidence、architecture 和 user impact；
- human 在 gate 上保留最终责任。

有用的形态不是“两个 Agent 无限辩论”，而是一个有边界的 lifecycle：一个角色构建，另一个角色对照同一份 contract 验证。

## 第三课：自然语言适合人，不适合路由

早期设计尝试从 Markdown 编译 TaskContract。看起来成本低，但真实 run 暴露了问题：Markdown 里有示例、复制的上下文、引用的 review 和旧 summary。Parser 很容易把这些误认为当前 scope。

修复不是“写一个更好的 regex”。真正的修复是把机器事实移到 typed artifacts：

```text
Markdown report 给人读
typed artifact 给 orchestrator 读
```

这个边界改变了项目方向。模型仍然用语言推理，但 harness 基于结构化状态和校验证据来路由。

## 第四课：Human Gate 是产品能力

一开始，human gate 看起来像摩擦。后来它变成了产品形态。

长任务里，人不应该盯每一次 Agent call。但在这些时刻，人需要清晰暂停：

- 需求仍然含糊；
- contract 即将成为 canonical；
- plan 即将变成代码；
- acceptance 有 notes 或 rejection；
- 是否进入下一阶段。

Review packet 成为关键 artifact。它让 UI 和 CLI 问出更好的问题：“现在证据是否足够 approve？”

## 第五课：失败需要词汇表

Verifier rejection 不是一种东西。它可能意味着：

- 代码错了；
- task contract 不完整；
- context 缺了；
- verifier 过度要求；
- 工具失败；
- 证据太弱。

把所有这些都处理成 “Claude 再试一次” 会导致循环。Failure arbiter 引入了词汇表，让 workflow 能路由失败，而不是盲目 retry。

## 第六课：Calibration 要保守

让系统从每个失败里自动学习很诱人，但太早自动学习也危险。

当前做法很保守：

1. classify failure；
2. 只为可复用模式生成 calibration candidate；
3. 让人 accept、reject 或 defer；
4. 生成 apply plan；
5. 只 promote 很窄的项目级原则。

这样知识闭环是真的，但不会把一次 noisy run 变成永久全局行为。

## 第七课：UI 应该跟随 Read Models

真实 run 复杂起来以后，operator panel 很自然地变得重要。但如果 UI 直接解析原始 ledger，它会成为另一个混乱源。

更稳的路径是：

```text
ledger -> timeline / metrics / review packet / recovery summary -> CLI and UI
```

这就是为什么 `dualctl trace` 和 operator panel 现在回答同一个问题：跑到哪、为什么停、下一步做什么。

## 当前形态

当前设计可以概括为：

```text
safe workspace
  + workflow state machine
  + typed artifacts
  + context packages
  + verifier acceptance
  + failure routing
  + human gates
  + ledger read models
```

项目仍是 pre-1.0，但方向已经清楚：不要把模型变小，而是让模型周围的 harness 更可信。

## 文章角度

文章不应该从“我们做了一个双 Agent 工具”开始。那听起来像工具。

更强的角度是：

> AI coding 不只需要更聪明的模型，还需要一个 engineering harness，把模型输出变成可审计、可 review、可恢复的软件工作。

人的故事也重要：目标不是把工程师移出流程，而是移除 copy/paste 式监督，把人的判断保留在正确的 gate 上。
