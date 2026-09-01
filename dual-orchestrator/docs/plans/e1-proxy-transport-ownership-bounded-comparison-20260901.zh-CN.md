# E1 Proxy Transport Ownership Bounded Comparison

日期：2026-09-01  
状态：`DRAFT / ARCHITECTURE REVIEW REQUIRED`  
基线：`fc0b1efe`

## 1. 唯一目标

关闭 R3 暴露的 proxy owner 冲突：Dual policy 把 proxy 变量列为 Bash sensitive-env deny，同时 Claude sandbox
又需要向 Bash 注入网络代理 transport。目标不是证明某个 URL 安全，而是让 policy 对“provider secret”与
“sandbox network capability”的分类、owner 和 enforcement 一致。

本比较不运行 provider，不恢复 OKX，不实现 `PUBLIC_NETWORK_DOMAIN`，不建设通用 env-provenance 平台。

## 2. 已确认事实

### 2.1 R3 occurrence

- policy SHA 经 launcher、session options 与归档文件三点绑定；
- `subprocess_denied_env_names` 含三个 Anthropic 名称和八个 proxy 名称；
- Bash effective env 中三个 Anthropic 名称 absent，`HTTP_PROXY` 为 `present_with_userinfo`；
- 脚本在网络请求前 `exit 73`，所以 network deny 与 OS direct-connect 未被测量；
- 原值未落盘，不能从该 occurrence 判断 userinfo 的具体来源或生命周期。

### 2.2 启动链排除

`scripts/acpx-claude-agent-env.sh` 在 exec adapter 前无条件 unset 八个大小写 proxy 名称，只从
`~/.claude/settings.json` 转发固定 Anthropic allowlist。captured R3 的 `settingSources=[]`，fork 只传入绑定的
managed sandbox settings。使用同一 `claude-agent-sdk 0.3.232` 执行只读
`resolveSettings({settingSources: []})`，八个 proxy key 在当前 effective managed env 中也全部 absent。因此本轮
值没有沿 Dual wrapper、用户 settings bridge 或当前 managed env 进入 adapter 的路径。

剩余 owner 位于 Claude adapter/SDK/embedded runtime；R3 无须、也不能继续细分其内部实现才能决定 Dual 的契约。

### 2.3 官方契约

Anthropic 文档（取证日期 2026-09-01）明确：

- [`sandbox.credentials.envVars`](https://code.claude.com/docs/en/sandboxing#protect-credentials) 的 `deny` 在每个 sandboxed command 前 unset 指定变量；
- sandbox 网络通过 sandbox 外部的 built-in proxy 执行 domain allowlist；
- [corporate upstream proxy](https://code.claude.com/docs/en/sandboxing#network-isolation) 是另一种显式配置，Claude 会先执行 domain allowlist，再转发到 upstream proxy；
- proxy endpoint/topology 不是对调用方承诺的稳定 authority。

因此，同一个 proxy 名称不能同时被 Dual 当作“最终环境必须不存在的 credential”与 Claude 的 network transport。

## 3. Owner 模型

```text
Definition / policy artifact
  owner: Dual ClaudeExecutionPolicy
  declares: network_transport_owner=claude_sandbox, provider-sensitive env deny set, domain policy

Adapter spawn environment
  owner: reviewed acpx fork（runtime enforcement）+ acpx-claude-agent-env.sh（defense in depth）
  enforces: E1-bound Claude child 在 process/session env 合并后删除八个 ambient proxy 名；wrapper 在 adapter 前重复删除

Session policy consumption
  owner: reviewed acpx fork
  enforces: exact schema/ref/SHA/lineage before session/new

Effective Bash environment and network effect
  owner: embedded Claude runtime
  may project: internal HTTP(S)_PROXY capability
  enforces: credential scrub + sandbox proxy + OS no-direct-connect

Execution evidence
  owner: occurrence record/stream and bounded probe
  proves: command, policy identity, denial behavior; never stores proxy value
```

不新增 lifecycle entity。policy artifact 仍是 definition input，session/run evidence 仍是 execution fact。

## 4. 候选比较

### Candidate A：保留 proxy deny，增加 HMAC provenance probe

做法：宿主与 Bash 分别对 proxy 值做 occurrence-local HMAC，仅记录 equal/unequal。

收益：能进一步判断两个值是否相同，不保存原值。

拒绝作为 production 方案：

- wrapper 已经机械清除了宿主 proxy，HMAC 重证一个已有确定性边界；
- 即使 unequal，也只能说明发生了后注入，不能证明内部 capability 的安全属性；
- 继续保留 proxy deny 仍与 Claude built-in proxy 的最终环境语义冲突；
- 会把一次性诊断手段变成长期 provenance 机制，不能关闭 owner 模型。

结论：`REJECT`。若独立评审发现 wrapper 存在旁路，HMAC 只可作为一次性 Gate fixture，不进 production。

### Candidate B：只缩小 deny 名单，不声明 transport owner

做法：从 `subprocess_denied_env_names` 删除 proxy 名称，仅保留三个 Anthropic 名称；其他代码不变。

收益：改动最少，并与 Claude credential deny 语义一致。

拒绝：它没有声明为何允许 proxy、谁拥有 sandbox transport，也没有使 wrapper scrub 成为
policy 的必要条件。能力仍依赖脚本约定，容易在新 adapter 入口漂移。

结论：`REJECT`，属于 workaround。

### Candidate C：固定 `claude_sandbox` owner + provider-sensitive env deny

做法：

1. policy contract 升级为新版本，在 network policy 内固定声明
   `network_transport_owner=claude_sandbox`；不开放任意字符串、endpoint 或配置 passthrough；
2. 新字段 `provider_subprocess_denied_env_names` 只含 `ANTHROPIC_API_KEY`、`ANTHROPIC_AUTH_TOKEN`、
   `ANTHROPIC_BASE_URL`；旧的泛化 `subprocess_denied_env_names` 不保留兼容双写；
3. reviewed fork 仅对“Claude agent + 当前 session 携带有效 E1 policy binding”的 child，在 process/auth/session env
   全部合并后删除八个大小写 proxy 名，并删除 ambient `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB`；不得影响 Codex、其他 agent
   或未启用 E1 的 legacy Claude session。后者必须被中和，因为官方契约说明该变量会关闭
   `autoAllowBashIfSandboxed`，不能让 env 在运行时否决 policy 声明；
4. wrapper 在 adapter spawn 前继续 unset 全部 proxy 名称，作为 defense in depth；Doctor 继续校验 wrapper identity，
   但 wrapper/Doctor 不再是唯一运行时防线；
5. fork exact-keys 校验 regime，只映射 provider-sensitive env deny set 到 `sandbox.credentials.envVars`，不设置
   `httpProxyPort` / `socksProxyPort`；SDK 的 `credentials.envVars` 只是执行 sensitive-env deny 的机制，
   不因此把 proxy transport 重新归类为 credential；
6. run metadata/Doctor 只投影 `claude_sandbox`、policy ref/SHA 与 wrapper identity，不记录 effective proxy URL；
7. 后续 capability Gate 删除“proxy 名称/URL userinfo 必须不存在”判据，改为验证 provider-sensitive names absent、
   domain-policy denial、OS no-direct-connect、无 unsandboxed escape 与 WebSearch coexistence。

收益：使用已有 Claude sandbox 网络实现，同时让 Dual 的 policy、spawn precondition 与执行证明同一语义。
该 owner 允许 Claude 按官方契约选择 built-in proxy，或在管理员显式配置时链接 corporate upstream；内部 endpoint、
认证和拓扑仍不成为 Dual authority。Dual 的 strict domain policy 必须继续在 upstream 转发前生效。

成本：policy/fork contract 版本同步，旧 E1 policy 明确不可 resume；需 wrapper、fork、Doctor、transport 与 Gate
sibling tests。没有新 module、proxy service 或通用 provenance schema。

结论：`RECOMMENDED`。

### Candidate D：Dual 自建并拥有 custom proxy

做法：Dual 启动 proxy，向 Claude 设置 `httpProxyPort`/`socksProxyPort`，自行管理认证、domain policy、日志与生命周期。

收益：endpoint、credential 与审计全部可由 Dual 直接拥有。

拒绝：这是新网络服务和跨平台生命周期，扩大安全责任；Claude built-in proxy 已提供当前所需 deny/allowlist 能力。
只有出现可复现的 built-in proxy 绕过、企业 upstream 强需求或官方契约无法满足 domain policy 时才可重评。

结论：`DEFER / REVISIT-ONLY`。

## 5. 推荐边界

选择 Candidate C。它不是为 R3 的 `HTTP_PROXY` 字符串打补丁，而是修正通用分类错误：变量名不能决定它是
secret 还是 transport；owner 与注入阶段才决定语义。

### 5.1 Declared by

occurrence-scoped Claude execution policy：固定 `network_transport_owner=claude_sandbox`、provider-sensitive env deny、
strict domain policy 与 filesystem policy。

### 5.2 Proven by

- wrapper deterministic test：输入八个 proxy canary，adapter child 全部 absent；Anthropic provider inputs 保留；
- fork option-capture test：exact policy 映射到 SDK，proxy 名称不进入 credentials deny，不出现 custom proxy port；
- session/run metadata：policy ref/SHA/schema/regime 绑定当前 occurrence；
- 独立 capability Gate：真实 Bash denial 与 OS direct-connect 行为，不检查 proxy URL 形态。

### 5.3 Consumed by

fork 消费 policy 并在最终 Claude child spawn env 强制 proxy scrub 与冲突开关中和；wrapper 重复执行 ambient-env scrub；embedded runtime
消费 SDK sandbox options；Doctor 和 run metadata 只投影同一 identity。

### 5.4 Enforced before

fork-side scrub 在 E1-bound Claude child 创建前且晚于 session env merge；wrapper scrub 在 adapter process 创建前；policy
ref/SHA/schema/regime 在 session/new 前；domain/OS boundary 在 Bash network side effect 前。

### 5.5 Failure route

wrapper identity/scrub test、policy mismatch、legacy schema、custom proxy field、fork identity 或 session conflict
任一不符均为 `NETWORK_AUTHORITY_NOT_CONFIGURED` / provider 前 hard failure；不 fallback official acpx，不 resume 旧 E1 session。

### 5.6 Cannot-bypass tests

1. fork 在 process env 与 session env 分别注入大小写八个 proxy canary 后，仅对 E1-bound Claude child 全部删除；
   Codex、其他 agent 与无 binding 的 legacy Claude child 保持既有环境语义；
2. fork 在 session env 合并后删除 ambient `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB`，session env 不能重新注入；
   `CLAUDE_CODE_EXECUTABLE` 保持 acpx-owned launch input；不得通配删除全部 `CLAUDE_CODE_*`；
3. wrapper 在大小写八个 ambient proxy canary 下启动 child，八项均 absent；wrapper 仍只从 settings 转发固定
   Anthropic allowlist；这里的“不透传”仅指 settings 中未列入 allowlist 的键，不声称清空全部继承的 process env；
4. policy 只接受固定 `claude_sandbox` owner，unknown/missing/其他 owner 失败；
5. `provider_subprocess_denied_env_names` 缺项、多项、顺序漂移或重新加入 proxy 名称均失败；旧字段出现也失败；
6. fork SDK capture 证明无 custom proxy port、provider deny 映射准确；
7. ensure/prompt/correction 同一 policy ref/SHA/regime，session reuse conflict 在 prompt 前失败；
8. named Claude profiles 全部配置新 policy，Codex/legacy approveAll 不冒充；
9. Doctor 对旧 fork、旧 schema、错误 wrapper identity fail closed；
10. 新 capability Gate 在带 userinfo proxy canary 的宿主环境下不读取/分类 proxy URL、不再以 73 退出；只验证
    provider secret absence、domain/OS 行为与 WebSearch coexistence。

### 5.7 Residual

- sandbox proxy 的内部 endpoint、token 与实现拓扑由 Claude runtime 拥有，Dual 不审计其值；
- adapter child 仍继承非网络 authority 的一般 process env；本 checkpoint 不建设通用 env allowlist。与 policy
  `autoAllowBashIfSandboxed=true` 冲突的 `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB` 由 E1 finalizer 删除，
  `CLAUDE_CODE_EXECUTABLE` 由 acpx 拥有；其他
  `CLAUDE_CODE_*` 只有在 reader/writer sweep 证明会改变 sandbox/network authority 时才可逐项 pin/reject，禁止通配清除；
- provider credential 的 Bash 隔离由 policy v2 `sandbox.credentials.envVars` 唯一拥有；hooks/MCP stdio 与任意其他
  非 proxy secret 的全子进程隔离不在本 checkpoint。为此建立通用 child-env allowlist 会扩大为独立安全平台；若后续出现
  自定义 secret 暴露的自然证据，再以独立 bounded comparison 重评；
- ambient host/user proxy 不进入 adapter；管理员显式配置的 corporate upstream 属 Claude sandbox transport 的基础设施，
  不能扩大 Dual 的 domain allowlist。当前环境未配置该 upstream，其跨环境效果仍 `NOT PROVEN`；
- capability effect 在下一次独立 Gate 前仍 `NOT PROVEN`；
- Codex 与容器网络不在本边界。

## 6. 最小实施面与顺序

实施计划必须在 coding 前完成 reader/writer/sibling sweep，锁定以下现有边界：

1. `ClaudeExecutionPolicy.java` 与测试；
2. reviewed acpx fork policy loader、session options/persistence、Claude-only spawn env 最终化与测试；
3. `acpx-claude-agent-env.sh`、configure/Doctor 与 shell tests；
4. `AcpxAgentTaskHandler` / transport / metadata 的 E1 identity projection；
5. 所有 bundled named Claude profile 与 legacy/Codex sibling；
6. R3 Gate fixture 的 proxy-value observation 删除边界；userinfo canary 必须成为“不会再触发 73”的负向回归。

只读 `resolveSettings({settingSources: []})` 复现证据归档在
`docs/testing/capability-proofs/20260901-e1-proxy-transport-ownership-comparison/`，其中只记录 SDK 版本、
setting sources 与八个 proxy key 的 present/absent，不记录任何值。

顺序：只读 sweep -> implementation plan -> 独立架构评审 -> 单一原子 checkpoint -> focused/fork/full reactor ->
独立 diff review -> 新 capability Gate。当前 comparison 不授权 production 修改或 provider 调用。

## 7. 成熟度

- policy transport：`ENFORCED`；
- R3 proxy owner conflict：`VALIDATED DEFECT`；
- Candidate C：`ASSESSED / NOT IMPLEMENTED`；
- sandbox network effect 与 WebSearch coexistence：`NOT PROVEN`；
- `PUBLIC_NETWORK_DOMAIN`：`NOT IMPLEMENTED`。

## Boundary Review

```text
Boundary Review: PASS (comparison only)
Touched boundary: external adapter / execution policy / sandbox network transport
Source of truth: occurrence-scoped Claude execution policy + execution evidence
Projection: run metadata / Doctor only; no proxy value
Required next step: independent architecture review of Candidate C and implementation boundary
Forbidden: provider rerun, generic provenance platform, custom proxy implementation, OKX resume
```
