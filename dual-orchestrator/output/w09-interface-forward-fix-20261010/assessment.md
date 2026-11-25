# W09 首例接口误拒与失败摘要 · 前向修复及发行候选

2026-10-10。**AUTHOR_OFFLINE_PASS / REFROZEN_CANDIDATE / AWAITING_AFFECTED_CC / FRESH_REAL_RUN_NOT_AUTHORIZED**。

首例 Gate **FAIL 保持**。本轮接收用户带回的独立评审、在既有 owner 内修复 F01/F02 并补成对回归，未救援/恢复首例、未启动第二个 workstream/原生客户端/provider、未安装/提交。自查不是 CC 复审；旧 CC 结论不能替代本轮独立判断。

## 1. 目标、scope 与改变

本轮推进 P0：合法原生会话形态可到达 prompt；核心唯一失败原因在摘要中保持一致。不做 provider 内核/权限平台，不放宽 scope、批准、证据、RPC/generation 或最终交付，不新增 native 精确版本锁。

- **F01 / AcpxSessionCompatibility**：acpxRecordId/acpSessionId 仍必需，schema/name/canonical cwd/open/protocol 原 guard 保留。agentSessionId 缺席/显式 null 为 NOT_PROVIDED；实际非空字符串为 PROVIDED（保存原值）；异常类型/空串为 INVALID 并拒绝。该内层 enum 只投影到原 command observation，非新 authority。绝不以 ACP ID 填充内部 ID。`AcpxAgentTransport` 主调用和 correction 共用原强制检查，兼容性 command result 先固定才产生 prompt intent。
- **F02 / FailureSummaryWriter**：存在已固定 agent_execution_outcome 时仅读其 primaryReason；typed primary 损坏时显示 INVALID 投影，不回落旧 acquisition reason。展示原 recoveryCategory/retryPolicy；原 parse_error 保留，非 artifact 主因时标为 Secondary Contract Diagnostic。真实 artifact failure 与非 agent 原因仍有原展示。动态 failureSummarySnapshot 与持久化摘要共用 render；投影不创建控制原因、重试资格或批准。
- **F03 / 夹具与兄弟**：共享 sessions-show 默认不提供可选 ID；明确 PROVIDED 正向的内部 ID 与 ACP ID 不同。直接正常/correction 两客户端 + 既有 reconnect/rebuild/command 顺序 + native permission 本地接口覆盖合法缺席。全仓直接消费点与原生可选 extractor 核对见 [sibling-map](sibling-map.md) / identity-consumer-sweep / source-checks。

源码增量 **8 文件，其中生产类 2 个**；相对开工前 dirty 独立记录，非相对 HEAD 掩盖已有改动。见 [incremental.patch](incremental.patch)、changed-files、before/ 原字节与 before-source-sha256。文档增量单独登记。

## 2. 完整链与失败切面

[transition-plan](transition-plan.md) 明确 Definition / Instance / Execution 与原写序：会话 ensure/show → intent/result 观察 → 兼容性判定 → prompt → 唯一核心 finalize → 原 R03/L03 固定 → 恢复/摘要。必需身份/协议不合格以及兼容性两写点失败仍零下一阶段；后续固定/恢复沿原 D06/D07，无新外部重发。F02 是投影改源，不是新增 Enforced-before 机制。

inline/worker 用真实 runtime/ledger 固定 run/node/outcome/recovery/summary，并在冷子 JVM 重读；外部 adapter 失败为合成 fixture。正向完整批准/作者/独立复评/验收/最终门/临时 Git 链由本轮全模块既有序列承载，不能当真实客户端业务成功。原非 agent 失败、真实 artifact 主因、malformed typed primary 均有展示对照。

## 3. 证据分层（不相加）

| 层 | 结果与边界 |
|---|---|
| 原真实首例 | sessions 创建/握手、兼容性误拒、R03/L03/停止已有事实；zero prompt，不证 GLM 业务连接/模型输出；仍 FAIL |
| 先红 | 两测试类共35项：6 assertion FAIL / 0 error；合法缺席2，接口与不完整 process 的摘要漂移4。不是测试作者错误。见 red-results / red-reports |
| 聚焦 | 43 / 0 failure/error/skipped；本地 shell/Unix socket peers 与真实 ledger、冷 JVM；不证真实原生工具。见 focused-results |
| 隔离删除式反证 | 4 当前实现对照 PASS + 4 旧 owner 预期 assertion failure；两个角色/两个执行入口，实际测试方法与加载 code-source 已固定。不改回生产源，非 Maven 数字。见 counterproof-results / counterproof/runs |
| 单轮完整 clean reactor | **1768 tests / 206 suites / 0 failure/error/skipped**，exit0；654输入前后相同。没有拼接旧1755或聚焦/反证。见 [test-results](test-results.json) / reactor-final.log / reactor-reports |
| 新发行 | package exit0（skipTests 是复用刚完成的单轮，不作第二次回归）；CLI/server own classes/resources与6内嵌模块字节核对，14资源、6 CLI help/定义validate 均通过。见 [artifact-verification](artifact-verification.json) / [release-identity](release-identity.json) |
| 仍未证 | 本轮新真实客户端/GLM业务回复、工具/usage实证、真实独立验收、真实目标交付、浏览器和全部变体能力均 NOT RUN/NOT PROVEN |

CLI SHA：`a1d1b011acfd45701012be170cc9facd6be171501307db16538e477f75b38f7b`。server SHA：`1280ada807511ccc59ba6cabbdb69b246a85eb44f88a678406f779477b05c7f4`。选定 wrapper/dist/global config 字节与原受审组合一致，native 实际 build-info 来源是原已测材料/首例R01；本轮仅重核字节，不冒称重新做连接/工具实测。

## 4. 六维与判定边界

| 维度 | 本轮证据/限制 |
|---|---|
| Declared by | 本轮承接的首例 CC F01/F02 判定，既有 P0 合法流程/唯一原因与原 Gate；不把独立评审当新真实运行授权 |
| Proven by | 先红后绿、成对合法/严格拒绝、原 durable 链和冷读、隔离旧 owner、最终单轮与发行；原生业务层仍空 |
| Consumed by | Compatibility → 原 transport prompt guard；核心唯一 outcome → 原 R03/L03 → RecoveryAssessment / 共用 FailureSummaryWriter；engine内部ID不用于身份路由 |
| Enforced before | 原必需身份/协议检查与其 command result 固定在 prompt 之前；F02只消费投影，外部副作用仍由原核心 guard 阻止，不把摘要当控制器 |
| Failure route | 原 ADAPTER_INCOMPATIBLE / MANUAL_ONLY / WAITING_FOR_RECOVERY；本地写失败与外部 UNKNOWN 按原 D06/D07，不增许可/自动重试 |
| Cannot-bypass test | 缺可选 ID 合法正向、必需 ACP/record/schema/name/cwd/open/protocol 严格负向；两写点零prompt；四旧 owner 反证；typed primary损坏不借旧原因；真实合同失败及非agent展示正向 |

F01/F02 在具名离线范围有据，不关闭整个 W02/W03/W08/W09，也不宣布真实研发交付已闭合。

## 5. 原现场、核对错误与下一步

首例 `ws-20261010T055644Z-6QCS` 的77个已归档索引文件均与原SHA相同，没有 resume、手改账本或产品变更。原 current-failure 的错误投影保留为事故证据，不反向修它。首轮目录核对把两个未入归档索引的空 `.mutation.lock` 算作差异，属于核验脚本口径 INVALID；实际原77字节未变，修正比较并保留说明见 failed-case-preservation.json。未删除空锁文件。新发行不用于续作旧冻结身份样本。

下一步明确为：**将本修复、新发行和 [fresh 第二次 Gate](fresh-second-gate.md) 合并交 [CC 受影响复审](cc-review-prompt.txt)**。通过并处理必须项后，交用户 fresh 当次运行授权；再新建目标/workstream并运行。原剩余17调用不自动使用；新任务候选/预算仍待此次授权，关键/最终人门与产品 commit 责任不变。无需再开 F01/F02 单独材料轮次。

自查记录见 self-review.md / review-disposition.md。本轮不新增实施族、安全实验或平台，不因首例止损正常就认定产品主流程可靠。
