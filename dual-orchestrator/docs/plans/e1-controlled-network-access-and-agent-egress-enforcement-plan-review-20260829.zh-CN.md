# E1 Controlled Network Access + Agent Egress Enforcement 计划 — 独立代码级架构评审

日期：2026-08-29
评审对象：`docs/plans/e1-controlled-network-access-and-agent-egress-enforcement-plan-20260829.zh-CN.md`（DRAFT）
评审方式：只读。自行核对 production 代码、既有测试结构与 captured OKX incident 磁盘证据；未调用 provider、未恢复
workstream、未修改 production。本报告为唯一产出。

## 0. 证据核实结果（不采信计划声明，逐项对盘）

### OKX incident（§2）— 全部证实，两处精度修正

| 计划声明 | 磁盘证据 | 结论 |
|---|---|---|
| workstream `ws-20260829T122452Z-9BZG`，node `node-implement-claude_implement-023` | `okx-btc-k-line-demo-agent-runs/.../workflow/current.json`：`WAITING_FOR_RECOVERY`，active=`node-implement-claude_implement-023` | ✅ |
| run `run-1788009030832`：605247ms、tool 计数全 0、final message 空、`OUTPUT_INCOMPLETE` | run.json：durationMs=605247、`OUTPUT_INCOMPLETE / agent_result_missing_or_invalid`、toolUsage 六项全 0 | ✅ |
| retry run `run-1788009717740`：Bash/curl 下载 KlineCharts | transcript.log：4 次 Bash，其中第 2 次从 `registry.npmjs.org` 下载 `408900` bytes tgz 存为 `klinecharts.min.js`（gzip 数据） | ✅ |
| ACP stream 两次 Bash 均有 `session/request_permission`，acpx 自动 allow | transcript：每次 Bash 前 `session/request_permission (running)` 后 `(completed)`，无人工介入 | ✅ |
| effective policy `autoApprove=[read,search,edit,execute]` | `AcpxPermissionProfile.java:108`（implement profile）→ `permissionPolicyJson()`（:160-172）→ `AcpxAgentTransport.java:388-390` 以 `--permission-policy` 传给 acpx | ✅ |
| operator stop-loss、stale recovery、persistent session 关闭 | workstream 处 WAITING_FOR_RECOVERY；session 名 `claude-main-ws-...`（stderr.log） | ✅ |

**精度修正 1（构成 P2-4）**：unpkg/jsdelivr 第一次 curl 失败后，模型 thinking 误判"sandbox likely blocks network"——实际
**不存在任何 sandbox**，是公网可达性差异；第 4 次 Bash 已把正确文件（`dist/umd/klinecharts.min.js`，194.6K ASCII，
KLineChart v9.8.0 license 头）落盘。当前工作树中的 vendored 文件是**正确的 min.js**，不是错误 `.tgz`。§9.5 恢复 Gate
的"清除/替换错误 .tgz 工件"与现状不符——真实需求是对**来源未经授权的既有文件**做 integrity/provenance 处置（对应 §3
"下载内容是否可消费 → package integrity owner" 行）。
**精度修正 2（构成 P1-2 证据）**：同一条 Bash 链使用 `/tmp` 暂存（`mv` tgz → `/tmp`、`tar -xzf -C /tmp`、`cp` 回
worktree、`rm -rf /tmp/package`）。计划引用了该 incident 却未把 worktree 外写入纳入 proof 矩阵。

### Production 事实（§3/§4/§6）

| 声明 | 代码证据 | 结论 |
|---|---|---|
| `AcpxPermissionProfile` 是工具能力 owner | `AcpxPermissionProfile.java:19-123`（profile→ACP kinds/Claude 免确认集翻译，注释明确 execute 过度批准问题） | ✅ |
| `execute` 隐含 Bash 网络授权（A 单独不可 ENFORCED） | implement profile `autoApproveKinds` 含 `execute`（:108）；acpx 层按 kind 自动应答 permission，Dual 侧仅记录 evidence（`AcpxSessionStreamProjector.java:86-89`） | ✅ 结构性证实 |
| WebSearch one-execution authority 存在且可复用 | `NodeStateWriter.approvePublicWebSearch`（:160-190）：note+attestation+escalation lineage 缺一 fail closed；`WorkstreamActionService:95-112` 带 lock/event；CLI/Server 双端 | ✅ |
| human authority 不可被自动批准 | `SchedulerPolicy.java:11-31` 仅对 `recoverable_agent_runtime/RETRY_AFTER_EXTERNAL_FIX` 自动 retry/resume，无任何 approve 路径 | ✅ |
| persistent session 复用不更新 policy（proof #8 必要） | `AcpxAgentTransport.java:473-489` 注释与实现：`sessions ensure` 仅在新建时应用 permission args，复用既有 session 原样返回 | ✅ 计划正确识别该结构 |
| `CLAUDE_CONFIG_DIR` 挂点已存在 | `scripts/acpx-claude-agent-env.sh`：读 `${CLAUDE_CONFIG_DIR:-$HOME/.claude}/settings.json` env 字段导出、不打印 secret、不写 token 到 acpx config | ✅ §6.4 分离方案可行 |
| Codex 不被 Claude settings 覆盖 | `AgentRunnerKind.java:38-42`：codex→`ACPX_CODEX_SESSION`，与 claude session 类型不同；proof #10 的"不得外推"诚实 | ✅ |

## 1. Findings

### P0

无。

### P1

**P1-1 Proof 矩阵缺"既有 WebSearch/WebFetch 权限流在 generated sandbox 下的功能共存"问题。**
§4.3 只声明两种 authority 的**逻辑独立**（type/validator/consumer 分离），test 7 也只测权限互不串通。但 mini-B 打开的
deny-by-default sandbox 作用在同一 Claude Code 子进程环境：若 WebFetch（client-side）或该 provider 栈下 WebSearch 的
执行路径经过 tool 侧网络，空 domain policy 会**破坏已被 PUBLIC_WEBSEARCH 批准的既有 retry 流**——即 E1 修一个
authority 时使另一个已 ENFORCED 的 authority 功能性回归。这恰是版本相关行为，必须进 proof：
- 新增 proof 问题："在 generated sandbox（空 domain allowlist）下，经 PUBLIC_WEBSEARCH 批准的 WebSearch/WebFetch
  retry 是否仍可完成 tool 网络动作？"
- 新增 cannot-bypass test：approved WebSearch retry 在 sandbox 下功能可用（或显式声明破坏 + 补救路径），补入 test 12
  的"non-network sibling 不退化"清单。

**P1-2 Proof 矩阵缺 sandbox filesystem scope 问题。**
计划自己的 incident 证据（§2 + transcript）显示同一条 Bash 链在 `/tmp` 完成解压暂存——这是 implement profile 的常见
操作形态。mini-B 强制 sandbox enabled，而 proof #5 只验证"worktree 内 Read/Write/Edit 与无网络 Bash 可用"，**未问
sandbox 的默认文件系统策略**：若默认限制 worktree 外写入，常规 implement 流（包解压、scratch）回归；若默认放行，则
"只声明网络"的收敛边界成立但应显式声明为 residual。两种答案都改变交付声明，不能不问：
- 新增 proof 问题："generated sandbox 下 worktree 外写入（以 incident 同款 `/tmp` scratch 为样本）是被拒绝、放行还是
  可配置？最终生效策略是什么？"
- 按答案显式配置 generated settings 或登记 residual，并补 test（`/tmp` scratch 场景不退化或按声明拒绝）。

### P2

**P2-1 localhost/LAN 出口未界定。** 多数 sandbox 实现默认放行 loopback；Dual server 自身监听 localhost，agent Bash 可
经 `curl localhost` 触发 workflow mutation API——这是与公网不同的另一条 authority 旁路。应在 proof 中钉住 loopback
行为，纳入范围（deny）或显式登记 residual，并补 test。

**P2-2 IP 直连与 DNS 残留。** deny-by-default + domain allowlist 大概率覆盖 IP-literal 与 DNS 隧道，但应作为 proof #4
矩阵的显式 case（`curl http://<ip>`、异常 DNS 查询）而非默认假设；测试增量成本低。

**P2-3 用户 settings env 的代理变量继承。** env script 整包导出用户 settings env；若其中含 `HTTP(S)_PROXY`，sandboxed
子进程会尝试连代理主机。deny-default 下应被拒，但 proof 应确认"代理主机连接被拒且 generated settings 不引入代理
允许"。

**P2-4 §9.5 恢复 Gate 与工件现状不符（见精度修正 1）。** 应改写为"对来源未经授权的 vendored 文件做 integrity 验证或
经批准路径重取；审计 partial writes"；`/tmp` 残留已被 run 内 `rm -rf` 清理，无需作为 Gate 主项。

**P2-5 `AcpxRunRequest.java:37` 遗留 `approveAll=true` 构造路径。** 生产主路径（`AcpxAgentTaskHandler:526`）仅在
profile 空白且配置允许时回落 approveAll。E1 的 policy 校验应显式拒绝经 approveAll 回落解析的 run（或确认该路径仅
test 使用），并在 sibling sweep 中清点。

**P2-6 network-permission-wait 失败与 scheduler auto-recover 的交互。** 新增的等待人工批准失败形态不应被归类为
`recoverable_agent_runtime`，否则 auto-recover 会在无批准下循环重试（非 bypass，但烧 provider 预算）。补一条断言：
该失败形态对 scheduler 不可自动恢复。

## 2. Boundary Review

| 维度 | 判定 |
|---|---|
| **A 与 mini-B 必须合并** | ✅ 成立且有结构性证据：implement profile 的 `execute` kind 在 acpx 层自动放行 Bash permission（`AcpxPermissionProfile.java:108` + `AcpxAgentTransport.java:388-390`），A-only 时任何网络授权都可被 Bash/curl 绕过，只能 MODELED/PROJECTED；mini-B-only 则剥夺现存的（虽未授权但真实的）公开事实获取能力且无授权路径。原子性声明正确 |
| **capability proof 可执行性** | ✅（补 P1-1/P1-2 后）。10 问全部可判定：Q1/Q2 有现成挂点（CLAUDE_CONFIG_DIR、env script 分离）；Q8 对应已证实的 `sessions ensure` 复用语义；Q3-Q7 依赖 pinned 版本真实行为，"任一无法证明即 FAIL 停止"的门纪律正确；Q10 对 Codex 不外推与 `AgentRunnerKind` 双 session 事实一致。需要真实 Claude tool turn 的部分已要求独立 Verification Gate（最小 prompt + stop-loss），未用 provider run 偷换——该例外应被视为 proof 阶段唯一 provider 接触点并保持显式 |
| **owner / human authority / session / fail-closed** | ✅ 边界完整：owner 表与代码一一对应；WebSearch 的 note+attestation+lineage+one-retry 模式（`NodeStateWriter:160-190`）是 `PUBLIC_NETWORK_DOMAIN` 的正确基座；scheduler 无 approve 路径；"不解析 Bash 字符串、不建黑名单"是对的方向（parser 必然可绕过）；approval 只进当前 retry execution 的 effective policy、不持久化——与 session 复用语义（ensure 不改旧 policy）配套的 fresh-session 要求（test 6）结构上必要 |
| **Bash/子进程绕过面** | 主通道封闭（sandbox 覆盖 direct/shell-chain/变量/脚本子进程/git/npm/Maven，proof #4）。残留：WebFetch/WebSearch tool 侧（P1-1）、loopback（P2-1）、IP/DNS（P2-2）、代理 env（P2-3）、Codex（诚实声明 NOT MODELED）、既有 persistent session（test 6 覆盖）、approveAll 遗留路径（P2-5） |
| **范围最小性** | ✅ §6 边界恰当：不动 workflow JSON/envelope/TaskContract/delivery read model，不建通用 permission engine/proxy/DSL；`ExecutionAuthorityPolicy` 限 execution metadata；三端经同一 ledger command；env script 只做分离不做 secret 复制 |
| **cannot-bypass tests 充分性** | 12 项覆盖核心矩阵（profile 语义分离、egress matrix、typed fail、forged/stale/cross-occurrence approval、session freshness、authority 互不串通、SHA 一致、删除 attestation 反证、incident replay、sibling 不退化）。需补：WebSearch 共存（P1-1）、filesystem scope（P1-2）、loopback/IP（P2-1/2）、approveAll 拒绝（P2-5）、auto-recover 不可恢复（P2-6） |

## 3. Implementation READY

**NOT READY（仅差计划修订，无设计推翻）。** 合并必要性、owner 结构、human authority 基座、session/retry 语义、
fail-closed 纪律、范围边界均经代码与 incident 证实成立。实施前必须：

1. （P1-1）proof 矩阵增加 WebSearch/WebFetch 在 generated sandbox 下的共存问题 + 对应 test；
2. （P1-2）proof 矩阵增加 sandbox filesystem scope 问题（以 incident `/tmp` scratch 为样本）+ 显式配置或 residual 声明 + test；
3. P2-1/2/3/5/6 按上述补入 proof 问题或 test 矩阵；P2-4 修正 §9.5 恢复 Gate 的工件事实描述。

修订后按 §9 顺序执行：本评审 → capability proof（FAIL 即停）→ 原子实施 → focused/full/diff-check/独立 diff review →
clean commit → OKX 恢复 Gate → 同 workstream retry。

## 4. End-to-end invariant（经证据核验的准确表述）

| 维度 | E1 invariant |
|---|---|
| Declared by | `AcpxPermissionProfile`（本地工具能力）+ session-scoped `ExecutionAuthorityPolicy`（网络资格，execution metadata） |
| Proven by | generated settings ref/SHA + sandbox availability/enforcement attestation + structured network permission evidence（ACP request 关联 source run/tool-call） |
| Consumed by | `AcpxAgentTransport` session/process launch（注入 isolated config、校验 policy）与 retry grant 的 one-execution 应用 |
| Enforced before | Claude tool subprocess 的任何网络 I/O；sandbox 不可用/attestation 缺失时在 side effect 前 typed fail |
| Failure route | typed execution failure 或 human network permission wait；零静默 unsandboxed、零自动 allow |
| Cannot bypass | direct/indirect egress matrix（shell chain/变量/脚本子进程/git/npm/Maven）+ forged/stale/cross-occurrence/cross-retry approval 拒绝 + session freshness + incident replay；补 P1/P2 项后含 WebFetch/loopback/IP/filesystem |
| Residual | approved domain 内路径不细分；Codex 未证明即 NOT MODELED；宿主机全局/非 Dual 进程不在范围；filesystem scope 与 loopback 待 proof 后显式声明 |

**成熟度声明核验**：当前 `NATURAL INCIDENT PROVEN / ENFORCEMENT NOT IMPLEMENTED` 与磁盘证据一致；后续两档声明口径
（deterministic `ENFORCED / REAL-CASE NOT PROVEN`、OKX 恢复自然触发后才 `PROVEN`）与项目成熟度纪律一致，未越界。
