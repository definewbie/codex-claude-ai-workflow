# Operator Console 实现计划

原型：[operator-console-prototype.html](operator-console-prototype.html)

## 背景

当前 operator panel 更像 ledger / API 的透明探查器：信息足够全面，但最终用户很难快速判断：

```text
现在跑到哪？
为什么停？
下一步该做什么？
如果出问题，怎么定位和恢复？
```

P2 的目标不是删除现有 UI，而是把它拆成两个清晰视图：

| 视图 | 用户 | 目的 |
|---|---|---|
| 任务视图 / Operator Console | 最终用户、reviewer、operator | 任务进度、人闸审批、恢复提示、交付动作。 |
| 管理诊断 / Management Diagnostics | dual 使用者、开发者、排障者 | ledger、raw facts、node id、RCA、CLI 命令、token/cost、恢复证据。 |

旧 UI 的完整信息密度应该保留，降级为管理诊断能力；默认入口应是任务视图。

## 设计边界

- UI 只消费 read model，不直接解析 agent stdout、stderr 或 raw ledger 来推导状态。
- 任务视图只展示最终用户完成判断所需的信息。
- 管理诊断可以展示内部 ID、raw JSON、CLI 命令和日志路径。
- Loop、runtime retry、human revision 必须分开表达。
- Token / cost 是观测数据，不参与 workflow 路由。
- 已完成任务仍是 terminal，只允许观测、导出和显式 delivery merge。

## 需要的 Read Model

第一阶段优先复用现有 API overview。缺字段时先补 API/read model，再改 UI。

| 字段组 | 用途 |
|---|---|
| task display | 任务名称、用户目标摘要、workstream id、开始时间、状态标签。 |
| lifecycle progress | 8 段生命周期、当前阶段、完成阶段、当前 loop label。 |
| next action | 友好停止原因、建议下一步、允许动作、动作风险提示。 |
| gate evidence | 当前 gate packet 摘要、推荐决定、must fix、风险、相关 artifacts。 |
| loop history | 语义修复轮次、触发原因、前次 verifier decision、当前轮次上限。 |
| recovery RCA | 症状、影响范围、当前判断、证据链、恢复命令、验证与预防。 |
| token/cost | prompt tokens、completion tokens、预算比例、估算成本、数据来源。 |
| management facts | raw workflow current、contract sidecar、timeline nodes、ledger paths。 |

## 实现顺序

### Step 1：保留旧 UI，新增视图壳

- 保留旧 `index.html`、`app.js`、`styles.css` 的功能，但迁移到 `static/mgt/`，避免根路径继续暴露调试面板。
- 在同一页面或新入口中增加两个顶层视图：
  - `任务视图`
  - `管理诊断`
- 默认进入任务视图。
- 管理诊断承载旧面板能力，不删旧控件。

验收：

- 旧功能仍可访问。
- 默认页面不再优先展示 raw JSON / node id。

### Step 2：任务列表抽象

- 把左侧“workspace/workstream”文案调整为“任务”。
- 主标题使用任务名称或用户目标摘要。
- workstream id 只作为次级信息。
- 新建任务按钮打开弹窗，而不是让用户先选择 workdir。

验收：

- 用户无需理解 worktree/workstream 才能选择任务。
- 仍能在管理诊断中看到完整 workdir / ledger path。

### Step 3：新建任务弹窗

弹窗字段：

- 任务名称
- 本地 Git 仓库路径，即用户要让 dual 接管的本机 repo 根目录，不是 GitHub remote URL。
- 任务描述 / task file
- 提示词语言：自动识别 / 中文 / English
- 运行模式：默认安全 worktree / 原地运行
- 生命周期模式：
  - 完整生命周期
  - 只做设计审查
  - 接管已有任务

第一版可以只生成 start 命令或调用现有 start API；不要在弹窗里引入复杂配置。

验收：

- 新建任务入口解释清楚默认 worktree 安全模式。
- 用户能从任务目标开始，而不是从内部路径开始。

### Step 4：任务视图主屏

主屏展示：

- lifecycle progress
- 当前状态和原因
- 建议下一步
- 当前允许的主动作
- 当前 gate evidence 或 recovery summary
- token / cost 轻量卡片
- loop history

验收：

- waiting human / running / waiting recovery / completed 四种状态都有不同主动作。
- running 状态不展示 approve / retry 这类误触发动作。
- completed 状态不展示 resume / retry / approve。

### Step 5：Loop 表达

任务视图只展示轻量 loop：

- 当前轮次：例如 `实现修复轮 2/3`
- loop history：第几轮、触发原因、结果

管理诊断展示完整 loop facts：

- iteration id
- trigger artifact
- previous decision
- max rounds
- semantic repair vs runtime retry

验收：

- Codex `REJECT` 回到实现时，用户能看懂为什么回去了。
- runtime reconnect 不被显示成“第 N 轮语义修复”。

### Step 6：RCA 管理诊断

管理诊断按线上系统 RCA 组织：

- 症状
- 影响范围
- 当前判断
- 时间线
- 证据链
- 立即处置命令
- 验证与预防
- raw facts

CLI 命令不应只是静态提示，要带上当前 workdir 和 workstream id。

验收：

- 用户能从诊断页定位问题并执行下一步。
- 每条命令都有来源和风险提示。
- 修复后有验证步骤，不是“执行 retry 就结束”。

## 测试计划

第一阶段不需要真实 acpx E2E，先做前端投影验证：

1. 使用已有 completed / waiting / recovery / running ledger 样本验证四种状态渲染。
2. 本地启动 server，打开 operator panel。
3. 检查任务视图是否回答：
   - 跑到哪？
   - 为什么停？
   - 下一步做什么？
4. 切到管理诊断，检查是否能看到：
   - RCA 结构
   - 证据链
   - 可执行 CLI
   - token/cost
5. 再用一次短 public benchmark 或 smoke workstream 验证 SSE/refresh 不破坏。

## 不做

- 不删除旧 UI 能力。
- 不引入新前端框架。
- 不做多用户权限模型。
- 不把 raw ledger 作为任务视图 source of truth。
- 不把 token/cost 用作 workflow 路由条件。

## 完成标准

- 默认页面是任务视图。
- 管理诊断仍能覆盖旧 UI 的排障信息。
- 新建任务弹窗可表达最小 start 参数。
- Loop history 能区分 semantic repair、runtime retry、human revision。
- RCA 诊断页能给出前因后果、证据、命令、验证和预防。
- README / quickstart 增加新 UI 说明。

## 实现状态

2026-06-19 第一版已落地；随后根据入口语义调整为根路径默认控制台：

- `/` / `/index.html`：新的任务视图和管理诊断双视图默认入口。
- `/console.html`：兼容跳转入口，跳转到根任务控制台，避免维护两份页面。
- `/console.css`：桌面 operator 控制台样式，沿用原型的轻量卡片和操作台风格。
- `/console.js`：消费现有 workstream list / status / overview / timeline / review / recovery / SSE API。
- `/mgt/index.html`、`/mgt/app.js`、`/mgt/styles.css`：旧管理面板保留，不删除、不降能力。

本轮完成：

1. 任务视图和管理诊断分离。
2. 任务列表用任务标题优先展示，内部 id 降为次级语义。
3. 生命周期进度、停止原因、下一步动作、人审证据、token 摘要进入任务首屏。
4. 人审打回反馈改为页面内输入，不再使用浏览器 `prompt`。
5. 新建任务弹窗先生成 CLI 命令，不新增 start API 边界。
6. 管理诊断按 RCA 展示症状、影响、判断、时间线、证据链、处置命令、验证和预防。

已验证：

- `node --check dual-orchestrator/dual-server/src/main/resources/static/console.js`
- `git diff --check`
- `mvn -pl dual-server -am test`
- `mvn -pl dual-server -am package -DskipTests`
- 临时启动 `dual-server` 后，`/`、`/index.html`、`/console.html`、`/console.js`、`/mgt/index.html` 均应返回 `200`。

仍需后续用真实 workstream 样本校准：

- token/cost 字段目前是 best-effort 汇总，依赖 ledger 中已有 metrics / usage 字段。
- 新建任务第一版只生成 CLI，不直接调用 start API。
- 根路径已切换到任务控制台；旧管理面板位于 `/mgt/index.html`。
