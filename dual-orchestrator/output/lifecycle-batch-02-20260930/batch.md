# B02：W05/D08 LiteFlow 生命周期互斥（2026-09-30）

状态：实施/离线验证完成，全模块 1,559 项通过；用户带回 CC PASS，按 §7.1 收口。用户在 B01 CC PASS 收口后要求“进入下一步”；本批为既有 W05 的冻结 D08 实施，不授权提交、真实运行或额外 agent。

## Boundary Review / 依赖与范围

目标 P0；W05 尚未完成。B01 的源码快照保持，开工前内容存于 before-source-text.json / before-source-sha256.json，后续增量单列，不混用 HEAD diff。目的：并发 workstream 的合法路由互不污染，close/异常清理不能破坏另一活动路由；相同/不同 chain id、不同实例共享同一个实际 holder 排他边界。

Definition 的 JSON/WorkflowDefinition 决定合法边；kernel 只计算候选，不写 gate/node 或批准。ledger/Runtime 拥有 Instance 写入与执行授权，原事实/副作用顺序不变。LiteFlowPrototypeKernel 拥有暂存 XML 和 JVM 全局 FlowExecutorHolder 的完整使用周期；Builtin 为显式对照。CLI/ServerLedgerFactory 是产品装配入口，UI/report 不生成内核或路由事实。修复是已冻结的 adapter 资源生命周期实现，不新增模型、服务、锁类型或调度平台。

主链：CLI/server/worker 装配→ledger RoutingEngine→kernel compile/load/execute/clean→候选返回→原 ledger 路由。恢复若调用 advance 也经过同一入口；本批不实现 D06 finish-forward，不能以互斥替代持久化一致性。

**批次范围的依赖理由：**冻结补记明确删除 WorkflowFileLedger 无参/隐式 builtin 构造并让下游共享装配 ledger/同一 D06 读会话。当前 D06 会话尚不存在；不能先用新默认/假 read-only helper 替代该迁移。B02 仅交付独立必要的 holder 生命周期互斥及真实 factory 装配核验；构造/读会话与持久化运行身份继续由 W05 的 D06/D07 联合承接。因此不关闭 D08 整项或 W05，不以拆批规避评审。按 R3 §7.1 仍提交本批完整受影响边界独立判断。

冻结依据：详设 §7.3、freeze-details §8、freeze-review-20260928-r2/review-disposition §LOW-1。保留产品默认 LiteFlow，builtin 只作显式配置/对照。未知配置仍拒绝。只核验仓库实际单 ClassLoader 装配；不声称外部插件/第三方 holder 直访或任意部署拓扑已安全。

## 完整 transition / 失败切面

| 前态/动作 | 按序行为与 owner | 后态/失败解释、下一消费者 |
|---|---|---|
| 已有合法定义/snapshot/signal→advance | 对实际 FlowExecutorHolder.class 互斥；编译、生成独占临时 XML、clean/load、execute、读取候选均处于同一周期 | 成功返回定义导出的候选；在 finally 清 holder/XML 后释放；随后 ledger 才消费候选 |
| 另一 kernel 的 advance / close 到达 | 等待同一 monitor，不能 load/clean 活动 holder 或删除活动规则文件 | 前次退出后才进入；不同 workstream 的文件锁不能替代此 JVM 边界 |
| 无匹配边 / 非法定义 | 无路由返回 / 异常，保持原领域语义 | 不制造 gate/成功边，下一合法调用仍可用 |
| XML 准备 / load / execute 失败 | finally 清本次 holder/临时规则；释放 monitor；原异常传播 | 无候选返回，无伪造成功；后续独立合法路由可以继续；已有 ledger 部分写按原失败处置，D06 未实现 |
| close / 重复 close | 与 advance 同一 monitor；clean 和临时文件清理 | 幂等清理，不产生业务动作；保持既有 close 后可再次 advance 行为，不新加终态协议 |
| 进程中止 / 冷启动 | JVM holder 消失，磁盘规则不是业务 authority | 不从临时 XML 恢复路由；新实例从定义重建；ledger 重启一致性仍待 D06/D07 |

## 实施与验证计划

修改 LiteFlowPrototypeKernel 的共享互斥；使用真实 LiteFlow component 执行中的可控 EdgeCondition barrier，测试中阻塞/异常，不给生产代码添加测试 hook。先在旧代码运行反证：A 已 load 并进入路由时，B.advance/B.close 必须无法进入全局生命周期；旧代码应失败。正向释放后两定义分别与 builtin 对照，防“所有并发都拒绝/死锁”假通过。

兄弟：相同/不同 chain id，两实例/同实例，close/advance/异常/无路由/重复调用，临时文件清理；真实 CLI/server factories 的缺省/显式 builtin/LiteFlow/未知配置；现有 ledger 人工门/重启和 B01 全链回归。记录实际 kernel/holder classloader/code-source 与单 JVM 拓扑；不以进程数量代替互斥证明。

六维：Declared=冻结 D08；Proven=先红后绿＋双 workstream 实际 ledger 路由＋CLI/server 装配＋最终 XML；Consumed=ledger→kernel→候选→原 routing；Enforced before=任何 load/execute/clean 与 XML 生命周期；Failure route=原异常/finally 清理、D06 持久化未决不改判；Cannot-bypass=跨实例 close/路由真实 barrier、异常后正向恢复、两内核候选对照。此处为实施前判据；实际结果及能力边界见末尾。

需要重新选择 kernel/引擎、引入新注入平台或扩大部署安全保证时停止交用户；普通离线验证不另请预算。B02 最终按真实结果自查并提供 CC prompt，不自动调用 CC。

## Review → Reflect → Analyze：范围与自查

- 实際生产改动只在 LiteFlowPrototypeKernel；锁为实际 `FlowExecutorHolder.class` monitor，覆盖 advance 的 compile→规则文件→load→execute→结果读取→finally 清理，close 使用同一 monitor。不是 kernel 实例锁或 workstream 文件锁，无新增持久化 authority。`advanceExclusive` 为 private，load/clean 的私有入口没有另一路公开调用。
- 没有更改业务路由、终态、人门、scope/身份/证据准入、provider 或网络权限；内核仅返回候选。新建测试分别覆盖真实 kernel 的生命周期、双 ledger 的真实候选消费、实际 CLI/server 工厂装配。factory 测试通过反射检查真实装配对象，不用另调 provider 的返回值代替实际工厂证据。
- Sibling Sweep 结果见 sibling-source-index.json：仓库生产源码 holder 直接使用者只有该类；CLI/server 仍经 provider，worker 经既有 runtime 装配。内部无参 ledger 路径仍存在，已明确归入冻结 D06 读会话联合迁移，不称本批已解决。
- 独立的生产 ClassLoader/第三方直接 holder 操作、实际发行 JAR 多 JVM 部署未验证。测试记录的是真实测试 JVM 的 PID/kernel/loader/code-source，证明该装配下两实例共享 holder；不能拿几个进程数量证明互斥，也不据此解除未核验部署拓扑的 Gate 约束。
- 并发反证在原代码 4 tests / 3 failures / 0 errors；失败项分别为并发路由、并发 close、异常时另一路提前进入。先红证据是较早测试版本，最终增加同实例路由和双 workstream 账本消费，不能把新增用例也算作已单独先红。
- 聚焦后为 21 tests / 0 failures / 0 errors / 0 skipped（17 kernel/ledger + CLI 2 + server 2），见 factory-and-concurrency.log。较早 focused.log 为 16 项；factory-test-compile-error.log 为新测试泛型断言编译错误，已纠正且保留日志。全模块最终结果另行绑定源码身份，不混加历次测试数。
- 正常成功、异常释放、无路由/非法输入、关闭后再调用、两个链名/同名不同定义、临时文件消失、跨实例 close 均被覆盖。未单独注入 Files.createTempFile/loadInstance I/O 故障或 holder.clean 内部故障；其锁与 finally 位置仅源码核验，不冒称该故障运行证据。
- 经验：冻结补记而非早期正文决定当前迁移范围；下游 D06 读会话尚未实现时不先造一个便捷默认绕过，完整保留该未竟项。此批未重开 D04 安全沙箱或添加新的恢复协议。

本批完成判据是生命周期互斥及合法路由可用，不是 D08/W05 全项关闭。R3 §7.1 按受影响运行边界与失败处置组织独立评审，不以生产文件少而豁免；完成自查后提供 CC 提示词。

## CC 评审提示词（已完成，保留原提示词）

请独立评审 `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator` 的 B02，遵守 AGENTS.md。入口：`output/lifecycle-batch-02-20260930/batch.md`、同目录 `incremental.patch`、`test-results.json`，主清单 §11；对照冻结 D08 及其构造迁移补记。

自行重建工厂装配→ledger→LiteFlow 加载/路由/清理→候选消费的链，检查并发、close、异常、合法续作与兄弟入口。请判断本批与 D06/D07 的依赖划分是否合理、测试是否能抓住绕过，以及是否虚报 D08/W05 完成。按 Declared by / Proven by / Consumed by / Enforced before / Failure route / Cannot-bypass test 给分级发现、证据与未核验范围，不预设结论。

只读，不改文件，不启动 provider/native 或额外 agent；无需重审无关历史、已冻结 D04 或重复 B01 全量评审。

## 最终结果（CC PASS，见收口登记）

`rtk proxy mvn -o test` 退出 0，8 模块 BUILD SUCCESS，**1,559 tests / 0 failures / 0 errors / 0 skipped，186 suites**。只取本次启动后 XML，与 reactor 汇总一致；测试后全部源码指纹未变。见 [test-results.json](test-results.json)、[reactor.log](reactor.log)、[reports/](reports/)。没有 provider/native 调用、额外 agent、提交或历史恢复。

相对批前 dirty 工作区仅改 1 个生产类、增加 3 个测试类；[incremental.patch](incremental.patch)、[changed-files.json](changed-files.json)固定增量，其他原有源码保持。原代码反证见 [red.log](red.log) / [red-reports/](red-reports/)。受影响图、依赖身份及 topology 观测分别在 sibling-source-index.json、dependency-identity.json 和最终 XML system-out。

Boundary Self Review：PASS（本批受限范围）。全 holder 生命周期互斥在被测装配达到 ENFORCED/PROVEN；合法路由与异常后续作均有实际反证对照。没有给投影新增状态，没有放宽严格身份/范围/批准。D08 整体与 W05 仍未完成，不能以本批解除未知部署拓扑或真实运行 Gate；构造/会话/本地提交与恢复仍按主清单推进。作者自查不是 CC 独立评审。

## 独立评审收口

用户已带回 CC PASS；当前源码与受测快照一致。LOW-1 并入 W05/T10 的 D06 故障测试族，仍未实施/验证，不另开测试轮次；详见 [处置记录](cc-review-disposition.md)。本批收口，D08/W05 整项未关闭，不自动开启 B03、提交或真实运行。
