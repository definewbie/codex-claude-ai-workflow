# E1 Policy Transport Checkpoint Implementation Plan

## 1. 目标与非目标

目标只有一个：让 Dual 生成的、SHA-bound 的固定 E1 execution policy 经 `acpx session/new._meta` 到达当前 Claude session，并在 session reuse、缺失 policy、env 混合时 fail closed。

本 checkpoint 不实现 domain approval、人机 gate、网络 permission routing，也不调用 provider。完成后只重新验证 E1 capability proof Q1-Q3；Q4-Q15、OKX recovery 和真实网络 enforcement 仍未授权。

禁止：任意 Claude options/`_meta` passthrough、通用 settings API、provider-neutral adapter framework、Bash parser、target `.claude` 写入、宿主 managed-settings 写入、workflow/schema/UI/CLI gate 扩张。

## 2. Boundary Map

| 边界 | Owner | 本 checkpoint 行为 |
|---|---|---|
| Execution policy definition | Dual `dual-adapter-acpx` | 生成固定 schema、ref、SHA；不含 secret |
| Policy transport | pinned acpx source patch | 校验 ref/SHA，映射为受限 `_meta.claudeCode.options` |
| Sandbox enforcement | Claude Code | 本轮不冒充已证明；仅验证 options 已到达 |
| Provider route env | `acpx-claude-agent-env.sh` | 只读取明确 allowlist，不再 export settings.env 全集 |
| Session lifetime | acpx persistent session record | 保存 policy ref/SHA；不同 SHA 不得复用 |
| Execution evidence | `run.json` | additive 保存 policy ref/SHA 与 transport disposition |

主链：

```text
AcpxAgentTaskHandler
  -> write E1 policy under runs/<run-id>/
  -> AcpxRunRequest(policy ref + SHA)
  -> AcpxAgentTransport passes two fixed flags to sessions new/ensure and prompt
  -> patched acpx validates schema/SHA
  -> session/new._meta.claudeCode.options
  -> claude-agent-acp consumes options
  -> run.json records the same ref/SHA and transport result
```

## 3. Frozen Dependency Baseline

### 3.1 acpx source patch

- upstream tag：`v0.13.2`；
- annotated tag object：`a5db336c1f886fb722b6ba5a4a9898181fe1a92a`；
- source commit：`fd173f04aa1b56f9e3f5ca5190c034ddcae28792`；
- npm tarball SHA-256：`b483b20ea1db5ffdeb0eb2e3105283ffda0b88ccf37e034d500522aaeef8d12b`。

不 vendoring 整个 acpx。Dual 保存 exact source patch、build manifest 与安装脚本；脚本下载上述固定 source、校验 SHA/commit、应用 patch、执行 acpx tests/build，再安装到 ignored `.tools/acpx-e1/`。不得改写用户全局 `acpx`。

patched binary 必须提供固定 build identity（例如 `acpx build-info --json`），至少返回 upstream commit、patch SHA、policy schema version。Dual doctor 对三项逐字验证，不接受仅版本号或 PATH 猜测。

### 3.2 Claude adapter baseline

当前 `claude-agent-acp@0.46.0` 固定 Claude Code `2.1.178`，早于 credential env deny 与 strict allowlist 所需版本，不能作为 E1 baseline。

本 checkpoint 候选冻结：

- `claude-agent-acp@0.70.0` npm tarball SHA-256：`da2cf1b5f66981578313126a49002c4ae7e0c7e71d92b545f2b834835c6db465`；
- `@anthropic-ai/claude-agent-sdk@0.3.232` tarball SHA-256：`64ced4c7ad6225575d244ab38b225a031c5fb687fe51d81f86a580e3233472a8`；
- bundled Claude Code：`2.1.232`。

升级仅为 E1 capability baseline。adapter 0.46 -> 0.70 的 protocol/session/permission/output compatibility 必须先由现有 deterministic adapter suite 与 minimal no-provider session handshake 验证；出现输出 grammar、permission evidence 或 persistent-session 语义漂移即停止，不顺手兼容。

## 4. Fixed Policy Contract

新增 adapter-local `claude_execution_policy.v1`，字段只允许：

- `schema_version`；
- `workstream_id`、`node_instance_id`、`run_id`；
- `setting_sources`，E1 模式固定为空；
- sandbox booleans：enabled、fail-if-unavailable、allow-unsandboxed、auto-allow-sandboxed-bash；
- network：strict allowlist、allowed domain 列表、local binding、Unix socket 策略；
- filesystem：worktree 与 run-owned scratch 的固定语义；
- `subprocess_denied_env_names`，只存变量名；
- generated-at。

禁止任意 `settings`、`managedSettings`、extra args、hooks、tools、MCP、model、system prompt 或 env value。acpx client 根据该固定 contract 构造 SDK options；未知字段、重复 key、trailing token、非 canonical domain/path、secret-like value 一律拒绝。

policy 文件由 Dual 原子写入 `runs/<run-id>/execution-authority/claude-execution-policy.json`，SHA 在写入后计算。文件是 execution fact；acpx session record 中的 ref/SHA 只是 transport binding，不是第二 policy owner。

## 5. Env Separation

`scripts/acpx-claude-agent-env.sh` 改为明确 allowlist，只可从用户 settings 读取以下当前已支持的 provider route / model-selection 键：

- `ANTHROPIC_BASE_URL`；
- `ANTHROPIC_AUTH_TOKEN`；
- `ANTHROPIC_API_KEY`；
- `ANTHROPIC_MODEL`；
- `ANTHROPIC_DEFAULT_SONNET_MODEL`；
- `ANTHROPIC_DEFAULT_OPUS_MODEL`；
- `ANTHROPIC_DEFAULT_HAIKU_MODEL`；
- `CLAUDE_CODE_MODEL`。

不得 export 任意其他 `settings.env` 项；尤其不得从 settings 注入 `HTTP_PROXY`、`HTTPS_PROXY`、`ALL_PROXY`、`NO_PROXY`、任意 `DUAL_*` 或未登记键。secret 值不写 policy、manifest、命令行、stdout/stderr 或 ledger。

provider 进程仍可持有连接所需 token/base URL/proxy。固定 policy 必须把 provider secret 与 `HTTP_PROXY`、`HTTPS_PROXY`、`ALL_PROXY`、`NO_PROXY` 的变量名写入 managed credential deny，使 sandboxed Bash 看不到它们。`claude-agent-acp@0.70.0` 仍会把 `process.env` 合并进 adapter 进程环境，因此 client 不能靠 `options.env` 删除 adapter 已继承的变量；真正的 Bash 隔离 owner 是 `sandbox.credentials.envVars` 的 deny 规则。

deterministic checkpoint 只能证明以下四项：wrapper 仅导出上述 allowlist；policy contract 含完整 denied env name 集合；`session/new._meta` 携带对应 `sandbox.credentials.envVars` deny；client 构造的 `options.env` payload 不含 proxy/secret 变量名。实际 adapter 进程仍继承 provider 连接所需环境，实际 sandboxed Bash 子进程看不到这些变量必须留给 E1 proof Q6 的 provider Verification Gate，不得提前标 `ENFORCED`。

## 6. acpx Source Patch Scope

预计只修改以下 upstream owner/sibling；sweep 后新增文件必须在 diff review 中解释：

- `src/types.ts`：fixed policy ref/SHA session option 与 persisted shape；
- `src/runtime/engine/session-options.ts`：merge/persist/load fixed binding；
- `src/acp/agent-command.ts`：读取并验证 policy，构造受限 Claude `_meta`；
- `src/cli/flags.ts`、`src/cli/command-handlers.ts`：两个固定 flag，不接受 inline JSON；
- `src/cli/queue/messages.ts`、`src/cli/queue/owner-env.ts`：queue owner 保真传递 ref/SHA；
- `src/session/persistence/parse.ts`、`src/session/conversation-model.ts`：冷加载保持 binding；
- `src/runtime/engine/manager.ts`：persistent session 同 ref/SHA 才可复用；不同 SHA typed conflict；
- build-info command/version 文件：暴露 upstream commit、patch SHA、policy schema；
- 对应 `client/cli-flags/queue/persistence/manager` tests。

不得修改 permission classifier、terminal manager、streaming、cancel、retry、flow engine 或通用 ACP message parser。

## 7. Dual Scope

Production：

1. `dual-adapter-acpx`
   - 新增固定 `ClaudeExecutionPolicy` 与 writer/verifier；
   - `AcpxRunRequest` 增加 policy ref/SHA，Codex 必须为空；
   - `AcpxAgentTaskHandler` 在 prepared run 写 policy；
   - `AcpxAgentTransport` 对 Claude 的 ensure/new/prompt 传 ref/SHA，correction 复用同一 SHA；
   - `AgentRunDocument` / `AgentRunMetadataWriter` additive 保存 ref/SHA/transport disposition；
   - `AcpxPermissionProfile` 本轮不改变 domain approval，仅拒绝 E1-enabled `approveAll`。
2. scripts/configuration
   - `acpx-claude-agent-env.sh` 收窄 provider env allowlist；
   - `configure-acpx.sh` 固定 adapter 0.70.0，并验证 local patched acpx build identity；
   - 新增 exact patch manifest 与 local installer；
   - quickstart/reference/troubleshooting 只更新安装与成熟度，不宣传网络已闭环。
3. `dual-cli`
   - `DoctorCommand` 校验 patched build identity、adapter package pin 与 policy capability；
   - 默认仍允许官方 acpx 用于未配置 E1 的 legacy/frozen workstream，但必须报告 `NETWORK_AUTHORITY_NOT_CONFIGURED`，不得标 ENFORCED。

零修改：workflow JSON、ledger human actions、server/UI、AgentResultEnvelope、TaskContract、delivery/completion、Codex adapter。

## 8. Cannot-Bypass Tests

### 8.1 acpx fork

1. fake ACP server 收到的 `session/new._meta` 与 fixed policy 精确对应；
2. unknown/duplicate/trailing/错误 SHA/缺文件/越界 ref 在 `session/new` 前失败；
3. inline JSON、任意 options、`_meta`、settings passthrough 无入口；
4. persistent session 同 SHA 可复用，不同 SHA 必须 conflict，不能静默忽略 options；
5. queue owner/cold reload 后 ref/SHA 不丢；
6. policy 不进入 Codex/其他 agent；
7. build-info 与 manifest 精确匹配；
8. 删除 sandbox、escape-disabled、strict-allowlist、credential-deny 任一字段，测试必须失败。

### 8.2 Dual

1. 四个 Claude permission profile 和 correction 都绑定 policy；Codex 无 policy；
2. policy lineage 与当前 workstream/node/run 不符即 provider side effect 前拒绝；
3. E1-enabled `approveAll=true` fail closed；legacy 显式 NOT_CONFIGURED；
4. ensure/new/prompt/correction 命令携带同一 ref/SHA；不同 SHA 强制 close/rebuild 或 typed conflict；
5. wrapper 只导出 provider allowlist，任意用户 env 不进入 adapter；
6. policy/command/run.json 不含 secret value，显式 SDK env 零 proxy/secret，managed deny 含变量名；
7. fake acpx 的 command sequence 保持既有 timeout/cancel/closure 行为；
8. Doctor 对官方 acpx、错误 fork SHA、错误 adapter pin、缺 build-info 全部 fail closed；
9. 删除 policy 文件后即使用户/project settings 完整也必须失败；
10. 全部既有 adapter/session/permission tests 不退化。

## 9. 实施顺序与 Stop-Loss

1. 先在临时 acpx source clone 实现最小 patch，运行 upstream focused/full tests，生成 patch diff 与 manifest；
2. 独立 review fork diff；
3. Dual 先加 failing fake-acpx/doctor/env tests；
4. 实施 policy writer/request/transport/metadata 与 scripts；
5. focused tests、full reactor、shell tests、`git diff --check`；
6. 独立 Dual diff review；
7. 仅重跑 capability proof Q1-Q3。Q1-Q3 PASS 后再为 Q4-Q15 制定 provider Verification Gate。

任一情况立即停止：fork 修改超出 §6、需兼容 adapter 0.70 的非 E1 行为漂移、需要通用 settings/_meta API、真实 tool env 只能靠 deterministic fake 冒充、需要改 workflow/gate/UI、provider env 无法与 Bash visibility 分开。

## 10. Maturity

- 当前：`TRANSPORT DEFECT VALIDATED / NOT ENFORCED`；
- 本 checkpoint deterministic PASS：`E1 POLICY TRANSPORT ENFORCED / SANDBOX EFFECT NOT PROVEN`；
- Q1-Q3 capability replay PASS：`POLICY INJECTION VALIDATED`；
- Q4-Q15 provider Gate PASS 前：network deny、domain approval、WebSearch coexistence、tool env isolation 均不得称为闭环。
