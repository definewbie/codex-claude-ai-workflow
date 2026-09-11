# 外部验证材料续作：最小实施计划

状态：IMPLEMENTED / DETERMINISTIC VERIFIED。CC计划评审无P0/P1，两条P2已落实；完整链与自查结果见[checkpoint](20260911-external-verification-evidence-checkpoint.zh-CN.md)。未恢复245D、未调用provider。
基线：0cb2a36b，复用88107d42源码基准能力。前置评估及独立评审见同目录20260911-human-verification-continuation-readiness-assessment及其review；R2的HEAD+porcelain建议已在评估§8明确否决。

## 1. 唯一目标和范围

闭合一个用户任务：agent环境不能完成验证时，操作者在合适环境执行验证，提交与当时源码绑定的材料，由既有self-test核验，再进入原acceptance；缺材料仍等待，有具体代码缺陷才回实现。
不开发网络权限、宿主命令执行器、上传平台或新工作流运行时；Dual不执行提交材料里的命令。
不为245D设计特殊恢复。现有样本继续只读，历史恢复支持不包含在本批。

源码hash只证明边界采样时字节相同，不证明执行真实、不证明未发生改后恢复、不证明测试充分。执行真实性包含操作者明确声明；相关性与充分性仍由self-test及acceptance判断。

## 2. 已核对的复用点

| 现有机制 | 复用方式 |
|---|---|
| runtime/handler/EvidenceCollectionSource.capture | 原样复用工作树字节摘要、index、HEAD、路径集合；不以porcelain替代 |
| ledger/EvidenceCollectionBaseline | 复用源码及qualified contract身份字段，不新增平行源码模型 |
| runtime/handler/EvidenceCollection | 复用CLI/API同源gate入口、执行前后及publication守卫；增加外部材料消费模式，不改变普通collect |
| ledger/WorkflowFileLedger.collectEvidence、RoutingEngine.resolveOpenGate | 增加专用typed提交入口；普通resolve不能凭action字符串或note伪造材料 |
| RoutingEngine:411+ collection marker传播 | 两种补采动作共用marker传播与失效规则，不另建状态机 |
| HumanGateActionSemantics | 合法动作唯一来源，scope violation gate不提供材料提交 |
| CliNextActionHints:27-34 | 删除固定approve/revise提示，消费真实资格 |

目前RoutingEngine先写gate决定，再完成node与routing；本批不假定它是跨文件原子事务。部分写入必须拒绝重复执行并保留可诊断状态，不能用latest扫描补救。

## 3. 用户操作：先建立基准，再提交结果

不接受“测试做完后才抓当前源码，便宣称材料属于它”。流程分两个必要操作，均复用当前gate，不增加workflow节点：

1. `review prepare-evidence`：当前等待gate下，核对qualified contract及动作资格，capture并立即复验源码；原子保存不可变准备记录，返回ref/SHA。gate仍OPEN，无provider、无命令执行。
2. 操作者在相同工作区运行验证；输出写入指定的非源码位置，保留日志/XML/截图。记录实际命令、环境、开始结束时间、退出状态及用途；不得声明失败测试通过。
3. `review submit-evidence`：显式指定准备ref/SHA、有限的本地材料文件及用途、执行说明和note；先重验当前源码与准备基准，再复制材料并hash保存字节，最后锁内再验并解析gate。后继为既有self-test，不是implement。

准备记录是必要的测试前快照，采用既有artifact/ref/hash方式；不新建approval状态、session或通用任务平台。
字段固定为既有EvidenceCollectionBaseline（gateNodeInstanceId、contractRef、contractSha256、head、indexSha256、sourceSha256、paths）加recordedAt；提交必须匹配当前gateNodeInstanceId。执行开始/结束时间是操作者声明值，不冒充机器观测时间。
准备文件仅为待采用材料，只有gate决定绑定后才有续作权威。重复prepare可返回同一内容地址，旧准备不会自动成为当前有效授权。
外部环境如果会改变源码，必须回正常修复，不得重抓基准来洗掉变化。

### v1入口取舍

CLI完成本地材料摄取；runtime共享服务拥有全部校验。v1不新建Web文件上传功能，server/UI只读展示准备/已绑定材料及CLI操作入口，不接受任意服务器文件路径，不展示一个不能执行的submit按钮。
这是显式界面能力差异，不是第二authority。现有普通gate动作和collect在CLI/server原样可用。是否必须本批支持网页上传交由评审挑战，不为界面齐全破坏最小范围。

## 4. 材料及证据保存规则

使用workstream内同一 `artifacts/verification/` 目录约定；准备记录、固化材料各用内容地址和原子写入，不覆盖稳定文件名。
材料描述必须含相对ref、保存字节SHA、大小、用途；集合绑定准备ref/SHA、执行说明与人工声明。不是新agent输出schema，也不把自由文本当控制信号。

- 输入只接收操作者显式指定的普通本地文件；拒绝symlink、目录、URL、路径逃逸、重复冲突ref，固定上限32文件、单文件8MiB、总量32MiB，超限明确拒绝，不静默截断。
- 不扫描或自动导入HOME、整个target、环境变量及完整敏感日志；操作者选择可披露材料，工具不声称具备自动脱敏保证。
- 内部自测原始报告也沿同一ref/hash习惯保存；修改hygiene为“先保留本次必要报告，再清理副产物”。不建立第二报告仓库或通用格式解析器。
- 材料中的脚本/文字都是不可信输入，不能直接执行；外部原文件后续变化不影响已固化副本。
- 输出位置不得进入源码基准集合。源码路径、合同中忽略文件仍按既有基准规则保护，不能扩大ignored豁免。

## 5. 下游必须实际消费

提交决定在gate node result绑定准备/材料ref和SHA及原EvidenceCollectionBaseline，沿既有输入链传递。
调用provider前强制校验源码、准备与全部材料；prompt只展示已验证材料及用途，不让模型挑latest。
发布self-test sidecar前再次机械校验，并要求该结果引用本次绑定材料（使用现有evidence字段，不另开模型schema）；机器只能证明引用和身份，不能证明结论可信。
引用判定为每个绑定ref在evidence字符串列表中的精确成员匹配（String.equals）；ref加前后缀、仅在正文嵌入ref或仅提文件名均不算引用，不使用contains/substring。

外部验证模式明确：先检查提交的运行记录，不要求在同一个已知不适用的沙箱重复同一测试。可做必要的只读核对；不能把上传或人工声明直接转成SELF_TEST_COMPLETE。
材料不足 -> SELF_TEST_INCOMPLETE且must_fix空 -> 原human gate；具体产品/测试修改项 -> 既有must_fix修复路径；COMPLETE -> 原scope/acceptance链，acceptance仍能拒绝。
acceptance须可追溯同一材料，不能只看到模型摘要。这条传递若现有refs不足，先扩展既有投影，不引入全局latest文件。

## 6. Transition及失败切面

| pre-state / action | 顺序与post-state | 失败解释 |
|---|---|---|
| 当前OPEN / prepare | 锁内资格与源码 -> 原子准备文件 -> 返回ref，gate不变 | 写失败无决定；准备孤立不授权 |
| OPEN / submit | 验准备/源码 -> 固化材料 -> 锁内重验当前gate/全部hash/源码 -> gate绑定 -> routing | 决定前失败保持OPEN，材料孤立；不读孤立材料续作 |
| 决定已写 / node或routing中断 | 依既有权威读取，不重复决定 | 明确部分状态拒绝、零重复provider，不承诺自动恢复 |
| 后继等待 / execute | 重验绑定 -> 原self-test -> 再验 -> publication | 缺失/篡改/源码漂移typed拒绝，无有效验证发布 |
| 自测仍不足 | 正常返回human gate | 不自动重跑同条件，不回实现；新提交需要当前gate身份 |
| 重复submit | 同一请求身份与内容可查询原结果，不重复routing | 同id异内容、旧gate、已消费后伪造请求拒绝 |
| prepare后revise/abort | 走原动作，准备仅孤立记录 | 旧准备不得提交到新gate；终态不得执行 |
| retry/restart | 从bound refs冷读并复验，不重抓基准 | 材料或源码变化仍拒绝；不以mtime/latest恢复 |

实现前须将上述每行对应到真实写入点测试；如现有gate部分写入会导致重复执行，先做有界修复或停止，不加入通用事务引擎。

## 7. 必须一起收口与可分开的工作

一个设计单元：测试前基准、材料固化与保留、gate绑定、self-test/acceptance消费、拒绝/冷读序列，以及“所有必需验证有可用出口”的Gate就绪规则。
CLI提示独立小修，可同批交付但不是材料存储的技术依赖。
作者聚焦测试职责后续独立收紧，本批不重写实现流程。

就绪规则：对需求必需且实际环境尚未证明的能力做短检查；复用已有同身份证据，不每轮跑全能力矩阵，不以30秒固定判provider失败。
verify-workspace的tracked-clean只属于提交后交付检查，不得列为未提交实现阶段的必过条件。材料也不能让未来阶段的检查提前成立；验收需明确适用阶段，不为此提前commit。

## 8. 生产变更面与sibling核对

限定在core动作语义/loader、ledger专用入口与routing、runtime EvidenceCollection及材料保存、已有prompt/validator/packet、CLI准备/提交入口与动作提示、server/UI只读投影、四个支持definition及测试、两份hygiene提示和就绪规则文档。
不改fork、provider配置、网络policy、产品代码、模型层、任务需求、delivery状态模型。
实施前列出上述类别的实际文件清单；超出类别或需要新运行时即停。不把本计划的类别清单伪称最终精确文件清单。

sibling：普通collect/revise/request_action保持；scope violation拒绝提交；初始自测与验收再入均可消费正确绑定；inline/worker/publication三处守卫；四definition动作+目的地一致；autorun/scheduler不可产生prepare/submit人工授权；CLI/server/UI均不虚构可执行能力。

## 9. Cannot-bypass验证

1. 完整序列：prepare -> 外部材料 -> submit -> self-test引用材料 -> acceptance可读同ref；不能只测上传返回成功。
2. dirty同路径A改B且HEAD/porcelain相同，提交必拒；untracked、删除、执行位及合同ignored路径同规。
3. 准备前执行的旧材料不得由系统宣称有机器证明；缺人工执行关联声明拒绝，声明真实性边界如实记录。
4. 源码在prepare后、复制中、provider前/后、publication前漂移，各拒绝。
5. 材料不存在/篡改/越界/symlink/超限拒绝；原外部文件变化不影响固化副本。
   准备记录本身被替换、字节篡改或传入SHA不匹配同样拒绝；必须断言gate/current不变且零provider，不仅检查异常。
6. 每个写入边界故障注入、重复/异内容请求、abort/revise后旧准备、冷JVM重放；禁止部分状态重复启动。
7. self-test材料不足再停门；报告有具体must_fix才回实现；禁止上传直接COMPLETE或ACCEPT。
8. 下游仅摘要而无材料ref的反例必须失败；移除publication材料校验的突变应使测试红。
9. 同边界sibling及CLI非法approve不展示；无网页上传时不误展示submit执行按钮。
10. 自测报告保留后清理，保存字节SHA复算；禁止“测试结束先删XML再写总结”。
11. 去业务化fixture以设备测试日志/一般单元测试XML代替OKX字段，生产代码不得出现业务域名/项目标识。

## 10. 实施门槛与顺序

CC先评审本方案，重点挑战准备记录必要性、CLI-only范围、现有baseline复用、材料实际消费、失败切面及更小替代。
通过后：精确文件sweep -> failing sequence tests -> 共享服务与ledger -> 消费/保存 -> 界面提示及文档 -> focused/full reactor/diff-check -> 只读diff review。
不以新real-case代替上述离线序列。只有完整链已实现并验证，才更新复用Gate的身份与增量条件。
当前成熟度：外部材料续作ENFORCED / DETERMINISTIC SEQUENCE PROVEN；自然样本NOT EXERCISED。源码字节基准未降标。不能外推自然E2E通过。

## 11. 首批实施记录（历史时点）

本批只完成两个前置反证和CLI提示减法，不宣称材料回路已实现。

- 源码身份反例使用真实Git工作区：dirty tracked和untracked文件分别改变内容，HEAD、index、porcelain与路径集均不变，而既有capture的内容摘要改变。保留现有完整字节身份，不引入HEAD+porcelain替代。
- CLI sibling sweep发现两处硬编码：CliNextActionHints与ReviewPacketCliHints。二者现共用后者的只读提示函数，消费当前OPEN gate对应definition和source_outcome的HumanGateActionSemantics.allowedActions；读取失败不生成mutation命令，不变更gate决定机制。
- 两个CLI反证先在旧实现失败，再在新实现通过：repair gate不展示非法approve；无账本时不臆造approve/revise。普通approve/revise提示的既有测试保持。
- 聚焦测试14/0/0/0：EvidenceCollectionSourceTest 4、EvidenceCollectionSequenceTest 7、ReviewPacketCliHintsTest 3。这是既有collect与CLI提示证明，不是prepare/submit完整序列证明。
- 回归经过两次修正验证：受限环境的两项进程枚举测试因sysctl权限失败，放开本地离线测试权限后通过；全量运行随后发现CliNextActionHintsTest仍断言旧参数顺序，已同步到共享提示输出并重新跑CLI全部86例、server全部71例。各模块最终分段通过合计1468/0/0/0，不宣称一次full reactor全绿；git diff --check通过。

首批结束时材料链尚未实现。随后已完成prepare/submit、固化材料、self-test精确ref消费和acceptance追溯；最新结果以checkpoint为准。245D继续只读；不调用provider，不提交或恢复real-case。
