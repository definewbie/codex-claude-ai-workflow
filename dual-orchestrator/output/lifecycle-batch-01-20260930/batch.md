# B01：W02 执行原因→候选校验→发布→恢复（2026-09-30）

用户授权：设计冻结后“如果不需要 [CC review]，可以进行下一步动作了”。进入冻结清单的首批实施；不授权提交、真实 provider/原生实验或额外 agent。工作区 HEAD 3362eb421514e75fdc4332f570df5bf274b3725e；未提交生产/测试内容与 9·27 对账基线一致，新增设计状态另行维护，完整开工内容身份见 before-source-sha256.json。

## Boundary Review / Analyze

- P0，部分 Implemented，未整体 Verified/Promoted。本批推进 R3 I2 / 详设 D02 §5.1–§5.3 的执行阶段保真与 E02 常驻；W01 已有合法/非法续作作为共享链回归，未声称实现 D01。
- Execution 的 transport process/signals 是事实；AgentExecutionOutcomeResolver 是唯一结果政策；ArtifactContractValidator 持有候选领域校验与发布；run finalizer 持久化该次已判定事实；Instance/task/execution 完成与 RecoveryAssessment 消费同一结果。Definition/业务批准/身份契约不改。
- 已确认偏离冻结设计：先判发布资格→构造假的 EXECUTION_NOT_PUBLISHABLE 合同失败→再次 resolver 导致 E02；incomplete process 排在 envelope/artifact 之后。不是另建状态模型的问题。
- 改动：同一现有 validator 内先准备校验结果与待发布动作，再唯一判定/记录，再按资格执行发布；runtime/worker 共用此入口，删除第二次结果判定。使用内部短期返回值组织步骤，不新增耐久 authority/管线平台。
- 原 validate 调用保留为校验/发布接口；产品 runtime/worker 显式消费 validateAndFinalize，在发布前写 run 结果。纯校验失败仍保留真实诊断；发布失败作为 I/O 失败，不改写模型结果。
- W02 尚依赖 D05 的 permission 精确非必需证明与 W05 的多文件提交/恢复，旧 legacy run fallback 也未因此获新组合准入；本批不关闭 W02/I2。不得把已有 contained-warning 测试当冻结 D02 义务已证。
- 跨层触发：core outcome→runtime validator/finalizer→ledger/recovery，R3 §2 多 owner 与 §4 执行/发布 transition；批末须用户发起 CC 对实际版本评审后才可收口，不自动委派。

## 完整 transition 与失败切面

| 前态/动作 | 按序事实、写入与下一消费者 | 失败与恢复解释 |
|---|---|---|
| 输入资格准备 | 可信 preparation-failure 或实际 transport 执行记录→validator | 无 provider 的失败保持原 code；不伪造 JSON 缺失，原 W01 补足续作仍合法 |
| 外部结束、候选存在 | 内存 domain 校验→resolver 消费 process/signals/envelope/artifact，最多一次 | 活跃阻断、timeout/interrupted/incomplete 不被衍生缺产物覆盖；坏候选诊断真实保留 |
| 唯一 outcome 确定 | run finalizer 持久化 outcome、合同事实、timeline→发布守卫 | run 写失败抛持久化错误；零新 sidecar，不调用 provider 补写 |
| outcome 不许成功 | 返回相同失败及真实候选校验→task/execution/node→RecoveryAssessment | 无 sidecar/无 reviewer；不得将没发布写成合同失败；正常人工恢复仍经原资格 |
| outcome 可成功 | 写 sidecar/proposal/plan→task/execution/node→路由 | sidecar/派生文件失败保留已确定 run outcome、workflow 不成功；I/O 不伪装模型格式失败；D06 幂等续完尚属 W05 |
| 发布后 ledger 写失败 | 已有 sidecar/run，未完成 Instance | 恢复不得仅凭 sidecar 调 provider/reviewer；现有拒绝反证不当作未来冷进程可续完 |
| 正常发布完成 | 相同 outcome 与候选身份进入后继/独立复评/批准 | 成功对照防全部拒绝；严格身份和终态拒绝不放宽 |

兄弟检查：inline/worker、有效/坏/缺候选、活跃/已恢复 signal、process 完整/timeout/interrupted/incomplete、成功/已有 warning、run/sidecar/node 写入失败、恢复投影、W01 人工修订。旧样本不恢复，新旧 binary 恢复仍按冻结 D06/D07 未来规则；本批不宣称兼容或重放新协议。

## 验证设计（执行前固定）

1. 常驻 E02 inline/worker：incomplete+合法候选，无 sidecar；node/run/recovery 都保持 process_output_incomplete，candidate 合同校验仍有效。
2. core 矩阵：incomplete 与缺 envelope/坏 artifact 的交叉，blocking signal 优先，完整坏产物仍为真实合同失败。
3. 正向完整结果可发布、run 只记一次合同判定；已有 warning 不被无差别拒绝；真实 bad artifact 无成功发布。
4. run 写失败→无新发布；sidecar 写失败→run 结果保留、节点不成功；发布后 node 失败→后继不启动，进程内重建不误称冷 JVM 恢复成功。
5. W01 合法 revise→同作者→复评→批准与错误身份拒绝，四变体正常链、terminal、CLI/API 统一 recovery 集合按受影响范围复跑。

未变准入阻塞：W01 的 D01/W05 耐久配对、W03/W04 分期批准/验证出口、W05 本地恢复/身份、W06 交付窗口、W07 回显减负、W08 组合及 W09 真实 Gate。仅 E02 与本批覆盖路径可据实际结果更新；无真实运行前提被本批自动解除。

## Review → Reflect → Analyze：恢复提示兄弟缺陷

执行层反证转绿后静态发现 `RecoveryAssessment.fromAgentExecutionOutcome` 把所有 OUTPUT_INCOMPLETE（含 process_output_incomplete）归为 RETRY_AFTER_OUTPUT_CONTRACT_FIX。按 D02 决定性原因矩阵，过程失败应先核对执行环境而不是要求模型重写 JSON。保留同一 RecoveryAssessment owner 与现有 readiness 值，仅区分已声明的 typed primaryReason；CLI/API 增加真实 ledger 投影的正反对照。此为原 W02/T06 范围内缺陷，不扩大验收、不新增状态机。

## 批末自查（最终离线回归通过；后续 CC PASS 见收口登记）

- 设计/代码映射：D02 原因矩阵→core 优先级；§5.1 先校验/唯一判定/先记录后发布→validator 内部临时 Validation/Publication；inline/worker 两入口均迁移，无第二次 finalizer；RecoveryAssessment 决定 readiness，attention 仅映射该 readiness 为说明，CLI/API/UI 不新增控制事实。
- 临时 Validation/Publication 不落盘，不可由 agent map 注入，不是新领域模型或可恢复发件箱。既有 validate 的无 run 持久化接口供合同校验调用；产品两条执行路径强制走 validateAndFinalize。没有为了消灭测试而引入兼容旧 ledger。
- 相对本批前工作区增量见 incremental.patch / changed-files.json；含先前 dirty 的文件保留其原修改，不用 HEAD diff 把两轮工作混成一批。全源指纹检测确认其他源码不变。
- 过程中的两条旧断言按冻结语义纠正，并保留完整过程真坏输出/缺 envelope 的对照；不是读取当前实现生成测试预期。
- 首次全模块回归的两个 process-tree 测试因执行沙箱 sysctl 权限失败（不是产品断言失败）；仅为离线回归使用允许枚举测试自建 sleep 子进程的环境。没有恢复 D04 原生实验，没有使用真实 provider。
- Sibling Sweep：原始准备失败与正常人工 revise、活跃阻断 signal、缺/坏 envelope、候选不发布、真实合同失败、可信完成警告、两执行入口、四变体/终态、run/sidecar/node 失败、共享恢复和 attention/CLI/API 已纳入检查。UI console 静态消费 overview.recoveryAssessment；未进行浏览器视觉验收，不据此关闭 W08 UI/report 组合义务。

六维：Declared=R3 I2/D02 §5/主清单 W02；Proven=本批先红后绿及最终 183 份 XML（见 test-results.json）；Consumed=resolver→validator 内统一结果→run/node/task/execution→RecoveryAssessment/attention；Enforced before=不合格 execution 零 sidecar，run 记录失败零新发布；Failure route=保留 typed 原因/真实 I/O，未知和多文件续完仍受 W05 约束；Cannot-bypass=E02 两入口＋移除发布会被正常/警告序列抓住＋run/sidecar/node 写点拒绝。

保留未完成：D01 决定/拒绝配对、D05 permission 精确关联与必需义务证明、run 缺元数据的旧 fallback 删除、D06 原子多文件与冷启动续完、D07 外部未知裁定。此为主清单既有待实施项，不宣称已闭合或以 TODO 关闭，不允许这批独立解锁真实 Gate。当前技术 NO-GO 不变。

批次跨层，用户已带回 CC 独立评审 PASS，LOW-1 日志引用已定点核实，见 [收口登记](cc-review-disposition.md)。本批按 R3 §7.1 收口；不自动实施其他批次、合入或真实开跑，不重开已冻结整体架构。

## 最终验证与证据索引

- 最终命令：`rtk proxy mvn -o test`，退出 0，8 个模块 BUILD SUCCESS；1,550 tests / 0 failures / 0 errors / 0 skipped，183 个 suite。只汇总本次启动后生成的 XML，与 reactor 各模块总数一致，不混入旧报告。
- [test-results.json](test-results.json) 记录命令、模块统计、报告 SHA 和源码快照身份；[reports/](reports/) 保存本轮原始 XML；[reactor.log](reactor.log) 为最终完整日志。测试后所有已快照源码指纹不变。
- [incremental.patch](incremental.patch) 和 [changed-files.json](changed-files.json) 是相对本批前 dirty 工作区的 13 个源码/测试文件增量；其余快照源码未变。HEAD diff 包含更早未提交工作，不能冒充本批增量。
- [red.log](red.log)、[red-sequence.log](red-sequence.log)、red-reports/ 保留 core 与双入口 E02 先失败的反证；最终 E02 常驻转绿，真实坏输出对照保持。
- reactor-sandbox-limited.log 记录两项 sysctl 环境错误；reactor-old-recovery-expectation.log 记录中途发现的旧恢复断言。最终日志覆盖修正后的实际源码，未隐藏过程失败。
- 本批结果：阶段保真、一次结果判定/记录后发布、相应恢复提示已实施并经离线自查；独立评审 CC PASS，本批收口（见 cc-review-disposition.md）。W01/W02 未整体完成，W05 耐久协议等依赖仍未完成；无真实 provider/native 运行、额外 agent、提交或历史样本恢复。

## CC 评审入口（已完成，保留原提示词）

请对本仓库 B01 实施做只读独立评审，遵守 AGENTS.md。入口为本文件、incremental.patch、test-results.json，以及主清单 §10；对照冻结 D02，直接核验当前源码。增量基线是本批开始前的 dirty 工作区，不是 HEAD。

自行重建输入准备→执行事实→候选校验→结果记录→发布→node/run/recovery 的控制链，检查合法成功/警告、非法拒绝、各写点失败、重启解释与兄弟入口；同时检查 W01 续作回归及清单是否虚报完成。按 Declared by / Proven by / Consumed by / Enforced before / Failure route / Cannot-bypass test 给分级发现、证据和未核验范围。自查与测试数不预设评审结论。

不改文件、不启动 provider/native 或额外 agent；无需重审无关历史及已冻结 D04 整体设计。本次独立评审不自动授权提交或真实运行。
