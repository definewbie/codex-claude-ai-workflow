# 人工处置兜底与证据续作设计 独立架构评审

日期：2026-09-10。评审者：CC（只读，不采信设计自述）。基线与设计稿一致（`331257cf` 工作树）。
未改代码、未调 provider、未恢复 real-case、未启动 agent。

## 0. 结论

设计方向正确、边界自律，**不是 IMPLEMENTATION READY**：两个 P1 设计决定（自测处置的最小
schema 形态；处置→目的地的路由机制）必须在开工前锁定，另有三项可收缩。§10 三项未决项
我给出可依据现有代码锁定的裁定（§4）。修订后无需第二轮整体评审，可直接进 failing tests。

## 1. 代码事实核证（设计 §2 全部属实，另补三项）

| 设计声明 | 核证 |
|---|---|
| SelfTestResult 无处置字段 | ✓ `AgentResultEnvelope.java:223-234`：decision/checksRun/checksSkipped/evidence/missingEvidence/acceptanceNotes/verificationSummary，**无 mustFix** |
| AcceptanceReviewResult 已有 VerificationRemediation | ✓ 存在于 validator/prompt/document/projector 四处（也即：复用它到自测会牵动四个 owner） |
| ReviewDecisionHandler 只提取 decision/source/summary | ✓ evaluate 逐字核对：按 decision 串对照 allowedDecisions 路由，无任何条件/处置提取 |
| SELF_TEST_INCOMPLETE 无条件回 context.implement_claude | ✓ workflow JSON :762 边无 condition；SELF_TEST_COMPLETE → implement.artifact_validation |
| HumanGateActionSemantics 统一动作可见性 | ✓ `dual-core/.../HumanGateActionSemantics.java:13-59`，definition 声明 + scope-repair 收窄 |
| CLI/server 收敛 resolveOpenGate(action,note) | ✓ ReviewDecideCommand:46 / WorkstreamActionService:216 |
| autorun 也调 resolveOpenGate | ✓ AutoRunCommand:175（"auto-resolved from autorun"）——新动作不得进 autorun 的关注成立 |
| JSON 已编译给 LiteFlow | ✓ dual-flow-liteflow 模块（LiteFlowElCompiler / LiteFlowPrototypeKernel / 三个 FlowComponent）；EdgeDefinition 有 `condition` 字段 |
| 自测 implement profile 可写 | ✓ AcpxPermissionProfile.fromName 仅支持 `observe`/`implement` 两档，无"跑测试+写临时证据"的只读档 |

补充核证：SELF_TEST_COMPLETE 后确实经过 `implement.artifact_validation`（git scope 校验在位）；
RepositoryFactBinding 已有 inventoryHead+inventorySha256+pathCount+ArtifactRef 的源树身份实现
（`RepositoryFactBinding.java:30-58`）；既有非 resolving 人工扩展先例——ExecutionNetworkGrantStore
（typed、note 必填、单 pending、claim 一次性）。

## 2. 六项判断

**1）通用而非 BXWV 特判；更小完整方案？**
处置语义（证据缺口/产品修改/环境阻塞/未知）与动作（collect_evidence/submit_evidence/
request_action）均无业务标识；§9.10 的设备测试报告替换检验在位。三个新动作各自承载必要闭环：
collect 是"缺证据重试"，submit 是**唯一不写代码回到验收的人工路径**（砍掉它则"人工处理后
实际回验收"断裂），request 是安全死胡同记录器。**不存在更小的完整方案**；但存在更小的
首增量（见 F4 分相建议）。

**2）人工输入边界；自由文本能否绕过？**
动作面有界（bounded 请求对象、客户端不选后继节点、loader 校验声明动作有实现与出边、
request_action 明确不需出边）。自由文本三层隔离成立：note 不授权限/scope；材料字节是
不可信数据不执行；request 正文是需求记录非动作标识。唯一半开口是"已有环境说明"字段——
当前无 producer/consumer，建议 v1 直接删除（F3），环境授权只留 Phase A grant 一个入口。

**3）流程连续性（contract/model/JSON/LiteFlow/ledger）？**
分层与 bounded assessment §6 一致且组件全部在位。未知/混合/再次失败在 §3 流程树与 §9.5
有出口。**但"处置如何变成目的地"未定**（F2）：是新增 decision 结果串（SELF_TEST_EVIDENCE_*
式），还是 EdgeDefinition.condition 消费 typed 事实？两者都不需要 Java 平行路由表，但改动面
不同（前者动 parser/validator/prompt 的决策枚举，后者要求 kernel 条件能读节点结果事实——
后者我未核证条件事实面，标为未验证）。

**4）§10 三项裁定**：见下节。

**5）transition/失败切面/冷启动/sibling**：§8 表覆盖材料写失败、决定未写、路由失败、
后继创建/current 未完成四切面，处置正确（fail-closed、不重复副作用、node SoT）。冷启动
对非 resolving 请求依赖重读 node——与 SoT 裁定一致。sibling 清单（CLI/server/UI/autorun/
scheduler/三 lifecycle）完备。六维 invariant 声明为 DESIGNED 成熟度，诚实。

**6）平台化/schema 扩张？**
无任意 map/插件/DSL/第二运行时/独立证据平台；ledger 唯一写入者；停止条件枚举了越界形态。
schema 面与不变量成比例——前提是 F1 的收缩被采纳（nested Failure 换平字段）。

## 3. Findings（按严重度）

**F1（P1）SelfTestResult 处置表达不是最小形态，且 VerificationRemediation 复用引入跨结果耦合。**
更小等价：**复用既有 missingEvidence（证据缺口）+ 新增平字段 mustFix（产品修改项，
与 PlanResult/AcceptanceReview 词汇对齐）**，路由规则= mustFix 非空走实现边；mustFix 空
且 missingEvidence 非空走补采边；两者冲突/未知走人工。无需嵌套 Failure、无需把
VerificationRemediation 枚举引入 SelfTestResult——后者会把"NONE-only 约束不放宽"的保证
从"按 result 类型隔离"削弱为"跨类型共享枚举后仍隔离"，设计自己警告过这一点（§4 末段）
却仍选择了共享枚举。事件顺序同批映射的代价两案相同。

**F2（P1）处置→目的地的路由机制未锁定。** 二选一必须定：(a) 新增 decision 结果串
（SELF_TEST_COMPLETE/INCOMPLETE 之外扩证据类结果）——parser/validator/prompt 同批改，
kernel 无需懂处置；(b) EdgeDefinition.condition 消费节点结果 typed 事实——JSON 表达力更强，
但需先核证 LiteFlow edge component 的条件事实面能否读到节点 result（未验证；若不能读则 (b) 不可行）。
(a) 与"JSON 拥有目的地"不矛盾——结果串本身是 typed contract 的一部分。

**F3（P2）"已有环境说明"字段半开放。** 无 producer/consumer、无字段集，违反本设计自己的
有界请求原则。v1 删除；环境授权仅 Phase A grant；将来有真实消费者再按 producer/consumer 锁字段。

**F4（P2）缺分相。** 首增量= F1 平字段 + 路由 + collect_evidence + request_action
（共用 gate 扩展载体，无需材料化链）；第二相= submit_evidence（材料固化/源码身份/自测消费，
是最大新面，值得独立评审）。两相都不砍"人工处理后回验收"的最终闭环。

**F5（P2）材料源码身份的复用点未核证到计算层。** RepositoryFactBinding 的
inventorySha256 覆盖工作树内容还是仅 HEAD——设计自己存疑（§6），我确认了字段存在但未核证
计算。若仅 HEAD，须扩为工作树 bytes 或显式接受"HEAD+dirty 标记"的弱化并如实声明。

**F6（P3）request_action 的 SoT 必须唯一化。** 裁定：gate 节点 result 内 machine-owned
`action_requests[]` 为 SoT，ledger 事件 `action_requested` 为派生审计投影；幂等= 同 id 同内容
返回原记录、同 id 异内容拒绝；写序= 锁内读→校验 source 身份→写 node→append 事件；
node 与事件间崩溃接受投影缺口（node 可重读，事件不可作 authority）。

**F7（P3）CLI 结构化请求面沿用子命令先例。** recovery network 的 show/approve/cancel 三子命令
是有界参数对象的现成形态；不要用单个 `--request <json>` 之类的开放入口。

## 4. §10 三项裁定（依据现有代码锁定）

1. **最小充分表达**：平字段 mustFix + 既有 missingEvidence（F1）。不新增嵌套 Failure，
   不复用 VerificationRemediation 到 SelfTestResult。
2. **载体与幂等**：请求记录=node result machine-owned 数组（SoT）+ 派生事件（F6）；
   材料= workstream 自有不可变目录 + ArtifactRef(ref,sha256) + TaskContractArtifactWriter.sha256
   （既有习惯）；源码身份= RepositoryFactBinding inventory，**先核证其覆盖工作树内容**（F5）
   再锁定；幂等读写序如 F6。
3. **补采写入边界**：现有能力= **post-hoc 可守、pre-hoc 不可守**——SELF_TEST_COMPLETE 后的
   implement.artifact_validation git scope 校验会拒绝补采期间的任何源码改动（边在位）；
   但 profile 只有 observe/implement 两档、E1 sandbox allow_write 覆盖 root，无"可跑测试不可写
   源码"档。锁定口径：如实声明"补采执行与普通自测同信任模型（事后 Git 拒绝），不声称
   事前只读 enforcement"，不为本轮新增 sandbox；设计 §7 的"无证明则强制人工"仅适用于
   环境配置类需求，而该类在 F3 删除后 v1 不存在。

## 5. 最小必要修订清单（全部文档级，改完即可进 failing tests）

1. §4 改为平字段方案并撤回 VerificationRemediation 复用（F1）；
2. §3/§7 锁定路由机制为 (a) 或先核证 (b) 的条件事实面（F2）；
3. §5 删除"已有环境说明"字段（F3）；
4. 增加 F4 分相与各相验收序列（复用 §9，标注第二相）；
5. §6/§10.2 按 F5/F6 锁定载体、幂等与身份复用点；
6. §8 表加一行：材料固化后、gate 决定前，操作者改选 abort——材料成为孤立事实无授权效果
  （现表已含同义切面，仅显式化）。

## 6. Implementation READY 判断

**NOT READY（当前稿）；CONDITIONALLY READY after P1×2。** F1/F2 是设计决定而非代码工作，
修订后不需要第二轮整体架构评审；按 §5 六条修订 + 分相后可直接按 §9 序列写 failing tests。
设计的停止条件（不得删"人工处理后回验收"换局部 READY、不得夹入动作执行器/第二运行时/
插件/证据平台/历史迁移）与我的核证一致，予以维持。

## 7. 20260910 更正（用户驳回三处，经代码复核全部成立）

1. **§4.3"post-hoc 可守"不成立，撤回。** `ChangeScopeEvaluation.evaluate:114-147` 逐行核实：
   路径在 `contract.scope().included()` 且 operation 匹配的改动进 `matchedPaths`、零 violation、
   **PASS**——scope 门只约束路径集与操作类型，不区分改动发生在哪个阶段。补采期间修改已批准
   文件不会被 scope gate 拒绝。修正后的裁定：补采"零改码"现有能力**事前不能阻止（profile 只有
   observe/implement，sandbox allow_write=root）、事后也不能由 scope gate 拒绝（in-scope 改动
   PASS）**。机械上仍可行的最小 enforcement：artifact validation 已经把 `actualChanges` 持久化
   进 change-scope-evaluation——加一条处置规则"**补采上下文的完成若 actualChanges 非空，
   不得作为补采被接受，转产品修改处置**"（用已有 git 事实 + 一条声明边/守卫，非新机制）。
   若设计不采纳该规则，则必须如实降级为"自报 + 人工 diff 复核"信任模型并写明。
2. **F5 的 inventory 复用建议撤回。** `GitTrackedPathInventory.capture:28-36` 核实：
   `sha256 = digest(paths)` 是对 `git ls-files --cached` **路径清单**的摘要，不含文件内容；
   同路径集下任意内容改动 head/sha/pathCount 不变。修正后的裁定：现有代码中**不存在整树
   内容身份**；可复用的只有 per-file 内容身份模式——`RepositoryFactBinding.ArtifactRef`
   （ref+sha256(content)）与 `ChangeBoundaryAuthoritySnapshot.fingerprint`（对声明路径集逐文件
   内容指纹）。材料源码身份的最小诚实方案：对材料声称覆盖的文件集逐文件 ArtifactRef +
   git HEAD + dirty 标记，核验时重算；整树内容身份若需要，须明确列为新增实现而非"复用现有"。
3. **F1 路由表第三行修正：混合=正常，不是冲突。** mustFix 与 missingEvidence 同时非空是
   合法混合问题——按设计 §4 与 bounded assessment §5 的原意（"混合不得伪装成纯补采"），
   **mustFix 非空即走实现修复，missingEvidence 作为上下文携带**（实现后自测自然再验）。
   人工兜底仅留给：两字段皆空但 INCOMPLETE/BLOCKED、或成功信号与分类字段矛盾。
   平字段方案本身不变，仅路由第三行改写。

三处均为我的事实/推断错误而非措辞问题：#1 是把"路径集守卫"误当"改动来源守卫"；
#2 是未核到计算层就写了"复用"；#3 是给正常业务形态造了人工兜底触发。§5 修订清单相应
更新第 1、6 条，其余结论（含 NOT READY 判定与 F2/F3/F4/F6/F7）不受影响。
