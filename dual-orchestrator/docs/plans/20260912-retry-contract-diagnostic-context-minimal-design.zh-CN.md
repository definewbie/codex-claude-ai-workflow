# 人工重试的合同拒绝诊断上下文：最小设计核对

日期：2026-09-12。基线：`8708eb764236762e736f15d5775a5e73075a6e33`。
状态：IMPLEMENTED / INDEPENDENT DIFF REVIEW PASS；采纳CC收缩建议完成两处生产接线，未调用provider。验证范围与结果见[checkpoint](20260912-retry-contract-diagnostic-context-checkpoint.zh-CN.md)，[独立diff review](20260913-retry-contract-diagnostic-context-implementation-review.zh-CN.md)允许选择性提交。
主目标：P0执行恢复输入完整性。不是提升自动化等级，也不承诺模型返工成功率。

## 1. 判断与边界

已有正常修订链不重做。只补一个信息缺口：人工对发布前合同拒绝执行既有retry后，
下一次同节点执行能够看到上一次确定性拒绝原因，并仍经原校验与正常review/gate。

```text
作者输出 → 发布前合同校验拒绝 → 保存失败execution → 等待人工
                                                    │
                                    既有retry，显式操作
                                                    ↓
                            将有界诊断随READY原子保存【新增】
                                                    ↓
                            新run/session + 原任务 + 拒绝诊断【新增】
                                                    ↓
                       原有全部校验 → 合法产物发布 → 正常review/gate
                              └ 拒绝 → 原失败出口，不自动再试
```

这叫“原任务重新执行时携带失败上下文”，不叫新增语义修订机制。它不产生新iteration、
不创建/关闭obligation、不修改批准的合同/设计/计划，不扩大写入范围。模型仍可重新生成
当前节点的完整候选产物，而不是parse correction的逐字段冻结；需要改变已批准决定时，
仍必须走已有正式修订路径，retry note不能授权该改变。

人工retry现有入口没有次数上限，本批也不新增次数限制；每次仍需新的显式操作，不能据此
称整个返工过程“有界”。本设计的有界仅指诊断体积和本次执行的作用范围。retry不消耗
revision预算不等于免费，provider执行仍有token、时间和可能的计费成本。

短期收益：去掉人工重试时重复转述机器诊断的负担。长期价值：恢复上下文可定位、可重放，
且适用于不同任务与Claude/Codex。诊断能否提高真实模型成功率，仍是未验证假设。

## 2. 本轮自行核对的代码事实

下列路径相对于`dual-orchestrator/`；行号为本基线锚点，实施前重新定位。

| 链段 | 代码与事实 |
|---|---|
| 确定性诊断 | `dual-runtime/.../handler/ArtifactContractValidator.java:469-528`：contractViolation把`reason`、`parse_error={code,message,details}`写进失败result；诊断ref另附在result/outputRefs。 |
| 稳定文件会覆盖 | `ContractFailureDiagnosticWriter.java:43-88`以nodeInstanceId命名并直接writeString。同节点第二次失败覆盖同一路径，不是每次执行的不可变证据。 |
| 可直接复用的事实 | 失败node.result已含parse_error；`dual-ledger-file/.../ExecutionLedgerWriter.java:115-153`另将完整result保存进该次execution。KPPZ的`nxe-1789202097872-569e466b.json`可供审计，但retry消费不必重新读取它。 |
| retry丢失诊断 | `NodeStateWriter.java:205-274`保留inputRefs，清空outputRefs和原result，只留下retry状态/note等；写node READY → append事件 → 写current RUNNING → instance投影。 |
| latest不能延迟读取 | `WorkflowFileLedger.java:326-336`在新执行start时立即更新latestExecutionId；handler执行时再读latest会读到自己，不是失败源。KPPZ节点runRef为null，也不能拿它代替失败execution。 |
| inline/worker | `WorkflowRuntime.java:304-340`与`AgentWorker.java:101-145`均start execution后加载core node、执行handler，再统一ArtifactContractValidator。 |
| 可用装配点 | `AcpxAgentTaskHandler.java:155-235`先资格校验，再profile/grant/policy和render；authored/evidence section已有明确装配模式，最终prompt及manifest落盘。 |
| result生命周期 | `NodeStateWriter.markRunning/markQueuedForAgentTask`合并保留result；`RoutingEngine.java:88-106`完成时用新result替换，不继承旧retry数据。 |
| 两端入口 | `RetryCommand.java:31-32`与`WorkstreamActionService.java:85-90`均持workstream锁调用同一ledger.retryActiveNode。autoRetryNode、WebSearch批准是另两条入口，不等于人工普通retry。 |
| 已有诊断展示 | `ContextStateEvidenceBoundaryRenderer.java:169-206`确有按mtime取三份诊断的摘要；不能笼统说“诊断完全无消费者”。它不是本次retry源，不保证重建当前retry prompt，禁止复用该扫描来选源。 |

CC报告中9BZG“相同prompt重试成功”属于进程失败样本，不能证明内容拒绝复犯率；本轮未重跑
CC的统计或provider实验。报告的“唯一缺口”应理解为当前候选范围，不是所有恢复边界均已完备。

## 3. 选用有界内嵌副本，不建立引用校验链

采纳CC F1。初稿只比较了“复制完整失败result”和“引用execution”，遗漏了更小的有界
诊断副本。诊断不是批准证据，不需要套用批准材料的ref/SHA消费链。

在既有node.result加machine-owned `retry_failure_context`，与READY状态同一次原子写入。
字段仅为`schema_version=1`、`diagnostic_text`、`truncated`、`source_run_id`、
`source_execution_id`。正文从锁内已加载失败节点的`parse_error={code,message,details}`
序列化而来，不复制完整result、权限grant、旧候选或会话。source_run_id取旧result.run_id，
source_execution_id取旧latestExecutionId；仅作历史标识，不据其读取任何文件，缺失显式记空。

diagnostic_text最多8 KiB UTF-8，按完整Unicode字符边界截断，truncated显式标记。它是
显示文本，截断后不得当JSON重新解析；标识字段与固定提示不计入该正文上限。8 KiB是本设计
的成本约束，不是已验证最优值。原始完整诊断仍可从历史execution人工审计。

所有权：失败事实由原validator产生；NodeStateWriter只保存本次retry所需派生快照；
adapter仅投影。事件保持原有retry审计用途，不复制正文、不新增事件匹配reader。
快照不是第二个失败分类/批准owner：任何消费者不得用其内容决定路由、权限或合格性。

信任边界：与现有node.json/retry状态同为ledger信任域；不增加针对宿主主动改写账本的
防篡改承诺。删除execution文件不妨碍已有快照消费，但会损失外部审计材料；不再把这说成
强身份绑定。prompt与manifest证明实际发送了什么，不证明模型理解或历史字节未被篡改。

### 3.1 创建资格

只在普通`node_retry_requested`入口、FAILED的`acpx.agent_run`、失败result具有既有
`AGENT_OUTPUT_CONTRACT_VIOLATION`及结构化parse_error时创建。不是按业务名、模型名或
`DESIGN_SCOPE_INVALID`单一错误特判。外部进程错误、BLOCKED、自动retry、WebSearch批准保持原行为。

正常入口还须处于WAITING_FOR_RECOVERY，节点active。对上述合同失败，若current仍RUNNING
或其他状态，视为未闭合前态，在写READY前拒绝；不能先接受不一致前态再声称current检查
能阻断半写。其他失败类型的既有retry不改。parse_error应为含非空code/message及对象details
的结构；应携诊断却形态损坏时零写入拒绝，不静默盲跑。不从retry note构造诊断。

锁内构造快照与READY node，沿用同一次AtomicJsonWriter写入；不读取execution、稳定诊断
或事件文件，不追latest，不新增source hash。上下文与READY的原子性不等于整个retry事务原子性。

### 3.2 唯一消费与装配

在handler资格校验之后、network grant claim与provider/session启动之前，读取当前node
result内的快照，校验版本/字段类型/文本字节上限及当前workflow RUNNING、节点active、
无open gate；不读取历史execution和events。由于合法前态明确为WAITING_FOR_RECOVERY，
而current写在事件之后，普通写中断留下的旧current可由此守卫阻断；不声称能检测任意
人为删事件或修改current。§6须以真实run/worker验证，不假设运行入口不会改变current。

渲染一个`Previous rejected execution` section，包含正文、截断标记和历史run/execution标识，
标注“仅解释上次不合格；当前批准输入与校验器优先；不构成范围或权限授权”。保存到既有
prompt/manifest。不读取标识对应文件、不把其当ArtifactRef、不增加validated inputRefs。
新execution更新latest不影响快照。错误文本可能回显模型内容，按引用数据处理，不把
recovery_hint、retry note或details当可执行指令，不因此丢弃后续合同正文。

快照形态无效或当前流程状态不一致：provider前返回FAILED，复用已有
`REQUIRED_AGENT_EVIDENCE_MISSING` outcome，诊断子码为`RETRY_FAILURE_CONTEXT_INVALID`，
不附recoverable=true，不创建自动恢复分类。这沿用同handler对authored输入无效的处理形态；
§6序列测试必须确认最终停在人工恢复、没有自动重试，不能只验证handler返回值。
无marker的正常执行不添加section；不得“为了补救”自动找最近失败。

新增section不加入validated inputRefs、不进入AuthoredExecutionContext active obligation集合，
不改变AgentResultContract或单次parse/reference correction预算。即使模型无视诊断，候选仍
必须经过现有合同、scope、lineage、source baseline与publication检查；这才是强制边界。

## 4. 完整transition与失败窗口

| 前态/动作 | durable顺序与成功后态 | 中断、拒绝或再次执行 |
|---|---|---|
| current WAITING_FOR_RECOVERY、节点FAILED；人工retry | 锁内从失败node构造有界快照 → node READY与快照同写 → append既有retry事件 → current RUNNING清openGateIds → instance投影 | 快照/前态检查失败零写入。READY与快照不会半分；node/event已写但current未完成仍是流程半写，新增现状守卫拒绝provider。 |
| READY，retry记录完整；run/worker | 既有dispatch/start写新execution → markRunning保留快照 → 形态/现状校验 → prompt/manifest落盘 → provider | 新execution/bootstrap失败仍走既有恢复；不继承旧grant。此阶段可能有执行记录，但诊断守卫失败必须零provider调用。 |
| 新候选返回 | 原有全校验 → finalize metadata → task/execution完成 → routing写node新result → 成功才发布后续路由 | 不新增发布写点。校验拒绝无有效sidecar/reviewer，仍失败；不自动retry。失败execution保存新的诊断。 |
| 再次人工retry | 若新失败仍是合同拒绝，从新失败result重新构造快照，不继承旧快照 | 第二次是新的操作，不凭上次快照获取自动执行权。新失败为进程类时不携旧合同诊断，保持该类恢复用途。 |
| 正常完成/后继节点 | routing新result替换旧marker，后继只消费既有合法refs | 旧prompt保留历史证据；旧marker不得传播到reviewer/gate或别的node。 |
| 完成前进程死亡 | 既有stale-running/恢复规则处理，不自动重放provider | 不承诺跨文件事务或自动恢复半写；本批只保证不使用不完整retry来源调用provider。 |
| abort/terminal | 原有终态守卫仍负责拒绝执行 | marker不授予重开权；诊断缺失不允许绕过abort。 |

retry期间已在磁盘留下但不再current的open recovery gate文档，仅按现有current定位规则解释；
本批不顺手改resolved状态。current写完而instance投影失败按现有current authority处理，
不把投影当第二提交标志。本批不得声称修复全部R1崩溃活性问题。

新旧支持范围：只保证新版本创建的快照；旧READY/正在执行的retry不追溯补写。旧FAILED
有合格node诊断即可在新操作时构造，不要求重读历史execution。禁止旧binary消费新快照
用于本能力验证；冻结样本
KPPZ/GCTW保持只读，不做single-case rescue或迁移工具。

## 5. 改动面与通用性

预计production仅两处：`NodeStateWriter`在普通retry保存有界快照/校验前态；
`AcpxAgentTaskHandler`检查形态与现状并渲染section。优先private方法，取消新ledger reader、
ref/hash/event验证器。不新建通用prompt管线；若需新节点、状态或第二材料平台则停止。

不改：workflow JSON/LiteFlow、合同/schema、ArtifactContractValidator规则、自动重试策略、
AuthoredStageLineageResolver、人门动作、网络grant/fork/model设置、CLI/server新命令或UI。
CLI/server本就同走ledger，须做parity测试但不重复实现；同handler覆盖Claude/Codex、
inline/worker。prepareOnly也必须验证marker但不得claim grant或调用provider。

通用性判断：**成立于“同一未完成任务的确定性输出合同拒绝”这类情形**，不是所有异常。
不含OKX、路径名、模型或错误文本特判；正常review修订、证据补采、网络批准和进程恢复不
变。ContextStateEvidenceBoundaryRenderer历史摘要不作为本设计选源，且不得成为缺marker
时的fallback；本批不宣称已清理全部历史投影扫描。

## 6. 必须通过的最小反证与序列测试

1. 真实合同拒绝产生失败node/execution → 人工retry → 新run prompt确含原拒绝诊断与历史标识 → 合法候选
   经全部校验 → reviewer/gate；design与plan、inline与worker参数化，不只断言字符串helper。
2. 快照版本/字段类型/长度非法、current非RUNNING、节点不active或仍有open gate：零provider。
   在合同失败而current已RUNNING的前态执行retry，拒绝且零写入。删除消费守卫接线则反例必红。
   不再测试消费侧跨文件SHA/symlink拒绝，因为实现根本不读取该外部源。
3. node写后/事件前、事件后/current前注入失败；丢弃运行对象、由新ledger/runtime实例读取仍阻断。完整retry在provider前
   冷读可用；start覆盖latestExecutionId也不能改变快照。确认run/worker入口没有先将
   半写current改成合法状态；若有则回设计，不靠额外事件平台补洞。
4. 首次失败A、重试再失败B、再次操作只携B；重试节点、运行节点、保存的prompt保持同一
   有界正文。删除/覆盖历史诊断文件或execution后快照仍可渲染，证明没有偷偷读取外部源；
   成功后后继无marker；没有快照不扫描历史补入。
5. 模型收到诊断仍返回越界路径、坏义务ID或未经批准修改：原validator继续拒绝，零合法
   sidecar/后继reviewer。不能把“已提示修复”当作已修复。
6. 进程失败、自动retry、BLOCKED、WebSearch批准、collect/submit证据路径保持原边界；
   CLI/server共用来源、Claude/Codex不分模型；network grant不能因marker获得续期或复制。
7. 诊断超限明确截断、正文带指令作为数据，不增加权限/动作/义务；最终prompt+manifest可
   复现实际输入正文，正常无retry执行不增加section。prepareOnly与执行复用同一校验。

## 7. 六维自查与交给CC的决定

| 维度 | 本设计要求（实施证据见checkpoint） |
|---|---|
| Declared by | 原任务/批准输入不变；普通人工retry + ledger生成有界快照 |
| Proven by | FAILED node诊断与READY同次原子写入；保存的prompt/manifest。不是跨文件防篡改证明 |
| Consumed by | NodeStateWriter生成 → 共用acpx handler读取 → prompt/manifest |
| Enforced before | 快照无效/流程不一致阻止provider；产物不合法仍在publication/路由前拒绝 |
| Failure route | 既有人工恢复/abort，不提高retry资格、不新增自动循环 |
| Cannot-bypass | §6正常、拒绝、半写、冷启动、连续失败、后继隔离与两执行模式反证 |

## 8. CC评审裁定与执行状态

F1采纳：改为有界内嵌副本，取消初稿引用链；F2采纳：不消费事件镜像，事件不重复存诊断。
但“没有跨文件诊断依赖”不等于“node/event/current事务窗口不存在”，因此保留现状守卫，
并明确合法前态必须为WAITING_FOR_RECOVERY，避免把current已RUNNING当作新retry提交证明。
F3补充：人工retry没有新增次数上限，非免费；F5已由主agent逐行补查
markQueuedForAgentTask，确认其mergedResult保留字段。

实施已按内嵌方案完成，未增加引用读取器或事件匹配机制。半写反证走真实run/worker入口，
以全新ledger/runtime对象从磁盘重读；不是OS kill/relaunch实验，不能外推R1崩溃活性。
原设计CC报告仅条件性认可内嵌方案；2026-09-13另收到独立实现评审并归档，不以设计认可代替实现核证。

新增序列直接反证重试后的scope越界；坏义务ID、语义偷改、补证据与交付消费者由既有测试
随全reactor回归，不声称每一项都与本次retry新序列做了组合遍历。未调用provider、未开额外
agent、未恢复KPPZ/GCTW；自然E2E与模型收敛成功率仍未证明。
