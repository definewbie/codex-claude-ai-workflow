# E1 Controlled Network Access + Agent Egress Enforcement 实施计划

## 1. 目标与优先级

**Target phase：P0 blocker。**

本 checkpoint 同时实现两个不可拆开的边界：

1. `A - Controlled Network Access`：Agent 需要公开网络事实或工件时，形成 typed request，经过 human authority 后仅对当前 execution 生效；
2. `mini-B - Agent Tool Egress Enforcement`：Dual 启动的 Claude Code Bash 及其子进程默认不能访问公网，且 sandbox 不可用时 fail closed。

两者必须作为一个原子 checkpoint 实施。只做 A 时，现有 `autoApprove execute` 仍允许 Bash/curl 绕过批准，A 只能达到
`MODELED/PROJECTED`，不能达到 `ENFORCED`。

本 checkpoint 不建设宿主机全局防火墙、通用代理、TLS 检查平台或 Docker 网络治理。强制范围仅为 **Dual 启动的 Claude Code
tool subprocess**；provider 自身连接、operator 终端和非 Dual 进程不在本轮声明范围内。

## 2. 自然 incident 证据

OKX fresh real-case：

- workstream：`ws-20260829T122452Z-9BZG`；
- node：`implement.claude_implement` / `node-implement-claude_implement-023`；
- 第一次 run `run-1788009030832`：`605247ms`、exit `0`、toolCall/read/write/bash 全为 `0`、final assistant message 为空，路由为 `OUTPUT_INCOMPLETE`；
- retry run `run-1788009717740`：Claude 发起 4 次 Bash，其中两次使用 curl 下载 KlineCharts；
- ACP stream 对 4 次 Bash 都产生 `session/request_permission`，但 acpx 均自动返回 `optionId=allow`；
- effective command policy 为 `autoApprove=[read,search,edit,execute]`，因此 request 未进入 Dual human authority；
- 第二次 curl 从 `registry.npmjs.org` 成功下载 `408900` bytes 的 `.tgz`，先误存为 `klinecharts.min.js`；同一 run 随后
  在 `/tmp` 解压并将正确的 `dist/umd/klinecharts.min.js` 复制回 worktree，因此当前 vendored 文件内容正确，但来源未经人工授权；
- operator 触发 stop-loss，中断 run、执行 stale recovery 并关闭 persistent session；workflow 当前为 `WAITING_FOR_RECOVERY`。

该事件满足旧 E1 revisit trigger：“发生 Bash/子进程未经人工授权访网的自然 incident”。它同时证明：

1. 网络技术可达，不是“缺少联网能力”；
2. ACP permission event 存在，但当前 policy 自动代表人选择 `allow`；
3. WebSearch one-execution authority 不拥有 Bash 子进程网络；
4. prompt 中的禁止任意 URL 纪律不能替代 enforcement。

当前 workstream 作为 incident evidence 保留。在 E1 deterministic checkpoint 与独立 diff review PASS 前不得 retry。

本节只回答“为什么重开 E1”，不定义 production 特例。OKX、KlineCharts、npm registry、当前 workstream id 和文件路径均不得
进入 policy、validator、approval schema、catalog 或 runtime 条件分支。

## 3. Owner 与状态边界

| Fact | Owner | Lifetime | 禁止的第二 owner |
|---|---|---|---|
| 节点需要哪些本地工具能力 | `AcpxPermissionProfile` | node execution policy | prompt、UI、模型自述 |
| Agent tool subprocess 是否可联网 | session-scoped `ExecutionAuthorityPolicy` | session/execution | `autoApproveKinds.execute`、Bash 字符串判断 |
| 本次网络请求的目标与理由 | typed ACP/network permission evidence | source execution occurrence | stderr 文本、Markdown、console |
| 人是否批准 | ledger human decision | workstream + node + source run/tool-call + domain/query identity | acpx 自动 option、adapter 本地默认 |
| 实际生效的 sandbox policy | generated settings ref + SHA + runtime attestation | concrete execution | 用户全局 mutable settings |
| 下载内容是否可消费 | target/product contract 或 package integrity owner | artifact occurrence | 文件名、HTTP 200、模型声称 |

`ExecutionAuthorityPolicy` 是 execution metadata，不新增 workflow phase/node，也不拥有业务 scope 或依赖选择。

## 4. 核心语义

### 4.1 mini-B：默认拒绝 Agent 子进程公网

- implementation/review/discovery profile 的 filesystem/tool intent 与 network intent 分离；
- `execute` 只表示可执行本地命令，绝不再隐含网络授权；
- Claude Code 使用独立、run/session-bound settings，强制：
  - sandbox enabled；
  - sandbox unavailable 时 fail closed；
  - unsandboxed escape disabled；
  - 默认 network allowed domains 为空；
- Dual 不解析 Bash 字符串，也不维护 curl/wget/npm/maven/git/docker 黑名单；
- provider 连接必须继续可用，网络限制只作用于 Claude Code tool subprocess；
- generated settings 不复制 token。现有用户 settings 仅作为 provider env 输入，sandbox policy 由 Dual 单独生成并以 SHA 记录。
- 默认拒绝范围必须覆盖公网、IP literal、DNS 旁路、loopback/LAN 对 Dual API 的直接访问以及用户代理变量形成的旁路；若
  provider sandbox 自身需要内部 loopback/proxy socket，只能保留版本可证明的内部通道，不能泛化为 Agent 可访问 localhost。
- mini-B 本轮只拥有网络边界，不应静默改变 filesystem authority。常规 worktree 写入与必要 scratch 必须保持可用；优先使用
  run-owned scratch。若 frozen sandbox 无法在网络隔离下保留或安全重定向该能力，capability proof FAIL，不通过扩大任意目录权限掩盖。

### 4.2 A：受控公开网络授权

Capability proof 必须先确定 frozen adapter 暴露的 network permission event 形状。优先复用 provider 的 structured domain request；
不得从任意 Bash command 文本中用 regex 猜 URL/domain。

批准事实至少绑定：

- schema/type；
- workstream id、node instance id；
- source run ref、ACP session/request/tool-call id；
- public-network attestation 与 operator note；
- normalized domain；
- 原始 structured request SHA；
- approved-at；
- one-execution consumption。

批准后只把该 domain 加入当前 retry execution 的 effective sandbox policy。不得自动批准 `WebFetch`、不得恢复全局 Bash 网络、不得把
批准持久化到后续 node/session。若 provider 只能表达 domain allowlist，成熟度必须诚实声明“同一 execution 内该 domain 的全部路径可达”，
不得冒充 exact-URL enforcement。

若 capability proof 证明 provider 没有可关联的 structured domain request，则停止该实现路径，改做独立 bounded comparison：

1. harness-owned artifact fetch broker；或
2. 升级 pinned adapter。

不能在本 checkpoint 中退化为 Bash parser。

### 4.3 与既有 WebSearch authority 的关系

- `PUBLIC_WEBSEARCH` 继续只批准 `WebSearch` tool，不开放 Bash domain；
- 新 network approval 只批准 sandbox domain，不自动批准 WebSearch/WebFetch；
- generated sandbox 下，经 `PUBLIC_WEBSEARCH` 批准的 WebSearch 必须仍能完成 provider tool 调用；WebFetch 继续保持拒绝。若
  sandbox 会阻断 WebSearch 的真实执行路径，必须在 capability proof 阶段停止并重新划分 owner，不能牺牲既有 authority；
- 两种 approval 可共用 ledger human-decision 基础设施，但 type、validator、consumer 必须独立；
- UI/CLI/API 必须展示当前请求的 tool/domain、source run、风险与一次性语义，不能用一个“允许联网”总按钮。

## 5. Capability Proof Gate

在修改 production 前，先冻结并记录：macOS 版本、acpx 版本、`claude-agent-acp@0.46.0`、实际 Claude Code 版本、官方文档 URL 与取证日期。
proof 的逐问题命令、原始输出 SHA、结论与残余统一写入
`docs/testing/capability-proofs/20260829-e1-claude-tool-egress-capability-proof/`。该目录是验证证据，不是 production
policy owner；缺 manifest、问题编号或原始证据时 proof 为 `INVALID`。

proof 必须回答：

1. Dual 能否为 named session 注入独立 settings，且不继承用户全局 sandbox/permission 漂移；
2. provider credentials/base URL 能否通过独立 env 继续工作，而不写入 generated settings/ledger；
3. sandbox enabled、fail-if-unavailable、unsandboxed escape disabled 是否在当前版本真实生效；
4. 空 domain policy 是否阻断 direct curl、shell chain、变量/替换、脚本子进程、git remote、npm/Maven 下载、IP literal 与
   异常 DNS 查询；
5. loopback/LAN 默认是否被拒，尤其 Agent Bash 是否无法访问 Dual localhost API；provider sandbox 所需内部 proxy/socket 是否
   与 Agent 可见网络严格分离；
6. 用户 settings/env 中的 `HTTP_PROXY`、`HTTPS_PROXY`、`ALL_PROXY`、`NO_PROXY` 是否会进入 tool subprocess 并形成旁路；若会，
   能否在不破坏 provider 连接的前提下移除或隔离；
7. worktree 内 Read/Write/Edit、无网络 Bash 与必要 scratch 是否仍可用；以 incident 的 `/tmp` 解压为兼容样本，最终必须选择
   “保留受控 scratch”或“显式拒绝并提供 run-owned scratch”，不能留下未声明行为；
8. generated sandbox 空 domain 下，已批准的 `PUBLIC_WEBSEARCH` 是否仍能完成 WebSearch；WebFetch 是否继续拒绝；
9. 被拒 domain 是否产生可关联 source run/tool-call/domain 的 structured evidence；
10. 人工批准后是否只对一次 retry execution 生效；
11. policy 变化是否要求关闭/rebuild persistent session，且不会复用旧 effective settings；
12. sandbox 不可用或 attestation 缺失时，是否在 tool network I/O 前形成 typed failure；
13. `approveAll=true` 的 legacy/fallback 路径是否仍可进入 E1-enabled production run；E1-enabled run 必须拒绝该路径，旧 frozen
    workflow 若保留只能显式标记 `NETWORK_AUTHORITY_NOT_CONFIGURED`，不得继承 ENFORCED 声明；
14. network permission wait 是否保持 `MANUAL_ONLY`，scheduler 不得在无人批准时自动 retry/resume；
15. Codex adapter 是否具备同等 enforcement；若没有，本 checkpoint 必须明确只声明 Claude，不能用 Claude proof 外推 Codex。

测试优先使用本地 HTTP endpoint 证明 allow path，并以不可路由测试域/本地拒绝设施证明 deny path。若验证 OS sandbox 必须经过一次真实
Claude tool turn，必须另写 Verification Gate，使用最小 prompt 和 stop-loss；不能用 provider run 偷换 deterministic proof。

任何一项无法证明，capability proof 为 `FAIL`，production implementation 不启动。尤其不能用“WebSearch 回归可接受”或“scratch
很少使用”把 P1 降级为 residual。

### 5.1 Capability Proof Result（2026-08-29）

结果为 `FAIL`，证据位于
`docs/testing/capability-proofs/20260829-e1-claude-tool-egress-capability-proof/`。当前 frozen stack 的底层 Claude SDK/adapter 支持 per-session sandbox options，但 `acpx@0.11.0` 只转发 `model/allowedTools/maxTurns/systemPrompt`，无法把 Dual 生成的 `settings/managedSettings/sandbox` 注入 `session/new._meta`；同时现有 wrapper 会 export 用户 settings 的全部 env，provider env 与 tool subprocess env 未分层。

止损已执行：未启动 provider tool turn、未修改 production、未恢复 OKX workstream。下一步改为 `e1-execution-policy-injection-bounded-comparison-20260829.zh-CN.md`，不得跳过 client capability 直接实施 §6。

## 6. 预期 production 变更面

最终文件清单以 capability sweep 为准，但 owner 必须限制在以下边界：

1. `dual-adapter-acpx`
   - 新增 `ExecutionAuthorityPolicy` 与 generated Claude settings writer/verifier；
   - `AcpxRunRequest` 显式携带 effective policy，而不是从 env/全局 settings 猜测；
   - `AcpxAgentTransport` 在 session 创建前验证 policy、注入 isolated config，并把 attestation 写入 execution timeline；
   - `AcpxAgentTaskHandler` 验证并消费一次性 network approval；
   - `AgentRunDocument/AgentRunMetadataWriter` 保存 policy ref/SHA、sandbox availability/enforcement、allowed domains 和 escape-disabled 状态；
   - permission projector/reader 只消费 structured network evidence。
2. `dual-ledger-file`
   - 在现有 recovery human authority 上增加独立 `PUBLIC_NETWORK_DOMAIN` action；
   - lineage/domain/request-SHA/note/attestation 任一缺失即 fail closed；
   - completed execution 消费 approval 后必须清除，禁止跨 retry/node 复用。
3. `dual-cli`、`dual-server`、console
   - 三端调用同一 ledger command；
   - 展示 exact domain、source occurrence、一次性范围与 residual；
   - 不直接修改 settings，不直接 resume provider。
4. `scripts/acpx-claude-agent-env.sh`
   - 将 provider env source 与 Dual generated `CLAUDE_CONFIG_DIR` 分开；
   - 不输出或复制 secret；
   - sandbox policy 缺失/错误时 fail closed。

不得修改 workflow JSON、AgentResultEnvelope schema、TaskContract、delivery/completion read model，也不得新增通用 permission engine、proxy module
或网络规则 DSL。

### 6.1 通用性约束

- production 代码不得出现 `okx`、`kline`、`npmjs`、`unpkg` 或当前 workstream/node/run id；
- policy 输入只能是现有 permission profile、structured permission evidence、human decision 与 execution identity；
- capability tests 使用本地 synthetic endpoint、不可路由地址和通用 shell fixtures，不依赖 OKX 或任何公开站点；
- sibling sweep 覆盖所有 bundled permission profiles、custom/frozen definition 与 persistent-session retry，不只覆盖 implement 节点；
- captured incident replay 只证明“execute 自动批准不得拥有网络”，fixture 应去除产品路径与域名后保留相同事件形状；
- 未来 JavaScript 依赖、Maven/npm 包、Git remote、公开 API 文档和其他项目的外部事实请求必须走同一 owner，不增加项目级分支。

## 7. Cannot-Bypass Tests

至少覆盖：

1. implementation profile 的 `execute` 不再代表 network authority；
2. 空 domain policy 下本地 Bash 成功，curl/wget/脚本子进程/变量拼接/git/npm/Maven/IP literal/DNS 旁路失败；
3. sandbox unavailable 与 escape 参数均在 provider/tool side effect 前 typed fail；
4. permission evidence 缺 lineage、伪造 domain、错误 request SHA、错误 node/run、空 note 全部拒绝；
5. approval 仅允许对应 domain 的一次 retry execution，sibling domain 与下一 node 仍拒绝；
6. policy 变化强制 fresh effective session/settings，旧 persistent session 不可继承 grant；
7. `PUBLIC_WEBSEARCH` 在 generated sandbox 下仍可完成 WebSearch，但不开放 Bash；WebFetch 仍拒绝；
8. `PUBLIC_NETWORK_DOMAIN` 不开放 WebSearch/WebFetch；
9. localhost/LAN 默认拒绝，Agent Bash 不能调用 Dual API；sandbox 内部 socket/proxy 不构成 Agent 通用出口；
10. 用户 proxy env 不形成旁路，provider 连接仍可用；
11. worktree 与受控 scratch 行为符合 capability proof 声明，incident 同类解压流程不发生静默 filesystem 回归；
12. `approveAll=true` 不能进入 E1-enabled execution，legacy 未配置路径不得冒充 ENFORCED；
13. human network wait 对 scheduler 为 manual-only，零自动 retry/resume；
14. CLI/server/UI parity，全都经过同一 ledger command；
15. generated settings 与 run metadata SHA 一致且不含 secret；
16. 删除 policy/attestation 后测试必须失败，不能回退用户全局 settings；
17. captured incident 形状的去业务化 replay：旧 `autoApprove execute` 会自动允许网络命令，新边界必须在网络 I/O 前拒绝并进入 human authority；
18. non-network implement/read/review sibling profiles 不退化。

focused tests、full reactor、`git diff --check` 与独立代码级 diff review 全部 PASS 后才允许提交。

## 8. End-to-End Invariant

| 维度 | E1 invariant |
|---|---|
| Declared by | permission profile + execution authority policy |
| Proven by | generated settings ref/SHA + sandbox attestation + structured permission evidence |
| Consumed by | AcpxAgentTransport session/process launch and retry grant application |
| Enforced before | Claude tool subprocess network I/O |
| Failure route | typed execution failure or human network permission wait；零静默 unsandboxed |
| Cannot bypass | direct/indirect egress matrix + forged/stale/cross-occurrence approval tests |
| Residual | approved domain 内路径不细分；Codex 若未证明则 NOT MODELED；宿主机全局网络不在范围；filesystem/loopback 仅可按 capability proof 的精确结果声明 |

## 9. 实施与 real-case 顺序

1. 本计划独立架构评审；
2. capability proof 与 producer/consumer/sibling sweep；
3. proof PASS 后，A + mini-B 作为一个原子 checkpoint 实施；
4. focused/full tests、diff-check、独立代码级 diff review；
5. clean commit 后结束 E1 checkpoint；产品 workstream 恢复不属于 E1 production 实施计划；
6. 另写 OKX case-specific Recovery Verification Gate，负责 partial-write 审计、现有 vendored artifact 的 integrity/provenance
   处置与同一 workstream retry；该 Gate 不得反向修改 E1 policy；
7. 恢复时不创建第二样本；再次发生同类 bypass 或 policy attestation 缺失立即 stop-loss。

成熟度：

- 当前：`NATURAL INCIDENT PROVEN / ENFORCEMENT NOT IMPLEMENTED`；
- deterministic checkpoint PASS：`CLAUDE TOOL EGRESS + DOMAIN APPROVAL ENFORCED / REAL-CASE NOT PROVEN`；
- OKX 恢复后自然触发 deny -> human approve -> one-execution allow：相应 slice 才可标 `PROVEN`；
- total host/Codex/container network safety 不得随之宣称完成。
