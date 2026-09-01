# E1 Claude Sandbox Network Transport Owner 实施计划

日期：2026-09-01  
状态：`IMPLEMENTED / DETERMINISTIC VERIFICATION PASS / DIFF REVIEW REQUIRED`  
基线：`fc0b1efe`

## 1. 唯一目标

修复 `claude_execution_policy.v1` 把 Claude sandbox 自身的 proxy transport 名称误分类为 provider credential 的
owner 冲突，并把 ambient proxy scrub 从 wrapper 约定提升为 E1-bound Claude child spawn 前的 runtime enforcement。

目标阶段：`P0 blocker`。该缺陷使 E1 capability Gate 在真正执行网络矩阵前停止，直接阻断复杂 real-case 的受控网络能力证明。

本 checkpoint 不实现 domain approval、不自建 proxy、不建立通用 env allowlist/provenance 平台、不运行 provider、
不恢复 OKX workstream、不修改 workflow/ledger/UI。

## 2. Boundary Map

| Fact | Owner | Lifetime | 本 checkpoint 行为 |
|---|---|---|---|
| E1 network policy | Dual `ClaudeExecutionPolicy` | execution occurrence | schema v2 固定声明 `claude_sandbox` owner、domain/filesystem 与 provider credential deny |
| Adapter child env | reviewed acpx fork | concrete agent process | E1-bound Claude child 最终 env 删除八个 ambient proxy 名与 ambient `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB` |
| Sandbox proxy topology | Claude runtime | Bash execution | Dual 不设置、不读取、不审计 endpoint/token/port |
| Wrapper/provider route | `acpx-claude-agent-env.sh` | adapter startup | provider allowlist + proxy scrub，作为 defense in depth |
| Execution evidence | run metadata + capability Gate | occurrence | 只记录 policy identity、行为结果；不保存 proxy value |

不存在新 lifecycle entity。policy 仍是 execution definition input，run/session evidence 仍是 execution fact。

## 3. Producer-Consumer Sweep 结论

### 3.1 Dual producer

`dual-adapter-acpx/.../ClaudeExecutionPolicy.java` 是 policy document 唯一 producer。现行 v1 写出固定 11 项
`subprocess_denied_env_names`：三个 Anthropic provider 名和八个 proxy 名。实施后升级为
`claude_execution_policy.v2`：

- `network_transport_owner` 固定为 `claude_sandbox`；
- `provider_subprocess_denied_env_names` 固定且仅含
  `ANTHROPIC_API_KEY`、`ANTHROPIC_AUTH_TOKEN`、`ANTHROPIC_BASE_URL`；
- 删除旧字段，不双写、不兼容 v1；
- network/filesystem/lineage/generated-at 语义不变；
- metadata/Doctor/build-info 同步投影 v2，旧 E1 session 明确 `NETWORK_AUTHORITY_NOT_CONFIGURED`。

### 3.2 acpx fork consumer

`src/acp/claude-execution-policy.ts` 是 policy grammar/SDK mapping owner；继续 exact-key、duplicate-key、SHA、lineage、
canonical path/domain 与 secret-like 校验。它只把三个 provider 名映射为 `sandbox.credentials.envVars` deny，
不写 `httpProxyPort`、`socksProxyPort` 或任何 env value。

policy ref/SHA 的 CLI、queue、cold-load、session reuse 路径保持既有单一 binding；schema identity 随 patch/build-info 更新，
不新增 transport channel。

### 3.3 child spawn enforcement

`src/acp/auth-env.ts` 当前先复制 `process.env`，再合并 auth 与 session env；`src/acp/client.ts` 随后直接 spawn。
通用 `buildAgentSpawnOptions` 服务所有 agent，不能无条件修改。

实施时新增一个小的 Claude E1 env finalizer。`resolveAgentLaunchPlan()` 先识别 `claudeAcp` 与完整 policy ref/SHA，
`ensureLaunchSupport()` 随后保留/补充 `CLAUDE_CODE_EXECUTABLE`，finalizer 必须挂在 `ensureLaunchSupport()` 之后、
`spawnAgentProcess()` 之前，并作用于所有 env merge 完成后的最终 child env：

1. 按平台一致的 key 比较删除 `ALL_PROXY/HTTP_PROXY/HTTPS_PROXY/NO_PROXY` 及四个小写名；
2. 删除必须晚于 session env merge，防止 session env 重新注入；
3. 使用与 proxy scrub 相同的平台一致 key 比较删除 ambient `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB`，session/process env
   不得以大小写变体重新注入。官方 settings reference 明确
   该变量会关闭 `autoAllowBashIfSandboxed`，若保留会让 env 成为第二 policy owner，并使 capability Gate 在 Bash probe
   permission request 处再次停住；
4. 保留 `CLAUDE_CODE_EXECUTABLE`，其 owner 仍是 acpx launch resolution；
5. 不通配删除其他 `CLAUDE_CODE_*`，不清洗一般 process env；
6. 非 Claude、无 E1 binding 的 legacy Claude、Codex/Gemini 等 sibling 维持原行为。

官方契约取证日期为 2026-09-01：
[`sandbox.autoAllowBashIfSandboxed`](https://code.claude.com/docs/en/settings-reference#sandbox-autoallowbashifsandboxed)、
[`CLAUDE_CODE_SUBPROCESS_ENV_SCRUB`](https://code.claude.com/docs/en/env-vars) 与
[`sandbox` network/OS enforcement](https://code.claude.com/docs/en/sandboxing)。provider credential 的 Bash 隔离继续由
policy v2 的 `sandbox.credentials.envVars` 唯一强制；本 checkpoint 不声明 hooks/MCP stdio 的全子进程 credential scrub。

这使 fork 成为 runtime enforcement owner；wrapper scrub 与 Doctor wrapper identity 只提供第二层保护。

### 3.4 wrapper 与 settings

`scripts/acpx-claude-agent-env.sh` 继续在 adapter 启动前删除八个 ambient proxy 名，并只从 settings 转发既有
Anthropic/model allowlist。测试中的“未透传”只指 settings 中未列入 allowlist 的键；一般 parent process env 仍可继承，
不得把它描述为全环境净化。

只读 SDK 复现证据已归档于：

- `managed-settings-proxy-key-presence.tsv`，SHA-256
  `51c330b57366abf39b6a49472951083fa71151bd8799489e08fe6c1268ca570c`；
- `README.md`，SHA-256
  `3e07ae6f28ef84124fabc6925453aa4db590cbf581fed829f6e93689d71dd9e2`；
- 同目录 `SHA256SUMS` 可独立复算。

### 3.5 Gate fixture

冻结 R3 evidence 不改写。新的 capability Gate 复制必要 fixture 后删除 proxy URL 读取、userinfo 分类和 exit 73 分支；
增加负向 fixture：即使宿主注入 userinfo proxy canary，probe 也不得读取其值或以 73 退出。Gate 仍必须验证 provider secret
absence、domain allow/deny、raw socket no-direct-connect、unsandboxed escape 禁止与 WebSearch coexistence。

## 4. 最小文件范围

### 4.1 Production / fork patch

1. `dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/ClaudeExecutionPolicy.java`
2. `dual-adapter-acpx/src/test/java/dev/dual/adapter/acpx/ClaudeExecutionPolicyTest.java`
3. reviewed fork `src/acp/claude-execution-policy.ts`
4. reviewed fork `src/acp/auth-env.ts`
5. reviewed fork `src/acp/client.ts`（只接线 Claude E1 env finalizer）
6. reviewed fork policy/client/spawn-options tests
7. `tools/acpx-e1/patches/acpx-v0.13.2-e1-policy-transport.patch`
8. `tools/acpx-e1/manifest.json` 与 installer/build-info frozen identity
9. `scripts/acpx-claude-agent-env.sh` 及 shell test（production 逻辑预计不变，测试扩为八 canary）
10. `scripts/configure-acpx.sh`、`dual-cli/src/main/java/dev/dual/cli/DoctorCommand.java`、
    `dual-cli/src/test/java/dev/dual/cli/DoctorCommandTest.java` 的 schema、patch SHA/build identity 期望与对偶测试
11. `dual-adapter-acpx` 的 `AcpxAgentTaskHandler`、`AcpxAgentTransport`、`AgentRunMetadataWriter` 只更新
    schema/identity 断言；不新增状态或路由

具体 Java 测试路径由 symbol sweep 锁定；若 production diff 超出上述 owner，停止并回到边界评审。

### 4.2 明确不改

- workflow JSON、ContractRegistry、AgentResultEnvelope；
- human gate、permission action、scheduler、recovery；
- Codex adapter 与非 Claude permission profiles；
- custom proxy、TLS inspection、domain approval；
- frozen R3 原始 evidence。

## 5. Cannot-Bypass Tests

1. policy v2 exact keys：owner 缺失/错误、旧字段存在、provider deny 缺项/多项/顺序漂移/proxy 回流均拒绝；
2. SDK capture：三个 provider deny 精确映射；无 custom proxy port；domain/filesystem 不退化；
3. process env 八 canary在 E1-bound Claude child 全 absent；
4. session env 八 canary同样 absent，证明 scrub 晚于 merge；
5. process/session 分别注入 `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB=1` 及大小写变体，最终 E1 child 中该键均 absent；
   policy 的 `autoAllowBashIfSandboxed=true` 不再被 ambient env 否决；
6. `CLAUDE_CODE_EXECUTABLE` 保留，provider auth/base URL 仍可到达父 Claude process；
7. 非 E1 Claude 与非 Claude agent 的 proxy/一般 env 行为不变；finalizer 入口必须验证 policy ref/SHA 成对存在，
   半绑定在 child spawn 前拒绝，不能只等到 `session/new`；
8. `isClaudeAcpCommand` 对 production profile 的精确命令形态
   `acpx-claude-agent-env.sh ... npx -y @agentclientprotocol/claude-agent-acp@0.70.0` 返回 true；命令形态漂移不得
   静默跳过 policy/finalizer；
9. wrapper 八 canary全 absent，settings allowlist 保留，settings 未登记键不进入；parent-only 非 proxy canary 可继承，
   用于防止测试把 settings allowlist 误写成 process env allowlist；
10. ensure/prompt/correction/queue/cold-load 继续绑定同一 v2 ref/SHA；v1 session reuse 在 prompt 前失败；
11. Doctor/installer 对旧 patch、旧 schema、错误 adapter pin、缺 build-info fail closed；
12. handler/transport 无效 binding 时 acpx 命令日志不存在，legacy/Codex 不冒充 E1；
13. 新 Gate 的 userinfo canary 不触发 73，fixture 静态扫描对 proxy URL/userinfo 分类逻辑零命中。

反证要求：删除 fork-side finalizer 后测试 3/4 至少一项必须失败；删除 v2 owner 或重新把 proxy 加入 credential deny 时
测试 1/2 必须失败。

## 6. 实施顺序

1. 在 pinned acpx source 上先加 failing spawn/policy tests；
2. 实施 policy v2 loader 与 Claude E1 child env finalizer；
3. 运行 fork focused/full tests，重新生成 exact patch、SHA、build-info 与 manifest；
4. 更新 Dual policy producer、schema identity、Doctor/configure 与 shell tests；
5. focused adapter/doctor/shell tests；
6. full Maven reactor、fork full tests、`git diff --check`；
7. 独立代码级 diff review；
8. clean commit 后另起新的 capability Verification Gate，先评审再申请 provider tool turns。

任一步发现需要 custom proxy、通用 env allowlist、修改 Claude provider auth 语义、改变非 E1 sibling 环境，立即停止。

## 7. End-to-End Invariant

| 维度 | 证明 |
|---|---|
| Declared by | occurrence-scoped `claude_execution_policy.v2` 固定 owner + provider deny + domain/filesystem policy |
| Proven by | policy ref/SHA、fork option capture、child env canary、build identity |
| Consumed by | reviewed acpx fork finalizer + Claude SDK sandbox options |
| Enforced before | E1-bound Claude child spawn；Bash network side effect |
| Failure route | schema/identity/binding/fork mismatch -> `NETWORK_AUTHORITY_NOT_CONFIGURED`，零 provider prompt |
| Cannot bypass | process/session env 双 canary、non-E1 sibling、v1 reuse、negative Gate fixture |
| Residual | Claude internal proxy endpoint/token/topology；一般非网络 process env；真实 network effect 等待新 Gate |

## 8. 成熟度与退出条件

- 当前：proxy owner conflict `VALIDATED DEFECT`；Candidate C `ASSESSED`；
- deterministic checkpoint PASS：`NETWORK TRANSPORT OWNERSHIP ENFORCED / SANDBOX EFFECT NOT PROVEN`；
- 新 capability Gate PASS 后，才可分别提升 domain deny、OS no-direct-connect、WebSearch coexistence 的成熟度；
- `PUBLIC_NETWORK_DOMAIN`、custom proxy、Codex network safety 均不得随本 checkpoint 宣称完成。

policy v2 的 `sandbox.credentials.envVars` 只保护列明的三个 provider 名，且只作用于 sandboxed Bash。
hooks/MCP stdio 与任意其他自定义 secret 仍可能存在于一般 child process env。把全部环境改为 allowlist 是独立安全平台，
当前成本与已观测缺陷不匹配；只有出现自然泄漏证据或组织级 secret-isolation 要求时才重新评估。

退出条件：若 fork-side scrub 不能在不改变非 E1 sibling 的前提下实现，或 Claude runtime 要求 Dual 提供自定义 proxy endpoint，
返回 bounded comparison，不以 wrapper-only workaround 收口。

## Boundary Review

```text
Boundary Review: P0 DELTA PASS / IMPLEMENTED / READY FOR INDEPENDENT DIFF REVIEW
Touched boundary: external adapter spawn / execution policy / sandbox network transport
Source of truth: occurrence-scoped ClaudeExecutionPolicy v2
Production implementation authorized: YES (delta review PASS; implementation complete)
Provider execution authorized: NO
```
