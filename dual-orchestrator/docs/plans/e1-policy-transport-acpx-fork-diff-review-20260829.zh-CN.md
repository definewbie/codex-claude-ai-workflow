# E1 acpx Policy Transport Fork — 独立代码级 Diff Review

日期：2026-08-30（评审对象日期 2026-08-29）
评审对象：`tools/acpx-e1/manifest.json`、`tools/acpx-e1/patches/acpx-v0.13.2-e1-policy-transport.patch`、
`docs/plans/e1-policy-transport-acpx-fork-checkpoint-result-20260829.zh-CN.md`、
`docs/plans/e1-policy-transport-checkpoint-implementation-plan-20260829.zh-CN.md`。
评审方式：只读。在 pinned upstream commit 的独立 clean worktree 上验证 patch；未修改任何代码、未调用 provider、
未恢复 real-case、未提交。

## 0. 独立核实结果

| 检查项 | 方法 | 结论 |
|---|---|---|
| patch 可应用性 | `git worktree add fd173f04` + `git apply --check` | ✅ CLEAN；实际 apply 亦成功 |
| patch SHA | `shasum -a 256` vs manifest `a65c050e…` | ✅ 逐字节一致 |
| upstream commit | `git ls-remote`（tag peel）+ clone HEAD 复核 | ✅ `fd173f04…`（注意：见 P1-1 的仓库 URL 问题） |
| 文件面 | apply --stat 逐文件 vs 实施计划 §6 | ✅ 一致，且 fork 使用了正确路径 `src/session/persistence/parse.ts`（计划笔误在实现中已纠正）；22 files，+899/−16 |
| policy 输入边界 | 通读 `src/acp/claude-execution-policy.ts`（359 行全量） | ✅ 见下 Boundary Review |
| `_meta` 消费链 | `agent-command.ts`/`client.ts` diff + 真实 client 测试 | ✅ 见下 |
| session ref/SHA 一致性 | `manager.ts`/`session-options.ts`/`parse.ts`/`conversation-model.ts`/queue 两文件 diff | ✅ 见下 |
| build identity | `build-info-command.ts` + `e1-build-info.generated.ts` | ✅ 占位 `UNBUILT` + installer 后续替换，诚实 |
| 测试可信度 | 静态核验 5 个测试文件断言 | ✅ 真实断言；⚠ 运行计数未能独立复跑（见 P2-3） |
| Node 基线声明 | upstream `package.json` engines | ✅ `>=22.13.0`；结果文档对 22.11.0 失败的记录与处置诚实 |

## 1. Findings

### P0

无。

### P1

**P1-1 manifest `upstream_repository` 指向不可访问的仓库。** manifest 记录
`https://github.com/openai/acpx.git`——实测该 URL 返回鉴权错误（私有或不存在，不可匿名 clone）；而 npm
`acpx@0.13.2` package.json 的权威 repository 字段为 `https://github.com/openclaw/acpx`（本评审两轮均从 openclaw
成功 clone 并核对 tag/commit SHA）。下一阶段 installer 的第一步就是按 manifest clone 并校验 commit——按现值必然
失败或要求凭据。**修法（一行）**：`upstream_repository` 改为 `https://github.com/openclaw/acpx.git`。commit/tag
SHA 本身经 openclaw 仓库验证无误，仅 URL 字段错误。

### P2

**P2-1 manifest 缺第二来源 SHA。** 实施计划 §3.1 冻结了 npm tarball SHA `b483b20e…` 与 annotated tag object
`a5db336c…`，manifest 只记 tag 名与 commit。installer 应能仅凭 manifest 完成双来源校验（git commit + npm
tarball），建议把两个 SHA 一并写入，避免从计划文档间接引用。

**P2-2 queue 两个入口对畸形 binding 的行为不一致。** `queue/messages.ts` 的 `assignSessionPolicyBinding` 对畸形
binding 返回 false（整条消息拒绝）；`queue/queue-owner-env.ts` 的同名逻辑对畸形值**静默跳过**。下游
`manager.assertCompatibleClaudeExecutionPolicy` 会对"单侧缺失 binding"抛 `CLAUDE_EXECUTION_POLICY_CONFLICT` 兜底，
故整体仍 fail-closed；但静默跳过与"零静默降级"的纪律读法相悖，建议统一为拒绝。

**P2-3 测试运行计数未独立复现。** 本评审尝试在 fork worktree 安装依赖以复跑 focused/full 套件，被本环境权限策略
拒绝（外部仓库 `npm ci` 执行第三方 lifecycle scripts，未经授权）——不绕过。测试可信度结论基于静态核验：断言真实
（真实 `AcpClient.createSession` 捕获 `_meta`、fixture 使用与 Dual 计划一致的
`runs/run-1/execution-authority/policy.json` 路径形状、conflict 测试断言 `clientFactory must not run after a policy
conflict` 即 side-effect 前拒绝、policy 测试覆盖 duplicate/SHA drift/unknown field/path escape/weakened
network/escape/credential 全族）。实施方声明的 `147/0/0/0`（focused）与 `972/0/0/0`（full，Node 24.19.0）**未经本
评审复现**；下一阶段 installer 必须把测试运行输出落盘为可审计工件，Dual 侧 diff review 时复核。

## 2. Boundary Review

| 维度 | 判定与证据 |
|---|---|
| **policy 输入边界（无通用 passthrough）** | ✅ 极强。`claude-execution-policy.ts` 是**全固定值断言**而非类型校验：`setting_sources` 必须为空数组；sandbox 四布尔必须分别等于 true/true/false/true；`strict_allowlist` 必须 true、`allow_local_binding` 必须 false、`allow_unix_sockets` 必须空；`subprocess_denied_env_names` 必须逐字等于固定 11 元素集合（大小写代理变量 + ANTHROPIC 三键）；filesystem allow 列表禁止为空、路径必须 canonical 绝对路径；domain 必须小写 canonical 模式。exact-keys 全层强制、手写 duplicate-key 扫描器（结构感知、转义安全）、SHA 先于 parse、secret-like 值递归拒绝、ref 必须 cwd 内相对路径且 realpath 双检。**唯一自由变量是 allowed_domains 与 filesystem 路径列表**；credential deny 由 fork 从固定集合构造而非输入可控。无任何任意 settings/`_meta`/env 通道 |
| **`session/new._meta` 消费链** | ✅ `client.ts` 把 cwd 传入 `buildClaudeCodeOptionsMeta` → `assignClaudeExecutionPolicyOptions`：binding 缺失即返回（legacy 路径不变）；**非 Claude ACP 携带 binding 即 throw**；半绑定（有 ref 无 SHA 或反之）即 throw；`Object.assign(target, {settingSources: [], sandbox, managedSettings: {sandbox}})`——policy 不写 `options.env`（结果文档 §3.4 声明属实）。真实 `AcpClient` 测试捕获 `_meta` 断言全部字段 |
| **persistent session ref/SHA 一致性** | ✅ `manager.ts` 在复用分支前 `assertCompatibleClaudeExecutionPolicy`：binding key（ref+SHA）不同即 `CLAUDE_EXECUTION_POLICY_CONFLICT`——含"单侧缺失"（旧无策略 session 不能被带策略复用，反之亦然），无静默复用。测试断言冲突先于 `clientFactory` 执行。queue 消息、queue owner env、conversation clone、冷加载 parse 四条持久化路径全部保真 binding（畸形值处理差异见 P2-2） |
| **无第二 policy owner** | ✅ session record 仅存 ref/SHA（transport binding）；policy 文件是唯一内容 owner；`build-info` 只暴露 upstream commit/patch SHA/policy schema 三元组，不含策略内容 |
| **无静默 fallback** | ✅ 全链 throw-first：CLI flag 成对校验、SHA 格式前置、非 Claude/半绑定/畸形/越界/弱化全拒绝；唯一"跳过"点见 P2-2（且有下游冲突兜底） |
| **成熟度声明** | ✅ `ACPX POLICY TRANSPORT VALIDATED / DUAL INTEGRATION NOT IMPLEMENTED / SANDBOX EFFECT NOT PROVEN` 精确——fork 只证明 transport（`_meta` 到达），未冒充 sandbox 生效、未冒充 Dual 集成；§6 明确禁止标 `E1 POLICY TRANSPORT ENFORCED`；Node 基线处置（22.11.0 低于 upstream engines `>=22.13.0`，改用 24.19.0）记录诚实 |
| **最小性** | ✅ 22 files 中 4 个新 production 文件（policy 模块/build-info/generated/无）、5 个测试文件；对 upstream 既有逻辑仅做 binding 透传与一次 `sessionOptionsFromGlobalFlags` 去重合并（prompt/exec 两处重复构造统一，净简化）；未触碰 permission classifier/streaming/cancel/retry/flow engine——符合实施计划 §6 禁改清单 |

## 3. 下一阶段判定

**允许进入实施计划 §9.3（Dual fake tests → installer → policy writer → transport binding），附一个前置条件：**

- **前置（阻塞 installer）**：修复 P1-1——manifest `upstream_repository` 改为 `https://github.com/openclaw/acpx.git`。这是一行修正，但不修则 installer 第一步（clone + commit 校验）无法执行。
- 建议随行落 P2-1（manifest 补 tarball/tag object SHA）与 P2-2（owner-env 畸形 binding 改拒绝）；P2-3 转化为 installer 工件要求（测试输出落盘）。
- 其余结论：fork 的 policy 输入边界、`_meta` 消费链、session binding 一致性与成熟度声明均经独立代码验证成立，无通用 passthrough、无静默 fallback、无第二 owner、无成熟度夸大。Dual fake tests / policy writer / transport binding 可立即开工（它们不依赖 installer 的下载步骤，仅最终联调需要）。

## 4. 复核锚点

- upstream：`fd173f04aa1b56f9e3f5ca5190c034ddcae28792`（openclaw/acpx v0.13.2，tag object `a5db336c…`）
- patch：`a65c050e7504d11f909920e8730ecdadc5e2f57431eeef9157dc2b7fd8f89cf3`（1309 行，`git apply --check` CLEAN）
- 关键文件：`src/acp/claude-execution-policy.ts`（:7-48 固定集合；:63-96 装载与断言；:98-157 sandbox/network/filesystem 映射；:159-167 deny 集合等值校验；:169-183 路径围栏；:265-359 duplicate-key 扫描器）、`src/acp/agent-command.ts`（新增 `assignClaudeExecutionPolicyOptions`）、`src/runtime/engine/manager.ts`（`assertCompatibleClaudeExecutionPolicy`）、`src/cli/build-info-command.ts`
- 本次评审未修改代码、未调用 provider、未恢复 real-case、未提交；外部仓库依赖安装被权限策略拒绝后未绕过（P2-3 如实披露）。
