# W09 固定 method 与 validation_plan 分工 · 有界实施报告

2026-10-28。本报告供独立 CC 复审，不代替独立判断、预算决定、业务批准或续跑授权。最终全模块凭证见 `test-results.json`，冻结身份见 `release-identity.json`；两者齐备才构成本批发行候选。

批末核验已完成：单轮 clean test **1,819 tests / 210 suites / 0 failures / 0 errors / 0 skipped，exit 0**；659 输入运行前后及打包后一致。独立 CLI/server 冻结、14 项资源与 6 次只读 CLI 检查通过；735 个受保护文件 SHA 全同、保护范围内零新增/删除。状态为 **离线实施完成、发行候选待 CC 复审**，不是运行准入或真实 E2E 结论。

| 独立候选 | SHA-256 |
|---|---|
| CLI | `1c8f563c94541baa464186c96812b26779f168e2470492a75d87962d61ff8c54` |
| server | `3e2fad3fdfc59d705279b263f20c073f98a4827753bb6bd944b0312aa29406e7` |

## 目标与实际改动

推进 P0 主流程：消除模型输入对「固定方法身份」与「逐 AC 验证细节」的职责混淆。固定 `method` 由已登记的 readiness 声明提供；模型按解码后的 JSON 字符串原样引用。例子、断言、命令、报告/代码检查放入原有 `plan.validation_plan`，标注已批 AC ID。

本问题按 BOUNDARY 处置：method 身份失配不是可豁免的格式噪声；本批不把它转为 TRANSIENT，也不增加 correction 或重试次数。原案例是启动前核对停止，不能把这一记录改述成原 JVM 已运行并产生新的失败主因。

仅改两个生产类：`VerificationReadiness` 定义一段共享指令并加入 readiness context；`AgentResultPromptContract` 的 plan 契约渲染复用该段。四个现有测试类补齐实际渲染、合法/拒绝续作和一次格式修正验证。完整增量在 `incremental.patch`（对照本批开工前 dirty 工作区备份，不与 HEAD 旧增量混算）。

没有新增字段、schema、DTO、路由节点、锁、服务、方法解析/自动加盖器、兼容分支或历史迁移。`validateBindings` 与 `sameVerificationBoundary` 所在源码区段保持字节一致（`review-scope.json`）。精确 method/ref/SHA、GOAL/环境/期限/撤回、验收要求、真实退出码要求及 EXECUTION 人批仍由原 owner 强制。

本批是输入契约投影纠正，不是新增语义证明器。`validation_plan` 的说明不得改变已登记 method、executor、environment、evidence kind、detail_owner 或已批验收；自然语言细节的合理性仍须独立复评和验收。程序没有新建自然语言语义比较或命令授权机制。提示正确不等于真实模型必然遵守。

## 控制链与失败边界

实施前完整转换表和写入切面沿受审设计 `../w09-readiness-method-boundary-audit-20261027/assessment.md` §5，摘要和实施边界见 `implementation-boundaries.md`。本批不改变任何 durable write 顺序：

登记事实/事件 → GOAL 上下文与 plan 输入 → R01/claim/资格重核/R02 → 输出严格解析、唯一 outcome 与固定发布/R03/L03 → exact-subject 独立复评 → plan.decision 的 readiness 检查 → EXECUTION 编译与当前人批 → 实现/程序观察/独立验收/交付。

无 readiness 的首份计划仍可走到原等待门登记和返修，未增加提前拒绝而制造死端。有 readiness 时，method 后缀、空格和换行仍被拒；不能靠 validation_plan 补说明而借未批准执行资格。新正常序列证明细节经作者、复评、EXECUTION 批准后进入实现输入；负向序列在原 readiness 门停止，零实现调用。

R01/R03/L03 前缀与冷 JVM 继续消费原固定事实：本地不完整写入走 D06；外部不明走 D07，不能把缺观察解释为未执行。retry/resume/abort、权限 C/X、终态与交付不新增解释。已有常驻写点/冷读/inline-worker/四变体测试的承接范围详见 `verification-coverage.md`，不外推为全部故障穷举。

## 证据分层

以下各层独立，不加总：

1. 先红：旧生产上两项完整提示/context 新断言失败，2 failures、0 errors，见 `red-result.json`、`red-reports/`。证明旧输入缺少职责约束，不宣称旧核心守卫错误。
2. 聚焦：runtime/adapter 四测试类共 70 项，0 failures/errors/skips，见 `focused-result.json`。
3. 旧材料只读对照：读取实际 GOAL、登记方法和当前 plan，在隔离新 fixture 重新生成 GOAL/环境/声明 refs；原五种追加说明方法均被原真实 validator 拒绝，纯登记原文组合通过，validation_plan 保留。原输入 SHA 不变。此为真实文本加隔离身份的消费对照，**不是原案例恢复、批准或续作可行性证明**，见 `historical-readonly-result.json`。
4. 隔离反证：正常类路径两项先通过；分别移除 plan 共享指令、把精确 equals 改成 startsWith，两份隔离副本各命中指定 AssertionFailedError。未修改工作区生产源码；独立于 Maven 计数，见 `counterproof-result.json`。
5. 单轮全模块 clean test：只消费本批唯一完整 reactor 的 XML、退出码及 659 输入前后身份，不挪用旧 1813 绿灯、不拼接聚焦数字，见 `test-results.json`、`reactor-reports/`。
6. 重冻：独立复制 CLI/server 候选，核嵌入模块、应用类/资源、关键资源及只读 CLI 命令；打包使用 skipTests，不把打包当另一轮测试，见 `release-identity.json`、`package-result.json`。

本地生命周期使用真实 Java runtime/ledger 和固定 provider 输出；correction 为 ScriptedTransport。零真实模型/原生客户端调用、零新增子 agent。模型实际遵守、当前原生组合能力、真实 OKX 后半链与交付均未证。

## 保全与预算

开工前保全清单为 `preservation-before.json`，逐条结论见 `preservation-result.json`。范围是当前续作包、当前 ledger（排除锁文件）、原冻结发行和原 Gate 停止文件，不冒称对原 Gate 所有其他准备文件做了完整快照。

workstream `ws-20261026T142203Z-7SNA` 保持 RUNNING，`node-plan-codex_plan_review-025` READY，未产生 EXECUTION 批准。两份原 stop-required 均保留，历史原始输出与选定旧 JAR 不改。新 JAR 只是独立候选，未绑定到当前案例；新旧 frozenRuntimeIdentity 的准入和当前历史状态是否支持新 binary 不在本批冒判。

累计原账：2,034,803 token / 9 请求 / 1,979.9747878720627 活动秒。4M / 18 / 3,600 上限不变；剩余 1,965,197 token / 9 请求 / 约 1,620.025 秒。原额外返修/复评/尾链/修正储备 2,025,000，比 token 余额多 59,803。本批不重置账目、不追加案例执行回执、不改变预算、不清停止记录、不取得续跑资格。

## 六维与成熟度

| 维度 | 本批可证范围 |
|---|---|
| Declared by | 用户有界实施授权、既有 readiness/plan 契约与受审最小设计 |
| Proven by | 完整渲染、真实本地生命周期正反、隔离反证、旧材料对照、单轮全回归和发行字节核验；不含新原生运行 |
| Consumed by | readiness context / plan render → 原严格 parser → 原 readiness 资格检查与 EXECUTION 编译/人批；后续程序观察和独立验收沿原消费者 |
| Enforced before | 原 method/ref/身份/批准检查在执行资格产生与实现 dispatch 前；新指令只是输入投影，不是模型行为硬守卫 |
| Failure route | 原拒绝、补登记/返修/复评/当前人门、D06/D07；无自动放宽方法或借旧批准 |
| Cannot-bypass test | method 后缀/空白拒绝、无声明合法补齐、未批零实现、一次 correction 上限、两隔离反证；写点/冷 JVM/终态承接原常驻族 |

新输入分工达到离线 VALIDATED/PROVEN；原资格边界通过本地正反序列证明。本批没有建立或声称「模型语义必然正确」的新 ENFORCED 边界。作者自查不是独立 CC 评审。

## ROI 与下一步

永久成本止于两处投影共用一段指令。无需自动解析方法、增设结构化验证平台、迁移历史或扩大状态机，未触发设计止损条件。

下一步仅做合并 CC 独立复审（`cc-review-prompt.txt`）。通过后再由用户另定预算与续跑；届时核新发行/原实例身份、最新外部状态、原停止与累计账、当前业务批准及储备，不沿用本批离线测试作为运行授权。本批未提交。
