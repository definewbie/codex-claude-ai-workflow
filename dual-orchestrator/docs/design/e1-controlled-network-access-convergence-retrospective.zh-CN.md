# E1 受控网络访问收敛复盘

日期：2026-09-02  
状态：`CURRENT ARCHITECTURE EXPLAINED / EFFECT PARTIALLY PROVEN`

## 1. 这项工作到底解决什么问题

E1 要解决的不是“让某个项目能下载依赖”，也不是“给 OKX 特批联网”。它解决的是一个通用执行边界：

> 当 Claude Agent 可以运行 Bash 时，Dual 必须允许任务所需的合法网络访问，同时阻止未授权的公网、局域网和本机网络访问；
> 这个约束必须由运行时强制，不能依赖模型自觉、prompt 提醒或人工事后阅读日志。

问题最初在 OKX K 线 real-case 中暴露。implement profile 自动批准 `execute` 后，Agent 可以在 Bash 中自行选择 `curl`、
包管理器或脚本子进程访问网络。Dual 当时能控制 ACP 工具权限，却不能证明 Bash 子进程的真实网络出口受控。

因此原问题包含两个不可分割的部分：

1. **网络访问能力**：合法任务必须能访问明确允许的公开域，否则复杂 real-case 无法完成。
2. **网络安全边界**：Bash、脚本、Git、包管理器和直接 socket 不应绕过同一执行策略；当前 R4 只验证其中的
   child/Git 与 IPv4 TCP external/loopback/LAN 边界。

只实现第一部分等于放开 `fetch`；只实现第二部分则所有联网任务都失败。E1 选择的是“A + mini-B”：复用 Claude Code
已有 sandbox，不自建 proxy 平台，但要求 policy、adapter 和 execution evidence 形成不可绕过的闭环。

## 2. 最终采用的三层 owner

```text
Dual ClaudeExecutionPolicy
  声明：sandbox、domain policy、filesystem、credential deny、transport owner
              |
              v
reviewed acpx fork
  负责：ref/SHA/lineage 校验、session 绑定、spawn 前环境收敛、冲突 fail-closed
              |
              v
embedded Claude Code sandbox
  执行：macOS Seatbelt、sandbox proxy、domain allowlist、credential scrub
              |
              v
occurrence evidence
  证明：实际 policy、tool call、网络结果、permission、session closure
```

权威边界如下：

| 事实 | Owner | 不是 Owner 的对象 |
|---|---|---|
| 允许哪些网络域 | occurrence-scoped `ClaudeExecutionPolicy` | prompt、模型正文、curl stderr |
| policy 是否绑定到本次执行 | reviewed acpx session options + ref/SHA | session 名称、latest 文件、人工解释 |
| Bash 网络是否被拦截 | Claude sandbox runtime | Dual 的字符串分类器 |
| 人是否批准 WebSearch | Dual human gate ledger | `--allowed-tools` 本身 |
| 工具是否对模型可见 | SDK `tools/disallowedTools` | permission auto-approval |

这套划分遵守 Anthropic 当前公开契约：sandbox 对 Bash 及其子进程实施 OS 级文件和网络边界；网络通过 sandbox 外部代理按域
过滤；`allowUnsandboxedCommands=false` 可关闭非沙箱重试逃生口；`credentials.envVars` 在每次 sandboxed command 前删除指定
变量。取证日期为 2026-09-02，参考 [Claude Code sandbox 文档](https://code.claude.com/docs/en/sandboxing)。

## 3. 解决思路

### 3.1 不解析 Bash，而是在执行层约束它

没有尝试分析 `curl`、shell 管道、变量替换或脚本内容。这样的 parser 永远会漏掉子进程、解释器、Git、Maven、npm 和 raw
socket。E1 把整个 Bash 进程树放进 Claude sandbox，让 OS/network proxy 成为 enforcement owner。

### 3.2 policy 是不可变输入，不是 prompt 建议

Dual 为每个 Claude execution 生成 `claude_execution_policy.v2`，固定：

- `network_transport_owner=claude_sandbox`；
- sandbox 必须可用，否则 hard fail；
- 禁止 unsandboxed fallback；
- domain allowlist 采用 strict managed-only 语义；
- workspace 与 session tempdir 保持可写；
- 三个 Anthropic provider credential 不进入 sandboxed Bash。

policy 以 ref/SHA/lineage 绑定 occurrence。任何删除、篡改、旧 schema、错误 owner 或 lineage 漂移都必须发生在 provider/Bash
副作用之前被拒绝。

### 3.3 acpx 是 transport guard，不是第二 policy owner

官方 acpx 当时没有完整转发 E1 所需 SDK options，因此采用最小 reviewed fork。fork 只做四类工作：

1. 严格解析并转发固定 policy；
2. 在 Claude child 最终 spawn 前清理宿主 ambient proxy 与冲突开关；
3. 持久化 session options；
4. 对 persistent session 的 requested/persisted binding 做全等校验。

它不决定允许域、不运行自建 proxy，也不从环境变量推导新政策。

### 3.4 把 WebSearch 与 Bash 网络拆开

WebSearch 是 provider-native/server tool；Bash 网络是本地进程 egress。两者不是同一能力：

- WebSearch 的 human approval transport 已接入 ledger；
- `allowedTools` 只表示免确认，不是模型可见性 owner；
- 当前 GLM Anthropic-compatible backend 对原生 WebSearch/WebFetch 返回 `modelCode` 错误；
- WebSearch visibility 需要另行使用 SDK `tools/disallowedTools` 建模。

因此 E1 closure 不再依赖 WebSearch 成功。否则 provider backend 不兼容会继续污染 sandbox 网络结论。

## 4. 实施过程中遇到的主要问题

### 4.1 “传输了 policy”不等于“policy 生效”

第一版先证明 policy 能从 Dual 到 acpx、再到 SDK，但真实 Gate 发现 Bash 环境与预期不一致。这说明当时只达到
`MODELED/TRANSPORTED`，没有证明最终 child environment 和网络效果。

修正：增加 child-spawn 行为测试、真实 capability Gate，并把成熟度严格拆为 `ENFORCED` 与 `PROVEN`。

### 4.2 错把 proxy 变量存在当成泄漏

早期 Gate 看到 Bash 中存在 `HTTP_PROXY` 等变量便立即失败。后来确认 Claude sandbox 本身会通过内部 proxy 实施网络策略，
这些变量可能是安全机制的一部分，并不等于宿主代理凭据泄漏。

修正：Dual 只清理进入 adapter 前的宿主 ambient proxy；不读取、不保存、不分类 sandbox 内部 proxy 值。credential deny 只保护
三个 Anthropic provider 输入。

### 4.3 Shadowsocks/TUN 让“宿主直连”这个说法失真

当前宿主开启 Shadowsocks，外部流量经过 TUN/透明路由。宿主请求成功只能证明“宿主路径可达”，不能证明未经过代理。

修正：后续证据统一称 `host-path reachability control`。它只用于排除目标全局不可达；sandbox 内 raw socket 的 `EPERM`
仍是在进程 connect 边界发生，与宿主后续如何路由是两个层次。

### 4.4 依赖错误字符串导致 Gate 无效

旧 Gate 要求 curl stderr 命中特定 sandbox 文案，真实结果是普通 CONNECT 403。403 可以来自 Claude proxy、Shadowsocks 链或上游，
无法机械归因，因此样本只能判 `INVALID_OBSERVATION_SIGNATURE`。

修正：新版 Gate 使用同一 occurrence、同一 host-path 下的 allow/deny 差分，加上运行前后宿主可达对照；不再要求某段英文错误文本。

### 4.5 persistent session 保存了旧 policy binding

allowlist 差分诊断中，有效 `session/new` 使用新 policy，但最终 record 留下较早 invocation 的 binding。根因是 acpx 在
reuse/replacement/fallback 多条路径上，guard 时机和 options 回写不一致。

修正：建立唯一 persistent-session comparator，并在 manager replacement、direct prompt、queue owner 和 fresh fallback
四条路径的副作用前强制。checkpoint `20b4e3f1` 已通过 fork `985/0/0`、Dual `1294/0/0/0` 和独立 diff review。

### 4.6 WebSearch 样本无法证明 sandbox

当前 provider 的文本调用可用，但原生 WebSearch/WebFetch backend 不兼容；两者在进入可区分的权限路径前都失败。

修正：把它分类为 `INVALID_PROVIDER_TOOL_BACKEND`，从 E1 Gate 移除。不得通过开放 WebFetch、批准整个 `fetch` kind 或解释模型
正文来制造 PASS。

## 5. 哪些工作是必要的，哪些不是

必要工作：

- policy v2 与唯一 owner；
- reviewed acpx 的固定字段转发与严格校验；
- child spawn 前 ambient env 收敛；
- persistent session binding replacement guard；
- allow/deny bounded 差分、IPv4 TCP raw socket、credential 与 filesystem 真实证据。

明确没有做：

- 没有自建 proxy、网络服务或通用 env provenance 平台；
- 没有写 Bash 命令解析器；
- 没有为 OKX、Mailpit 或某个域写 production 特判；
- 没有把 WebSearch、MCP、Bash fetch 合并成一个“联网”抽象；
- 没有声称控制 Codex、Docker、hooks/MCP stdio 或宿主机总网络。

## 6. 当前成熟度

| 能力 | 状态 | 依据 |
|---|---|---|
| policy schema、ref/SHA/lineage transport | `ENFORCED` | strict loader、Doctor、fork identity、deterministic tests |
| network transport owner | `ENFORCED` | policy v2 + final child env tests |
| persistent policy replacement guard | `ENFORCED` | 四入口副作用前拒绝 + fork 985 tests |
| Claude sandbox connectivity + domain 差分 | `OBSERVED / BOUNDED DIFFERENTIAL` | 同 occurrence 允许成功、非允许失败；无 enforcement-owned causal signal |
| OS raw-socket no-direct-connect | `OBSERVED PASS` | external/loopback/LAN 均 `EPERM`，但旧 Gate 整体 INVALID |
| Claude Bash IPv4 TCP direct-connect boundary | `NOT PROVEN` | 尚缺 binding 修复后的统一有效样本与机械 evaluator |
| WebSearch ledger approval transport | `ENFORCED` | human gate + exact approval binding |
| WebSearch provider-side cannot-bypass | `NOT PROVEN` | backend 不兼容；visibility 未建模 |
| `PUBLIC_NETWORK_DOMAIN` | `NOT IMPLEMENTED` | 尚无 operator domain grant 产品能力 |
| Codex/容器/宿主总网络 | `NOT MODELED` | 不在 E1 范围 |

## 7. 决策反转：停止 R4，恢复产品主线

R4 不再执行。尚未提交的 Gate、runner、evaluator 和 fixture 已删除，已激活但尚未消费的 provider 预授权随 Gate 一并失效。
这不是因为网络安全不重要，而是因为 R4 已从“验证当前任务能否安全访问必要网络”扩张成“证明第三方 sandbox 在特定
OS、代理拓扑和协议上的完整内部因果机制”。后者成本高、边界不稳定，也不是完成当前 real-case 的必要条件。

需要重新区分三个问题：

1. **目标应用运行时网络**：例如 Java 应用通过 HTTP client 调用 OKX API。这是产品功能和产品测试边界，不需要 Agent 的
   Bash 网络权限来证明。
2. **构建与依赖网络**：Maven、npm 或容器拉取依赖。这是受控执行环境的基础能力，可由 operator 或 CI 策略配置。
3. **Agent Bash 网络**：模型自行运行 curl、Git、包管理器或脚本联网。这是执行安全边界，应由显式授权控制，但它不应在
   没有阻塞产品目标时成为 real-case 的前置证明工程。

当前保留已经完成且有直接价值的 deterministic checkpoint：policy transport、唯一 transport owner、child env 收敛和
persistent-session binding guard。它们防止策略丢失、串用或被旧 session 覆盖。完整 sandbox 网络效果仍为 `NOT PROVEN`，
但不再阻塞 OKX real-case。

未来若产品确实需要 Agent 主动联网，优先实现最小的人类授权模型，而不是恢复 R4：

```text
Agent -> typed NETWORK_ACCESS_REQUIRED(domain, purpose)
      -> human: deny | allow_once | allow_session | allow_global
      -> occurrence/session-scoped policy
      -> fresh execution
```

默认使用 `allow_once`；`allow_session` 与 `allow_global` 必须显式、可撤销且可审计。IP literal、localhost、LAN、通配域默认拒绝。
这只建模“谁批准了什么范围的网络访问”，不承诺证明 Claude sandbox 的全部内部实现。

## 8. 为什么 scope 一再扩大

本轮扩张不是单一技术判断失误，而是五个决策错误叠加：

1. 发现安全风险后，未先证明它是否阻塞当前产品目标，就把它提升为 real-case 的前置条件。
2. 把“策略已传输并由运行时消费”与“必须证明第三方 sandbox 的内部因果机制”混成同一个完成标准。
3. 把同行评审中的每个观察项都当成同一 checkpoint 必须关闭的缺口，导致测试矩阵和文档不断增殖。
4. Verification Gate 从一次性判定规则演化成 runner、evaluator、fixture 和证据平台，本身开始成为新产品。
5. 忘记“先有后优”：已经有可工作的业务网络路径，却持续优化一个尚未成为真实阻塞的安全证明边界。
6. 没有及时区分“Agent 执行沙箱”与“产品验收环境”。OKX self-test 在 Claude sandbox 内无法绑定本地回环端口，
   因而把集成测试缺失判为环境限制；同一代码在宿主环境运行后暴露真实 Spring 初始化循环依赖。安全策略改变了测试的
   可观察范围，却一度被当成产品质量结论。

以后执行以下止损纪律：

- 开工前先写一句：当前主目标是什么，这个缺陷是否正在阻止它。答案不是“是”时，默认进入 TODO 或 bounded assessment。
- 验证代码和验证文档的规模若超过 production 改动，立即停止并重新判断是否在证明错误的问题。
- 不证明第三方实现内部细节，只验证 Dual 依赖的可观察契约和失败行为。
- 同一主题连续两轮 review 都扩大文件、状态或测试面时，必须在“缩小、延期、停止”中选一个，不能自动进入下一轮修订。
- 新机制只有在 owner、唯一 consumer、被阻止的副作用和 cannot-bypass test 都明确时才进入 production。
- Agent sandbox 无法执行某类标准验证时，不允许模型据此把产品实现判为正确；必须由等价的 harness-owned 验证环境运行该
  测试，或将结果明确标为未验证并阻止成功路由。

## 9. `grill-me` 的使用边界

`grill-me` 已是本地 skill，不需要安装插件。它适合两种时机：新增 schema/protocol/module 等高成本架构之前；或者同一计划经过
两轮评审仍持续扩张时。它不应成为每个小修复的固定流程，否则会再次把决策工具变成流程负担。

下一步恢复 OKX workstream，以“产品实现完整、测试通过、workflow terminal”为目标继续。E1 降为重要但不紧急的安全 TODO，
除非后续真实任务再次被 Agent 网络授权边界直接阻塞，否则不恢复 R4。
