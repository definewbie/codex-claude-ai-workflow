# E1 Controlled Network Access + Agent Egress Enforcement 计划 — Delta 复审

日期：2026-08-29
评审对象：`docs/plans/e1-controlled-network-access-and-agent-egress-enforcement-plan-20260829.zh-CN.md`（本轮 delta）
前序：首轮评审 `PASS 前置 / NOT READY（P0=0 / P1=2 / P2=6）`，见
`e1-controlled-network-access-and-agent-egress-enforcement-plan-review-20260829.zh-CN.md`。
评审方式：只读；未修改 production、未调用 provider、未恢复 workstream。首轮已对盘的代码与 incident 证据继续有效，
本轮仅核对 delta 是否真实关闭前序发现。

## 1. 前序发现关闭确认

| 编号 | 关闭状态 | Delta 证据 |
|---|---|---|
| **P1-1** WebSearch/WebFetch 在 generated sandbox 下的功能共存 | **已关闭** | 三层落位：§4.3（:107-108）"经 PUBLIC_WEBSEARCH 批准的 WebSearch 必须仍能完成 provider tool 调用；WebFetch 继续保持拒绝。若 sandbox 阻断 WebSearch 真实执行路径，必须在 capability proof 阶段停止并重新划分 owner"；proof #8（:129）直接提问；test 7（:191）功能断言；§5 FAIL 门加固（:142-143）明确禁止以"WebSearch 回归可接受"降级为 residual |
| **P1-2** sandbox filesystem scope 未问 | **已关闭** | §4.1（:73-74）"mini-B 本轮只拥有网络边界，不静默改变 filesystem authority；常规 worktree 写入与必要 scratch 必须保持可用；优先 run-owned scratch；若 frozen sandbox 无法保留或安全重定向该能力，capability proof FAIL，不通过扩大任意目录权限掩盖"；proof #7（:127-128）以 incident `/tmp` 解压为兼容样本，强制二选一（受控 scratch / 显式拒绝 + run-owned scratch），"不能留下未声明行为"；test 11（:195）不发生静默 filesystem 回归 |
| **P2-1** loopback/LAN 未界定 | **已关闭** | §4.1（:71-72）默认拒绝覆盖 loopback/LAN 对 Dual API 的直接访问 + "provider sandbox 内部 loopback/proxy socket 只能保留版本可证明的内部通道，不能泛化为 Agent 可访问 localhost"；proof #5（:123-124）；test 9（:193，含内部 socket 不构成通用出口） |
| **P2-2** IP literal / DNS | **已关闭** | proof #4（:121-122）加入 "IP literal 与异常 DNS 查询"；test 2（:186）同步 |
| **P2-3** proxy env 继承 | **已关闭** | proof #6（:125-126）点名 `HTTP_PROXY/HTTPS_PROXY/ALL_PROXY/NO_PROXY` 四变量，要求在不破坏 provider 连接前提下移除或隔离；test 10（:194） |
| **P2-4** §9.5 恢复 Gate 工件事实失实 | **已关闭** | §2（:28-29）改写为与磁盘证据一致（"当前 vendored 文件内容正确，但来源未经人工授权"）；§9（:224-226）把恢复整体移出 E1 production 计划，另写 case-specific Recovery Verification Gate（partial-write 审计 + integrity/provenance 处置 + 同 workstream retry，且"不得反向修改 E1 policy"） |
| **P2-5** `approveAll=true` 遗留路径 | **已关闭** | proof #13（:134-135）：E1-enabled run 必须拒绝该路径；旧 frozen workflow 保留时显式标记 `NETWORK_AUTHORITY_NOT_CONFIGURED`，不得继承 ENFORCED 声明；test 12（:196） |
| **P2-6** network-wait 与 scheduler | **已关闭** | proof #14（:136）`MANUAL_ONLY`，scheduler 不得无人批准自动 retry/resume；test 13（:193 之外独立成条 :197） |

八项全部真实关闭，且关闭方式均为可执行约束（proof 提问 + test 断言 + FAIL 门），非措辞性安抚。

## 2. 本轮五个重点问题

### 2.1 上轮 P1/P2 是否真实关闭

是。见上表——每项都有 proof 问题编号、test 编号与语义约束三重落位；两条 P1 的关闭均附带"不得降级为 residual"的
显式禁令（§5 :142-143），防止实施期打折。

### 2.2 WebSearch 共存与 filesystem scratch 是否进入 capability proof

是。proof #8（WebSearch 可用 / WebFetch 拒绝）与 proof #7（worktree + 必要 scratch，以 incident `/tmp` 解压为兼容
样本，强制声明二选一）。两者均受"任一无法证明即 FAIL、production 不启动"的总门约束，且 §4.3/§4.1 分别给出对应
的停止语义（重新划分 owner / 不扩大目录权限掩盖）。

### 2.3 loopback、IP/DNS、proxy env、approveAll、scheduler 边界是否完整

完整。分别对应 proof #5、#4、#6、#13、#14 与 tests 9、2、10、12、13。特别认可两处细节：
- proof #5 预判了 provider sandbox 可能以本地 proxy socket 实现允许列表，要求内部通道与 Agent 可见网络严格分离、
  "不泛化为 Agent 可访问 localhost"——这是唯一可能被实现细节侵蚀的口子，已封；
- proof #13 为旧 frozen workflow 给出 `NETWORK_AUTHORITY_NOT_CONFIGURED` 终态，避免"未配置即冒充 ENFORCED"的
  成熟度谎报，与 D2-A/D2-B 的 not_configured/not_final 纪律同构。

### 2.4 production 设计是否已与 OKX/KlineCharts 个案解耦

是，且是本轮 delta 最强的新增。§2 末段（:41-42）声明 incident 一节"只回答为什么重开 E1，不定义 production 特例"，
OKX/KlineCharts/npm registry/workstream id/文件路径五类标识不得进入 policy、validator、approval schema、catalog 或
runtime 条件分支；§6.1 通用性约束（:172-179）进一步要求：production 零业务字面量、policy 输入封闭清单、capability
tests 用本地 synthetic endpoint、sibling sweep 覆盖全部 bundled profiles + custom/frozen definition +
persistent-session retry、incident replay 去业务化（保留事件形状、剥离产品路径与域名）、未来同类外部事实请求走同一
owner。恢复流程整体移出 E1 计划（§9.5-9.6），个案处置与通用边界彻底分离。

### 2.5 capability proof 是否具备执行条件

**基本具备**，15 问全部可判定：版本冻结项（macOS/acpx/`claude-agent-acp@0.46.0`/实际 Claude Code 版本/文档 URL/
取证日期）均可在不调用 provider 的情况下取得并记录；allow path 用本地 HTTP endpoint、deny path 用不可路由测试域，
均为本地确定性设施；唯一需要真实 Claude tool turn 的 OS sandbox 验证已约束在独立 Verification Gate（最小 prompt +
stop-loss），且被明确为"不能用 provider run 偷换 deterministic proof"。FAIL 即停的门纪律使"执行条件"不依赖乐观
假设。

唯一残留（见 P2-1）：proof 证据的落盘位置未指定。

## 3. Findings

### P0

无。

### P1

无。

### P2

**P2-1 capability proof 证据无指定落盘位置。** §5 要求"冻结并记录"版本与取证，但未指定 capture 载体（建议如
`docs/testing/capability-proofs/e1-egress-<date>.md`：逐问题记录命令、原始输出、结论与 FAIL 判定）。proof 是
production 实施的唯一准入门，其证据应与 incident evidence 同等可审计；缺指定位置会在实施后变成不可复核的口头结论。

**P2-2（知会）§2 措辞精度。** :25-26 称"4 次 Bash，其中两次使用 curl""ACP stream 对两次 Bash 都产生
request_permission"——磁盘 transcript 显示**每次** Bash（4/4）前均有 `session/request_permission`。机制结论不变，
建议下一版顺带改为"每次 Bash 均产生"以免后续引用时被质疑证据精度。

### 无新 P1 的依据

本轮 delta 未引入新 owner、新 schema、新 durable 概念；§6 变更面与停止条件未放松；run-owned scratch 若被 proof
选中，其实现落在 §6.1 `dual-adapter-acpx` generated settings writer 边界内，生命周期归 `ExecutionAuthorityPolicy`
（execution metadata），不构成文件系统 authority 的新 owner；恢复 Gate 的外移反而缩小了 E1 范围。

## 4. Boundary Review

| 维度 | 判定 |
|---|---|
| **A + mini-B 原子性** | 维持首轮判定：结构成立（`AcpxPermissionProfile.java:108` execute 自动批准 + `AcpxAgentTransport.java:388-390` 传递，A-only 不可 ENFORCED），本轮未动摇 |
| **Owner / human authority / session / fail-closed** | 完整且较首轮更强：15 问 proof 矩阵把 owner 交叉验证前移到 production 之前；`NETWORK_AUTHORITY_NOT_CONFIGURED` 终态补齐 legacy 成熟度语义；`MANUAL_ONLY` 补齐 scheduler 边界；filesystem authority 显式排除在 mini-B 之外 |
| **Bash/子进程绕过面** | 主通道 + WebFetch/loopback/IP/DNS/proxy/approveAll/旧 session 全部进入 proof+test 矩阵；Codex 仍诚实 NOT MODELED 除非 proof #15 证明；无新增已知缺口 |
| **与个案解耦** | 完整（见 §2.4）：五类业务标识禁入 production、去业务化 replay、恢复 Gate 外移、通用 owner 约束 |
| **范围/测试最小且充分** | production 边界不变（§6 四组文件 + 三条禁改）；proof 15 问 / cannot-bypass 18 条与首轮 10/12 相比的新增全部对应已核实缺口，无锦上添花项；§5 FAIL 门 + 禁止降级条款保证测试矩阵不被实施期稀释 |
| **成熟度声明** | 三档口径不变且与磁盘证据一致；residual 行新增"filesystem/loopback 仅可按 capability proof 的精确结果声明"——诚实 |

## 5. Implementation READY

**READY（进入 capability proof 执行阶段）。** 上轮全部 P1/P2 真实关闭，五个重点维度全部达标。执行顺序按 §9：
本 delta 复审（本文）→ capability proof（建议先落 P2-1 的证据载体）与 producer/consumer/sibling sweep → proof PASS 后
A+mini-B 原子实施 → focused/full/diff-check/独立 diff review → clean commit；OKX Recovery Verification Gate 另立，
不阻塞、不反向修改 E1。proof 任一问 FAIL 即停止并回到本计划修订，不得进入 production。

本次复审未修改 production、未调用 provider、未恢复 workstream；唯一产出为本文。
