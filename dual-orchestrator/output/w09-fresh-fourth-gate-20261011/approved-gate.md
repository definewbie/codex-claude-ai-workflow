# W09 完整主流程新 Gate 预算候选

2026-10-11；**USER_APPROVED / PREPARING / NOT YET RUN**。

目标保持完整开发→真实测试→独立验收→最终批准→目标应用→用户commit→COMMITTED。不是GOAL/设计阶段Gate，不转成本优化实验；当前CLI/server冻结候选来自F03/F04修复发行，原三个样本保持停止。任务采用原[换行规范化输入](../w08-preparation-20261010/task-requirement.md)，不改批准标准或默认改模型。

## Verification Gate

- **Target**：一个fresh标准库Python任务，在GOAL/EXECUTION/最终用户批准和Claude Code作者/Codex独立复评之后，真实逐项验收成立、批准candidate在新目标apply，用户实际commit由Dual精确确认COMMITTED。只证明当前组合一个完整合法主流程。
- **Source of truth**：当前definition及实际runtime identity；GOAL/EXECUTION/gate/node occurrence及ref/SHA；R01/R02/R03、唯一outcome、有效verification-observations、源码/候选identity与review谱系；delivery plan/operation与实际GitHEAD/parent/tree/clean；逐原生completion(session,request)关联并固定的usage。预算索引仅引用这些事实，不产生authority。
- **Trigger precondition**：本材料与预算候选受影响复审通过、用户明确批准新总预算/隔离准备/真实执行；冻结源码654及CLI/server/受审wrapper与配置选定；新目录/workstream/session不借旧账本；实际验收出口仍可达，无未知旧执行重叠。用户已批准本例预算与运行；各业务门许可仍须实际subject产生后分别取得。
- **How precondition is checked before run**：按下表G1–G7保存实际核对，不用当时SHA冒填今天有效；start不带run，核实际runtime/kernel/loader；模型派发前原qualification通过且operator保存预算核对。readiness动态引用在实际GOAL和真实等待occurrence后由原record-readiness入口产生，不提前虚构。原输入若形成AC-4/AC-5等新增条款，逐条映射实际出口，不只核原AC-1–AC-3。
- **PASS**：完整批准谱系成立；实际实现/自测/另一客户端独立验收逐条通过当前合同；最终批准绑定当前candidate；实际apply后用户commit、finalize精确确认COMMITTED；token归因完整、预算准入与停止纪律满足。自然合法修订在同额度内走原链，不要求一次直通。
- **FAIL**：有效冻结组合出现合法流程误拒/死端、未批执行/采纳/交付、错身份/缺证据采纳/主因漂移、产品验收不满足、实际止损或尾链预算不足而无法在批准额度内完成。真实预算不足不降为INVALID，也不改模型SUCCESS为失败。
- **INVALID**：开跑前发行/配置/fresh/观测或验收出口前置错误；运行中更换冻结输入、手改权威账本/判据、以错发行测试。有效运行内的领域/归因错误按FAIL停止；外部UNKNOWN分列，不从缺日志推出零执行。
- **Stop-loss**：可观察累计1700000token；18次顶层请求；3600s主动运行窗口；同根因无新证据两次或最多两次有新证据语义循环；任一实际/可归因usage不完整、派发前储备不足、身份/scope/批准漂移、外部结果不明、需新authority/扩大目标即停止新dispatch。1200000token预警不自动停合法尾链，也不修改总线。额度是完成粒度的停止线，**不是累计绝对硬限**。
- **Run artifact freshness**：沿用经受审的F03/F04发行作为候选，开跑时重新测源码/资源、CLI/server JAR、选定wrapper/dist/实际build-info/config/launch及kernel/loader；任何实质变化重核受影响结论。native采用用户现有兼容配置/实际握手，不新增精确版本锁；本轮安装缓存源码SHA只作本轮案头证据，不冒称过去整个子进程可复现。

## 1. 当次边界与前提

| ID | 启动前/消费前核对 | 当前状态 |
|---|---|---|
| G1 任务与人门 | 原纯任务输入，GOAL目标/验收/禁止范围；实际EXECUTION精确文件与CREATE/MODIFY；最终candidate分别请用户批准 | 既定产品目标保留；新实例各门未产生，不能借第三例GOAL |
| G2 发行与拓扑 | CLI/server候选SHA分别4c31221d…/6113d924…，完整值取原release-identity并重新测；terminal-delivery@5、liteflow、单INLINE/单路由JVM/同一ClassLoader | 本轮未改字节；新启动事实待当次核 |
| G3 native配置 | 本仓wrapper与受审patch1517f828…；Claude当前GLM路由/effort default、Codex当前reasoning high；保存安全选择项、实际session返回和显式策略。默认approve-all不成为许可 | 本轮案头事实已有；未来会话未创建。无新thinking/turn/output限制、compaction/缓存变更 |
| G4 验收出口 | 原readiness/prepare/submit/self-test/acceptance可达；实际GOAL全条款有method/executor/evidence kind、真实环境identity、必要tool证据生产者。宿主Python测量不替原生执行 | 原出口沿用；未来真实合同/计划绑定待产生并核齐 |
| G5 fresh/旧执行 | 候选父目录`/private/tmp/dual-w09-newlines-20261011-fresh-04`，独立target和linked worktree，分支`codex/w09-newlines-20261011-fresh-04`；已存在则换nonce，不覆盖。记录canonical/common-dir/base/tree/clean、旧具名session/PID/未决外部状态 | 本次已新建独立目标；既有停止事实启动前重新核，不泛化任意子树全停 |
| G6 预算observer | 原固定usage/实际completion逐请求计数；下述储备式规则用独立新observer执行，开跑前离线复核。不能复用旧脚本的160k及len(runs)*2+2 | 新observer已具备派发前检查及实际completion核对，13个预算对照和提取正反例通过；真实行为尚待本例 |
| G7 授权与责任 | 用户批准170万token/18请求/60min/隔离初始fixture；业务门与原生once逐项仍由用户决定，最终产品commit仍用户执行 | 用户已批准预算与fresh运行；各业务门和用户commit仍分别待决 |

真实GOAL若重述原目标且加入可核验的语义/scope条款，AC-4可用现有实际源码读取+测试观察与reviewer判断支持，AC-5可用实际变更/路径操作核对与原artifact scope校验支持；不能因称其治理条款就跳过EXECUTION verification_bindings。实际方法名以reviewed plan为准，不在本页造ref或新证据类型。若新增要求不能落到既有执行/证据出口，停在批准前说明，而非临时降验收或做通用环境认证。

默认省去额外连接读取前缀：必要ACP创建/兼容性检查仍在正常run前执行，第一次正常业务请求就是其实际模型证明，失败即停。此前允许的额外试探不成为必须开跑成本；如当次出现新的接口未知，先案头核对，另提有界方案，不在18池内临时加无目标探针。

## 2. 暂定预算，不宣称统计上界

| 正常节点 | 暂定token储备 | 证据性质 |
|---|---:|---|
| intake作者 | 40000 | 有同阶段实测，含第二例36945 |
| intake独立复评 | 50000 | 第三例27560/39574 |
| design作者 | 150000 | 单样本130731+暂定余量，非p95 |
| design独立复评 | 75000 | 本任务未测 |
| plan作者 | 100000 | 本任务未测 |
| plan独立复评 | 75000 | 本任务未测 |
| implement作者 | 200000 | 本任务未测，给写入/工具执行更大空间 |
| self-test | 150000 | 本任务未测，保留真实测试/证据生产 |
| acceptance独立验收 | 100000 | 本任务未测，不省复评 |
| **正常9节点合计** | **940000** | 混合实测与临时规划，不能说已测成本 |

再示例预留：readiness计划重入175000、一次intake作者/复评修订90000、一次格式修正150000，合1355000/14请求，余345000和4个调用槽。两个语义循环及更多correction只在实际余量容纳时可走，不保证都能用满。**170万比原16万高，理由是原线连真实设计单请求后的半程都容纳不了，完整链还需六个未测模型节点；不是以额度购买成功结论。**若用户不接受此量级，维持不运行，不能默认改阶段目标。

每次最多一个模型节点的分段runner；最多一次既有deny-all格式correction可能在该节点内部发生。因此在父请求之前就为它预留token和一次调用，不能等父请求消费后再发现超池。

派发前规则（由operator读取原事实、保存check记录）：

1. 先核当前合法节点/批准/identity、无pending本地mutation或旧外部UNKNOWN。等待中的当前合法原生ask不是外部失败；它只能按原C/X、原offered once及期限处理，不能据此另开模型请求。
2. 取本例全部已完成主调用与实际格式修正的唯一completion及usage；T为完整可加总totalTokens之和、C为实际顶层请求数，不能按run数乘2或tool消息数计数。无关联/歧义/部分/快照/结果不明即停；in-flight不是零消耗。
3. R为**全部未完成正常主链**的表中储备，加本次需要的新返修/重入节点储备，再加一次可能correction150000；调用保留数同样包含尾链、新额外请求及一次correction。要求`T + R ≤ 1700000`且`C + required_calls ≤ 18`，否则不发新模型请求。正常尾链从当前真实node/定义确认，不由模型正文或预算脚本创造流程。
4. 主动累计时间另按实际分段runner计，人工业务门等待排除、native在途/工具及permission等待不伪装为零。模型节点前暂留1500s（两次600s可能请求+300s暂定准备/收尾）且已用加储备≤3600s；该余量不是物理时长上界。30s观测，实际到3600s停止许可并按原通道核停止，不等待用完预留。
5. 完成后R01/R02/R03/L03、候选/证据/usage固定均有效才更新索引。实际请求超表中储备不单独伪造模型失败；按真实总量重算余量/尾链，装不下就停止。不下调剩余储备来凑通过，不动cachedRead口径，不从SDK美元推实际订阅费用。

储备减少“下一步已知余量不足仍启动”的风险，不保证原生调用≤储备或170万。若一个请求越过总线，Gate仍FAIL、领域合法结果保持，零下一dispatch；不会自动抬额、改模型、再开例或沿用旧余额。用户批准时须知这一风险。

## 3. 完整控制链与写入/外部失败窗口

| 前态/事实owner | 动作、写序与下一消费者 | 失败与停止出口 |
|---|---|---|
| 预算/冻结前置齐备，无实例 | 经当次批准新建fixture/target/worktree→start无run→记录实际身份→核准入 | 前置不齐不调provider；目录冲突不覆盖；原样本不恢复 |
| intake/design/plan合法READY | 每次预算记录→原qualification→R01→R02 intent→原请求/可能一次correction→唯一outcome/R03/L03→原review/decision | 本地部分写D06固定补齐；外部不明D07人工，零自动重发；有效领域拒绝保留typed主因 |
| 真实GOAL门/EXECUTION门 | 展示当前subject→用户原review决定→L04→原compile→后继资格消费；readiness原记录→revise→作者/独立复评→新门 | 决定未完整固定不推进；新预算不是批准；readiness声明不证明成功或grant |
| 批准EXECUTION且预算可达 | 作者产品写入→真实自测/固定工具观察→源码/证据重核→原scope validation→Codex独立验收→completion/candidate | S事后拒绝不是写前沙箱；D拒过期证据不是阻止源码变化；scope/证据不足零交付，不换工具绕过 |
| 当前accepted candidate、最终门 | 原报告/plan→用户最终批准→原delivery L07意图→PID固定后patch输入→实际结果/目标观察→APPLIED | 未批零apply；intent后外部UNKNOWN依D09裁定，不reset/clean/自动重复apply |
| APPLIED、用户实际commit | 用户提交→原finalize核parent/tree/clean/停止依据→COMMITTED→历史只读 | commit存在不跳过停止依据；丢回执同请求幂等重读，不制造fresh资格 |
| 任一预算/身份/外部异常 | 先停新派发/新许可→原cancel/close→具名session/PID/start/结果核对→静止快照与Gate结论 | 领域正常状态保持；不使用RUNNING的通用retry提示续作；不承诺任意子任务物理全停 |

R/L各写入前缀及冷恢复、C/X交错、交付UNKNOWN已由既有离线批次证明；本例不强行注入，不实际触发则NOT_EXERCISED。新binary不续作旧ledger，新observer不改过去的事实。终态不得新模型调用；只读归档/重复合法finalize不造新副作用。

## 4. 新 observer 的开跑前义务与证明边界

[check-budget-offline.py](check-budget-offline.py)是纯operator算术核对，不是新预算平台或生产guard。其ALLOW_BUDGET_ONLY不授业务/原生/交付权限。新observer准备时须同时满足：实际completion计数（含correction）与固定usage一致；metadata/context/decision不计模型调用；每个会引发模型的run前核主链+可能correction储备；字段缺席不当零；pending/漂移拒绝且合法余量放行；30s进度只作观察，不把usage_update的used当累计量。原13个对照随新observer核对，未核齐不得启动。

本提案不靠继续配置实验开跑：默认/实际high保持，无thinking禁用、低max-turns、缓存/compaction切换、命令分类器或安全沙箱扩建。F2工具计数和F3恢复投影缺口登记保留，不能充当预算/许可SoT。若妨碍真实验收或安全停止，按原纪律停止，另做有证据的前向修复。

证明范围仍限定：一次CLI/INLINE主流程成功不证worker/多变体/浏览器/所有中断窗口、全诊断族、节省比例或全部原生ask行为。自然没有ask/UNKNOWN/revise等记NOT_EXERCISED；本轮离线budget对照不是第四例证明，也不授权恢复第三例。

**下一步**：CC合并只读复审本核对与Gate；必要项处置后交用户明确批准新预算/隔离准备/真实执行。到那时才准备并核齐独立新observer、创建fresh目标并按原门执行。本轮不安装、不启动第四例、不承接任何旧剩余额度。


当次生效补记：用户已批准170万token及fresh第四例；原页的待授权/下一步描述为候选历史背景，不覆盖本授权。业务GOAL/EXECUTION/最终门仍待当前subject产生后分别决定。新observer必须离线核齐后才能派发。
