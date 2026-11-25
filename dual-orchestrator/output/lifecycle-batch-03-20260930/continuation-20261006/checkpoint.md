# B03 后续迁移检查点（2026-10-06）

状态：IN_PROGRESS。本文是当前代码及离线证据的评审入口，不是 B03 PASS、独立评审或真实运行授权。用户授权完成运行身份装配、准备/执行/恢复、失败返修和消费者迁移；没有启动额外 agent、native/provider、提交或历史样本恢复。

后续状态更新（2026-10-06）：用户已带回本检查点的 CC 材料层 PASS，非 B03 整批 PASS。随后明确采纳 [D05 逐项证据要求决定](d05-evidence-decision.md)，解除下文历史状态中的“待用户确认”语义阻塞；接线、原生运行身份核对、最终干净全模块回归和批末评审仍未完成。CC 将完整 C/X 桥接同时列为“非本批”和本批阻塞，按 batch.md 已批准范围校正：B03 承接相关字段及生命周期义务，完整 native 桥接是总体后续工作，不因本次评审自动扩入本批。此次仅回写决定，无新增测试或能力证明。

## 对总体目标的贡献与范围

目标仍是 P0 生命周期可靠性：同一合法研发任务可以从目标批准推进到精确执行批准、作者/独立复评、验收与交付；失败有真实续作出口，中断不重发不明的外部执行。此次把原来相互脱开的合同、节点、队列、run 和查询消费者接到统一事实与写序。没有把 D04 恢复成安全沙箱，也不把本地成功测试当真实 agent 证据。

详细转换/写入失败语义在 [transition-plan.md](transition-plan.md)，未竟项在 [migration-obligations.md](migration-obligations.md)。增量相对开工时 **dirty 内容** before-source.json，包含 tracked/untracked，不是相对 HEAD 混算之前工作。本次续作 incremental.patch/changed-files.json 为 202 个源码/资源/POM 文件；另提供 batch-cumulative.patch/batch-cumulative-changed-files.json，对 B03 原始 before-source-text.json 的累计 252 个源码/资源文件。B03 原始文本基线没有 POM，累计 diff 不伪造这部分 before；3 个 POM 的本次变化见 continuation 增量，adapter POM 的更早改动不冒领。本次 hash-only 的两个基线资源确认未变，不误列新增。二者不包含无关既有文档改动，不能混称从干净 HEAD 开始。

## 已落地的控制链

1. CLI/server factory 显式注入 ledger、实际 kernel 与运行内容身份；L00 固定完整初始化，后续读写会话核验同版。删除隐式 builtin 构造。Java 开发 classpath 内容与顺序、发行 CLI/server 成对工件及共享依赖均纳入；不以 HEAD 或版本字符串代替。
2. L02 唯一 claim → R01 不可变准备和输入引用 → 短 L 绑定 → R02 调用意图 → 外部结果的一次判定/固定发布计划 → R03 固定完成 → L03 同次更新 task/execution/node/后继/event/current。inline、worker 和明确恢复共用消费者；R/L 锁不嵌套持有。
3. 外部意图已有但结果不明保留 UNKNOWN；仅 PID 消失、mutable run 的 success/exit=0 或模型产物都不授自动重试资格。停止证据及人工裁定后才允许新 attempt/终止；采纳仅接受原执行的固定完成。采纳保留先前 UNKNOWN，不重开旧许可，不调用 provider。
4. readiness 在计划明确后补齐；缺项不会堵死 GOAL。编译、批准与 dispatch 核验相同 GOAL/epoch/引用与可观察依赖；失败后的显式 revise 进入计划作者→复评→同授权核验或重新执行批准。scope 或合同语义变化必须走新批准；旧证据不借给新身份。
5. query、CLI/API、补证据、最终报告、交付消费者使用显式会话/不可变合同资格。BUSY、PENDING、INVALID 不回退旧缓存/旧 schema。终态 resume/retry/worker 不改状态，也不因空恢复会话重写投影时间。

## 六维与证明边界

| Declared by | Proven by | Consumed by | Enforced before | Failure route | Cannot-bypass |
|---|---|---|---|---|---|
| D06/D08 同版会话与本地提交 | LedgerSession/WorkflowCommitSequence/ExecutionClaim；FrozenRuntimeIdentity、CLI/server factory；发行包检查另列 | 显式 WorkflowFileLedger 会话与原 writer | 读取混代状态、认领和本地提交前 | before-manifest 保留旧态；pending 只补固定字节；漂移 INVALID | 冷 JVM、逐写前缀、重复 claim、错误身份拒绝，合法跨入口读取对照 |
| D07 准备/意图/完成与未知裁定 | AgentRunCommitProtocol、AcpxNativeCommandSequence、PhasedContractContinuation、WorkflowFirstLifecycleSequence | RecordedExecutionRecovery + AgentExecutionCompletion；inline/worker/stale | provider dispatch、完成路由、新 attempt 前 | intent 无完成为 UNKNOWN；已固定结果仅本地续完；明确停止/裁定 | mutable run 诱饵、错输入/输出、重复发送、原执行仍活拒绝；原完成合法采纳 |
| D03/D05 readiness 与返修 | VerificationReadiness、PhasedContractContinuation、AcceptanceRepairSequence/Interruption、HumanRequirementContinuation | compile/qualification/ReviewGateActions/准备消费者 | 执行批准/调用前；不是 OS 写前防护 | 缺项进原计划门或明确 recovery；返修重新独立复评 | 正向注册/复用/恢复、错 epoch/撤回/依赖漂移、越范围重批、缺 route 拒绝 |
| 合同与投影同源 | LegacyContractConsumerRejection、真实批准链 report/context、CLI/API/query 回归 | TaskContractProjectionVerifier + 同一 session/read model | context/report/采纳/推进前 | 明确资格失败，不退到 artifacts/task-contract.json | 未批准稳定副本诱饵无效；合法批准合同正常显示；终态全文件 SHA 不变 |

这些证据不覆盖 D05 实际机器执行证据成立，也不证明已实施 D04 原生人工权限桥接；不能据此关闭 W03/W04/W05/W08。

## 批中纠偏记录

- 缺 authored route 曾退回最近 authority：改为唯一创建 route 及逐级谱系核验。反证 consumer-39。正向 readiness repair 在 consumer-40 揭示“所有父节点必须成功”过宽；现按具名 repair 的 FAILED 父输入/决定/route/SHA 检查，未放宽普通 route。
- 终态全文件比较在 consumer-50 发现空会话刷新 metrics 时间：改为实际 mutation 或显式 refresh 才更新投影，保留终态断言；consumer-51 转绿。
- 从 mutable run/PID 猜完成的无调用旧恢复类及其机制专用测试删除；原行为义务由 R01/R02/R03/UNKNOWN 的正反序列承接，详情见 migration-obligations，不能把删测当修复证明。
- 报告、失败分类等旧夹具用完整批准链重建；旧批准占位文本改成明确拒绝。测试的合法期望来自批准用户流程，不把缺 schema/缺证据伪装兼容。
- B02 LOW-1：新增真实 createTempFile 失败后同 JVM 恢复路由；loadInstance/holder.clean 内部故障仍无注入实证，不声称该族全部证明。

## 尚未完成与不能声称的内容

- [D05 待确认的机器执行证据语义](d05-evidence-decision.md)：固定 Claude ACP 的部分 exit 字段是原生成功/失败状态的合成值，不是子进程真实 exit。源码证明见 claude-tool-result-source。未擅自降低验收、没有填 0；依赖该决定的程序事实复用、精确义务/permission 关联和机器验收消费仍未完成。模型模拟 ACCEPT 不能填此空白。
- 运行身份的已实现范围是 Dual Java 内容、显式行为设置和所要求的 reviewed acpx 标识。它 **不是已安装 native/adapter 字节或全体原生配置的观测**；项目/全局 acpx agent command 与配置的实际身份接线尚需在 W03 原生入口中复核，不宣称全部外部配置已冻结，不为此引入通用依赖扫描或新安全机制。
- D04 callback/批准回复 C/X 的 native 人工桥接不由本次离线 run 协议自动完成；本批仅保留 claim/关闭事实和恢复边界。
- 任意 OS 崩溃/存储介质断电、多 ClassLoader/外部 holder 直访、浏览器视觉验收、真实模型/产品 E2E 未证明。旧账本不迁移。
- 本轮作者自查已完成，整体判 WARN：已实施的离线协议/迁移有证据，但上述 D05/原生接线不满足全批结束条件；交用户发起 CC 受影响范围独立评审，不预置 PASS、不授权提交或真实运行。

## 验证结果

[verification.json](verification.json) 去重后 **1,604 tests / 195 suites，0 failures / errors / skipped**。这是两次执行覆盖同一当前生产源码的结果，**不是单次全绿 reactor**：

- reactor-55：完整扫描至 adapter，1,433 项仅 1 项失败（计划提示词仍要求旧的预先文件批准），CLI/server 因 Maven 正常失败停止尚未执行。该失败日志和原 XML 完整保留。
- consumer-56：只修改上述测试的旧预期；该类全 31 项及 CLI 全 94、server 全 77 项通过，共 202。两次源码 SHA 对比只差这一测试文件，生产/资源/POM 完全相同。verification 的逐 suite 索引指向实际 XML 与 SHA，不汇总混杂 target 目录，重跑的 31 项不重复计数。
- 模块去重数：core 123、ledger 153、LiteFlow 18、Git 6、runtime 707、acpx 426、CLI 94、server 77。
- package-57 离线打包成功（skipTests 只用于已验证源码的打包，不当测试证明）。[新包装配证明](packaged-identity-final/results.json)：三个独立 JVM，从真实 CLI/server 发行 jar 加载实际 factory，CLI 初始化 L00→server 读取→CLI 再读，冻结身份一致。使用 PropertiesLauncher 加载离线 probe main，**不是运行服务或真实 CLI 用户流程**；未调 native/provider。probe 唯一附加类及源码/最终 jar SHA 可复核。
- git diff --check 通过；最终源码身份为 after-source-sha256.json。动态测试证明范围见上表；不挪用 B01/B02 数字，不把 mock agent 的自测/ACCEPT 充当机器测试证据。

本轮机制/接线自查是作者自查，不是 CC 独立判断。待用户确认的语义不因离线回归通过而获得批准；B03 仍 IN_PROGRESS。
