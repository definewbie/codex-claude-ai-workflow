# W08 准备批次：G01 修正、G02–G04 与具体 Gate

2026-10-10；P0。用户明确批准准备批次；**AUTHOR_SELF_REVIEW / AWAITING_CC / REAL_RUN_NO_GO**。本轮实现和元数据核对不构成真实运行、预算、安装、目标Git、提交或额外agent授权。

## 1. 当前结论及总体贡献

G01 作为前向接线缺陷已纠正：GOAL 下 canonical 文件/操作是设计提案，EXECUTION 才授具体实现范围。修改既有 prompt/template owner，不改身份、scope、compiler、批准/事实、验证器、router或恢复协议。合法续作与非法拒绝有实际qualified输入渲染和程序流程对照；这消除了首个真实case开设计时的已知指令矛盾，**不证明真实模型必能完成任务或80%成功率**。

G02已获得实际本仓 acpx build-info/config/help；G03逐条出口、生产入口与消费时序具名；G04完成本轮新发行核验和内容身份候选。最终新target、实际启动/协议与工具观察、当前GOAL/readiness引用都只能在未来授权运行中产生，不在材料中冒填。

G05具化为一个小型换行规范化任务、新临时target/worktree、18顶层调用/60min主动窗口/160000可见实际token候选限额、关键/最终门用户决定和实际commit责任。均待用户批准；本轮交CC合并受影响评审，不另开权限/安全/预算平台。

## 2. 验证与证据分层

- 修改前当前654项输入与上轮W08交评身份相同；356条dirty状态保留。相对批前增量共14文件，其中只改12个既有source/resource/test文件（10个提示生产相关文件、2个测试），详见 changed-files.json / incremental.patch；文档更新单列。未覆盖其他脏改动。
- 先红：实际qualified GOAL仍含旧复制指令、qualified EXECUTION缺明确精确约束，两项预期断言失败/零error，原XML/log保留。
- 聚焦：首轮 98 tests / 7 suites / 0 failures/errors/skipped；预算纠正后扩入 WorkflowIntegrationTest，108 tests / 8 suites / 0 failures/errors/skipped，覆盖提示、合法提案、非法/control/身份/不批准拒绝、readiness→作者→复评→新批准、manifest与correction。不是只测substring返回值。
- 全模块单轮：**1,755 tests / 206 suites / 0 failures / 0 errors / 0 skipped，exit 0**；654项输入前后相同，全部XML新鲜，原样归档于 reactor-reports。源码前后身份需一致，不能拼旧绿灯。
- 本轮离线发行/真实JAR元数据核验：package exit 0；CLI/server各六个内嵌项目模块与本轮模块JAR字节一致，两个应用自身class/resource逐项一致；全部14个adapter/runtime资源一致；新CLI三次help及三个定义validate共6次、exit均0。package跳过测试不作第二份回归；发行内嵌模块与修正模板/资源逐字节核对。
- 实际 acpx **仅元数据**：build-info与当前受审1517f828一致，resolved config与两个permission-reply/help在位；source/dist静态声明不再代替本次实际输出。未执行Claude/Codex客户端、创建native session、调用provider或installer。
- 宿主Python/stdlib可启动/加载为实际本地程序观察；原生工具在该环境执行仍未证。不能把host检查或operator readiness升级成产品测试证据。

完整计数/报告及内容身份见 test-results.json / release-identity.json / material-sha256.json；历史B01–B06凭证原样保留。

## 3. Owner 与同族核对

| 表面 | 原漂移与本次动作 | 权威及严格边界 |
|---|---|---|
| design模板/renderer共享字段 | 从“复制已批准实现范围”纠为GOAL提案/EXECUTION约束 | DesignScopeValidator仍 canonical/excluded/control 强制，GOAL不授write |
| design/plan reviewer与plan作者 | 不再要求尚不存在的已批精确文件清单；评审不等于执行批准 | 原 reviewed_candidate/lineage与EXECUTION compile/人门 |
| intake作者/原则/typed示例 | 初始scope.included是候选路径，不能自动成为授权；具体格式仍closed | Proposal validator/compiler决定真实GOAL/EXECUTION，不让自然语言路由 |
| implement | 明确复制已批准EXECUTION文件/操作，设计/计划不能授权额外修改 | 原qualification/dispatch/业务scope采纳检查；不称OS写前沙箱 |
| RepoScopeGovernance文案 | GOAL提案与实际repository edits分开 | 只改指引字符串，未改scope policy或权限算法 |
| 自测/验收/handoff/correction | 未发现同型精确scope前置矛盾；保留既有证据/受限correction | correction用当前typed contract，格式修正不能制造批准/观察 |

新qualified GOAL渲染通过真实fixture/程序人决定，三种plan拓扑×四个设计/计划角色，included为空而canonical提案仍到独立reviewer；实现渲染只在真实EXECUTION批准后。输出未运行provider，仅程序夹具，无scope豁免。四variant/inline-worker/失败写点/冷JVM等由当轮常驻全回归覆盖，不能外推真实native行为。

## 4. G02/G03/G04：必要条件与剩余动态核验

- 装配：明确runner使用本仓wrapper、timeout600、liteflow。resolved config默认approve-all不能当许可；原transport具名profile产生approve-reads/deny-all、explicit fail/policy，源码flags显式选项优先于default。contract_discovery走simple Claude、不启C/X；correction deny-all；其余适用lane须实际发生ask才证明，未发生记NOT_EXERCISED。旧audit历史不改，也不锁死升级版本。
- 验收：verification-exits.md及criterion-exit-candidates.json给AC-1/2真实unittest、AC-3真实read/例子核对，executor/evidence_kind/method/exit口径明确。现有record-readiness入口在fresh GOAL后的原等待门生产有效引用，登记→原revise→作者/独立复评→EXECUTION编译/批准。动态ref/SHA占位不是schema可执行样例或虚构事实。CLI只返回ref，SHA取同次已登记L04事件并重核记录字节，不能假定命令已输出。宿主依赖sha支持可用性声明，不保证native工具；缺工具停止，若转operator出口须改变合同并重批，不能自动替代。
- 发行：固定本轮source/resources/module/CLI/server JAR与选定wrapper/config元数据；Gate最终workspace复核config/launch/PATH/实际R01。单INLINE runner/单JVM路由/单ClassLoader是本次范围约束，实际PID/kernel/code-source/loader在启动后核原身份；不先造另一个ledger/daemon来证明它。

## 5. 控制链、失败切面与六维

修改前分析和写序见 analysis-before-edit.md；本轮未修改L/R协议。typed intake候选→原GOAL compile→人批准→只读作者/独立复评→计划/readiness补齐→EXECUTION compile→人精确批准→原资格/dispatch→R02/R03真实观察→scope/证据/独立验收→最后人门→原L07/Git/人工commit/finalize。每个原本地写入前缀仍固定字节补齐；外部UNKNOWN不重发、不靠缺日志推导零执行；权限C/X和交付fresh资格未动；新binary/旧ledger不支持时保持INVALID。

| 六维 | 本轮有限结论 |
|---|---|
| Declared by | 用户准备授权、分期批准R3/D03/D10、CC G01/LOW-1 |
| Proven by | 实际qualified提示先红后绿、严格程序流程成对、当轮单轮全回归、metadata/发行核验；真实模型未证 |
| Consumed by | 当前renderer/typed output→原validator/compile/qualification/recovery；Gate索引仅定位原事实 |
| Enforced before | 原validator采纳/发布前、qualification实现dispatch前、人门/交付前；prompt纠正不冒称新硬权限机制 |
| Failure route | 原D02/D06/D07/D09保真与恢复；不齐前提或新authority/成本偏差停止。无历史rescue |
| Cannot-bypass test | GOAL旧指令负向、EXECUTION精确指令正向；提案不写/未批准零实现/非法路径无发布等常驻；并非每条prompt句子的删除mutant |

Boundary Review：本修正离线自查PASS（等待独立判断）；真实Gate仍NO-GO。模型/权威边界未新增。其他族UNKNOWN、首版全量声明/局部补声明入口、kernelI/O、browser视觉/WORKER真实能力与时钟一次性证明继续按原触发/义务登记。

## 6. 自查纠偏与下一步

首轮全模块 FAIL：acceptance 上下文6555字符超既有6500预算；精简重复 passive guidance 67字符，未改预算/守卫/断言，原失败日志/XML单列。修正后的108项聚焦通过，再执行全模块新单轮，不拼旧绿灯。

发行核验脚本首次在扫描 *.js 时遇到同名目录，release索引生成中断；这是验证脚本错误，非生产失败。已保存 release-verifier-correction.json，用实测wrapper所指的具名dist重核完成，未重跑CLI/测试或改发行。

本轮还发现旧Gate草案把正常主链写成8个agent节点，实际9个；不增18次池，只明确两读取前缀使用后剩7次额外容量；本候选readiness补齐若引发计划作者/复评重跑2次，剩5次。readiness必须真实GOAL/等待门后登记，不能在fresh创建前要求假ref。另提供task-requirement.md作为纯产品输入，Gate/harness材料不得混进用户需求。这些均并入本次合并评审，不另开材料轮次或偷改验收。

下一步：用 cc-review-prompt.txt 让CC独立评审G01修正、证据与更新Gate的可执行性和ROI；随后按发现纠偏，向用户提交G05具体任务/目标/预算与批准责任。评审PASS也不等于真实运行许可；本轮尚未创建目标/运行模型/安装/提交。
