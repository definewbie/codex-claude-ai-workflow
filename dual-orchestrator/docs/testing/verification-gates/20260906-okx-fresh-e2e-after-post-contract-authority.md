# OKX Fresh E2E After Post-Contract Authority Verification Gate

日期：2026-09-06
状态：`INDEPENDENT REVIEW PASS / USER-AUTHORIZED CONFIG DELTA / BUSINESS NOT STARTED`

## 1. 目标与范围

在 E1-E4 checkpoint 后，用未变更的 OKX 产品需求完成一个 fresh workstream：从 intake 到用户验收、workflow COMPLETED、可审计的 accepted delivery candidate，以及可运行的产品。不是再分别证明四个局部 validator。

本文件的 E1-E4 指 contract qualification、typed design scope、authored revision/approval、plan scope/subtraction；不是历史网络 E1。不重开网络、模型适配、迁移框架或历史样本恢复。继续使用 small workflow，不以 8-call、零 revision 或某个错误必然发生为成功条件。

沿用上一 Gate 的交付边界：止于 effective workspace 中的产品验收及 accepted candidate；不自动 apply、commit、finalize 到原 target checkout。本 Gate PASS 不等于产品已合并，也不证明 D1 apply/finalize 的自然路径。那些外部写入另由用户授权，不为本轮验证扩大范围。

## 2. Verification Gate

- Target: 唯一 fresh workstream 完成需求全文，workflow=`COMPLETED`，§3 产品验收与 §4 authority 消费链同时成立。
- Source of truth: frozen inputs；持久化 workflow current、node/gate/iteration/events、成功 execution、validated sidecar、qualified contract、authored input manifest 及其来源、repository fact binding、Git 实际变更、completion audit、accepted candidate；产品测试报告、live smoke 原始结果和浏览器证据。Markdown 可供人评审业务语义，但不代替 typed decision、批准 identity 或测试结果。
- Trigger precondition: §6 身份一致；Dual 无未提交生产变更，target clean；source 与持久化 effective workspace 的既有 check 通过；reviewed acpx Doctor 通过；相同执行环境的 Claude/Codex minimal health 成功并关闭；新样本无历史合同、diff、revision context。新 JAR 必须含 E1-E4，不得混用旧二进制或 frozen workflow。
- How precondition is checked before run: 按 §5 顺序执行并保存原始输出；任一失败不启动业务 provider。执行前仍须做检查，本文的只读身份复算不是环境健康证明。
- PASS: 完整需求验收通过，全部 human gate 由用户本人决定；§4 每个实际发生的消费者使用当前合法批准来源；终态、completion audit、最终 acceptance 与 candidate 可由持久化链重建；无未处置 blocker、越权写入或 stop-loss。仅 COMPLETED、测试计数或评审正文说“通过”均不足。
- FAIL: 有效运行中 authority 被绕过、已批准约束丢失、未批准候选被消费、实际越界写入未受阻、ledger/工件持久化失败、provider fatal、产品验收不通过或 stop-loss。自然的合法 NEEDS_REVISION/修复 gate 本身不判 FAIL，按声明路径继续；守卫拒绝只证明该次拒绝，不等于产品完成。
- INVALID: 前置 identity/环境失败；旧样本或旧 JAR；手改 ledger/evidence/合同；中途修改需求、workflow、harness、模型环境；autorun；人为制造 violation；失败后修改 fixture/输入原地重跑。不得解释为部分成功。
- Stop-loss: authority 漂移或副作用绕过立即停止；provider fatal/存储失败停止；同一语义 blocker 在无新事实下连续两轮重复，或同一 scope repair 相同 path set 再次失败，停止而非再写更长 note；按持久化 execution 起止时间累计 active provider runtime，达 120 分钟停止，human 等待不计。允许用户合法修订不等于允许无限循环。
- Run artifact freshness: 归档 code/tree/JAR/workflow/task/check/acpx 身份、唯一 workstream/workspace、每次 run/session、provider/model、prompt/manifest/sidecar refs+SHA、人工决定及产品结果；报告列出实际执行次数与未触发路径，不声称覆盖全部生命周期。

## 3. 产品验收：以原需求为准

不再让 operator 预写类名、文件清单或架构方案。intake 从原需求生成合同，用户批准后才成为精确 scope；后续设计不得自行换文件或换已决定语义。需求有歧义时在允许的 human gate 明确处理，不能靠 operator 手改合同或偷增机制。

1. Java 25 下在最终 effective workspace 原样 `mvn test`，记录 Surefire 报告；公网不是默认测试依赖。测试覆盖以 REQUIREMENT.md 为准，不能用“测试全绿”掩盖缺项。
2. 执行产品交付的 opt-in live smoke，记录一次真实 OKX 只读请求的结构验证结果。preflight 的 curl 不替代最终应用 smoke；超时也不能用 stub 冒充通过。
3. 启动最终应用，保存 SQLite 持久化、后端有序数据与同步状态证据；桌面/移动端检查真实蜡烛图及成交量、定时刷新、中文说明与不重叠布局。核对浏览器只请求本应用后端、图表资源本地打包，不能用截图中“像 K 线”代替数据来源证明。
4. 失败保留旧数据、陈旧响应处理、事务边界、重启持久化等不可稳定在公网制造的情况，消费产品的本地 HTTP fixture/临时 SQLite 自动化证据，不人为破坏自然样本。

“中文说明”延续用户先前 README/需求使用中文的约定，不是 REQUIREMENT.md 新增的产品功能或独立失败条件。

测试/应用产生的数据库、日志和构建输出不能进入 accepted product bytes；只使用既有 candidate exclusion 与 Git scope 机制。额外验收不编辑产品源码；若验收发现缺陷，只能走既有非终态人工修订路径。terminal 后发现问题则记 FAIL，不擅自 reopen 或加 rescue。

## 4. 整体消费链与失败切面

| 转移 | 必须核验的事实与下一消费者 | 不满足时 |
|---|---|---|
| fresh intake → product review → compile | 原始需求与 repository facts 进入实际输入；proposal/review 为成功 execution 的有效输出；合同仍须用户批准 | 不能借用旧样本合同、伪造 intake 或绕过用户 gate |
| compile → contract gate → post-contract context/agent | resolved gate 的 source compile 唯一确定 qualified contract；实际 prompt 含该合同正文，不是只留 SHA | 未批准/损坏不得启动后续业务调用；不得回退 stable/latest |
| design 发布 → review → human approval | typed planned files 受 qualified scope/operation 约束；成功 occurrence sidecar、review 与 gate source 一致 | 无效候选不能成为批准设计；Markdown 路径清单不能替代 typed scope |
| 自然 design/plan revise → 新作者 → reviewer/gate | 新 execution 输入含先前有效正文、人工 note 与 active obligations；revision_responses 覆盖当前要求；packet 向人显示来源和回应 | 不用旧候选再评审冒充作者修订；同一问题重复按 stop-loss，不自动推断已解决 |
| plan 发布/批准 → implement/self-test（含自然再入） | 非空 canonical unique edit_files 属于 qualified contract；实际 prompt 消费当前批准设计/plan 与其正文；直接 sidecar/manifest identity 有效 | 不批准或不启动相应下游；canonical plan 文件不提供 authority 或 metrics 豁免 |
| 实际变更 → self-test → acceptance → delivery/terminal | Git 实际 scope 独立于“计划合法”；typed 测试/验收、用户 acceptance source 与不可变 candidate 一致；completion audit ready | 不能靠合法 plan 掩盖越界 bytes，不能靠 final report 掩盖缺证据 |

只检查自然发生的 occurrence，不要求制造 revision、correction、scope violation 或 crash。自然未触发的再入路径明确记 `NOT EXERCISED`，不能把 deterministic 测试外推为本 case 的实证。

写入与恢复边界：发送前 prompt/manifest 写入和读回完成；provider 成功不等于 sidecar 已有效发布；sidecar 存在不等于 source execution/node 已成功；gate resolved 不等于后继路由已完整。每次 human gate 前、terminal 后从磁盘重新读取当前直接消费链，使用既有 resolver/CLI read path 与引用 SHA 核对，不靠进程缓存或目录排序。沿实际到达的节点保留上述状态事实，不新增通用审计工具。

若上述写入间出现部分状态，保留现场并停止；不手改、补写、自动 retry 或恢复旧 ledger。execution retry 与 semantic revise 不混用；本 Gate 不授权事故后的额外 provider retry。正常的带 note human revise 仍由用户决定。冷读证明引用可重建，不冒称多文件事务原子性或 R1 liveness；异常样本不能用另一 fresh 样本补成 PASS。

operator 会话中断后先重读磁盘 current、gate、execution 与已归档身份；不能凭上次终端输出重复 approve/run。若发现仍在执行，只观察；只有完整合法的等待状态才继续既定步骤，部分写入/失败仍按上述规则停止。

## 5. 执行顺序与授权

1. 本 Gate + active plan 先交 CC 独立整体评审，P0/P1 清零后选择性提交文档；本轮只起草，不创建 workstream、不调用 provider。
2. 执行前复算 §6：code checkpoint 为 clean execution HEAD 祖先，其间仅本 Gate/评审/记录等文档；确认已 package 的 JAR SHA、workflow validate 与 Doctor。若需重新 package 导致 JAR 字节变化，先更新冻结身份并核对 code 来源，不能静默换包运行。
3. 在 source checkout 执行原有 `scripts/verify-workspace.sh`。它已经用 curl 直连 OKX 并校验响应，不新增网络评估层。Shadowsocks 为宿主既有环境，保持不变；连接失败按环境前置失败归档，不临时改脚本/代理重跑样本。
4. 用下列统一环境完成 minimal probes 并立即关闭；它们沿用 standing authorization。模型由 Codex 自身有效配置解析，Dual 不传固定 --model、不复制一份模型配置；记录 config 来源及 ACP 实际 model metadata，成功 health 才证明当前调用可用。业务沿用同一 home/config，运行中不静默换模型。当前配置观测为 gpt-6-astra/medium，此值是本轮快照，不是 harness 默认常量。
5. 唯一 fresh workstream 的 start/capture/verify/首段 run 须用户执行授权；使用 `start` 不带 `--run`，通过持久化 workstream→workspace 链定位 effectiveWorkdir，复用既有 workspace preflight capture/verify（两者均 passed）后才启动业务。source check 不能替代该 cwd 检查。
6. 每段停在 human gate，先读当前 packet/typed sources 再由用户本人批准或给 note；不 autorun、不由 CC/本 agent 代批。后续命令一律保持以下三个环境变量，不复制 CLI 中省略环境的裸命令。
7. 到终态完成 §3/§4 核验，关闭测试应用/health sessions，归档结果及按相对路径排序的 SHA manifest。FAIL/INVALID 均不改代码继续当前样本、不创建第二样本；返回原因与下一决策。

```sh
export CODEX_HOME=/Users/kriswang/.codex
export DUAL_ACPX_EXECUTABLE=/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/.tools/acpx-e1/bin/acpx
export DUAL_ACPX_COMMAND_TIMEOUT_SECONDS=1200
```

## 6. Frozen Inputs

以下于起草时只读复算；workflow validate 已通过，运行前环境检查/probes 尚未执行。

下列 Dual code HEAD/tree 是代码 checkpoint 的固定参照，不要求文档提交后的 execution HEAD/tree 与其相等；运行时按 §5.2 验证祖先关系、区间仅文档变更及 JAR SHA 不变，并另行记录实际 execution HEAD/tree。

```text
Dual code HEAD: ffe9347f13fa330c08ad76b1630ca140978cbec7
Dual code tree: b307b55c4948a4b2dafd2ee5113f9ad328846a45
E1/E2/E3/E4: 306a555d / a4e38ffe / 1e9723ad / ffe9347f
JAR: dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar
JAR SHA256: 6a28fcce8b94f95f87cd7d39887f716f55a81cfbe1b79977088032af0e9e4a18
Package evidence: E4 checkpoint; 162 XML, 1397/0/0/0 (implementation-side run, not CC full-reactor reproduction)
Workflow: dual-runtime/src/test/resources/dev/dual/runtime/lifecycle/agent-lifecycle.small-plan-human-review-experiment.json
Workflow SHA256: 5aa17a2be27e68f22b9c27e9994f64cfdbcd0002f0574023bfe32d5b69382575
Workflow: 28 nodes / 47 edges / 8 agent_run definitions / 6 gate definitions (not occurrence counts)
Target: /Users/kriswang/work/projects/ai/okx-btc-k-line-demo
Target HEAD: c84807946493a603c789ee40c6a4c615aa846c3b
Target tree: 81520ac179f17c35c7c358c2a02102007cbe7cba
Target status: clean (including untracked)
REQUIREMENT.md SHA256: 9ac1544b1216eca575456e1b4c639b3d716d6eb9b89693991222192a5a2ac33a
scripts/verify-workspace.sh SHA256: c9401ad2933920406ff643fc39f8c4078a3624e537a09264ba0928d00aa28f23
acpx build-info upstream: fd173f04aa1b56f9e3f5ca5190c034ddcae28792
acpx patch SHA256: 41ee5f00c95dc07fc113fbac171270204ca98c842e9fb6838df900f4109e8d2c
acpx policy: claude_execution_policy.v2
```

所有旧样本仅作证据，包括 PNBV、3B2P、HHZL、9BZG 和 WA53；禁止 resume。未跟踪的旧 S2 Gate 与 output/ 不纳入本次提交。产品技术栈/需求不改，不在此 Gate 硬编码下一合同的文件路径。

## 7. 六维与通用性裁定

| 维度 | 全链要求 |
|---|---|
| Declared by | 原需求 + frozen workflow + 用户批准的合同/设计/计划与合法人工决定 |
| Proven by | 成功 execution、sealed sidecar、manifest 来源、gate/iteration/event、Git 和产品验收原始证据 |
| Consumed by | qualification/authored resolver → 实际 prompt → publication → human gate → implementation/self-test → acceptance/completion/delivery |
| Enforced before | 未满足 authority 不发相应 provider 调用/不发布有效候选；实际越界不准通过验收；无有效候选不得交付 |
| Failure route | 前置失真 INVALID；合法 revision 走声明修复；越权/产品失败/写入失败或止损 FAIL，不做样本 rescue |
| Cannot-bypass test | E1-E4 已评审的正常/拒绝/批准期切换/全部再入/冷读/损坏/突变测试提供 deterministic 反证；本 Gate 检查真实消费者和产品，不注入故障、不以单样本宣称穷尽覆盖 |

通用性判断：authority 断言只依赖角色、声明图、批准来源、canonical 路径和 occurrence，不依赖 OKX、Java、表名或文件名；产品项才使用 OKX 需求。已有双业务/一致改名测试支撑通用机制，本自然样本用于证明组合使用有效，不单独证明任意复杂项目均能收敛。

收口分别报告：产品 E2E PASS/FAIL/INVALID；实际观察的 E1-E4 连续性；自然修订是否完成；未触发路径；未合并交付与 R1 等残余。未完成本 Gate 前，维持 `DETERMINISTIC ENFORCED / NATURAL E2E NOT PROVEN`。

## 8. 独立评审收口

用户转交的 CC 评审重申：基线 ffe9347f，P0/P1 无，Execution READY；这是既有只读评审的重申，不算新增独立复跑。P2-1 已以 §6 固定参照标注关闭；四条 P3 仅澄清中文约定、计时来源、intake 消费链与会话中断，不改变产品需求、授权或新增机制。文档提交后进入前置检查；fresh workstream 仍待用户执行授权。

2026-09-06 配置 delta：旧隔离 home 的 gpt-5.4 health 被拒，证据目录 20260906T123313Z-okx-e1-e4 保持不变并判 INVALID，未创建业务样本。用户随后明确要求读取正常 Codex 配置并授权确认后直接继续 E2E，无需再次询问启动权限。本轮仅撤销 operator 命令中的旧 home 选择；生产 transport 未固定模型，也不新增模型解析/兼容层。新 health/业务证据另存新目录，不覆盖旧失败、不增加第二业务样本；human gates 仍由用户本人决定。官方配置来源说明：https://learn.chatgpt.com/docs/config-file/config-basic 。
