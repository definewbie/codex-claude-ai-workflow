# B03 · 2026-10-06 联合实施检查点

**IN_PROGRESS，非 B03 PASS，非可运行交付物。** 用户继续指令承接既有联合实施授权。本文件记录本轮实际改动与验证，不缩减 batch.md 的结束条件。未提交、零 provider/native 实验、零额外 agent；真实运行 NO-GO 保持。新增代码尚未独立评审，按约定与 B03 批末合并，不额外发起材料轮次。

## 本轮完成的实现面

1. `VerificationReadiness` 的 operator 声明、重复请求、替代/撤回、环境具名依赖和期限校验；CLI/API 共用入口。`WorkflowFileLedger.recordReadiness` 在 L04 保存不可变材料、subject 引用和事件；已有 execution 的 permission reply 关闭参与同次本地写集。记录不批准、不调用模型、不改变等待状态。
2. plan reviewer 的真实 READY 与程序 readiness 缺项分开。四份定义增加 `VERIFICATION_READINESS_REQUIRED` 到原 plan 门；补齐后走作者/独立复评。模型合同不允许伪造该程序 outcome。
3. EXECUTION compiler 消费当前 GOAL、两份 reviewed 事实和 readiness 引用，输出按编译 occurrence 保存的合同；CLI/API 共用批准入口在同一写会话重验候选与 readiness。调用准备保留 typed readiness 失败原因，恢复提示不改写成模型输出问题。
4. authored 消费允许“精确 GOAL 下的设计/计划→绑定该 GOAL 的 EXECUTION”，不放宽跨 epoch/源引用检查。执行批准视图改读门绑定的候选；需求修订展示追随该候选的 GOAL iteration，而不是计划修订 iteration。
5. ledger 测试迁移到完整初始化/显式测试身份。已有网络 grant store 的内部 ledger 来源与 claim 读取调用做装配迁移，仅为 D06 同边界迁移；没有改变网络目标或执行 R4/Phase B 实验，该 store 全部读写会话迁移仍未完成。

## 控制链与失败切面

| 转换 | authoritative fact / owner / 消费者 | 本轮验证与剩余边界 |
|---|---|---|
| 等待 plan/contract 或 readiness 失败 → 登记 | runtime 核验；ledger 固定 request bytes、subject ref、事件；compiler/qualification 消费登记且有效的引用 | 声明正反序列已测；低层登记逐写入前缀固定补齐已测。失败 recovery → plan 修订入口仍未完成，不能把提示文字当可达能力 |
| reviewer READY + 缺材料 → plan 门 | ReviewDecisionHandler 保存真实 review/谱系，程序 outcome 单独表达缺项 | 离线实际 runtime 序列通过；四变体只验证定义边及不能缺失分支，尚非四变体真实运行 |
| 人工 revise 或 approve 解决方案 → 新作者 → 复评 | 原 gate 决定与 note；新 iteration/author/reviewer；新的 reviewed_candidate | 两种动作均重新调用模拟作者与模拟 reviewer；不以人决定代替 reviewer READY；note 必须进入新作者义务 |
| 材料齐备 → EXECUTION compile → 批准 | compiler 固定 GOAL/ref/两 reviewed/bindings；批准入口同 L 核验候选并提交 | 实际 compiler、门、ledger 与消费者通过；批准前依赖变化零决定/零实现；CLI/API 外壳编译通过，完整应用装配尚未迁完 |
| 批准 → 准备 → 实现 | TaskContractProjectionVerifier / AgentRunGovernanceContext 的 typed qualification | 依赖变化在准备前拒绝，原字节恢复后原引用可继续。R01/R02 冻结记录及 worker/correction/session 全入口尚未完成 |
| 本地提交被中断 | D06 prepared bytes 为唯一恢复输入 | ledger 前缀测试与已有冷 JVM 机制测试通过；不扩大为全链 R01–R03 或所有业务 writer 的冷恢复证明 |

readiness 原生权限含义未改变：声明不授予 tool/network grant，不证明测试执行成功。程序证据复用分支仍等待 D05 工具结果与 D07 固定完成引用，不拿使用统计、模型 checks 或上传 JSON 冒充程序事实。

## 发现与纠偏

- `readiness-commit-sequence-red.log`：新不可变 readiness 目录未进入 D06 允许写集，合法登记失败。已加入精确目录及不可覆写约束，未放开任意路径。
- `phased-continuation-facts-red.log`：实际 reviewed 事实落盘后，Java long/Integer 的 JSON 节点差异被误判为语义漂移。按持久 JSON 表示比对；修改 epoch 数值或类型仍被拒绝。
- `phased-continuation-display-red.log`：计划修订后的执行批准页把计划 iteration 交给需求修订解析器，合法流程打不开。已按 GOAL 生产 occurrence 解析需求历史，删除该显示路径已无调用者的旧 compile 查找 helper。
- `phased-continuation-red.log` 与 `phased-continuation-initial.log` 属新测试编写阶段的签名/配置错误，不冒称产品反证。`phased-continuation-fixture-loop.log` 属测试 fixture 漏掉真实定义的 approved-loop metadata；补齐真实语义后保留 note 断言，未降低预期。
- ledger 原测试曾断言事件失败后仍保留半写的人请求。D06 合法预期应为 manifest 前无业务写入、manifest 后 pending 固定补齐；已改为该用户语义，不保留“旧损坏形态即正确”的断言。

## 验证证据与适用范围

`verification.json`、`reports/`、日志及当前内容指纹可复查：

- 全模块**生产代码**编译通过：`compile-all-final.log`。不是全测试通过或应用可运行。
- Maven core **123**、ledger **151**，均零失败/错误/跳过：`ledger-regression-final.log` 与归档 XML。不是 B01/B02 历史计数。
- runtime 定向 **13** 项通过：readiness 6、定义路由 2、阶段续作 5；见 `phased-continuation.log`。通过脚本选定测试源码编译并运行固定本地 JUnit；因为旧 runtime 测试仍不能整体编译，**不是 Maven runtime 全套通过**。
- 阶段续作从明确的“已批准 GOAL”fixture 开始，后续采用真实 context/validator/decision/compiler/批准/ledger 与确定性模拟 provider 输出。证明合法续作、approve/revise 均须复评、批准前漂移拒绝、批准后准备 typed 拒绝、恢复原环境可继续、变造 review facts 拒绝；不证明 GOAL 真实生产或完整产品验收/交付。
- `current-test-compile.log` 保留 clean 测试编译失败；旧 runtime 构造器、schema、合同/审批 fixture 尚待迁移，未跳过、删除或改成宽松兼容。全模块回归仍 **NOT PASSED**。

## 必须继续的原清单

1. D05 具名程序工具事实采集/适用性消费与最终机器证据；不可让 operator 路径变成所有任务的强制人工前置。
2. readiness 编译/准备失败的 `review revise --subject` 与 API 合法返修、同授权补充证明及实质变化重批。当前已证的是 plan 等待门补齐路径，不能扩大到 recovery 路径。
3. 当前 occurrence/epoch 的统一解析、历史消费与剩余固定路径移除；D01 决定检查等既定义务仍按总清单，不因公共入口已增加而宣布不能绕过。
4. D07 R01/R02/R03 固定准备/外部 intent/不可变完成、唯一 claim、未知裁定、资格关闭全入口。`closePermissionReply` 局部接线不等于 C/X 全协议完成。
5. CLI/server 实际 frozen identity、隐式构造删除、query/补证据/delivery/network 等所有读写会话；当前 factory 仍没有完整运行身份装配，不能启动产品使用。
6. runtime/adapter/CLI/server 旧测试和 fixture 迁移，四变体 × inline/worker 整链、冷 JVM/失败写点/B02 故障族、完整回归及 CC 批末独立评审。

六维结论：Declared 已有；Proven 仅为以上切片；部分消费者与批准/准备前检查已有实现和测试；全链 Consumed、Enforced before、Failure route、Cannot-bypass 未全部成立。**W03/W04/W05/W08 不关闭，不称 B03 闭环。**

## 增量隔离

`before-source.json` 保存本轮开工前 608 个源码/资源/POM 的真实 dirty 内容；`changed-files.json` / `incremental.patch` 对此基线，包含本轮新增文件，不以 HEAD diff 混算既有改动。`before-documentation.json` 是本轮追加进展说明前的四份材料内容；更新只追加历史后续状态，未覆盖用户原改动或旧实验结论。
