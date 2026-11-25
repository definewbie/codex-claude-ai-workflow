# W08 离线对账与真实 Gate 草案

2026-10-09；P0。**材料已形成 / 作者自查 WARN / 技术 NO-GO / 无真实运行授权**。本轮只做已有链路的组合准入对账、一个既有 LOW 的常驻化和离线发行工件核验，不新建平台、不开始 W09、不修改生产代码/模板。

## 1. 判断与本轮结果

B01–B06 均有独立批末 PASS，当前生产/资源内容与 B06 单轮 1752/206/0 的 654 项输入相符（本轮只有一个测试文件增量）。这些证明可以复用；不因历史工作清单仍写“待实施”而从零重做，也不能把批次 PASS 当作所有 W 项关闭。

本轮已完成：

- B06 LOW-1 从一次性补证进入 DeliveryViewServiceTest：实际 status/overview 的观察在 L 外、同代组合在 L 内，合法读不产生 delivery intent。6 项聚焦测试通过；隔离副本将观察移入 L，常驻新用例预期断言失败，1 failure / 0 error。不是生产新缺陷，也没有修改生产 hook。
- 当前源码离线 package 成功。实际新 CLI JAR 的 help 和三个内置定义 validate 均 exit 0；CLI/server 内嵌六个领域模块 JAR 与本轮模块字节一致。[发行工件核验](artifact-verification.json)。package 明确跳过测试，不冒充全模块新回归。
- [18 个既有套件索引](retained-sequence-map.json)及 [B01–B06 接收依据](batch-evidence-index.json)覆盖正常、拒绝、返修、重批、补证据、UNKNOWN、固定补齐、四变体/两入口与临时 Git 交付。不是把套件数量当完成率。
- 案头检查 [W07 字段和提示词](w07-field-audit.md)、[真实 Gate 草案](verification-gate-draft.md)与 [fresh 需求候选](fresh-requirement-candidate.md)。需求是未批准候选，没有创建目标 repo/workstream、执行产品实现或赋予文件写权。

不重复全模块回归：本轮没有生产/资源变化，新测试已有聚焦正向与隔离反证；1752 是 B06 当时的全模块凭证，不加上本轮 6 项改报“1758”。W07 纠正之后须按其实际改动完成必要验证并重新打包，当前 JAR 含已确认旧提示词，不能作为最终 Gate 冻结版本。

## 2. 按完整用户流程对账

| 主项 | 已成立的有限结论 / 依据 | 剩余项与本轮分类 |
|---|---|---|
| W01 人工决定→作者→复评→批准；失败后补足 | HumanRequirementContinuationSequenceTest、PhasedContractContinuationTest、B05 合同资格族；正常/拒绝/真实本地恢复及四分类单族有据 | 其余诊断族 UNKNOWN，是用户 C 范围决定下的未完成，不阻首个有界主流程；不自动开第二族。B05 时钟 LOW 未到本轮受影响触发点，继续登记 |
| W02 准备→执行→验证→发布→恢复 | B01 E02 常驻保真、B03 固定 R01–R03/唯一 outcome、权限关联双通道、未关联阻断、退出码按条款、inline/worker | 真实原生信号/工具观察需 Gate；全量非必需声明首版限制已接受；具体运行中补声明入口未证，真实反复阻塞才重新评估，不强迫本轮建设声明平台 |
| W03 目标批准→设计/计划→复评→精确执行批准 | B03 PhasedContractContinuationTest、四定义、兼容接线；B04 C/X/原 callback 一次回复、程序批准与原生许可分离 | **运行前必要缺口 G01：设计提示词要求复制已批准精确路径，而 GOAL 禁带实现 scope，DesignScopeValidator 在 GOAL 允许提案。** 需按 W07 纠正，并扫同族 intake/design/plan/reviewer，不能放宽身份/scope 校验 |
| W04 实现→自测→独立验收；缺材料时补证据 | VerificationEvidenceSequenceTest、ExternalVerificationEvidenceTest、PhasedContractContinuationTest；程序/模型/人工三分，漂移拒绝与合法复用均有据 | fresh 每条验收先映射 AGENT 的实际工具观察或既有 operator 路径；readiness RECORD 和事后 prepare/submit 分立。真实环境未证只核任务必需能力，不建通用认证矩阵 |
| W05 同冻结版本恢复/外部 UNKNOWN | B03 协议/B04 C-X/B05-B06 冷 JVM/写前缀；B02 实际 holder monitor/默认装配与异常后恢复 | kernel 规则文件/load/clean 内部 I/O 专项未测，原义务继续登记，不能用 ledger failpoint 代替。首 Gate 限单进程路由/单 ClassLoader、无并行 workstream；正常 Gate 不宣称全故障 I5 或部署普适性已证。若目标要解除这些限制，先补证明/取舍 |
| W06 实际批准候选→目标 apply→人工 commit→finalize | B06 完整受控链、临时真实 Git、停止类型/每窗/重复/历史/同源 view 已独立收口 | fresh 真实客户端产出的 candidate 与新目标实际交付仍 NOT PROVEN，归 W09；自然中断仅用原裁定，零现场兼容救援 |
| W07 机器身份/语义义务与减负 | 单一 envelope 示例、typed context 去重、受限 correction 与错身份/漏义务拒绝已有代码/回归；逐字段案头索引本轮完成 | **G01 为确定的实际方向冲突**；其他重复头部是定位信息不是第二 authority，暂不删除。GCTW 负担的降低没有新模型证据，不宣称 token 节约或真实修订成功率提高 |
| W08 组合/投影/发行/Gate | 复用 B06 稳定生产全回归；本轮查询顺序常驻/反证、JAR/定义校验、字段/角色/入口检查 | 全 UI 浏览器视觉仍未证，Node VM 不替代；首 Gate 明确 CLI/INLINE 范围，UI/browser 与 WORKER 真实运行仍单列，不称总体 W08 已通过。实际选定 native/装配/预算/停止/出口未核齐，见 G02–G05 |
| W09 fresh 真实闭环 | 无本轮实际运行；只有本轮新需求候选及可执行草案结构 | 必须先通过具体 Gate 前提并获得用户批准；不恢复旧 OKX/历史样本，不用旧预算/旧代理授权 |

## 3. 真正到开跑的缺口

| ID | 必需工作 / owner | 结束证据；不接受的替代 |
|---|---|---|
| G01 提示词与批准分期一致 | 现有 AgentPromptRenderer、design-claude 模板及同族输出/上下文提示；W07 | GOAL 下可提出 canonical 文件/操作，不能写代码；首次实现仍须 EXECUTION 精确批准。实际渲染和合法编译/拒绝成对证明；不以合成成功模型遮住旧指令，也不删 scope guard |
| G02 实际选定执行装配 | 既有 acpx resolver/ReviewedAcpxBuildIdentity；operator | 当前 DUAL_ACPX_EXECUTABLE 未设置，PATH acpx 不是本仓 wrapper。必须显式选本仓受审入口并核实际 build-info/config/启用路径。静态 audit 为旧 patch 41ee5f00，生成源码和 dist 声明 1517f828；这是静态记录不一致，不足以判运行旧/新。无需默认重新安装或锁死 Claude/Codex 版本，更不重开底层安全实验 |
| G03 验收出口与观测 | 原 D05 消费者/当前实际 operator | EXECUTION 前逐 criterion 核必需 Python/读取/测试输出、env/authority/readiness 来源；补证据路径若选中须真的可用。真实退出码只在批准条款明确要求时必需；不拿模型文本当工具观察 |
| G04 冻结发行与拓扑 | CliLedgerFactory / FrozenRuntimeIdentity / operator | G01 改后重新验证、package并记录 JAR/定义/templates/关键配置/实际 kernel/loader/code-source。首 Gate 一个 INLINE runner，诊断/UI如启用只读，禁止另一个执行者。源码 dirty 可按内容冻结，不要求先提交全部既有改动 |
| G05 当次目标/预算/人批准 | 用户与 Gate 执行者 | 审批新需求、目标隔离位置、执行预算及关键/最终门由谁批准；每段前核调用/elapsed/真实可观察 token。新 repo 尚未创建，预算只是候选。无旧授权自动延用 |

G01 是可定点纠正的职责接线，当前未实施；G02–G05 是当次 Gate 前提，不是要求先建设新能力平台。判断来自 [具名源码锚点](source-checks.json)、静态装配/脱敏配置与现有消费者，不从历史总账臆造新的阻塞。

## 4. 控制链及失败切面（沿原协议，不另造状态机）

| 已确认前态 / action / owner | 写入与副作用 / 下一消费者 | 失败及合法出口 |
|---|---|---|
| Gate 前提未齐 / operator 核对 | 固定本轮清单和输入身份，零 provider | 缺任一必需项 NO-GO；修前提后重核，不标部分成功 |
| fresh 已创建 / 分段 run / runtime | 原 L00/R01→本次原生执行→R02观察→R03完成→L03→路由 | 每个本地前缀补固定字节；外部不明走 D07人工，零新重试；新/旧身份不兼容 INVALID |
| 目标候选 / user approve /原 L04 | 正式 GOAL→只读作者/独立复评→EXECUTION候选→精确批准 | READY 不伪造人决定；缺资格/provider零调用；revise 返回作者/复评/新门 |
| 原生 ask / user once /原 R与 L C-X | 人决定耐久→同 L提交 C→原 live sender一次IPC→原callback同步核验→R03 | X先则零新allow；C后在途依已接受语义；送达未知不重发，停止未确认不新 attempt；ACK非执行完成 |
| 自测/验收候选 /程序观察+模型语义 | scope采纳检查/D05逐项证据与漂移重验→唯一发布/独立复评→最后用户门 | 缺材料按已有 prepare/submit，代码缺陷返修；不弱化已批要求，局部声明首版限制如实 |
| 最终批准candidate / delivery merge | 原target→L07 intent→实际Git→PID/start完整→输入patch→实测退出→APPLIED | 任一外部不明不二次apply；仅原resolve具名停止及风险裁定。冲突保留现场，不reset/clean |
| 目标已人工commit / finalize | 原owner核clean/parent/base/tree/停止→L07 COMMITTED | 缺停止证据先resolve；同请求重读不被当前演进误拒；终态不重开 |

批内已经证明的前缀/冷启动，不在真实样本中强行制造所有 crash。未自然触发路径记 NOT EXERCISED，仍保持现有离线证明与真实能力边界。

## 5. 六维自查、边界与下一步

Declared：主清单 W08/R3 §6 和本轮用户请求。Proven：保留 B01–B06，新增查询常驻正反/离线 package/JAR validate；无原生能力新证明。Consumed：原 qualification/证据/发布/recovery/delivery owner，不由本文产生许可。Enforced before：既有 guard在 dispatch/采纳/原生回传/交付前；本轮 Gate 不齐则不运行。Failure route：G01修正、Gate前提重核、外部UNKNOWN沿原裁定。Cannot-bypass：新增真实入口锁序反证，保留具名套件与历史 mutation，不声称每一行都已有删除式反证。

Boundary Review：**WARN / NO-GO**，原因 G01 实际提示矛盾及当次未齐的 G02–G05。不是体系重写或新权限决定。现有独立 PASS 保留，不回填历史材料，不把自查冒称独立复审。

下一步：用 [CC prompt](cc-review-prompt.txt) 合并评审本对账和 Gate 草案，重点判断必要阻塞与延期是否合适。通过后按受审边界纠正 G01、核齐 G02–G04，再把具体需求/目标/预算交用户批准；未齐不申请真实运行。CC PASS 也不自动实施、安装或开跑。
