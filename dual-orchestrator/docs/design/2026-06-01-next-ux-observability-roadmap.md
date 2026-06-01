# 下一阶段功能规划：让完整生命周期更容易被人接住

日期：2026-06-01  
目标：在主流程已经能真实跑起来之后，把下一步重点从“继续堆内部能力”转向“让人能舒服地观察、review、接管和恢复”。

## 当前判断

目前 orchestrator 的主功能已经从 demo 进入可验证状态：

- 可以启动 workstream。
- 可以创建隔离 worktree。
- 可以调用真实 acpx Claude / Codex。
- 可以经过 intake、design、plan、implementation、self-test、acceptance、delivery prepare、next-stage handoff。
- 可以通过 human gate 卡点。
- 可以记录 run ledger、events、node state、agent runs、reports。
- P5 TaskContract 和 P6 failure classify 已经接进 workflow variant。

现在最明显的问题不是“缺一个新 handler”，而是使用体验：

- 人在 review gate 前不知道该看什么。
- 流程跑到哪里，需要翻 `events.jsonl` 或 `status`。
- 失败原因虽然在 ledger 里，但定位成本高。
- Agent 输出很多，缺少一个聚合视图。
- 真实 E2E 的结果需要人工整理。

所以接下来的功能优先级应该围绕“可观察、可 review、可恢复”展开。

## 从之前 TODO 中重新排序

### P0：先把 E2E 证据继续稳定下来

这部分仍然是基础，不稳定的 UI 只是漂亮壳。

1. **deterministic p6 reject fixture**
   - 目标：稳定触发 `acceptance.classify_failure`，验证 failure classification 分支。
   - 当前 p6 smoke 能完整跑通，但分类分支是否触发取决于 Codex 是否 REJECT。
   - 建议做法：新增一个 fixture case，让需求或检查标准明确包含一个容易被故意遗漏的验收点，或者支持一个 controlled fake reviewer mode。短期优先真实 acpx，但 classify 分支需要确定性样本。

2. **status 字段校准**
   - 当前 `dualctl status` 会显示一些旧 checkpoint/stage 信息，比如 `Stage: design (pending)`，即使 workflow 已经跑到后面。
   - 这会影响 UI 和人工 review 的可信度。
   - 需要整理 status 的数据来源，以 active node / workflow current 为准。

### P1：人工 review gate 体验

这是最值得马上开始的功能，因为它直接对应真实使用场景。

#### 1. `dualctl review show`

先不用做 Web UI，先做一个 CLI 聚合视图，作为后续 UI API 的雏形。

命令形态：

```bash
dualctl review show --workdir <workspace> <workstream_id>
```

输出内容：

- 当前 gate 名称
- gate 允许动作
- 本轮用户需求摘要
- 当前阶段 Agent 输出摘要
- Codex Must Fix / Should Fix / Decision
- 相关 artifact 路径
- 建议 review 命令

这一步的价值是把“人应该看什么”固定下来。

#### 2. `dualctl review approve/revise/block` 的提示增强

现在命令能工作，但人不容易判断该输入什么 note。

可以增强：

- approve 前显示将进入哪个节点。
- revise 时提示会回到哪个 agent / 哪个阶段。
- block 时提示如何恢复。
- 每次操作后输出 next command。

#### 3. Review packet artifact

每个 human gate 生成一个稳定文件：

```text
.agent/workstreams/<id>/reviews/<gate_id>.md
```

内容就是这次 review 需要看的最小包：

- Decision
- Must Fix
- Summary
- Changed files
- Verification/self-test
- Links to full artifacts

这个文件既能给 CLI 展示，也能给 UI 读取，还能作为通知内容。

### P1：实时进度和失败原因

#### 1. Event projection

现在有 `events.jsonl`，但它是原始事件。需要一个 projection，把它变成人能读的 timeline。

产物：

```text
.agent/workstreams/<id>/timeline.json
```

内容：

- node 顺序
- 每个节点状态
- actor
- started_at / finished_at / duration
- decision / outcome
- error summary
- artifact refs

这不是替代 ledger，而是 ledger 的读模型。

#### 2. Failure summary

失败时生成：

```text
.agent/workstreams/<id>/recovery/current-failure.md
```

内容：

- 失败节点
- 失败类型
- agent exit code
- stderr tail
- parser error
- 建议动作：retry / revise / abort / wait
- 相关文件路径

这解决“失败原因在 ledger 里但人找不到”的问题。

#### 3. Watch mode

命令形态：

```bash
dualctl watch --workdir <workspace> <workstream_id>
```

短期可以轮询 `workflow/current.json` 和 `events.jsonl`，不用一开始就做 SSE。

显示：

- 当前节点
- 最近事件
- 当前 agent run
- 当前 gate / recovery 状态
- report 路径

这会显著降低“人要盯着 console”的压力。

### P2：轻量本地 UI

UI 可以开始设计，但不建议第一步就做复杂前端。先做 local read-only dashboard。

建议形态：

- 一个 `dual-server` 模块，提供本地 HTTP API。
- 一个极简 Web UI，读取 workstream ledger。
- 初版只做 read-only + human gate action。

页面优先级：

1. Workstream list
2. Workstream detail timeline
3. Current gate review page
4. Failure/recovery page
5. Artifact viewer

第一版 UI 不需要：

- 多用户权限
- 远程部署
- WebSocket
- 专用通知集成
- PR 操作
- 复杂搜索

## 推荐下一步实施顺序

### Step 1：Review packet + review show

原因：这是 UI 的最小内核，也是当前人工 gate 最大痛点。

产出：

- `reviews/<gate_id>.md`
- `dualctl review show`
- 单测覆盖 gate packet 生成

### Step 2：Timeline projection

原因：UI / watch / status 都需要这个读模型。

产出：

- `timeline.json`
- `dualctl timeline` 或增强 `dualctl status`
- 每个 node 的 duration、outcome、artifact refs

### Step 3：Failure summary

原因：真实运行中失败一定会发生，恢复体验要比成功路径更重要。

产出：

- `recovery/current-failure.md`
- `dualctl recovery show`
- stderr / parser / acpx failure 分类聚合

### Step 4：Watch mode

原因：不需要 Web UI 就能先解决“看进度”。

产出：

- `dualctl watch`
- 轮询式终端 dashboard

### Step 5：Local dashboard MVP

原因：前面几个功能会自然沉淀出 API 和读模型，UI 到这个时候成本最低。

产出：

- `dual-server`
- read-only workstream dashboard
- gate approval/revision form

## 为什么不马上做完整 Web UI

UI 是需要的，但不能让 UI 先于读模型。

如果现在直接做页面，页面会被迫直接读各种 ledger 原始文件：`events.jsonl`、`nodes/*.json`、`runs/*/run.json`、outputs、reports。这样 UI 会和 ledger 内部结构强耦合，后面一改 ledger，UI 就碎。

更稳的路径是：

1. 先把 review packet、timeline、failure summary 做成稳定读模型。
2. CLI 先消费这些读模型。
3. UI 再消费同一套读模型。

这样 UI 不是另起一套，而是自然长出来。

## 这和完整生命周期目标的关系

完整生命周期不只是“机器自己跑完”，还包括：

- 人能在关键 gate 做判断。
- 人离开后回来能看懂现在发生了什么。
- 失败时能知道是模型输出、权限、上下文、测试、验收还是外部工具问题。
- 可以恢复，而不是只能重来。
- 下一阶段 prompt / handoff 能继续接上。

所以接下来最该做的不是更多 workflow 分支，而是让这个生命周期对人可见、可审、可恢复。

## 建议开工项

下一批建议开一个小分支：

```text
dev-review-packet-and-timeline
```

目标只做两件事：

1. Review packet artifact
2. Timeline projection

这两件事做好以后，再做 `dualctl watch` 和本地 UI。它们是同一条路上的下一步，不会浪费。
