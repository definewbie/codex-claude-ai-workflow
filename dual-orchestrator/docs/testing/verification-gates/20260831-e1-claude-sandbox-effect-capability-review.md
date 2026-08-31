# Review: E1 Claude Sandbox Effect Verification Gate（独立只读架构评审）

日期：2026-08-31
评审对象：`docs/testing/verification-gates/20260831-e1-claude-sandbox-effect-capability.md` 及
`fixtures/e1-sandbox-effect/` 四个 fixture。结论均自行核实，不采信 Gate 自述。
评审方式：只读。未修改 production、未调用 provider、未恢复/创建 real-case。已执行 fixture SHA 复算、probe 脚本
`bash -n`、Gate 编译配方逐字复跑（纯编译）与 production 机制核对。

## 0. 独立核实结果

| 检查项 | 方法 | 结论 |
|---|---|---|
| fixture 冻结 | `shasum -a 256` 四文件 vs Gate Frozen Fixtures | ✅ 逐一吻合（launcher `039f46af…`、script `49b63e3c…`、bash prompt `af1cce49…`、websearch prompt `36f4bd2e…`） |
| 基线祖先 | git log | ✅ HEAD=`07eaa16`（offline proof），parent=`e8cacb7`（transport enforcement）——Gate 的 ancestor 前置在提交后即成立 |
| fork identity | manifest/Doctor 常量 vs Gate 冻结值 | ✅ `fd173f04 / 304577dc… / claude_execution_policy.v1` 三处一致 |
| 离线证明状态 | active plan :1871-1881 | ✅ Q1 PASS、Q2 transport/env separation PASS + provider connection NOT PROVEN、Q3 option consumption VALIDATED + OS sandbox behavior NOT PROVEN——Gate 声称的"待证事项"与记录一致，无重复宣称 |
| launcher 忠实性 | 通读 + 编译复跑 | ✅ 只调用 production `ClaudeExecutionPolicy.write`、`AcpxAgentTransport.runClaudeDesign`（:136，真实入口）与既有 profiles；不写 ledger、不实现 network policy。**Gate 的编译配方由本评审逐字执行——`LAUNCHER COMPILE: OK`** |
| probe 脚本 | 通读 + `bash -n` | ✅ 先验 filesystem（workspace+TMPDIR 读写，退化即 exit 71）；11 个敏感 env 名只记 presence 不记 value；网络矩阵 11 项，每项独立 out/err + results.tsv；**首个意外成功立即 exit 70**（probe 内建 stop-loss）；loopback/LAN 打真实 sentinel |
| WebSearch fixture 与 production 一致性 | handler :545 | ✅ production 批准后的启用形态就是 `resolved.withAutoApprovedTool("WebSearch")`；fixture 采用完全相同形态（review profile + 同方法），WebFetch 保持 escalate→deny。fixture 无第二 authority（见 P2-5 注记） |
| prompt 钉扎 | 通读两 prompt | ✅ exact tool count + exact input（`./e1-network-probe.sh`；WebSearch 恰一次 + WebFetch 恰一次尝试）；模型正文明确非 authority |

## 1. Findings

### P0

无。

### P1

无。

### P2

**P2-1 npm/Maven 探针使用不存在的 id，fail-open 时产生 404 而非硬成功。** `npm view @dual-e1-capability/nonexistent…`
与 Maven `nonexistent-artifact` 在网络**可用**时返回 E404（非零、非 sandbox 可归因）——脚本层不会 exit 70，只能靠
PASS 4 的"stderr 必须含可归因于 sandbox/policy 的拒绝证据"在证据评审时判 FAIL。硬判别器（fail-open 必成功 →
exit 70）是 direct curl / git_remote（真实公开仓库）/ loopback / LAN。建议：case report 模板逐探针标注
"hard success-discriminator / 404-capable" 两类，并要求对后者给出 stderr 归因分类，防止评审疲劳时把 404 误读为
"拒绝生效"。

**P2-2 env presence 只记录不硬失败。** filesystem 退化 exit 71 是硬失败，而 `environment.tsv` 出现 `present` 时脚本
照常跑完全矩阵——PASS 3 全 absent 依赖步骤 8 的机械评审。建议把"environment.tsv 全 absent"写成步骤 8 的显式机械断言
（或脚本对称地 exit 73），消除不对称。

**P2-3 步骤 9 的冲突 ensure 缺可执行命令。** "同一 session 名 + 不同 SHA 的合法 policy 执行 sessions ensure"未给出
确切命令与构造方式（可 naturally 由第二次 launcher 调用产生新 `generated_at` → 新 SHA，再手工
`acpx --claude-execution-policy-ref … --claude-execution-policy-sha256 … claude sessions ensure --name <同名>`）。
应写明命令与"conflict 必须先于 prompt/provider side effect"的判定点。

**P2-4 sentinel 未指定工作目录。** `python3 -m http.server 18765 --bind 0.0.0.0` 以启动时 CWD 为根提供文件。应在
专用空目录启动（并记录该目录），避免内容相关响应与无意暴露。

**P2-5（知会）fixture 的 WebSearch 批准来源须在 case report 中登记。** fixture 用 profile 级
`withAutoApprovedTool("WebSearch")` 模拟"已批准"状态（与 production 批准后形态一致，但批准由 operator 对 Gate 的
opt-in 代替了 `PUBLIC_WEBSEARCH` human authority）。case report 应记录该来源，防止日后把本 Gate 引用为"production
允许 profile 级 WebSearch 自动批准"的依据。

## 2. Boundary Review（对照四个评审问题）

| 问题 | 判定 |
|---|---|
| **1. 是否真实验证六类效果** | ✅ sandbox effect：production writer→launcher→fork→`_meta` 全链 + 恰一次 exact-input Bash + 无 unsandboxed retry/escape（PASS 1/5）；网络矩阵：11 向量含 IP/DNS/loopback/LAN，且 loopback/LAN 用**真实监听 sentinel** 作硬判别（PASS 4）；env 隔离：11 名 presence-only + 同一 provider turn 仍成功（adapter 有凭据而 subprocess 无）（PASS 3）；filesystem：workspace+TMPDIR 硬校验且禁止降级为 residual（PASS 2——上轮登记的 tmpdir scratch 未决项在此被正面裁决）；WebSearch/WebFetch 共存：独立 session、exact 计数、WebFetch 保持拒绝（PASS 6）；structured domain evidence：PASS 7 以"无自然语言解析可得 session/run/tool-call/domain identity + disposition"为硬条件，缺失即 FAIL 并触发 bounded comparison——正是 Q9 的决策点，且明确禁止 Bash parser |
| **2. Q10/Q14 延后是否正确、有无循环前提/成熟度夸大** | ✅ 分类表把 provider capability（Q3-Q9、Q11）/既有 deterministic enforcement（Q13）/implementation-deferred（Q10/Q14）分开——Q10 的 one-execution grant 与 Q14 的 network wait 都以尚未实现的 `PUBLIC_NETWORK_DOMAIN` 为前提，作为 capability 前置就是循环；延后到实施后验收正确。Q11 在 deterministic ENFORCED 之上仅做真实交叉核对，不重复宣称。Maturity Boundary 五档与 active plan 记录一致，无夸大；INVALID 不可当部分成功、不可第二样本，杜绝样本购物 |
| **3. fixture/stop-loss/PASS-FAIL-INVALID/授权是否可执行且不可绕过** | ✅ 可执行性经本评审实证：四 fixture SHA 冻结、编译配方逐字复跑成功、脚本语法通过、production 入口/机制核对无虚指。三层 stop-loss：probe 内建（exit 70/71）、Gate 运行中（首个异常即 cancel+close、600s/prompt、20min 总量、≤2 tool prompts、prompt-retries=0）、precondition（任一失败不触 provider）。PASS/FAIL/INVALID 边界以可机械核验的 identity/SHA/流内容为判据；模型正文与 exit code 明确非 authority。P2-1/2/3 是判据表述的精确化，非结构缺陷 |
| **4. 有无平台扩张/隐含授权/第二 authority** | ✅ launcher 为 package-local fixture，只调 production API，无 ledger/网络 policy/workflow/schema 变更；无新 production 代码。授权分层明确：静态评审→（可单独授权的）source-side preflight→standing minimal health probe（fresh+close 一次）→两个带工具 prompt 需 review PASS 后的**一次性明确授权**；不授权 OKX 恢复、workstream/real-case、domain approval 实现、production policy 修改。WebSearch 批准机制与 production 形态一致（P2-5 登记来源即可），无第二 authority；WebFetch 经 escalate→deny 保持 `PUBLIC_WEBSEARCH` 边界，"不能一个允许联网总按钮"的约束未被触碰 |

## 3. Execution READY

**READY（作为 Gate 文档进入同行评审-提交-预检流程），附五条 P2 修订建议**——均为文本/程序精确化，不动 Gate 结构：
1. case report 模板区分 hard success-discriminator 与 404-capable 探针并要求逐项 stderr 归因（P2-1）；
2. "environment.tsv 全 absent" 写为步骤 8 显式断言或脚本硬失败（P2-2）；
3. 补冲突 ensure 的确切命令与判定点（P2-3）；
4. sentinel 指定空专用目录（P2-4）；
5. case report 登记 fixture WebSearch 批准来源（P2-5）。

按 Authorization Boundary 执行顺序：本评审（P0/P1=0）→ 文档/fixture checkpoint 提交 clean SHA → source-side
preflight（fresh package/installer/Doctor/hash 复算，不触 provider）→ 用户对两个带工具 prompt 的一次性明确授权 →
执行与 case report。在本 Gate PASS 之前，`PUBLIC_NETWORK_DOMAIN` 不得实施；FAIL 即回 bounded comparison；INVALID
不得重跑第二样本。

## 4. 证据锚点

- fixtures：`docs/testing/verification-gates/fixtures/e1-sandbox-effect/`（四文件 SHA 已复算）
- 编译配方：本评审在 `/private/tmp/e1-probe-*` 逐字执行，`E1SandboxProbeLauncher.class` 产出成功
- production：`ClaudeExecutionPolicy.write`（固定值+自验）、`AcpxAgentTransport.runClaudeDesign:136`、
  `AcpxAgentTaskHandler:545`（WebSearch 启用形态）、fork patch `304577dc…`（owner-env throw 修订版）
- 状态记录：active plan :1871-1881（Q1/Q2/Q3 离线结论与四项 P2 登记）

本次评审未修改 production、未调用 provider、未恢复或创建 real-case。
