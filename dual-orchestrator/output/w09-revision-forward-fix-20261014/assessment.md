# W09 身份减负与初次 GOAL 前目标级返修：实施、回归与发行候选

状态：**IMPLEMENTED / OFFLINE_VERIFIED / FROZEN_CANDIDATE / AWAITING_AFFECTED_CC / SIXTH_NOT_RUN**。本报告是主 agent 自查，不能替代独立 CC 结论，不关闭整体 W01/W07/W08/W09，不授权真实运行。

## 1. 授权目标与本轮结果

用户批准两个既有 owner 内的有界接线、设计 LOW、回归、重冻和合并 CC 复审；明确暂不启动第六例。对应 P0 合法主流程：去掉冗余 SHA 转录负担，并让首次 GOAL 前的真实目标/禁区返修回到当前复评和人门。没有 token 节省验收，也没有新增安全沙箱任务。

| 项 | 实际变化与强制消费者 | 证明及边界 |
|---|---|---|
| 身份减负 | 模型只输出确切 base_proposal_id；adapter 同一次基准解析供提示词与既有 R01；完整 ref/SHA/义务/允许形态由程序固定。dispatch、publisher/merger、reference/lineage/compile 强制消费 | 合法无 SHA 正向、旧 SHA 键严格拒绝、基准字节漂移/缺 R01/伪派生拒绝；未运行真实客户端，不承诺消除所有 ID 转录错误 |
| 目标级返修 | 首 GOAL 前可信 NEEDS_REVISION 或人 revise 可用已有 full proposal + base ID；真正 objective/excluded 实变、有限 delta/当前义务仍检查；新 subject 必须复评→当前人门→GOAL | 四变体 × inline/worker 合法链及未批 EXECUTION 拒绝；已授权/late/pending 人批不可借用，历史追加不反向撤销旧合法提案；不新增跨 GOAL 在线迁移 |
| LOW-1 | resolveFromRefs 比完整 reference，相同 ID 不能抹平不同完整内容身份 | 两个独立有效引用、顺序互换拒绝；同一完整引用重复合法；删除守卫反证 |
| LOW-2 | derivation 注释说明 patch_source_output_ref 为历史字段名，full goal_reproposal 同样指作者原始输出 | 不迁移字段，不洗旧历史，不新增兼容分支 |

增量相对开工前 dirty 工作区，见 [changed-files](changed-files.json)、[incremental.patch](incremental.patch)、[before](before/) 与输入指纹；不是 HEAD 全量 diff。本轮 29 个源码/测试文件（15 生产、14 测试）及 2 份当前规划文档；生产变更仅 runtime 契约/执行准备/消费者及 adapter 作者接线。新增一个局部 ContractRevisionBinding，复用 R01；没有新节点、artifact type、锁、服务、存储、DSL 或第二套审批权威。

## 2. 完整链、失败切面与不能绕过

[消费者与 transition 表](consumer-and-transition-map.md)、[覆盖索引](verification-coverage.md)和[开工边界](implementation-boundaries.md)记录 SoT、owner、写序、下一消费者、恢复和兄弟。链为：原返修决定→一次 capture→R01/L claim→dispatch/R02→raw/严格语法→唯一 outcome→发布/完整基准 merger→derivation→R03/L03→新复评→当前人批→原 GOAL/EXECUTION。

新增常驻序列实际覆盖 R01 已固定后的 L bind 和 R03 已固定后的 L03 三个关键前缀（before_manifest / after_replace / before_pending_delete），两形态与双入口；真实冷子 JVM 消费固定完成而不重发模型。无 R03 不授完成，orphan/pending 不授新派发，缺固定材料不伪造成功。原共享协议测试在本轮全回归中复跑，但不宣称全部旧 failpoint 已分别穷举所有新形态。

初始、普通 patch、新 full、人工 revise、late clarification、四实际工作流装配和 inline/worker 均入检查。contract 冷 JVM 在后来 GOAL/EXECUTION 批准后重读初始/full/patch 三份历史，证明正常历史追加不制造假漂移。原权限 C/X、scope、退出码/证据、独立验收、交付批准语义未变。

## 3. 验证证据分层（不能相加）

| 层 | 凭证 | 能证明什么 / 不外推什么 |
|---|---|---|
| 旧生产先红 | red-wire.log：2 项预期失败 | 旧生产拒绝合法 ID-only，并接受现已撤销的 SHA 键；不等于真实模型行为 |
| 常驻新增及迁移 | RevisionIdentityWireTest、ProposalReferenceIdentityTest、HumanRequirementContinuationSequenceTest 与原契约/编译/lineage 族 | 真正本地 prepared/ledger/Git/冷 JVM，模型输出由离线 transport 产生；合法与拒绝成对 |
| 删除守卫 | [counterproof-results](counterproof-results.json)：3 个隔离变体均命中预期断言，源码恢复 | 完整 reference、merger 完整 SHA、full 真实目标变化三守卫；不计入回归总数 |
| 唯一最终回归 | [test-results](test-results.json)、reactor-final.log、reactor-reports/：**1805 项 / 210 suites / 0 failure / 0 error / 0 skipped**，8 模块，exit 0 | 单轮 clean；659 输入运行前/后及打包后一致；不拼接定点绿灯或历史 1790 |
| 发行核验 | [release-identity](release-identity.json)、[artifact-verification](artifact-verification.json) | CLI/server 冻结副本及内嵌项目模块/自身 class/resource 字节一致；14 资源、6 个只读 CLI 检查；打包跳测试不冒充第二轮回归 |
| 未运行 | 原生客户端/provider、第六例、真实完整主流程、token 节省 | 全部 NOT_RUN；本地帮助/定义校验不证明这些能力 |

中间编译/夹具/错误断言、运行中源码变化、主动中断、attempt-02 一项旧拒绝时点断言失败均保留日志，并在[自查复盘](review-and-reflection.md)分立；均非当前全绿凭证。attempt-02 的迁移仅把断言移到新的更早原拒绝入口，补 reference 不可用/零输出/零新调用，不改生产 guard。counterproof 的初始分类标记大小写错误单列为检查器错误，原日志未改。

## 4. 六维自查

| 维度 | 本轮落点 |
|---|---|
| Declared by | 用户有界实施授权、受审 D10/D03 补记、设计接收 LOW；原批准/证据义务不撤销 |
| Proven by | 上述常驻完整合法/拒绝链、真实本地写前缀/冷 JVM、隔离反证、唯一单轮回归；真实客户端仍空 |
| Consumed by | capture→同 R01→qualifyDispatch→ArtifactContractValidator/merger→reference/lineage→compile；projection 仍消费原 read model |
| Enforced before | 基准/资格在 dispatch 前；ID/完整内容/形态/义务在 sidecar/reference/复评推进前；当前人批在 GOAL/EXECUTION 权威产生前；不是 OS 写前隔离 |
| Failure route | 原 typed failure/outcome、D06 固定字节、D07 外部 UNKNOWN；无 outbox/自动重发/旧批准借权/第二控制原因 |
| Cannot-bypass test | 同 ID 异 fullref、旧 wire、变基准、只删 R01、伪 kind/initial、无实际目标变化、未批写资格；关键守卫删除后正反对照失败 |

## 5. 历史、发行与复杂度边界

[historical-preservation](historical-preservation.json)：设计包 15、第五例 326、A 修复包 398 条提交材料逐条 SHA 不变；第五例仍 FAIL，旧 raw 不补 SHA、不恢复。旧 release-identity 记录与事故材料保持；原记录指向的 target 构建目录按本次授权重建，**不声称旧 target JAR 仍保留**。本次 CLI/server 冻结副本实际存于本包 release/。

[选定 native 字节核对](selected-native-bytes-recheck.json)只证明 wrapper/dist/配置文件未改变，未运行 installer/native 或测 fresh effective config。未来批准的 Gate 必须重核实际装配、配置、当前 subject、出口、observer、预算与进程拓扑。当前运行与请求预算均为零；不继承第五例余额。

收益限定为消除已见 64 位 SHA 冗余复制和接通被字段形态限制的首 GOAL 前合法返修。其余模型身份字段、真实后半链与 token 成本未声明完成。实际增量没有越出受审 owner 或引入平台；未触发需要用户新取舍的成本收益偏差。

## 6. 下一步动作

提交[合并 CC 只读复审提示词](cc-review-prompt.txt)，独立判断实施/离线证据、发行候选与残余缺口。通过后再准备并由用户分别批准 fresh 第六例当次 Gate/预算/真实运行；本轮不创建或启动第六例、不提交代码。
