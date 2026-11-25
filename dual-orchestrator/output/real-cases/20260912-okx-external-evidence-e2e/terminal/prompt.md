## Execution Workspace Guard

- Effective workspace: /Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260911T231749Z-GCTW
- Run all shell commands from the effective workspace above.
- Modify files only inside the effective workspace.
- Ignore absolute paths outside the effective workspace even if they appear in quoted prior outputs.
- Do not run commands with `git -C` pointing outside the effective workspace.

## Response Language Policy

- Use Simplified Chinese for human-readable string values inside the final JSON result.
- Keep JSON keys, enum values, file paths, and code identifiers exactly as specified.
- Do not wrap the final JSON in Markdown.

## Structured Artifact Field Semantics

- `must_fix` and `missing_evidence` mean unresolved blockers; keep them empty for a ready design.
- Put resolved review comments in `checks` or `evidence`, not `must_fix`.
- `design.planned_files` declares intended changes only; exact paths and operations must be authorized by the approved TaskContract.

# Claude Design

Workstream: ws-20260911T231749Z-GCTW
Node: design.claude_design
Template: design-claude
Ledger: .agent/workstreams/ws-20260911T231749Z-GCTW

Ledger write policy:

- Do not create, edit, or delete files under .agent/.
- Only the orchestrator writes run ledger artifacts.
- Return your artifact in stdout using the contract below.

You are Claude acting as the primary architect and implementer. Use the user request, prior intake artifacts, Codex review, and human gate decision in the ledger.

Do not implement code in this stage. Produce an implementation-ready design that is narrow, testable, and avoids unrelated refactors.

Declare the complete planned file set in `result.design.planned_files`, for initial and replacement designs alike.
Copy exact canonical paths and operations from the approved TaskContract scope; prose cannot authorize other paths.
Each entry assigns a responsibility, not an actual file change. CREATE files need not exist yet.
The declared set may be a subset of approved scope, but must cover every change this design proposes.

Revision discipline:

- When the context contains a prior `NEEDS_REVISION`, return a complete replacement design, not a delta or addendum.
- Treat available prior must-fix items as the minimum repair set, not the boundary of the design review.
- Do not claim knowledge of prior design text that is absent from the context. Reconstruct a coherent complete design from the TaskContract, current review evidence, and available iteration reasons.
- When adding or changing state, an index, lock, transaction, queue, cache, external side effect, or compensation, cover every applicable lifecycle boundary in `checks` or `evidence`: normal completion, concurrency, timeout/provider failure, interruption/crash, restart/recovery, rollback/conflict, and a cannot-bypass test. State why a boundary is not applicable instead of inventing requirements.
- Prefer deleting unnecessary mechanisms or simplifying the state model. Do not add platform capability merely to close one finding.

## User Request

# OKX BTC 分钟级 K 线 Web 应用需求

## 目标

构建一个本地 Web 应用，通过 OKX 官方公开行情 REST API 获取 BTC-USDT 现货分钟级 K 线，将数据持久化到 SQLite，并展示可定时刷新的 K 线图。

本应用只读取公开行情，不得下单、访问账户、提供投资建议或抓取 OKX 网页。

## 强制技术栈

- Java 25。
- Spring Boot 4.1.0。
- Maven。
- MyBatis-Plus 3.5.17，必须使用 `mybatis-plus-spring-boot4-starter`。
- SQLite 是唯一应用数据库。
- 使用成熟的 K 线图表库，生产资源必须随应用本地打包，不得依赖运行时 CDN。

不得用 JPA、JdbcTemplate、其他 ORM 或自行编写的平行 repository 基础设施替代 MyBatis-Plus。

## OKX 行情契约

- 数据源只能是 OKX 官方公开 Market Data REST API，不得抓取 HTML 页面。
- 交易品种固定为 BTC-USDT 现货。
- K 线周期固定为 `1m`。
- 接口为 `GET /api/v5/market/candles`。
- 首次启动拉取并保存最近 300 根 K 线。
- 响应行格式为 `[ts,o,h,l,c,vol,volCcy,volCcyQuote,confirm]`。
- `ts` 是 K 线开盘时间的 Unix 毫秒时间戳。
- `confirm=0` 表示当前 K 线尚未闭合，后续允许更新。
- `confirm=1` 表示 K 线已经闭合。
- OKX 可能按时间倒序返回数据，后一次请求也可能命中较旧缓存。应用不得假设响应严格单调，也不得因旧响应删除本地较新的 K 线。
- OKX base URL、连接/读取超时、调度开关和刷新周期必须支持外部配置。
- 调用公开行情不得要求 API Key 或 OKX 账户。
- 生产应用和显式 live smoke 必须直接调用 OKX 官方 API，不得通过测试桩、代理业务服务或抓取服务替代。

## 数据持久化

- 通过 MyBatis-Plus 将 K 线保存到 SQLite。
- 数据库必须对 `(instrument, bar, open_time)` 建立唯一约束。
- 重复同步必须幂等。
- 未闭合 K 线应原位更新。
- 已闭合 K 线在应用重启后仍可查询。
- 价格和成交量不得以二进制浮点数作为权威值，Java 与 SQLite 均采用 decimal-safe 表示。
- 保存足够的同步状态，以展示最近一次尝试、最近一次成功和最近失败；错误文本不得反向成为生命周期状态 owner。
- 数据库初始化必须确定且版本化，不得在业务代码中临时拼接建表流程。

## 定时同步

- 启用同步时，应用启动后执行一次初始回填。
- 默认每 15 秒轮询一次 OKX，周期可配置。
- 每轮必须先完整解析并校验 OKX envelope，再写数据库。
- 同一响应中的合法 K 线在一个事务内完成 upsert。
- HTTP 超时、非 2xx、OKX 业务错误、限流、非法 JSON、非法行或数据库异常不得删除已有 K 线。
- 不得进行无界立即重试；失败后记录 degraded 状态，等待下一轮调度。
- 单应用实例内不得出现重叠调度执行。
- 日志只记录必要运行元数据，失败时不得打印完整 OKX 响应正文。

## 后端 API

提供稳定 JSON API：

1. 查询最近最多 300 根已保存 K 线，按开盘时间升序返回。
2. 查询同步健康状态，包括当前状态、最近尝试时间、最近成功时间和有界安全的失败摘要。

非法 limit 必须返回明确的客户端错误。浏览器只能调用本应用后端，不得直接请求 OKX。

## Web 体验

- 首屏直接展示可用的行情界面，而不是营销落地页。
- 展示真实蜡烛 K 线和成交量。
- 展示 `BTC / USDT`、`1m`、最新收盘价、最近闭合 K 线时间、同步状态和最近成功刷新时间。
- 浏览器默认每 10 秒从后端刷新数据，不整页重载。
- 同步 degraded 时保留最后可用图表，并显示克制但清晰的状态。
- 正确处理首次加载、无数据、数据降级和后端失败状态。
- 当前桌面和移动端视口均应可用，文字、图表和操作不得重叠。
- 不得把模拟价格冒充为 OKX 实时行情。

## 验证要求

- 生产实现只能直连 OKX 官方公开 API。
- `mvn test` 不得依赖公网 OKX 的可用性；自动化测试使用本地、契约一致的 HTTP stub 和冻结 fixture，专门验证正常响应及公网无法稳定制造的超时、429、乱序、陈旧缓存与畸形响应。
- 测试必须覆盖首次回填、倒序输入、重复同步、未闭合 K 线更新、闭合 K 线持久化、陈旧响应、非法 envelope、非法行、超时、非 2xx、OKX 业务错误和调度不重叠。
- 使用临时 SQLite 数据库验证唯一约束与重启后持久化。
- 覆盖后端 API 排序、limit 校验、健康状态，以及同步失败后保留已有数据。
- 提供浏览器级或 DOM 级验证，证明图表消费本应用后端数据、定时刷新且不从浏览器调用 OKX。
- Java 25 下执行 `mvn test` 必须通过，并且测试执行期间无需访问公开 OKX API。
- 提供显式 opt-in live smoke 命令，直接向 OKX 官方接口发起一次只读请求，并验证至少一根结构合法的 BTC-USDT 1m K 线；该命令不得进入默认 `mvn test`。
- README 必须记录架构、配置、SQLite 路径、启动、测试、live smoke、API、数据新鲜度语义和限制。

## 不在范围内

- 交易、账户访问、认证、API Key、余额或任何私有 OKX API。
- 投资建议、告警、策略、技术指标或预测。
- WebSocket 行情。
- 多交易品种或多周期切换。
- 分布式调度或多实例协调。
- 用户账户或管理后台。
- 通用交易所抽象或行情平台。
- 运行时 CDN 依赖。



## Context Package

### Direct Input Artifacts

- .agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789171190662/outputs/design-codex-review.stderr.log
  (content omitted; no typed context projection)

- .agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789171190662/outputs/design-codex-review.transcript.log
  (content omitted; no typed context projection)

- .agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789171190662/outputs/design-codex-review.json

```json
{
  "workstreamId" : "ws-20260911T231749Z-GCTW",
  "runId" : "run-1789171190662",
  "nodeId" : "design.codex_arch_review",
  "nodeInstanceId" : "node-design-codex_arch_review-033",
  "stageOutcome" : "NEEDS_REVISION",
  "lifecycleStatus" : "NEEDS_REVISION",
  "decision" : "NEEDS_REVISION",
  "summary" : "状态模型和图表适配已收敛，仍需补齐真实提交失败与启动等待退出两个边界。",
  "sourceArtifactType" : "agent_output_contract",
  "sourceOutputRef" : ".agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789171190662/outputs/design-codex-review.json",
  "blocking" : true,
  "checks" : [ "复核完整当前设计及 37 项 planned_files，未发现新增路径或业务范围扩张。", "检查正常完成、并发、提供方失败、事务提交与回滚、中断、启动失败和重启恢复。", "AR-1、AR-1a：NOT_ADDRESSED；事务职责已统一，但 beforeCommit 注入不能替代底层提交失败验证。", "AR-1b：ADDRESSED；已删除内容镜像和时间戳择新，采用持久化事实与明确不可用响应，恢复序列测试已纳入。", "AR-2：NOT_ADDRESSED；全部入口已有启动约束，但仅成功释放的阻塞等待缺少失败和关闭时的退出规则。", "AR-3：ADDRESSED；采用人工门禁提供的已核实依据，固定版本直接接收毫秒，测试期望独立来自 fixture。", "AR-4、AR-5：ADDRESSED；安全摘要和新鲜度修订保持完整。", "人工门禁 023、029：NOT_ADDRESSED；其余要求已落实，剩余提交失败和启动失败边界见 must_fix。" ],
  "evidence" : [ "SyncStateService、SyncStateMapper 与 KlineSyncService 已一致声明 markSuccess 加入行情事务，markFailure 在回滚后独立执行。", "当前故障测试使用 TransactionSynchronization.beforeCommit 抛异常，并称其“建模 SQLite COMMIT 提交阶段失败”；该故障发生在底层提交调用之前，未覆盖提交调用本身抛异常后的清理路径。", "startupReady 使用 CountDownLatch，仅在启动转换成功提交后释放；全部同步入口调用阻塞式 awaitStartupReady。", "设计以“启动失败则应用已退出”解释不会无界等待，但未声明等待线程如何被取消或唤醒；上下文启动失败不能单独证明手动调用线程已退出。", "内存不可用标志、503 响应、后续提交后恢复及重启读取持久化事实已有明确设计和测试序列。", "图表毫秒依据来自 gate.human_design_approval 的操作者核实记录，本次未独立访问外部文档。" ],
  "mustFix" : [ "AR-1a：保留 beforeCommit 用例作为提交前失败测试，删除其等同底层 COMMIT 失败的表述。补充提交调用本身抛异常的测试设计，并明确事务管理器如何完成回滚、释放连接，再进入 markFailure；在单连接池下断言连接可继续使用、行情与本轮成功标记共同回滚、历史成功信息保留。无需增加文件，可在现有测试中通过连接或事务管理器故障注入实现。", "AR-2：删除成功条件未满足时的无界阻塞依赖。优先采用启动未就绪时同步入口直接返回、启动转换完成后显式触发回填的非阻塞方案；若保留 CountDownLatch，则必须定义启动失败、上下文关闭和线程中断时等待者的退出行为。测试须先让定时及手动入口进入等待，再令启动转换失败，证明调用结束、零 OKX 请求且无遗留等待线程，不能仅断言启动方法抛异常。" ],
  "contractObligations" : [ ],
  "missingEvidence" : [ ],
  "acceptanceNotes" : [ "其余已解决项不再作为阻塞；本轮两项修订均可保持批准合同和 37 项路径不变。", "这是设计审查，计划中的约束与测试尚不能描述为已执行证明或实现闭环。", "未修改文件、执行测试或进行外部查询。" ],
  "openRisks" : [ ],
  "changedFiles" : [ ],
  "verificationSummary" : "完整设计复核完成；剩余阻塞为底层提交失败处理与启动等待退出，其他既有评审项已在设计层面解决。",
  "completedWork" : [ ],
  "importantDecisions" : [ ],
  "recommendedNextStage" : "",
  "suggestedNextPrompt" : "",
  "control" : {
    "summary" : "状态模型和图表适配已收敛，仍需补齐真实提交失败与启动等待退出两个边界。",
    "decision" : "NEEDS_REVISION"
  },
  "failure" : {
    "categoryHint" : "失败路径与资源生命周期",
    "verificationRemediation" : "NONE",
    "contractGaps" : [ ],
    "outOfScopeFiles" : [ ],
    "missingTests" : [ "底层提交调用失败后的回滚、连接释放及后续失败状态入账。", "已有定时和手动等待者时启动失败，验证全部调用退出且无遗留等待线程。" ],
    "contextGaps" : [ ],
    "reason" : "提交前异常测试未覆盖提交调用失败；启动失败也未形成阻塞等待者的明确退出路径。"
  },
  "rereadReasons" : [ ],
  "reviewSubject" : null,
  "taskContractProposal" : null,
  "repositoryFactBinding" : null,
  "proposalDerivation" : null,
  "planArtifact" : null,
  "design" : null,
  "authoredRevisionResponses" : null,
  "authoredContext" : {
    "ref" : ".agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789171190662/prompts/design-codex-review.md.manifest.json",
    "sha256" : "de428b28cb7cdd25cf3dcad4a4196dd7f4bf6d453b2cc4976b526503a48469ba"
  }
}
```

- .agent/workstreams/ws-20260911T231749Z-GCTW/artifacts/context-governance/occurrences/node-design-codex_arch_review-033-run-1789171190662/binding.json
  (content omitted; no typed context projection)

- .agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789171190662/outputs/design-codex-review.contract.json
  (validated typed evidence already projected)

- .agent/workstreams/ws-20260911T231749Z-GCTW/context/agent-governance-node-design-claude_design-036-run-1789171318596.md

```text
# Agent Run Governance Context

- Workstream: ws-20260911T231749Z-GCTW
- Target Node: design.claude_design
- Agent Node Instance: node-design-claude_design-036
- Run Id: run-1789171318596
- Generated At: 2026-09-12T00:01:58.748275Z

## Repo Scope Guidance

This section is a passive harness guardrail. It guides repository exploration but does not replace task contract, artifact validation, or human approval.

- Stage: design
- Governance refs: capabilityCatalog=.agent/workstreams/ws-20260911T231749Z-GCTW/artifacts/context-governance/occurrences/node-design-claude_design-036-run-1789171318596/agent-capability-catalog.json; readReuseContract=.agent/workstreams/ws-20260911T231749Z-GCTW/artifacts/context-governance/occurrences/node-design-claude_design-036-run-1789171318596/read-reuse-contract.json; repoMap=.agent/workstreams/ws-20260911T231749Z-GCTW/artifacts/context-governance/occurrences/node-design-claude_design-036-run-1789171318596/repo-map.json; repositoryFactBinding=.agent/workstreams/ws-20260911T231749Z-GCTW/artifacts/context-governance/occurrences/node-design-claude_design-036-run-1789171318596/binding.json

- Tracked inventory: count=0; head=; sha256=

- Available context capabilities: direct_file_read:Read

- Recommended read files:
  - .gitignore
  - README.md
  - pom.xml
  - scripts/live-smoke.sh
- Expected edit files: (none planned)
```

Context use rule:

- If the package contains `Working Memory`, use it before reading repository files.
- Re-read files only when the memory is missing, stale, or insufficient for this design decision.
- If the Context Package contains a Read Reuse Contract, follow that contract exactly. Do not invent read-reuse fields when the contract does not declare them.

## External Research Discipline

- Use repository facts and supplied context first.
- WebSearch is not auto-approved; request human approval if necessary for this occurrence. Use it only for public package, version, or documentation facts.
- Never use WebFetch or arbitrary URL retrieval. Do not place secrets, source contents, credentials, or private identifiers in a search query.
- If the needed public fact cannot be obtained under the active permission policy, state the evidence gap instead of bypassing the policy.

## Output Contract

Return exactly one JSON object as the final assistant message.
Do not use Markdown fences, XML tags, headings, commentary, or text before/after the JSON.
Preserve the exact key names and JSON primitive types shown below. Replace example values with factual values.
Every JSON object is closed. Do not add keys that are not declared for that object.
Allowed `result.decision` values: [READY_FOR_ARCH_REVIEW]




`result.revision_responses` is required: return [] when there are no active authored obligations. Otherwise cover every active obligation_id from Required Authored Context exactly once; do not add unknown IDs or omit unresolved obligations. Each entry has exactly obligation_id, disposition (ADDRESSED or NOT_ADDRESSED), and non-empty evidence. Use NOT_ADDRESSED when unresolved; ADDRESSED is a claim for review, not an approval or closure. Preserve all previously required behavior and the approved contract while making this revision.Each `result.revision_responses` item is a closed object with exactly these fields: [obligation_id, disposition, evidence]. No other keys are permitted.

{
  "schema_version" : 1,
  "workstream_id" : "ws-20260911T231749Z-GCTW",
  "run_id" : "run-1789171318596",
  "node_id" : "design.claude_design",
  "result" : {
    "artifact_type" : "design_result",
    "decision" : "READY_FOR_ARCH_REVIEW",
    "summary" : "one short sentence",
    "lifecycle_status" : "READY",
    "checks" : [ "read the approved contract" ],
    "evidence" : [ "design evidence" ],
    "missing_evidence" : [ ],
    "must_fix" : [ ],
    "open_risks" : [ ],
    "verification_summary" : "Design only; no implementation performed.",
    "design" : {
      "planned_files" : [ {
        "path" : "repo/relative/new.ext",
        "operation" : "CREATE",
        "responsibility" : "responsibility assigned within the approved scope"
      } ]
    },
    "revision_responses" : [ ]
  }
}


# Required Authored Context

The following verified inputs are mandatory. Respond to every active obligation; ADDRESSED does not close it. Use NOT_ADDRESSED honestly. Initial authors return [].

## Revision parent

.agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789170793937/outputs/design-claude.contract.json

{
  "artifact_type" : "agent_output_contract",
  "schema_version" : 1,
  "workstream_id" : "ws-20260911T231749Z-GCTW",
  "run_id" : "run-1789170793937",
  "node_id" : "design.claude_design",
  "node_instance_id" : "node-design-claude_design-030",
  "handler_type" : "acpx.agent_run",
  "decision" : "READY_FOR_ARCH_REVIEW",
  "summary" : "按人工门禁 029 减法指示完成完整替换设计：markSuccess 并入行情事务原子提交、易失状态收敛为单一不可用标志、启动转换门控全部同步入口、图表 timestamp 毫秒直传（操作者已核实 v9 官方依据），37 项 planned_files 路径与操作不变。",
  "required_keys" : [ "summary", "decision" ],
  "allowed_decisions" : [ "READY_FOR_ARCH_REVIEW" ],
  "source_output_ref" : ".agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789170793937/outputs/design-claude.json",
  "source_artifact_type" : "design_result",
  "generated_at" : "2026-09-11T23:59:50.450809Z",
  "control" : {
    "summary" : "按人工门禁 029 减法指示完成完整替换设计：markSuccess 并入行情事务原子提交、易失状态收敛为单一不可用标志、启动转换门控全部同步入口、图表 timestamp 毫秒直传（操作者已核实 v9 官方依据），37 项 planned_files 路径与操作不变。",
    "decision" : "READY_FOR_ARCH_REVIEW"
  },
  "control_source" : {
    "kind" : "strict_agent_result_envelope"
  },
  "revision_responses" : [ {
    "obligation_id" : "sha256:4bea5bad54ccaf0660f1980bc23e591a0836984ea6de734028d668261aae7210#must_fix:0",
    "disposition" : "ADDRESSED",
    "evidence" : "本轮替换设计最终态：成功路径唯一短事务内整批 upsert 与 markSuccess(attemptAt, successAt, OK, 清空错误字段) 原子提交（markSuccess 以 REQUIRED 传播加入行情事务，绝不独立提交），事务不含 HTTP，attemptAt/successAt 获锁后仅存内存不预写库；失败路径整轮回滚后仅 markFailure 以 REQUIRES_NEW 独立短事务入账 DEGRADED；该写自身 DB 异常时仅输出 STATE_WRITE_FAILED+分类+异常类名的固定文本有界 WARN 并置位易失不可用标志，绝不声称已持久化 DEGRADED，异常不外抛，守卫 finally 释放；健康接口对标志置位或行读取失败统一 503 有界不可用。故障测试覆盖行中注入、markSuccess UPDATE 注入与 beforeCommit 提交阶段注入三类，断言行情与成功标记共同回滚、历史 last_success_at 保留，重启读取最后持久化行。"
  }, {
    "obligation_id" : "sha256:4bea5bad54ccaf0660f1980bc23e591a0836984ea6de734028d668261aae7210#must_fix:1",
    "disposition" : "ADDRESSED",
    "evidence" : "维持并加强：applyStartupMode 归 SyncStateService 所有（ApplicationReadyEvent、@Order 先于回填入口），enabled=false 短事务仅置 state=DISABLED 并保留全部历史时间与错误字段（种子 PENDING 与旧 OK 不再示出）；enabled=true 且持久化为 DISABLED 时置 PENDING；本轮补齐：转换成功提交后释放 startupReady 门，回填/@Scheduled/手动全部 syncOnce 入口第一步 awaitStartupReady，未完成零 OKX 请求零状态写；转换读/写失败异常传播使启动 fail-fast 失败。测试覆盖首启禁用（stub 计数=0、health=DISABLED）、成功后禁用重启（历史保留、计数不增）、重新启用（不再 DISABLED、请求恢复），并新增转换阻塞竞争与转换失败启动失败用例。"
  }, {
    "obligation_id" : "sha256:4bea5bad54ccaf0660f1980bc23e591a0836984ea6de734028d668261aae7210#must_fix:2",
    "disposition" : "ADDRESSED",
    "evidence" : "MarketController 显式嵌套 KlineResponse record（openTime/open/high/low/close/volume/volumeCcy/volumeCcyQuote/confirmed），与 app.js 映射输入、MarketControllerTest/ChartPageDomTest 断言三方同名；timestamp 单位已由操作者核实（KLineCharts v9 官方 data-source 文档明确毫秒、quick-start 示例 1587660060000），固定 9.8.0 直接传 openTime 毫秒、无转换函数；ChartPageDomTest 捕获真实 /api/klines 响应、悬停断言 tooltip 时间/OHLCV/成交量与 fixture 一致、等待第二次请求断言无导航且更新、全部网络请求仅本应用源——不止证明 canvas 存在。"
  }, {
    "obligation_id" : "sha256:4bea5bad54ccaf0660f1980bc23e591a0836984ea6de734028d668261aae7210#must_fix:3",
    "disposition" : "ADDRESSED",
    "evidence" : "维持已闭合方案：固定分类枚举+受控字段（HTTP 状态码整数、整体匹配 ^[0-9]{1,10}$ 的 OKX code），外部 msg 一律不进入摘要，不再有截断透传；写 sync_state、渲染 health JSON、输出日志三个 sink 统一 sanitize（剔除 C0/C1 控制字符与零宽/BIDI 字符、≤200 截断）；fixture 与 stub 内联构造 U+0000、ANSI 转义、HTML 片段、超长文本、非数字 code 响应，断言健康 JSON、页面 DOM 与日志 appender 均无未处理内容。"
  }, {
    "obligation_id" : "sha256:4bea5bad54ccaf0660f1980bc23e591a0836984ea6de734028d668261aae7210#must_fix:4",
    "disposition" : "ADDRESSED",
    "evidence" : "维持已闭合方案：README 与前端无『最多滞后一个轮询周期』表述；明确最近成功同步时间≠行情最新时间、confirm=0 行可变、OKX 陈旧缓存与连续失败可致无固定上界滞后、恢复取决于下一次成功调度；页面分列展示行情时间（最新收盘价对应 K 线与最近闭合 K 线时间，取自数据本身）与最近成功刷新时间，degraded 文案为『数据可能滞后，最后成功同步于…』；last_attempt_at 的『最近一次已入账尝试』语义写入 README。"
  }, {
    "obligation_id" : "sha256:a93bafaa80b5336b0cf7d6b463798e868a9d0bb03e4fd24c8b28fc537b542305#human-note:0",
    "disposition" : "ADDRESSED",
    "evidence" : "本轮按门禁 029 在同一范围内继续收敛：markSuccess 从『独立短事务』修订为 REQUIRED 加入行情事务（SyncStateService/SyncStateMapper/KlineSyncService 的 planned_files 职责已逐字同步，消除三处矛盾）；内存行情状态镜像、墙上时钟择新、双源合并与 statePersisted 字段全部删除，改为单一易失不可用标志+503 明确不可用；启动转换以 startupReady 门约束全部同步入口、初始化读写失败 fail-fast；timestamp 单位已由操作者核实为毫秒并直传，删除『首日复核』表述；AR-4/AR-5、confirm 规则、schema 保持不变，37 项路径与操作不变，未新增文件、框架或平台能力。"
  }, {
    "obligation_id" : "sha256:f9626a2e8bddac7e32d12b016553cc1e0e89e7d546b29245090b756ec0c6413b#must_fix:0",
    "disposition" : "ADDRESSED",
    "evidence" : "三处已统一：checks、evidence 与 planned_files（SyncStateService、SyncStateMapper、KlineSyncService）一致声明 markSuccess 仅以 REQUIRED 传播加入行情短事务、随整批原子提交、绝不独立提交；仅回滚完成后的 markFailure 使用 REQUIRES_NEW 独立短事务。测试新增提交边界断言：markSuccess UPDATE 注入失败与 TransactionSynchronization beforeCommit 注入失败（建模提交阶段失败）两用例，均断言行情与本轮成功标记共同回滚（零写入/原值保留）、状态不为 OK、历史 last_success_at 保留，不以行中途异常替代提交边界验证（行中注入用例保留为补充）。"
  }, {
    "obligation_id" : "sha256:f9626a2e8bddac7e32d12b016553cc1e0e89e7d546b29245090b756ec0c6413b#must_fix:1",
    "disposition" : "ADDRESSED",
    "evidence" : "采取减法：删除内存行情状态镜像、墙上时钟择新与双源合并（health 契约移除 statePersisted 字段）。内存仅一个易失布尔标志，仅在 markFailure 独立事务写失败时置位；任一后续状态写提交成功（成功事务确认或后续 markFailure 成功）即清除，为确定性事件驱动，OK 恢复唯一路径是成功事务提交确认。healthView：标志置位或持久化行读取失败→统一 503 SYNC_STATE_UNAVAILABLE 有界响应，不以旧 OK 或未持久化 DEGRADED 冒充；否则持久化行即权威；重启标志归零只读持久化事实。测试：markFailure 写失败→下一轮成功→DB 读取失败→503（无内存内容可回退，旧失败不复活）；注入固定 Clock 构造相同 attempt 时间戳两轮证明无时间戳比较路径；写失败后重启新上下文健康=最后持久化行。"
  }, {
    "obligation_id" : "sha256:f9626a2e8bddac7e32d12b016553cc1e0e89e7d546b29245090b756ec0c6413b#must_fix:2",
    "disposition" : "ADDRESSED",
    "evidence" : "不再依赖两个监听器排序代表调度入口受约束：applyStartupMode 成功提交后释放 startupReady 门（CountDownLatch），启动回填、@Scheduled 轮询与手动 syncOnce 三个入口第一步均 awaitStartupReady（门检查先于守卫获取），未完成不发起任何 OKX 请求或状态写；启动转换读/写失败异常传播使 SpringApplication 启动 fail-fast 失败，不新增恢复状态机。测试：@TestConfiguration 以 CountDownLatch 阻塞转换+极短 fixed-delay 断言竞争期间 stub 零请求、释放后恢复同步；转换注入异常断言上下文启动失败+零请求；禁用零请求、历史字段保留与重新启用用例保留。"
  }, {
    "obligation_id" : "sha256:f9626a2e8bddac7e32d12b016553cc1e0e89e7d546b29245090b756ec0c6413b#must_fix:3",
    "disposition" : "ADDRESSED",
    "evidence" : "依据已由操作者核实并给出来源：KLineCharts v9 官方 https://v9.klinecharts.com/guide/data-source 明确 KLineData.timestamp 为毫秒，https://v9.klinecharts.com/en-US/guide/quick-start 示例为 1587660060000。固定 9.8.0，app.js 直接传 openTime 毫秒、不除 1000、toChartTimestamp 转换函数整体删除；代码设计、README 与测试说明同步改写并删除全部『9.x 为秒』『首日复核』表述。ChartPageDomTest 时间期望值独立来自 fixture 的 Unix 毫秒 openTime（OKX 契约 ts 即毫秒），不复用被测映射（亦无转换函数可复用），单位错误必然渲染 1970 年代坐标而失败。证据缺口消除，missing_evidence 为空。"
  }, {
    "obligation_id" : "sha256:477d5aabb49db4519e633a6a3cccb27d52f6d593a1de3960fa63ae4be0d99910#human-note:0",
    "disposition" : "ADDRESSED",
    "evidence" : "逐条落实：删除内存行情状态镜像/墙上时钟择新/双源合并，状态持久化或读取失败统一 503 明确不可用，不以旧 OK 或未持久化 DEGRADED 冒充，成功事务确认后才恢复 OK；失败路径 finally 释放守卫；重启只读持久化事实；markSuccess 只参与整批 upsert 所在事务绝不独立提交，仅回滚后的 markFailure 用独立短事务；补成功状态写入失败与提交失败（beforeCommit 注入）的原子性断言，断言行情与本轮成功标记共同回滚、历史成功信息保留；启动转换在任何回填/定时/手动 syncOnce 入口前完成（startupReady 门），未完成不执行同步，初始化读写失败 fail-fast 使启动失败，测试覆盖启动延迟/失败与定时入口竞争；图表依据操作者核实的 v9 官方毫秒文档（data-source 与 quick-start 示例 1587660060000），固定 9.8.0 直接传 openTime 毫秒不除 1000，测试期望独立来自 fixture Unix 毫秒且不复用被测转换函数；AR-4/AR-5、confirm 规则、schema、37 项路径与操作保持不变，未新增文件/框架/平台，文案与测试设计一次完成。"
  } ],
  "lifecycle_status" : "READY",
  "checks" : [ "已重读批准 TaskContract tc-ws-20260911T231749Z-GCTW-v1（human gate 016 APPROVE）、人工门禁 023/029 指示、两轮 codex 评审 must-fix、REQUIREMENT.md 及基线仓库现状（本轮 Glob/Read 复核：root 仅 .gitignore/README.md/REQUIREMENT.md/scripts/verify-workspace.sh，无 pom.xml、无 src/、无 scripts/live-smoke.sh；.gitignore 已有 *.db/*.db-shm/*.db-wal/*.sqlite/*.sqlite3；verify-workspace.sh 第 8-9 行确为 test ! -e pom.xml / test ! -d src）；planned_files 保持 37 项（3 MODIFY + 34 CREATE）路径与操作逐字一致，responsibility 与本轮修订逐字同步，无越权路径、未新增文件、框架或平台能力；AR-4/AR-5 已闭合结论、confirm 条件 upsert 语义与 V1 schema 保持不变。", "AR-1a 一致性：checks、evidence、planned_files 三处统一为——markSuccess 仅以 REQUIRED 传播加入行情短事务、随整批 upsert 原子提交、绝不独立提交；仅行情轮回滚完成后的 markFailure 使用 REQUIRES_NEW 独立短事务；测试覆盖行中注入、markSuccess UPDATE 注入与 beforeCommit 提交阶段注入三类故障点，不以行中途异常替代提交边界验证。", "生命周期-正常完成：成功轮 门检查→守卫获取→attemptAt/successAt 仅存内存（可注入 Clock）→HTTP 获取与 envelope+全部行校验（零 DB 写）→唯一短事务内逐行 upsert + markSuccess 原子提交；提交后 /api/klines 与 /api/sync/health 即时一致反映，不存在已提交行情但状态滞后的窗口。", "生命周期-并发：fixedDelay（上次结束才计时）+ spring.task.scheduling.pool.size=1 + AtomicBoolean 在飞守卫三层防重叠；启动门检查先于守卫获取，门等待不占用守卫；守卫 try/finally 必释放（覆盖解析失败、事务回滚、markFailure 写失败被吞等全部分支）；启动回填与定时轮共用 syncOnce 同受守卫；Hikari maximum-pool-size=1 串行化 DB 访问；跨实例协调为契约非目标。", "生命周期-超时/提供方失败：TIMEOUT/HTTP_STATUS/RATE_LIMITED/BUSINESS_ERROR/MALFORMED_RESPONSE/MALFORMED_ROW/DATABASE 归一为该轮失败——解析期失败零写入；写库或提交失败时行情与 OK 状态同事务整体回滚；失败后经 markFailure 独立短事务入账 attempt+DEGRADED+受控摘要，该事务自身失败时仅一条固定文本有界 WARN + 置位易失不可用标志，不外抛、不谎称已持久化、无立即重试；失败路径无 DELETE，唯一恢复路径是下一轮调度。", "生命周期-中断/崩溃：未提交事务由 SQLite WAL/journal 回滚，库中不存在部分轮次；崩溃至多丢失当轮（含其状态记录），sync_state 保持最后一次已入账轮次；易失不可用标志随进程消失，不产生残留状态；对 OKX 仅只读 GET，无外部写副作用，补偿不适用。", "生命周期-重启/恢复：Flyway 迁移幂等跳过；启动回填重跑为幂等 upsert；重启后只读持久化事实行（易失标志归零），不会出现未持久化内存值；已闭合 K 线跨重启可查（KlinePersistenceTest 同一文件新建上下文验证）；markFailure 写失败后的重启读取有专用故障测试。", "生命周期-回滚/冲突：行键冲突是 ON CONFLICT 正常路径而非错误；唯一约束兜底并以直插重复键用例证明不可绕过；beforeCommit 注入的提交阶段失败断言行情与成功标记共同回滚、历史 last_success_at 保留。", "cannot-bypass 测试映射：调度不重叠（SchedulerOverlapTest 并发峰值=1，含状态写失败分支后守卫仍释放）；任何失败不删除数据（失败矩阵断言行数不减）；confirm 1→0 不可回退不可覆盖；非法 limit 返回 400；浏览器零出站、零 CDN 且图表真实消费本应用后端数据（ChartPageDomTest，时间期望独立来自 fixture Unix 毫秒）；禁用期间零 OKX 请求；启动门未完成零同步执行；live smoke 不进默认 mvn test（surefire 排除+独立脚本）。", "AR-1b 单一权威状态模型：删除内存行情状态镜像、墙上时钟择新与双源合并（health 契约移除 statePersisted 字段）；内存仅一个易失布尔标志，markFailure 写失败置位、任一后续状态写提交成功清除，确定性事件驱动、无时间戳比较路径；标志置位或持久化行读取失败统一 503 SYNC_STATE_UNAVAILABLE 有界响应，不以旧 OK 或未持久化 DEGRADED 冒充当前状态；OK 恢复唯一路径是成功事务提交确认；测试覆盖 写失败→下一轮成功→读取失败→503、相同时间戳（固定 Clock）、重启只读持久化行。", "AR-2 启动门：applyStartupMode 于 ApplicationReadyEvent（@Order 先于回填监听器）按 enabled 转换并成功提交后释放 startupReady 门；启动回填、@Scheduled、手动 syncOnce 全部入口第一步 awaitStartupReady，未完成不发起任何 OKX 请求或状态写；启动转换读/写失败异常传播使 SpringApplication fail-fast 失败，不新增恢复状态机；测试覆盖转换阻塞+极短 fixed-delay 竞争（零请求）、转换注入异常启动失败（零请求）、禁用零请求、历史字段保留与重新启用。", "AR-3 timestamp 依据：操作者已核实 KLineCharts v9 官方文档（https://v9.klinecharts.com/guide/data-source 明确 KLineData.timestamp 为毫秒；https://v9.klinecharts.com/en-US/guide/quick-start 示例 1587660060000）；固定 9.8.0 直传 openTime 毫秒，toChartTimestamp 转换函数整体删除；代码设计、README、测试说明同步改写，全部『9.x 为秒』『首日复核』表述清除；ChartPageDomTest 期望值独立来自 fixture 的 Unix 毫秒语义，不复用被测映射（亦无转换函数可复用）。", "不适用边界声明：缓存（未引入，本地毫秒级读无缓存）、异步队列（无）、分布式锁/多实例协调（契约非目标）、对外系统写副作用补偿（OKX 仅只读 GET）；未新增重试框架、内存缓存或额外线程池。", "前后端契约一致性：KlineResponse 显式字段 = app.js 映射输入 = ChartPageDomTest/MarketControllerTest 断言字段三方同名；/api/sync/health 五字段（state/lastAttemptAt/lastSuccessAt/lastErrorCategory/lastErrorSummary）与 503 不可用响应在 controller、README、前端、测试四处一致。", "测试离线性由构造保证：自动化测试仅连本地 StubOkxServer 与冻结 fixture（时间经注入 Clock 固定/步进），默认 surefire 排除 **/smoke/**；生产 base-url 仅在显式 live smoke 中使用。" ],
  "evidence" : [ "架构分层：okx 层（OkxMarketClient+CandleRowParser：HTTP+envelope+行校验）→ sync 层（KlineSyncService 门/守卫/事务编排 + SyncStateService 状态机与安全摘要 owner）→ mapper 层（MyBatis-Plus 唯一持久化通道）→ web 层（MarketController 显式 DTO + GlobalExceptionHandler + 同源静态前端）；浏览器仅访问本应用，唯一出站流量是服务端到 OKX 的只读 GET /api/v5/market/candles?instId=BTC-USDT&bar=1m&limit=300。", "AR-1a 事务边界（核心修订）：syncOnce 过门获守卫后 attemptAt/successAt 仅存内存、不预写库；HTTP 获取与 envelope/全部行校验完成（零 DB 写）后，成功路径在唯一短事务内先逐行 upsert 再调用 markSuccess——markSuccess 以 REQUIRED 传播加入同一事务（checks/evidence/planned_files 三处一致），随整批原子提交，绝不独立提交，recordSuccess-after-commit 失败模式结构性消除；事务不含 HTTP；任何失败（含提交阶段）整轮回滚（行情零写入且状态不为 OK），进入失败路径归类 DATABASE。", "AR-1a 故障测试：成功事务第 N 行 upsert 注入 mapper 失败、markSuccess 单语句 UPDATE 注入失败、TransactionSynchronization beforeCommit 抛出（建模 SQLite COMMIT 提交阶段失败）三类用例，均断言行情与本轮成功标记共同回滚（零新增/原值保留）、状态不为 OK、历史 last_success_at 保留、随后失败路径以独立短事务入账 DEGRADED/DATABASE。", "AR-1b 失败路径与状态写失败：失败路径先整轮回滚，再以 REQUIRES_NEW 独立短事务 markFailure(attemptAt, state=DEGRADED, category, 受控摘要)（last_success_at 不动）；该事务自身抛 DB 异常时的有界处理：仅一条固定文本 WARN（STATE_WRITE_FAILED+分类+异常类名，绝不声称已持久化 DEGRADED、不含 OKX 正文）、置位易失不可用标志、异常不外抛、守卫 finally 释放、不阻塞下一轮调度。", "AR-1b 健康投影（单一权威）：内存仅一个易失布尔标志（无内容镜像）；标志置位（存在未确认入账的状态变更）或持久化行读取失败→统一 503 有界不可用 SYNC_STATE_UNAVAILABLE，不以旧 OK 或未持久化 DEGRADED 冒充；标志未置位→持久化行即唯一视图（state/lastAttemptAt/lastSuccessAt/lastErrorCategory/lastErrorSummary）；标志清除为确定性事件：后续任一状态写提交成功（成功事务确认或后续 markFailure 成功），OK 恢复唯一路径是成功事务提交确认；无墙上时钟比较、无双源合并、无 statePersisted 字段；重启后标志归零、持久化行为准；last_attempt_at 语义为『最近一次已入账尝试』（README 记录）；健康不可用时 /api/klines 独立可用，前端保留最后已提交图表数据。", "AR-2 启动门与转换：SyncStateService 拥有 ApplicationReadyEvent 上的 applyStartupMode（@Order 先于回填监听器）——enabled=false 时短事务仅将 state 列置为 DISABLED（last_attempt_at/last_success_at/last_error_* 历史全部保留，种子 PENDING 与旧 OK 不再示出）；enabled=true 且持久化为 DISABLED 时置为 PENDING（重新启用不沿用旧 DISABLED）；转换成功提交后释放 startupReady 门（CountDownLatch）；启动回填监听、@Scheduled 轮询与手动 syncOnce 三个入口第一步 awaitStartupReady（门检查先于守卫获取），未完成不发起任何 OKX 请求或状态写；转换读/写失败异常传播使 SpringApplication 启动 fail-fast 失败，不引入恢复状态机。", "AR-3 API 契约统一：MarketController 不直接序列化 domain Kline，改用显式嵌套 KlineResponse record：openTime(long 毫秒)、open/high/low/close/volume/volumeCcy/volumeCcyQuote（Jackson WRITE_BIGDECIMAL_AS_PLAIN 输出 plain 数值）、confirmed(boolean)；/api/sync/health 返回 state/lastAttemptAt/lastSuccessAt/lastErrorCategory/lastErrorSummary，状态不可用时由 GlobalExceptionHandler 返回 503 {error:SYNC_STATE_UNAVAILABLE, message:固定中文有界说明}。", "AR-3 前端映射与 timestamp 依据：锁定 klinecharts 9.8.0 UMD（vendored 本地打包，README 记录版本与获取方式）；app.js 单点适配 toChartData：{timestamp: openTime, open, high, low, close, volume}——KLineCharts v9 官方 https://v9.klinecharts.com/guide/data-source 明确 KLineData.timestamp 为毫秒，quick-start 示例为 1587660060000（操作者已核实），openTime 毫秒直传、不做任何除以 1000 换算、不存在转换函数；VOL 内置指标消费 volume。", "AR-3 DOM 级验证：ChartPageDomTest（Playwright+随机端口应用+stub OKX+冻结 fixture）：捕获真实 /api/klines 响应 JSON；悬停图表触发内置 tooltip，断言文本包含最新一根 K 线的时间串（期望值由测试独立依据 fixture 的 Unix 毫秒 openTime 推导，不复用生产映射代码）、开/高/低/收与成交量数值串与 fixture 一致（毫秒直传错误必然渲染 1970 年代坐标而失败）；等待第二个 /api/klines 请求（默认 10 秒周期）期间 stub 更新数据，断言无主框架导航、图表与头部最新价随之更新；断言全部网络请求仅命中本应用源（无 okx.com、无 stub 直连、无 CDN）；stub 切换 500 后页面保留旧图且不出现 stub 原文。", "AR-4 安全摘要（维持已闭合）：失败分类固定枚举 TIMEOUT/HTTP_STATUS/RATE_LIMITED/BUSINESS_ERROR/MALFORMED_RESPONSE/MALFORMED_ROW/DATABASE/STATE_WRITE_FAILED；摘要仅由分类固定中文标签与受控数字片段组成——HTTP 状态码（整数）、OKX envelope code（仅当整体匹配 ^[0-9]{1,10}$ 才输出 code=数字），外部 msg 一律不进入摘要；写 sync_state、渲染 health JSON、输出日志三个 sink 统一 sanitize（剔除 C0/C1 控制字符与零宽/BIDI 字符、截断 ≤200 字符）；OkxMarketClient 异常消息本身即该受控摘要；fixture 与 stub 内联构造 U+0000、ANSI 转义、HTML 片段、超长文本、非数字 code 响应做三 sink 断言。", "AR-5 新鲜度语义（维持已闭合）：README 与前端无『最多滞后一个轮询周期』表述；明确最近成功同步时间≠行情最新时间、confirm=0 行仍会变化、OKX 陈旧缓存与连续失败可使滞后超过一个轮询周期且无固定上界、恢复取决于下一次成功调度；页面分列展示行情时间（最新收盘价对应 K 线与最近闭合 K 线时间，取自数据本身）与最近成功刷新时间（同步状态区），degraded 文案为『数据可能滞后，最后成功同步于…』。", "AC-6 upsert 语义不变：KlineMapper 单条 upsert SQL：INSERT ... ON CONFLICT(instrument,bar,open_time) DO UPDATE SET 行情字段=excluded.对应字段、confirm=excluded.confirm、updated_at=excluded.updated_at WHERE excluded.confirm=1 OR kline.confirm=0——0→0/0→1/1→1 均取最近一次成功校验响应值，1→0 因 WHERE 不触发整行保持原状；全链路无 DELETE；新旧判定只用行内 confirm，本地较新行因不在冲突键内原样保留。", "解析先于写入：每轮先校验 HTTP 2xx、JSON 合法、code=='0'、data 为数组与全部 9 字段行（任一非法整轮拒绝零写入），随后才进入写事务；CandleRowParser 仅做格式校验，明确不做价格大小/方向等行情语义校验。", "调度与并发：@Scheduled(fixedDelayString 与 initialDelayString 均取 okx.sync.fixed-delay，默认 15s）+ 单线程调度器 + AtomicBoolean 守卫三层防重叠；attempt/success 时间戳来自构造注入的 java.time.Clock（生产 Clock.systemUTC()，测试固定/步进），保证故障用例确定性；SchedulerOverlapTest 以 fixed-delay 约 1s + stub 响应约 5s 断言并发峰值=1、请求串行，并以两线程并发调 syncOnce 验证守卫跳过（含状态写失败分支后守卫仍释放）。", "状态机与持久化 schema：sync_state 单行（V1 迁移种子 id=1 PENDING），state CHECK IN (PENDING,OK,DEGRADED,DISABLED)；状态仅由轮次结果分类枚举与启动转换驱动，错误文本只是 ≤200 字符有界展示元数据，绝不参与状态判定；Flyway V1 是唯一且版本化的建表入口，业务代码零 DDL。", "decimal-safe 链路：OKX 十进制字符串 → BigDecimal（parser）→ KlineEntity String(toPlainString) → SQLite TEXT → 读取经 BigDecimal 构造 → API 以 Jackson WRITE_BIGDECIMAL_AS_PLAIN 输出 JSON 数值；全程无 float/double/REAL；实体以 String 承载 TEXT 列规避 xerial ResultSet.getBigDecimal 对 TEXT 列的不确定行为，且不引入未授权 TypeHandler 文件。", "SQLite 并发与配置：Hikari maximum-pool-size=1 串行化读写 + WAL + busy_timeout=5000 + 短事务，缓解单写者 BUSY；OKX base URL、连接/读取超时、okx.sync.enabled、fixed-delay 全部经 @ConfigurationProperties(okx.sync) 外部可配置。", "测试矩阵映射：TR-1→OkxMarketClientTest+KlineSyncServiceTest；TR-2→KlinePersistenceTest；TR-3→MarketControllerTest；TR-4→SchedulerOverlapTest；TR-5→CandleRowParserTest；TR-6→ChartPageDomTest；TR-7→OkxLiveSmokeIT+-Plive-smoke+scripts/live-smoke.sh；TR-8→全量本地 stub+冻结 fixture；TR-9/TR-10→KlineSyncServiceTest 专用用例；429/非 2xx/非法 JSON/超时由 StubOkxServer 内联正文与挂起策略构造。", "新增故障/状态/启动用例（KlineSyncServiceTest，注入 Clock 保证确定性）：AR-1a 三类注入（行中/markSuccess UPDATE/beforeCommit）断言共同回滚与历史成功保留；AR-1b 序列 markFailure 写失败→健康 503+守卫已释放→下一轮成功提交→标志清除健康=OK→再注入行读取失败→健康 503（无内存内容可回退，旧失败不复活）；固定 Clock 构造相同 attempt 时间戳两轮证明无时间戳比较路径；写失败后重启新上下文读同一 DB→健康=最后持久化行；AR-2 用例：@TestConfiguration 以 CountDownLatch 阻塞启动转换+极短 fixed-delay 断言竞争期间 stub 零请求、释放后恢复；启动转换 mapper 读/写注入异常→上下文启动失败+零请求；enabled=false 首启（stub 计数=0、health=DISABLED）、成功一轮后禁用重启（历史时间保留、计数不增）、重新启用（不再显示 DISABLED、请求恢复增长）。", "smoke 隔离：默认 surefire 排除 **/smoke/**（叠加 *IT 命名双保险）；live-smoke profile 以 combine.self=override 清空排除并仅 include OkxLiveSmokeIT；OkxMarketClient 为构造式无 Spring 注解依赖，可被 smoke 直接 new 以直连 www.okx.com。", "基线文件修改点（本轮已实地复核）：verify-workspace.sh 仅将第 8-9 行 test ! -e pom.xml / test ! -d src 翻转为 test -f pom.xml / test -d src，保留 Java 25 校验、清洁树校验与只读 OKX 探测；.gitignore 现有 *.db/*.db-shm/*.db-wal/*.sqlite/*.sqlite3，仅补 *.db-journal 与 data/；README 为基线文档整体重写覆盖 REQUIREMENT 验证条目。" ],
  "missing_evidence" : [ ],
  "must_fix" : [ ],
  "open_risks" : [ "Spring Boot 4.1.0 与 mybatis-plus-spring-boot4-starter 3.5.17、xerial sqlite-jdbc、Flyway 的坐标解析与最小上下文装配需实现首日验证（含 Flyway SQLite 支持归属 flyway-core 还是附加模块）；如需附加坐标仅添加于已授权 pom.xml。", "Hikari data-source-properties 传递 xerial pragma（journal_mode/busy_timeout）的键名绑定需首日验证；备选为 JDBC URL 参数或 yml 显式键，不影响语义。", "Playwright for Java 浏览器二进制与 vendored klinecharts 9.8.0 UMD 的构建期获取属环境准备项（README 记录）；离线构建环境缺浏览器二进制时 ChartPageDomTest 无法执行，属环境限制而非设计缺口。", "live smoke 依赖公网 OKX 可用性与限流策略，可能偶发非确定性失败；仅影响显式 opt-in 命令，不影响默认 mvn test。" ],
  "verification_summary" : "本阶段仅产出实现就绪的完整替换设计：未执行任何代码实现、构建或测试运行；AR-1a/AR-1b/AR-2/AR-3 按人工门禁 029 以减法方式收敛，AR-4/AR-5 维持已闭合结论，37 项 planned_files 路径与操作和批准 scope 逐字一致。",
  "design" : {
    "planned_files" : [ {
      "path" : ".gitignore",
      "operation" : "MODIFY",
      "responsibility" : "补充 SQLite 运行期产物忽略（*.db-journal、data/ 目录），与既有 *.db/*.db-wal/*.db-shm/*.sqlite/*.sqlite3 共同覆盖默认数据库文件及辅助文件。"
    }, {
      "path" : "README.md",
      "operation" : "MODIFY",
      "responsibility" : "重写为交付文档：架构、配置表（okx.sync.* 与 OKX_KLINE_SQLITE_PATH 环境变量）、SQLite 默认路径、启动、离线 mvn test、live smoke 命令、API 契约（/api/klines 与 /api/sync/health 五字段，及状态不可用时的 503 SYNC_STATE_UNAVAILABLE 响应；last_attempt_at 的『最近一次已入账尝试』语义）、数据新鲜度语义（最近成功同步时间≠行情最新时间；confirm=0 行可变；陈旧缓存与连续失败可致滞后超过一个轮询周期且无固定上界；闭合行可被后续合法响应修正）、klinecharts 9.8.0 版本与 timestamp 毫秒依据（v9 官方 data-source 文档与 quick-start 示例 1587660060000，操作者已核实）、已知限制；全文不出现『最多滞后一个轮询周期』『timestamp 为秒』『首日复核』表述。"
    }, {
      "path" : "pom.xml",
      "operation" : "CREATE",
      "responsibility" : "Spring Boot 4.1.0 parent + Java 25；依赖 spring-boot-starter-web、com.baomidou:mybatis-plus-spring-boot4-starter:3.5.17、org.flywaydb:flyway-core、org.xerial:sqlite-jdbc；测试依赖 spring-boot-starter-test 与 com.microsoft.playwright:playwright；surefire 默认排除 **/smoke/**，live-smoke profile 覆盖排除并仅执行 OkxLiveSmokeIT。"
    }, {
      "path" : "scripts/live-smoke.sh",
      "operation" : "CREATE",
      "responsibility" : "显式 opt-in 包装：mvn -Plive-smoke test，仅触发直连 OKX 官方公开接口的只读 smoke，不进入默认测试。"
    }, {
      "path" : "scripts/verify-workspace.sh",
      "operation" : "MODIFY",
      "responsibility" : "将基线断言 test ! -e pom.xml、test ! -d src 翻转为 test -f pom.xml、test -d src；保留 Java 25 校验、清洁树校验与只读 OKX 探测。"
    }, {
      "path" : "src/main/java/com/example/okxkline/OkxKlineApplication.java",
      "operation" : "CREATE",
      "responsibility" : "@SpringBootApplication 启动类 + @MapperScan(com.example.okxkline.mapper)；main 中仅为配置的 SQLite 路径创建父目录（初始化准备，不含任何建表逻辑）。"
    }, {
      "path" : "src/main/java/com/example/okxkline/config/OkxSyncProperties.java",
      "operation" : "CREATE",
      "responsibility" : "@ConfigurationProperties(okx.sync)：base-url、connect-timeout、read-timeout(Duration)、enabled、fixed-delay(Duration)，经宽松绑定支持环境变量覆盖；limit 固定常量 300，不配置化。"
    }, {
      "path" : "src/main/java/com/example/okxkline/domain/Kline.java",
      "operation" : "CREATE",
      "responsibility" : "纯领域 record：instrument、bar、openTime(long 毫秒)、open/high/low/close/volume/volumeCcy/volumeCcyQuote(BigDecimal)、confirmed(boolean)；服务层与 API 的 decimal-safe 权威载体。"
    }, {
      "path" : "src/main/java/com/example/okxkline/okx/OkxMarketClient.java",
      "operation" : "CREATE",
      "responsibility" : "构造式注入 OkxSyncProperties，基于 RestClient+JDK HttpClient 按 base-url/超时构建；GET /api/v5/market/candles?instId=BTC-USDT&bar=1m&limit=300；校验 HTTP 状态与 envelope（code=='0'、data 数组）并委托 CandleRowParser；失败抛出带分类枚举（TIMEOUT/HTTP_STATUS/RATE_LIMITED/BUSINESS_ERROR/MALFORMED_RESPONSE/MALFORMED_ROW）的 OkxCallException，异常消息即受控摘要（分类标签+可选数字片段，绝不包含外部 msg 或响应正文）；无 Spring 注解依赖，可被 live smoke 直接构造。"
    }, {
      "path" : "src/main/java/com/example/okxkline/okx/CandleRowParser.java",
      "operation" : "CREATE",
      "responsibility" : "纯静态行解析器：校验 9 字段、数值可解析 BigDecimal、ts 为正整数毫秒、confirm∈{0,1}；仅做格式校验，明确不做价格大小/方向等行情语义校验；输出 domain Kline。"
    }, {
      "path" : "src/main/java/com/example/okxkline/entity/KlineEntity.java",
      "operation" : "CREATE",
      "responsibility" : "MyBatis-Plus @TableName(kline) 实体；价格/成交量以 String 承载 TEXT 列（存储编码），confirm 为 Integer 0/1，openTime 为 Long；提供与 domain Kline 的双向转换（toPlainString/BigDecimal 构造），全链路无 float/double。"
    }, {
      "path" : "src/main/java/com/example/okxkline/mapper/KlineMapper.java",
      "operation" : "CREATE",
      "responsibility" : "extends BaseMapper<KlineEntity>；核心单条 @Insert upsert：ON CONFLICT(instrument,bar,open_time) DO UPDATE SET 全部行情字段=excluded.对应字段、confirm=excluded.confirm、updated_at=excluded.updated_at WHERE excluded.confirm=1 OR kline.confirm=0；另有 @Select 以子查询取最近 N 行并按 open_time 升序返回；无任何删除语句。"
    }, {
      "path" : "src/main/java/com/example/okxkline/mapper/SyncStateMapper.java",
      "operation" : "CREATE",
      "responsibility" : "MyBatis 注解 mapper：读取 id=1 单行状态；markSuccess 单语句 UPDATE（state=OK、last_attempt_at、last_success_at、错误字段置 NULL）——仅在 KlineSyncService 行情短事务内被调用、随整批原子提交或回滚，绝不独立提交；markFailure 单语句 UPDATE（state=DEGRADED、last_attempt_at、last_error_category、last_error_summary，不动 last_success_at）——仅在行情轮回滚完成后以 REQUIRES_NEW 独立短事务执行；applyStartupState 单语句 UPDATE 仅 state 列（启动转换专用，保留全部历史字段）；无删除。"
    }, {
      "path" : "src/main/java/com/example/okxkline/sync/KlineSyncService.java",
      "operation" : "CREATE",
      "responsibility" : "同步引擎，全部入口共用启动门与守卫：启动回填监听（ApplicationReadyEvent，@Order 晚于 SyncStateService 启动转换）与 @Scheduled(fixedDelayString/initialDelayString 取 okx.sync.fixed-delay) 轮询第一步 awaitStartupReady（阻塞至启动转换成功提交；启动失败则应用已 fail-fast 退出，不存在无界等待），门检查先于守卫获取；enabled=false 时入口直接返回（零 OKX 请求、零状态写）；AtomicBoolean 在飞守卫 try/finally 必释放；syncOnce：获守卫后 attemptAt/successAt 仅存内存（可注入 Clock）→HTTP 获取与 envelope+全部行校验（零 DB 写）→成功路径唯一短事务内逐行 upsert 并调用 markSuccess（REQUIRED 传播加入同一事务，随整批原子提交，绝不独立提交；事务不含 HTTP）→任何失败（含提交阶段）整轮回滚后调用 markFailure（REQUIRES_NEW 独立短事务）记录 DEGRADED，其自身 DB 异常由 SyncStateService 有界处理（WARN+置位不可用标志，不外抛、不谎称持久化）；日志仅记状态/耗时/行数/错误分类，不打响应正文。"
    }, {
      "path" : "src/main/java/com/example/okxkline/sync/SyncStateService.java",
      "operation" : "CREATE",
      "responsibility" : "同步生命周期与状态事实 owner，减法后的单一权威模型：sync_state 持久化单行是唯一事实源，内存仅一个易失布尔标志（无内容镜像、无墙上时钟比较、无双源合并）。markSuccess 以 REQUIRED 传播加入行情事务、绝不独立提交；markFailure 以 REQUIRES_NEW 独立短事务仅在回滚完成后执行，其自身 DB 异常仅输出固定文本有界 WARN（STATE_WRITE_FAILED+分类+异常类名）并置位标志，不声称已持久化；任一后续状态写提交成功（成功事务确认或后续 markFailure 成功）即清除标志，OK 状态唯一恢复路径是成功事务提交确认。applyStartupMode 于 ApplicationReadyEvent（@Order 先于回填入口）按 enabled 转换（false→state=DISABLED；true 且持久化为 DISABLED→PENDING），仅改 state 列保留全部历史字段，成功提交后释放 startupReady 门，读/写失败异常传播使启动 fail-fast 失败。healthView：标志置位或持久化行读取失败→抛 SyncStateUnavailableException→503 有界不可用，不以旧 OK 或未持久化 DEGRADED 冒充；否则返回持久化行视图（state/lastAttemptAt/lastSuccessAt/lastErrorCategory/lastErrorSummary）。composeSummary 固定分类+受控字段（外部 msg 一律不进入），sanitize（剔除控制/零宽/BIDI 字符、≤200 截断）应用于写库/health JSON/日志三个 sink。"
    }, {
      "path" : "src/main/java/com/example/okxkline/web/MarketController.java",
      "operation" : "CREATE",
      "responsibility" : "GET /api/klines?limit=（缺省 300；非正整数或>300 抛 InvalidLimitException→400）：经 mapper 取最近 N 行升序，映射为显式嵌套 KlineResponse record（openTime/open/high/low/close/volume/volumeCcy/volumeCcyQuote/confirmed，BigDecimal 以 plain 数值输出），不直接序列化 domain Kline；GET /api/sync/health 返回 state/lastAttemptAt/lastSuccessAt/lastErrorCategory/lastErrorSummary，状态不可用时由 GlobalExceptionHandler 返回 503 有界错误。"
    }, {
      "path" : "src/main/java/com/example/okxkline/web/GlobalExceptionHandler.java",
      "operation" : "CREATE",
      "responsibility" : "@RestControllerAdvice：InvalidLimitException 与参数类型错误→400 明确客户端错误 JSON；SyncStateUnavailableException→503 有界错误（SYNC_STATE_UNAVAILABLE，固定中文说明，不含任何外部文本）；未捕获异常→500 通用信息，不泄漏内部细节。"
    }, {
      "path" : "src/main/resources/application.yml",
      "operation" : "CREATE",
      "responsibility" : "声明式配置：datasource url jdbc:sqlite:${OKX_KLINE_SQLITE_PATH:./data/okx-kline.db}、Hikari maximum-pool-size=1、journal_mode=WAL、busy_timeout=5000；flyway classpath:db/migration；jackson write-bigdecimal-as-plain；spring.task.scheduling.pool.size=1；okx.sync 默认值（www.okx.com、connect 5s、read 10s、enabled=true、fixed-delay 15s）。"
    }, {
      "path" : "src/main/resources/db/migration/V1__init_kline_schema.sql",
      "operation" : "CREATE",
      "responsibility" : "Flyway V1：kline 表（UNIQUE(instrument,bar,open_time)、confirm CHECK 0/1、价格/成交量 TEXT、open_time INTEGER 毫秒、updated_at TEXT）与 sync_state 单行表（state CHECK IN (PENDING,OK,DEGRADED,DISABLED)、attempt/success 时间与错误字段），并种子 INSERT id=1 PENDING 行；唯一且版本化的建表入口。"
    }, {
      "path" : "src/main/resources/static/index.html",
      "operation" : "CREATE",
      "responsibility" : "首屏即行情页：仅引用本地 /vendor/klinecharts.min.js、/app.js、/styles.css（零外链）；头部信息区（BTC / USDT、1m、最新收盘价、最近闭合 K 线时间=行情时间字段、同步状态与最近成功刷新时间=独立的同步时间字段）、图表容器、无数据/降级/错误状态区；状态区仅渲染受控摘要文本，健康 503 视为状态不可用而非数据删除。"
    }, {
      "path" : "src/main/resources/static/app.js",
      "operation" : "CREATE",
      "responsibility" : "加载即拉取 /api/klines 与 /api/sync/health；单点适配 toChartData：{timestamp: openTime, open, high, low, close, volume}——KLineCharts 9.8.0（v9 线）KLineData.timestamp 为 Unix 毫秒（v9 官方文档 https://v9.klinecharts.com/guide/data-source 与 quick-start 示例 1587660060000 已由操作者核实），openTime 毫秒直传、不做任何除以 1000 的换算、不存在转换函数；applyNewData 渲染蜡烛+VOL 成交量；setInterval 10 秒刷新不整页重载；失败或健康 503 不可用时保留旧图并显示『数据可能滞后，最后成功同步于…』；最新收盘价取最后一行 close、最近闭合 K 线时间取最后一行 confirmed=true 的 openTime（行情时间与最近成功刷新时间分开标注）；空数据与首次加载提示；全部请求相对路径仅指向本应用。"
    }, {
      "path" : "src/main/resources/static/styles.css",
      "operation" : "CREATE",
      "responsibility" : "桌面/移动响应式布局，信息区、状态区、图表不重叠；克重的 degraded 状态样式。"
    }, {
      "path" : "src/main/resources/static/vendor/klinecharts.min.js",
      "operation" : "CREATE",
      "responsibility" : "构建期固定的 KLineCharts 9.8.0 UMD 产物（版本、timestamp 毫秒依据与获取方式记录于 README），运行时零 CDN。"
    }, {
      "path" : "src/test/java/com/example/okxkline/okx/CandleRowParserTest.java",
      "operation" : "CREATE",
      "responsibility" : "纯单测：合法行、BigDecimal 精确保持、ts 毫秒语义、confirm 0/1；非法：字段数≠9、非数值、非正 ts、confirm 越界、空字段。"
    }, {
      "path" : "src/test/java/com/example/okxkline/okx/OkxMarketClientTest.java",
      "operation" : "CREATE",
      "responsibility" : "经 StubOkxServer：正常 envelope、code≠0、非 2xx、429、非法 JSON、读超时（挂起不回包）；断言查询参数 instId=BTC-USDT&bar=1m&limit=300 与异常分类；恶意响应用例（msg 含 U+0000、ANSI 转义、HTML 片段、超长文本，及非数字 code）断言异常消息仅为受控摘要、不含原始 msg、控制字符或 HTML。"
    }, {
      "path" : "src/test/java/com/example/okxkline/sync/KlineSyncServiceTest.java",
      "operation" : "CREATE",
      "responsibility" : "真实上下文（stub OKX+临时 SQLite+长 fixed-delay 隔离调度，注入 Clock 固定/步进时间）覆盖：启动回填（300 行 fixture 断言由启动流程触发的请求与升序入库）；倒序/乱序输入；重复同步幂等；confirm 0→1 原位更新；闭合行 1→1 更新被接受、1→0 不回退不覆盖且无删除；陈旧响应不删较新行；非法 envelope/行整轮零写入+DEGRADED+无立即重试（请求计数静止）；同步失败后已有数据保留；AR-1a 原子性三用例：成功事务第 N 行 upsert 注入失败、markSuccess UPDATE 注入失败、beforeCommit 提交阶段注入失败（TransactionSynchronization 建模），均断言行情与成功标记共同回滚（零写入/原值保留）、状态不为 OK、历史 last_success_at 保留、随后 DEGRADED/DATABASE 入账；AR-1b 序列：markFailure 写失败→WARN+健康 503 不可用+标志置位+守卫已释放（紧接手动 syncOnce 立即执行）→下一轮成功提交→标志清除健康=OK→再注入行读取失败→健康 503（无内存内容可回退，旧失败不复活）；固定 Clock 构造相同 attempt 时间戳两轮证明无时间戳比较路径；写失败后重启新上下文读同一 DB→健康=最后持久化行；AR-2 启动边界：@TestConfiguration 以 CountDownLatch 阻塞启动转换+极短 fixed-delay 断言转换完成前零 OKX 请求、释放后恢复；启动转换 mapper 读/写注入异常→上下文启动失败+零请求；enabled=false 首启（stub 计数=0、health=DISABLED）、成功一轮后禁用重启（历史时间保留、计数不增）、重新启用（不再显示 DISABLED、请求恢复增长）。"
    }, {
      "path" : "src/test/java/com/example/okxkline/sync/SchedulerOverlapTest.java",
      "operation" : "CREATE",
      "responsibility" : "fixed-delay 约 1s + stub 响应约 5s：断言并发峰值=1、请求串行；另以两线程并发调 syncOnce 验证在飞守卫跳过（含 markFailure 状态写失败分支后守卫仍释放）。"
    }, {
      "path" : "src/test/java/com/example/okxkline/persistence/KlinePersistenceTest.java",
      "operation" : "CREATE",
      "responsibility" : "临时 SQLite 文件库：MP BaseMapper.insert 重复键证明唯一约束生效（cannot-bypass）、upsert 幂等；关闭上下文后以新上下文复用同一文件模拟重启，已闭合 K 线仍可查询。"
    }, {
      "path" : "src/test/java/com/example/okxkline/web/MarketControllerTest.java",
      "operation" : "CREATE",
      "responsibility" : "MockMvc+真实库：升序与 300 上限；limit 缺省与 0/301/abc→400 明确信息；/api/klines 逐字段断言（openTime/open/high/low/close/volume/volumeCcy/volumeCcyQuote/confirmed）；health 五字段完整性（state/lastAttemptAt/lastSuccessAt/lastErrorCategory/lastErrorSummary）；状态不可用（标志置位或行读取失败注入）→503 SYNC_STATE_UNAVAILABLE 有界响应；同步失败后 klines 仍返回既有数据。"
    }, {
      "path" : "src/test/java/com/example/okxkline/web/ChartPageDomTest.java",
      "operation" : "CREATE",
      "responsibility" : "Playwright 加载随机端口应用（OKX 指向 stub+冻结 fixture）：捕获真实 /api/klines 响应；悬停图表触发内置 tooltip，断言时间串（期望值由测试独立依据 fixture 的 Unix 毫秒 openTime 推导——OKX 契约 ts 即毫秒——不复用生产映射代码，亦无转换函数可复用，单位错误必然渲染 1970 年代坐标而失败）、开/高/低/收与成交量数值串与 fixture 一致（证明图表真实消费本应用后端数据且毫秒直传正确）；等待第二个 /api/klines 请求（默认 10 秒周期）断言无主框架导航且 stub 更新数据后图表与最新价随之变化；断言全部网络请求仅命中本应用源（无 okx.com、无 stub 直连、无 CDN）；stub 切换 500 后页面保留旧图且不出现 stub 原文。"
    }, {
      "path" : "src/test/java/com/example/okxkline/support/StubOkxServer.java",
      "operation" : "CREATE",
      "responsibility" : "基于 JDK com.sun.net.httpserver 的契约一致 stub：可编程状态码/正文/延迟/挂起不回包；记录请求与并发计数；支持运行中切换响应序列；零新增依赖。"
    }, {
      "path" : "src/test/java/com/example/okxkline/smoke/OkxLiveSmokeIT.java",
      "operation" : "CREATE",
      "responsibility" : "仅 -Plive-smoke 执行：直接构造 OkxMarketClient（默认 www.okx.com 配置）发起一次只读 GET，断言至少一根结构合法（9 字段、confirm∈{0,1}、开盘时间对齐分钟）的 BTC-USDT 1m K 线。"
    }, {
      "path" : "src/test/resources/fixtures/okx/candles-normal.json",
      "operation" : "CREATE",
      "responsibility" : "冻结 300 行 BTC-USDT 1m 响应（ts 新→旧、分钟对齐，最新一根 confirm=0 其余 1），驱动启动回填与幂等用例，并作为 ChartPageDomTest 的时间/价格/成交量断言基准（ts 为 Unix 毫秒）。"
    }, {
      "path" : "src/test/resources/fixtures/okx/candles-reversed.json",
      "operation" : "CREATE",
      "responsibility" : "冻结倒序/乱序小集合（含早于本地已有数据的键），驱动乱序与陈旧响应不删较新数据用例。"
    }, {
      "path" : "src/test/resources/fixtures/okx/candles-unconfirmed.json",
      "operation" : "CREATE",
      "responsibility" : "冻结同一 open_time 的 confirm=0 与 confirm=1 两个响应版本，驱动未闭合 K 线原位更新用例。"
    }, {
      "path" : "src/test/resources/fixtures/okx/candles-malformed-row.json",
      "operation" : "CREATE",
      "responsibility" : "code=0 但含非法行（字段数/数值/confirm 越界其一）的冻结响应，驱动整轮拒绝零写入用例。"
    }, {
      "path" : "src/test/resources/fixtures/okx/envelope-error.json",
      "operation" : "CREATE",
      "responsibility" : "code≠0 的业务错误冻结响应，msg 填充控制字符、HTML 片段与超长文本（非数字 code 变体由 stub 内联正文构造），同时驱动 BUSINESS_ERROR 与 AR-4 安全摘要用例。"
    } ]
  },
  "stage_outcome" : "READY",
  "stage_outcome_source" : "declared_status",
  "evidence_state" : {
    "blocking" : false,
    "evidence_count" : 21,
    "missing_evidence_count" : 0,
    "must_fix_count" : 0,
    "acceptance_note_count" : 0,
    "open_risk_count" : 4
  },
  "authored_context" : {
    "ref" : ".agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789170793937/prompts/design-claude.md.manifest.json",
    "sha256" : "5c80b3b3f19c7d30739dc50cc2b2d8cce9ddae616f5cbe670646c445dd88f175"
  }
}


## Active obligations

[ {
  "id" : "sha256:4bea5bad54ccaf0660f1980bc23e591a0836984ea6de734028d668261aae7210#must_fix:0",
  "source" : {
    "ref" : ".agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789170046878/outputs/design-codex-review.contract.json",
    "sha256" : "4bea5bad54ccaf0660f1980bc23e591a0836984ea6de734028d668261aae7210"
  },
  "text" : "AR-1：明确行情提交与成功状态的事务边界。优先让行情 upsert 和成功状态在同一短事务提交，避免 recordSuccess 失败后把已提交行情描述为失败轮零写入。另明确尝试记录、失败记录自身遇到数据库异常时的有界处理、健康接口结果及守卫释放；数据库不可写时不得声称已持久化 DEGRADED。补充状态写入失败、提交失败与重启读取的故障测试。"
}, {
  "id" : "sha256:4bea5bad54ccaf0660f1980bc23e591a0836984ea6de734028d668261aae7210#must_fix:1",
  "source" : {
    "ref" : ".agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789170046878/outputs/design-codex-review.contract.json",
    "sha256" : "4bea5bad54ccaf0660f1980bc23e591a0836984ea6de734028d668261aae7210"
  },
  "text" : "AR-2：由 SyncStateService 明确拥有启动时 enabled 配置对应的状态转换。禁用时不得继续返回旧 OK 或种子 PENDING；重新启用时不得沿用旧 DISABLED。保留历史尝试、成功和失败信息，并测试首次禁用、成功后禁用重启及重新启用，确认禁用期间零 OKX 请求。"
}, {
  "id" : "sha256:4bea5bad54ccaf0660f1980bc23e591a0836984ea6de734028d668261aae7210#must_fix:2",
  "source" : {
    "ref" : ".agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789170046878/outputs/design-codex-review.contract.json",
    "sha256" : "4bea5bad54ccaf0660f1980bc23e591a0836984ea6de734028d668261aae7210"
  },
  "text" : "AR-3：统一 API 的公开字段与 domain Kline 序列化结果，并明确 app.js 的 OHLCV 和时间适配。核实固定图表版本的 timestamp 单位后决定是否转换；ChartPageDomTest 须通过真实 API 响应核对图表实际消费的时间、价格、成交量及后续刷新结果，不能仅证明 canvas 存在与请求发生。"
}, {
  "id" : "sha256:4bea5bad54ccaf0660f1980bc23e591a0836984ea6de734028d668261aae7210#must_fix:3",
  "source" : {
    "ref" : ".agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789170046878/outputs/design-codex-review.contract.json",
    "sha256" : "4bea5bad54ccaf0660f1980bc23e591a0836984ea6de734028d668261aae7210"
  },
  "text" : "AR-4：将失败摘要收敛为固定分类与受控字段，或定义可验证的外部文本清理规则；仅截断业务 msg 不足以支持安全摘要声明。补充包含控制字符和敏感样式文本的错误响应测试，验证健康 JSON、页面和日志不暴露未经处理的内容。"
}, {
  "id" : "sha256:4bea5bad54ccaf0660f1980bc23e591a0836984ea6de734028d668261aae7210#must_fix:4",
  "source" : {
    "ref" : ".agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789170046878/outputs/design-codex-review.contract.json",
    "sha256" : "4bea5bad54ccaf0660f1980bc23e591a0836984ea6de734028d668261aae7210"
  },
  "text" : "AR-5：修正 README 与前端的新鲜度说明，不得承诺最多滞后一个轮询周期。明确最近成功同步时间不等于行情最新时间，陈旧缓存和连续失败可能导致更长滞后；展示继续使用真实行情时间与最近成功时间。"
}, {
  "id" : "sha256:a93bafaa80b5336b0cf7d6b463798e868a9d0bb03e4fd24c8b28fc537b542305#human-note:0",
  "source" : {
    "ref" : ".agent/workstreams/ws-20260911T231749Z-GCTW/gates/gate-node-gate-human_design_approval-023.json",
    "sha256" : "a93bafaa80b5336b0cf7d6b463798e868a9d0bb03e4fd24c8b28fc537b542305"
  },
  "text" : "接受AR-1至AR-5，保持当前批准TaskContract和37项路径不变，一次性同步checks/evidence/planned_files/测试描述。AR-1：删除每轮解析前recordAttempt写库；获锁后attemptAt仅在内存，HTTP获取及整个envelope和所有行校验完成后，在同一短事务内提交整批upsert与attempt/success/OK状态。失败回滚后再用独立短事务记录attempt/DEGRADED；状态写入本身失败时仅有界报告，不谎称持久化成功，finally释放守卫。补完整故障断言。AR-2：明确SyncStateService的启动enabled/disabled转换，保留历史时间，禁用零请求，覆盖禁用/重启/重新启用。AR-3：统一实际JSON字段与图表OHLCV映射，依据固定图表版本的真实接口确定timestamp单位，不臆测毫秒转秒；浏览器测试核对真实API数据、图表实际数据与刷新，不以canvas存在替代。AR-4：错误摘要使用固定分类与受控字段，不透传或仅截断外部msg；覆盖控制字符与敏感样式响应。AR-5：删除最多滞后一轮的承诺，分别展示行情时间与最近成功时间。其余已闭合的confirm更新规则、schema、scope保持不变，不新增框架、文件或平台。"
}, {
  "id" : "sha256:f9626a2e8bddac7e32d12b016553cc1e0e89e7d546b29245090b756ec0c6413b#must_fix:0",
  "source" : {
    "ref" : ".agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789170644963/outputs/design-codex-review.contract.json",
    "sha256" : "f9626a2e8bddac7e32d12b016553cc1e0e89e7d546b29245090b756ec0c6413b"
  },
  "text" : "AR-1a：统一 checks、evidence 和 planned_files：markSuccess 必须参与行情所在事务，不得独立提交；只有回滚完成后的 markFailure 使用独立短事务。明确成功状态写入失败及提交阶段失败的测试，断言行情和本轮成功标记共同回滚、历史成功信息保留，不能以行中途异常替代所有提交边界验证。"
}, {
  "id" : "sha256:f9626a2e8bddac7e32d12b016553cc1e0e89e7d546b29245090b756ec0c6413b#must_fix:1",
  "source" : {
    "ref" : ".agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789170644963/outputs/design-codex-review.contract.json",
    "sha256" : "f9626a2e8bddac7e32d12b016553cc1e0e89e7d546b29245090b756ec0c6413b"
  },
  "text" : "AR-1b：简化并补齐易失状态的生命周期。不要依靠墙上时钟比较决定状态权威；由 SyncStateService 在串行轮次完成和事务提交确认后明确发布、替换或清除未持久化失败，并定义健康读取的同步规则。也可删除双源合并，采用明确的不可用响应。补充失败写入失败→下一轮成功→数据库读取失败、相同时间戳及重启用例，证明旧失败不能重新覆盖已确认成功。"
}, {
  "id" : "sha256:f9626a2e8bddac7e32d12b016553cc1e0e89e7d546b29245090b756ec0c6413b#must_fix:2",
  "source" : {
    "ref" : ".agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789170644963/outputs/design-codex-review.contract.json",
    "sha256" : "f9626a2e8bddac7e32d12b016553cc1e0e89e7d546b29245090b756ec0c6413b"
  },
  "text" : "AR-2：明确启动模式转换与所有 syncOnce 入口的共同执行边界，不能仅用两个事件监听器的排序代表调度入口已受约束。定义启动状态读写失败时是停止启动还是提供明确不可用状态，并阻止错误状态下继续执行依赖该转换的同步。补充启动转换延迟或失败与定时入口竞争的测试，保持禁用零请求和历史字段保留。"
}, {
  "id" : "sha256:f9626a2e8bddac7e32d12b016553cc1e0e89e7d546b29245090b756ec0c6413b#must_fix:3",
  "source" : {
    "ref" : ".agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789170644963/outputs/design-codex-review.contract.json",
    "sha256" : "f9626a2e8bddac7e32d12b016553cc1e0e89e7d546b29245090b756ec0c6413b"
  },
  "text" : "AR-3：依据固定版本的实际接口确定 timestamp 单位，删除未经核实的“9.x 为秒”断言并同步适配代码设计、README 和测试说明。若当前无法取得依据，保留明确证据缺口，不得声称已解决；图表时间测试的期望值必须独立来自 fixture 的 Unix 毫秒语义，不复用待验证的转换函数。"
}, {
  "id" : "sha256:477d5aabb49db4519e633a6a3cccb27d52f6d593a1de3960fa63ae4be0d99910#human-note:0",
  "source" : {
    "ref" : ".agent/workstreams/ws-20260911T231749Z-GCTW/gates/gate-node-gate-human_design_approval-029.json",
    "sha256" : "477d5aabb49db4519e633a6a3cccb27d52f6d593a1de3960fa63ae4be0d99910"
  },
  "text" : "接受本轮AR-1a/AR-1b/AR-2/AR-3，保持当前合同和37项路径不变，保留已关闭的AR-4/AR-5与confirm规则。采取减法：删除内存行情状态镜像、墙上时钟择新和双源合并；状态持久化或读取失败时明确返回不可用，不以旧OK或未持久化DEGRADED冒充当前成功。成功事务确认后才恢复可用，失败路径finally释放守卫；重启只读持久化事实。markSuccess只参与整批upsert所在事务，绝不独立提交；仅回滚完成后的markFailure用独立短事务。逐字同步checks/evidence/planned_files，补成功状态写入失败和提交失败的原子性断言。启动模式转换必须在任何回填/定时/手动syncOnce入口前完成，未完成不执行同步；初始化读写失败就使启动失败，避免再新增复杂恢复状态，测试覆盖启动延迟/失败与定时入口竞争。图表依据已由操作者核实：KLineCharts v9官方 https://v9.klinecharts.com/guide/data-source 明确timestamp为毫秒，https://v9.klinecharts.com/en-US/guide/quick-start 示例为1587660060000。固定9.8.0，直接传openTime毫秒，不除1000；独立fixture Unix毫秒作为测试期望，禁止复用被测转换函数或继续写首日再猜单位。一次完成所有相应文案与测试设计，不新增文件/框架/平台。"
}, {
  "id" : "sha256:a5dbcdeb191dbf3a4d2e68032be9794b6d9e112c08635f9e2e7c9705b2bea6eb#must_fix:0",
  "source" : {
    "ref" : ".agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789171190662/outputs/design-codex-review.contract.json",
    "sha256" : "a5dbcdeb191dbf3a4d2e68032be9794b6d9e112c08635f9e2e7c9705b2bea6eb"
  },
  "text" : "AR-1a：保留 beforeCommit 用例作为提交前失败测试，删除其等同底层 COMMIT 失败的表述。补充提交调用本身抛异常的测试设计，并明确事务管理器如何完成回滚、释放连接，再进入 markFailure；在单连接池下断言连接可继续使用、行情与本轮成功标记共同回滚、历史成功信息保留。无需增加文件，可在现有测试中通过连接或事务管理器故障注入实现。"
}, {
  "id" : "sha256:a5dbcdeb191dbf3a4d2e68032be9794b6d9e112c08635f9e2e7c9705b2bea6eb#must_fix:1",
  "source" : {
    "ref" : ".agent/workstreams/ws-20260911T231749Z-GCTW/runs/run-1789171190662/outputs/design-codex-review.contract.json",
    "sha256" : "a5dbcdeb191dbf3a4d2e68032be9794b6d9e112c08635f9e2e7c9705b2bea6eb"
  },
  "text" : "AR-2：删除成功条件未满足时的无界阻塞依赖。优先采用启动未就绪时同步入口直接返回、启动转换完成后显式触发回填的非阻塞方案；若保留 CountDownLatch，则必须定义启动失败、上下文关闭和线程中断时等待者的退出行为。测试须先让定时及手动入口进入等待，再令启动转换失败，证明调用结束、零 OKX 请求且无遗留等待线程，不能仅断言启动方法抛异常。"
}, {
  "id" : "sha256:41f08fffbe5deec3cb0e54e6e03236e163f7249ac771bfc2ed8963db5cef46ae#human-note:0",
  "source" : {
    "ref" : ".agent/workstreams/ws-20260911T231749Z-GCTW/gates/gate-node-gate-human_design_approval-035.json",
    "sha256" : "41f08fffbe5deec3cb0e54e6e03236e163f7249ac771bfc2ed8963db5cef46ae"
  },
  "text" : "接受本轮仅两项Must Fix，其余已闭合项全部保持，不扩张合同或37项路径。AR-2直接采用非阻塞减法：删CountDownLatch/awaitStartupReady；所有syncOnce入口发现startupReady=false或enabled=false立即返回。启动转换成功提交后发布ready，再由启动入口显式执行首次回填；启动转换失败使启动失败且ready始终false，不保留等待线程。测试在转换尚未完成/最终失败时调用定时与手动入口，断言及时返回、零HTTP、无阻塞线程。AR-1a保留beforeCommit仅作为提交前异常测试，不再冒充底层COMMIT失败；另在现有测试文件内对实际连接commit调用注入可确定未提交的SQLException，核对当前Spring事务管理器回滚配置和真实清理顺序，确保先rollback/归还连接后才独立markFailure，单连接池仍可用、该轮行情/成功标记未写入、历史成功保留。只采用标准事务管理器与配置，不新增事务框架或文件；测试结论不得外推到已提交后丢失确认等未测故障。checks/evidence/planned_files/revision_responses全文同步，不重复设计其他事项。"
} ]
