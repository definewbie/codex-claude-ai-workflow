# 下一阶段功能规划：先补校准闭环，再做 review/UI

日期：2026-06-01  
目标：在主流程已经能真实跑起来之后，把下一步重点从“workflow 能执行”推进到“harness 能从失败中变好”。人工 review 和 UI 很重要，但它们应该建立在稳定的校准数据、读模型和失败解释之上，而不是先做一个直接读取 ledger 原始文件的页面。

## 当前判断

目前 orchestrator 的主功能已经从 demo 进入可验证状态：

- 可以启动 workstream。
- 可以创建隔离 worktree。
- 可以调用真实 acpx Claude / Codex。
- 可以经过 intake、design、plan、implementation、self-test、acceptance、delivery prepare、next-stage handoff。
- 可以通过 human gate 卡点。
- 可以记录 run ledger、events、node state、agent runs、reports。
- P5 TaskContract 和 P6 failure classify 已经接进 workflow variant。

现在最明显的问题不只是“缺一个好用 UI”，而是有两层缺口：

1. **Harness 闭环缺口**
   - FailureArbiter 已经能分类部分语义失败，但分类结果还没有沉淀成 calibration candidate。
   - p6 classification 分支还缺确定性 E2E 样本。
   - 失败没有进入“改 contract template / review checklist / context rule / regression test”的外环。

2. **人机协作体验缺口**
   - 人在 review gate 前不知道该看什么。
   - 流程跑到哪里，需要翻 `events.jsonl` 或 `status`。
   - 失败原因虽然在 ledger 里，但定位成本高。
   - Agent 输出很多，缺少一个聚合视图。

所以接下来的优先级不应该是“先做 UI”，而应该是：

```text
deterministic failure evidence
  → CalibrationCandidate
  → review/timeline/failure projection
  → CLI watch/review
  → local UI
```

## 从之前 TODO 中重新排序

### P0：先把 E2E 证据继续稳定下来

这部分仍然是基础，不稳定的 UI 只是漂亮壳。

1. **p6 reject path 作为技术债保留**
   - 目标：当真实 case 需要时，再稳定触发 `acceptance.classify_failure`，验证 failure classification 分支。
   - 当前 p6 smoke 能完整跑通，但分类分支是否触发取决于 Codex 是否 REJECT。
   - 取舍：如果 Codex 实际上很少 REJECT，这条路径就是边界条件。当前不为了确定性引入 fake reviewer / controlled fixture；脚本只在 classify 分支真实触发时校验 failure classification、calibration candidate 和 failure summary。

2. **status 字段校准**
   - 当前 `dualctl status` 会显示一些旧 checkpoint/stage 信息，比如 `Stage: design (pending)`，即使 workflow 已经跑到后面。
   - 这会影响 UI 和人工 review 的可信度。
   - 需要整理 status 的数据来源，以 active node / workflow current 为准。

### P1：CalibrationCandidate MVP

这是 long-term-plan 中 P7 的最小闭环，也是比 UI 更重要的下一步。

当前已有：

- TaskContract / ContractCompiler
- ContractReviewGate
- FailureArbiter
- iteration ledger
- events.jsonl
- final report / handoff

还缺的是把失败分类沉淀成可审查、可提升的候选项。

产物：

```text
.agent/workstreams/<id>/calibration/candidates/<candidate_id>.json
.agent/workstreams/<id>/calibration/candidates/<candidate_id>.md
```

候选类型：

- `CONTRACT_TEMPLATE`
- `REVIEW_CHECKLIST`
- `CONTEXT_RULE`
- `PROMPT_TEMPLATE`
- `REGRESSION_TEST`

候选内容：

- 来源 workstream / node / failure class
- 证据 refs
- 建议改什么
- 为什么这不是一次性修复
- 是否需要人审批
- 状态：`DRAFT | APPROVED | REJECTED | PROMOTED`

为什么它优先于 UI：

- UI 只能让人更方便地看一次失败。
- CalibrationCandidate 让系统能从失败中积累规则。
- 没有这个层，FailureArbiter 的价值只停留在“这次该怎么路由”，还没有变成 harness 的长期学习能力。

### P2：人工 review gate 体验

这是用户体验上最明显的痛点，但它应该消费稳定读模型，而不是直接解析 ledger 原始结构。

#### 1. `dualctl review show`

先不用做 Web UI，先做一个 CLI 聚合视图，作为后续 UI API 的雏形。

当前状态：第一版已落地。human gate 会写入 `reviews/<gate_node_instance_id>.md` 和 `.json`，`dualctl review show` 会展示当前 gate 的 packet。

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

当前状态：第一版已落地，作为 CLI 和未来 UI 共用的 review 读模型。

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

### P2：实时进度和失败原因

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

当前状态：第一版已完成。`dualctl watch` 基于 `timeline.json` 轮询展示当前 workflow 状态、active nodes、open gates 和最近节点；这不是 SSE/WebSocket 实时推送。

命令形态：

```bash
dualctl watch --workdir <workspace> <workstream_id>
```

短期采用轮询 `timeline.json`，不用一开始就做 SSE。

显示：

- 当前节点
- 最近事件
- 当前 agent run
- 当前 gate / recovery 状态
- report 路径

这会显著降低“人要盯着 console”的压力。

### P3：轻量本地 UI

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

### Tech Debt：deterministic p6 reject fixture

原因：没有稳定触发 failure classification 的样本时，相关分支无法做严格 E2E 回归。但它目前更像边界条件，不值得在主功能还没完全顺滑前投入 fixture 体系。

当前状态：真实 acpx smoke 仍保留 `PASS-WARN`，不伪造 Codex REJECT。短期只补强“如果 classify 分支发生，就必须检查新产物”的断言。等真实 smoke 或实际 workstream 遇到 REJECT 后，再根据现场数据决定是否补 controlled fixture。

产出：

- 一个 p6 reject smoke case
- 稳定生成 `failure-classification.json`
- 稳定出现 `failure_classified` event
- 明确区分主流程成功和 classify 分支成功

### Step 1：CalibrationCandidate MVP

原因：这是 contract-driven harness 从“会分类失败”到“会沉淀经验”的关键一步。

当前状态：第一版已落地在 `FailureClassifyHandler`。可校准分类会生成 JSON + Markdown 候选；`TOOL_LIMITATION` 和 `REVIEW_NOISE` 暂不生成候选，避免把一次性运行噪声变成长期规则。

产出：

- calibration candidate schema
- candidate writer handler
- 从 FailureArbiter 输出生成 candidate
- candidate markdown view
- 单测和 fixture 验证

产出：

- `calibration/candidates/<id>.json`
- `calibration/candidates/<id>.md`

### Step 2：Review packet + review show

原因：这是 UI 的最小内核，也是当前人工 gate 最大痛点。

当前状态：第一版已完成。

产出：

- `reviews/<gate_id>.md`
- `dualctl review show`
- 单测覆盖 gate packet 生成

### Step 3：Timeline projection

原因：UI / watch / status 都需要这个读模型。

当前状态：第一版已完成。`WorkflowFileLedger` 会在 workflow 初始化、节点路由、运行状态变化、retry/recovery 操作后自动更新 `timeline.json`，`dualctl timeline` 可以查看当前节点概览。

产出：

- `timeline.json`
- `dualctl timeline`
- 每个 node 的 duration、outcome、artifact refs

### Step 4：Failure summary

原因：真实运行中失败一定会发生，恢复体验要比成功路径更重要。

当前状态：ledger 已在普通 `FAILED` / `BLOCKED` 节点停住时生成 `recovery/current-failure.md`，覆盖 acpx exit code、失败原因、output refs 和 retry 提示。分类失败路径仍会生成更语义化的摘要，并关联 classification / calibration artifact。后续可以继续增强 stderr 摘录、parser error 摘录和权限错误分类，但不再阻塞当前主流程。

产出：

- `recovery/current-failure.md`
- `dualctl recovery show`
- stderr / parser / acpx failure 分类聚合

### Step 5：Watch mode

原因：不需要 Web UI 就能先解决“看进度”。

当前状态：第一版已完成。

产出：

- `dualctl watch`
- 轮询式终端 dashboard

### Step 6：Local dashboard MVP

原因：前面几个功能会自然沉淀出 API 和读模型，UI 到这个时候成本最低。

产出：

- `dual-server`
- read-only workstream dashboard
- gate approval/revision form

## 为什么不马上做完整 Web UI

UI 是需要的，但不能让 UI 先于 calibration 和读模型。

如果现在直接做页面，页面会被迫直接读各种 ledger 原始文件：`events.jsonl`、`nodes/*.json`、`runs/*/run.json`、outputs、reports。这样 UI 会和 ledger 内部结构强耦合，后面一改 ledger，UI 就碎。

更稳的路径是：

1. 先把 failure classification 已经产生的结果沉淀成 CalibrationCandidate。
2. 再把 failure summary 做成更完整的稳定读模型。
3. CLI 先消费这些读模型。
4. UI 再消费同一套读模型。
5. deterministic p6 reject fixture 等真实 REJECT 样本或稳定回归需求出现后再补。

这样 UI 不是另起一套，而是自然长出来。

## 这和完整生命周期目标的关系

完整生命周期不只是“机器自己跑完”，还包括：

- 人能在关键 gate 做判断。
- 人离开后回来能看懂现在发生了什么。
- 失败时能知道是模型输出、权限、上下文、测试、验收还是外部工具问题。
- 可以恢复，而不是只能重来。
- 下一阶段 prompt / handoff 能继续接上。

所以接下来最该做的不是更多 workflow 分支，也不是马上做完整 UI，而是让这个生命周期形成最小学习闭环：失败能分类，分类能变成候选改进，候选能被人审，未来能提升为模板、规则或回归样本。

## 建议开工项

下一批建议开一个小分支：

```text
dev-calibration-candidate-mvp
```

目标收口为这些主线能力：

1. CalibrationCandidate MVP
2. Review packet + `dualctl review show`
3. 最小 timeline projection，供后续 `watch` 和 UI 消费
4. 最小 failure projection，供 recovery / watch / UI 消费
5. 保留 p6 reject fixture 技术债，不在当前主线投入

这些事情做好以后，再做 failure summary 扩展和本地 UI。这样 UI 解决的是 harness 的真实观察与校准问题，而不是只给 ledger 套一层页面。
