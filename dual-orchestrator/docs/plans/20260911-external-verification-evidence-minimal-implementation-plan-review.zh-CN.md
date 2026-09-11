# 外部验证材料最小实施计划 独立评审

日期：2026-09-11。评审者：CC（只读；基线 `0cb2a36b`；未调 provider、未恢复 245D、
未改 production、未启动额外 agent）。代码与工件逐项核证，不采信计划自述。

## 0. 总判定

**计划成立，READY（附两处 P2 计划文本修订）**。五个挑战点的答案均为肯定；未发现更小且
不削弱身份验证的方案。前置评估 §8 对我上轮 R2（HEAD+porcelain）的否决经核证**正确**，
我在下文 §1 先予确认。

## 1. 先确认：R2 否决成立，现有基准强于我的建议

我逐行核读了 `EvidenceCollectionSource.capture`（88107d42 引入）：
HEAD + `ls-files --stage` index 摘要 + 路径集（tracked + 非 ignored untracked，排除 .agent）
+ 逐路径存在性/类型/执行位/**完整内容 SHA-256**（原始工作树字节，非 git blob），拒绝路径链上
任何 symlink，并在读完所有文件后**复验 HEAD/index 未变**（防捕获中途漂移）。
§8 的反例（同 HEAD 下 A→B、porcelain 同为 ` M file.txt`；untracked 同为 `??`）成立——
HEAD+porcelain 不是未提交工作树的内容身份，245D 恰处于未提交+未跟踪阶段。
我的"必然改变 HEAD 或 porcelain"断言未经计算层核证，错误类型与我此前三次被纠正相同，
记录在案。计划 §2 第一行的复用判断（原样复用、不以 porcelain 替代）正确。

## 2. 五个挑战点逐项裁定

**1）真正闭合提交→消费→验收，而非上传入口？是。**
链路核证：submit 经专用 typed 入口（普通 resolve 不能以 action/note 伪造材料，§2）→
gate node result 绑定准备/材料 ref+SHA+baseline，沿既有输入链传递（RoutingEngine:412-423
的 marker 传播机制已在位）→ provider 前强制校验源码/准备/材料，prompt 只展示已验证材料 →
publication 前再校验且**结果必须引用绑定材料**（§5）→ acceptance 可追溯同一 ref（§5 末段，
明确"若现有投影不足先扩展既有投影"）。反向钉子也在：§9.1 全序列测试、§9.8"仅摘要无 ref
必须失败 + 删 publication 校验突变必红"、§9.7"上传不得直接 COMPLETE/ACCEPT"。
这是消费闭环的定义性结构，不是上传入口。

**2）测试前准备记录必要？现有基准能否直接复用？必要；能。**
窗口论证：操作者在外部环境执行验证必然隔开"决定采集"与"提交结果"两个时刻（分钟到小时级）。
若无准备记录，提交时抓当前源码即宣称"材料属于它"——期间源码可已变化，绑定无意义。
prepare（采集+立即复验+原子内容寻址记录）+ submit（重验当前源码==准备基准）正是该窗口的
最小闭合。复用核证：`EvidenceCollection.resolveGate` 现流程=资格校验→qualified contract→
`capture(included paths)`→`EvidenceCollectionBaseline(gateInstanceId, contractRef/sha,
head/indexSha/sha/paths)`→verifySource→`ledger.collectEvidence`——prepare 记录可直接沿用
该 baseline 形态，仅新增持久化时刻与 recordedAt。评估 §8"测试开始前/结束后/提交时如何关联
同一基准仍须锁定"由 §3 的两命令+复验回答。

**3）CLI-only 摄取 + server/UI 只读是否合理最小范围？是。**
校验全部在 runtime 共享服务（`WorkstreamActionService:231` 已与 CLI 同走
`EvidenceCollection.resolveGate`——不对称只是能力子集，非第二 authority）；本地文件摄取留在
CLI 避免新增服务端上传面（multipart/路径处理/大小限制）；server/UI 只读展示 + 不显示不可
执行的 submit 按钮（§3）是诚实的界面差异。操作工作流本就以 dualctl 为主。要求保留一条：
server/UI 投影应显示当前合法动作含 submit-evidence 的存在与 CLI 入口提示（§3 已含）。

**4）失败切面是否完整？§6 表覆盖以下全部，我逐项推演无洞：**
准备写失败（无决定、孤立不授权）；提交决定前失败（OPEN 保持、孤立材料不被消费、内容寻址
使重提交幂等补齐）；决定已写路由中断（既有权威读取、零重复 provider）；后继执行前重验绑定；
材料不足回 human gate（must_fix 空不回实现）；同 id 同内容返回原记录/异内容拒绝/旧 gate/
已消费拒绝；prepare 后 revise/abort 使准备孤立且不得提交到新 gate；冷启动从 bound refs
重读复验、不重抓基准、无 mtime/latest。外部原文件后续变化不影响固化副本；伪造准备记录
无收益（重造合法 prepare 等价）。缺一项显式反例，见 P2-A。

**5）更小且不削弱身份的方案？未找到。**
内容寻址→稳定文件名（§4 已拒，正确：丢失幂等+篡改证据）；prepare 记录→省略（见 2，
削弱绑定语义）；submit→collect+note 引用（§2 已拒：note 伪造通道）；acceptance 追溯→
延期（正是"仅上传入口"陷阱，不可）；逐文件身份→HEAD+porcelain（§8 已否决，正确）。
32 文件/32MiB 上限合理。

## 3. Findings

**无 P0/P1。**

**P2-A | §9 缺"准备记录篡改/SHA 不匹配"显式反例。** §9.5 覆盖材料篡改，但准备记录本身的
ref/SHA 不匹配拒绝（提交者给错 SHA、记录被编辑）未列。虽然内容寻址使其无攻击收益，
测试仍应钉住拒绝行为。加入 §9.5 或单列。

**P2-B | §5"结果引用本次绑定材料"需钉死匹配规则。** evidence 字段是字符串列表；"引用"
必须是**精确 ref 串成员判定**（或前缀+SHA 双要素），不得模糊包含。写明规则，防止实现层
滑向 substring 匹配导致弱引用通过 publication 校验。

**P3-C | 准备记录字段清单未枚举。** 应显式写为既有 `EvidenceCollectionBaseline` 形态
（gateInstanceId、contractRef/sha、head/indexSha/sha/paths）+ recordedAt；submit 校验记录内
gateInstanceId == 当前 gate（§6 第 7 行的语义依赖此字段在位）。

**P3-D | 建议在聚合上限外增加单文件上限**（如 8MiB/文件），防单个巨型文件占满预算。

**P3-E | 时间戳边界如实化。** 系统不读文件 mtime、不声称能判定材料产生于 prepare 之后；
执行时间关联是操作者声明的一部分（§1/§9.3 已如此声明，建议在 §4 材料描述字段中把
"开始/结束时间"明确标注为声明值而非机器核验值——§3.2 现文已有此意，钉一句即可）。

## 4. 批次与顺序认可

§7 的"一个设计单元"（基准/固化/绑定/消费/拒绝冷读序列/就绪规则）与我上轮评审的
必须-一起清单一致；CLI 提示独立小修、作者测试职责独立收紧的切分正确。§10 顺序
（评审→文件 sweep→failing sequence tests→共享服务/ledger→消费/保存→界面/文档→
reactor→只读 diff review）正确；成熟度声明（外部材料链 NOT IMPLEMENTED、collect 基准
ENFORCED 但自然样本 NOT EXERCISED）与工件一致。

## 5. 结论

**READY after P2-A/P2-B 两处计划文本修订**（均为规格补句，不动结构）；P3 可随批处理。
修订后无需第二轮整体评审，可直接进入精确文件 sweep 与 failing sequence tests。
245D 继续只读；本计划落地并验证后，其三动作中 submit-evidence 才成为合法前进路径。
