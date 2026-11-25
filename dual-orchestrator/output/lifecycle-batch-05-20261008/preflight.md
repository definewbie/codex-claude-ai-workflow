# B05 W01/D01 实施前边界核对

用户已授权实施。阶段 P0：完整研发生命周期；不进入 P1/P2/P3。B04 已独立收口；本批不增加 native/provider、installer、提交、agent、网络 R4/Phase B、旧样本恢复授权。

## 主目标与既有 owner

人决定的实际检查依据可追溯；后继拒绝与这次决定配对；按可信证据区分矛盾/漂移/留待消费的缺项/诊断不足，合法补足后可继续完整作者→复评→批准。保留现有严格资格、scope、身份、批准和证据；诊断不成为第二控制原因或新许可。

Definition 仍是原 workflow 与 checker；Instance 是实际 gate/source/target occurrence；Execution 保存原准备失败事实及 R03/L03；人工决定 authority 仍是既有 gate/node/event；projection 只消费 RecoveryAssessment。

- RoutingEngine/既有人工决定入口产生 decision_checks，与 L04 同次 prepared 提交。
- 原五类 checker 的实际规则提供稳定语义 id 与本版内容身份，替代设计材料词法序号；不运行 precondition-map.json，不按 exception message 猜原因，不建权限 DSL。
- AgentExecutionResultFacts 在真实 resolving-decision 谱系适用时产生 continuation_failure，封闭结果构造/核验继续强制；普通无决定来源失败不能伪造 gate。
- R03/L03 原 owner 固定失败和配对，本地写失败不冒充已完整记录；恢复仅本地固定补齐，零 provider 重发。
- RecoveryAssessment 是唯一分类/动作解释 owner，CLI/API/UI/report 消费同一结果；原 reason/code 保真，UNKNOWN 不包装成矛盾或安全 retry。

## 转换与失败切面

| pre-state/action/owner | durable 顺序/post-state/唯一下一消费者 | 失败、重启、合法出口 |
|---|---|---|
| 当前合法 gate；用户决定；原批准入口 | 实际校验→原 subject/规则/coverage 快照→同 L04 写 gate/node/route/event→当前后继 | 非法 action/note/身份零决定；校验采集失败不保存假 PASS；prepared 任意前缀 reader pending，冷补固定字节不重做决定 |
| 后继准备；原 checker | 按实际规则重验→合法才 R01/R02；失败保留原 code/consumer/phase/观察 identity，与原决定固定 ref/SHA 配对 | 缺决定记录 UNKNOWN；普通无谱系错误保持普通准备失败；错 workstream/跨轮/伪 subject 零 provider/发布 |
| 真实输入准备失败；Facts→R03→L03 | 原失败+配对同时进入固定完成材料→同 L03 node/task/execution/恢复事件 | 任何记录失败不宣称诊断完整；有固定 R03 仅完成本地，不能调用 provider；pair 与 node 冷读一致 |
| 当前恢复查询；RecoveryAssessment | 核对固定决定/配对→原 reason不变，分类消费实际差异/coverage→统一投影 | 可信同 rule/input 已 PASS 却拒绝才矛盾；实际漂移失效批准；明确 NOT_CHECKED 才消费缺项；缺记录/身份不明 UNKNOWN，无默认安全 retry |
| 用户补足/返修 | 原可用动作/资格重核→作者→独立复评→新批准→新 occurrence | 不编辑旧批准、不跳 reviewer、不支持旧 binary/ledger rescue；修订耗尽只显示已证可执行处置 |

## 实施落点与测试策略

冻结映射含五类 200 个旧词法点；先对账当前新增/删除/合并条件，稳定语义常量归原 checker，运行时不消费旧设计枚举。诊断采集仅观察真实 checker 的分支结果，不提前运行未来副作用、不放宽现有 guard、不造新 authority。只有真实执行的检查可以 PASSED，未来目标/执行 epoch/外部环境条件明确留后验；记录缺失 UNKNOWN。

测试从用户序列出发：合法 READY/BLOCKED revise/clarify/重新批准；三个有据分类和 UNKNOWN 实际可触发对照；错身份/伪 pair/漏 pair 无旁路；L04/R03/L03 写入前缀与冷 JVM；inline/worker、四变体、修订额度出口、中央投影消费；删除式反证检验记录义务而非只看字段存在。测试引入的可控故障须为测试自身组件或现有 failpoint，不添加生产 hook。

结束条件：当前内容稳定的完整回归 + 配对合法/拒绝/中断序列 + 独立 CC 合并评审；不得只以 200 常量或绿色局部单测宣布 W01 完成。不为填表硬造机制；若需改变冻结语义、新 authority 或成本收益明显偏离，停止提交证据与选项。

开工核对后的当前状态：停在范围/ROI 取舍前。见 [成本与边界决定材料](cost-and-boundary-decision.md)。没有产品/测试源码增量；原冻结验收尚未修改，B05 未收口。869 个基线文件内容未变的核对见 verification.json，不是测试通过。

当前补记（用户选择 C 后）：上述停止点为历史状态。用户已选单合同资格族完整切片、其余 UNKNOWN；具体输入/接线、R3/详设/主清单覆盖回写待合并受影响复审，见 [C 候选](scope-c/design.md)。本文件原全族实施前设想不再是首版范围；没有产品/测试源码增量，不以本次文档修改重写先前 verification.json 的时点结论。
