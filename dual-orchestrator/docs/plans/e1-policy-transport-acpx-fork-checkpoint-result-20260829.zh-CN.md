# E1 Policy Transport — acpx Fork Checkpoint Result

日期：2026-08-29

## 1. 结论

本 checkpoint 只完成实施计划 §9.1 的 fork 边界：在 `acpx@v0.13.2` 固定源码上实现受限的
Claude execution-policy transport，并冻结为可复核 patch。尚未修改 Dual Java、shell wrapper、workflow、
provider 配置或 real-case。

当前成熟度：`ACPX POLICY TRANSPORT VALIDATED / DUAL INTEGRATION NOT IMPLEMENTED /
SANDBOX EFFECT NOT PROVEN`。

## 2. 冻结工件

- upstream tag：`v0.13.2`
- upstream commit：`fd173f04aa1b56f9e3f5ca5190c034ddcae28792`
- patch：`dual-orchestrator/tools/acpx-e1/patches/acpx-v0.13.2-e1-policy-transport.patch`
- patch SHA-256：`304577dc136c87b055b5873b0562929cd5c16b1b0d18321e27f851209b3ab811`
- manifest：`dual-orchestrator/tools/acpx-e1/manifest.json`
- policy schema：`claude_execution_policy.v1`
- adapter baseline：`@agentclientprotocol/claude-agent-acp@0.70.0`

patch 已在同一 upstream commit 的独立 clean worktree 上通过 `git apply --check`。

## 3. 实现边界

1. 新增两个固定 CLI 参数，只接受 policy ref 与 SHA-256，不提供任意 settings、`_meta` 或 env passthrough。
2. policy ref 必须位于 session cwd 内；文件 SHA、严格字段集、重复键、lineage 标识、sandbox/network/filesystem
   固定语义与 credential deny 集均 fail closed。
3. 只有 built-in Claude ACP 可以消费该 policy；其他 agent 携带 binding 时拒绝。
4. `session/new._meta.claudeCode.options` 同时投影 direct sandbox 与 managed sandbox，`settingSources=[]`；
   policy 不写入 `options.env`。
5. persistent session 保存 ref/SHA；warm owner、queue/cold owner 与 prompt/exec 复用同一 binding；不同 binding
   返回 `CLAUDE_EXECUTION_POLICY_CONFLICT`，禁止静默复用旧 session。
6. `acpx build-info --json` 仅暴露 upstream commit、patch SHA 与 policy schema。源码保留 build-time placeholder，
   后续 installer 必须在核验 patch SHA 后生成真实 identity；本 checkpoint 未实现 installer。

## 4. 验证结果

- TypeScript test compile：PASS。
- build：PASS。
- format check：PASS。
- lint（含 persisted-key casing 与 flow-schema terms）：PASS。
- focused：`147/0/0/0`，覆盖 policy parser/mapping、真实 `AcpClient.createSession()` `_meta`、build-info、
  queue/cold-owner persistence 与 session binding conflict。
- full upstream suite：使用满足项目 `>=22.13` 要求的 Node `24.19.0`，串行运行 `972/0/0/0`，exit 0。
- fork `git diff --check`：clean。

验证期间使用系统 Node `22.11.0` 时出现 queue-owner stderr 截断测试失败；同一测试在 Node `24.19.0`
立即 PASS，且相关 production/test 文件不在 diff 中。因此正式基线采用满足 upstream engine 约束的 Node 24，
不修改无关 queue 代码。

## 5. End-to-End Invariant

| 维度 | 本 fork checkpoint 的事实 |
|---|---|
| Declared by | 固定 `claude_execution_policy.v1` 文件与 CLI ref/SHA pair |
| Proven by | cwd containment、SHA、严格 parser、固定 sandbox 映射与 build manifest |
| Consumed by | `AcpClient.createSession()` 的 Claude `session/new._meta` |
| Enforced before | ACP session 创建；binding 缺失、篡改、越界或 session 冲突时拒绝 |
| Failure route | 参数错误或 `CLAUDE_EXECUTION_POLICY_CONFLICT`，不回退 user/project settings |
| Cannot-bypass test | duplicate/unknown/SHA/path/weak sandbox/credential deny、non-Claude、queue persistence、warm-session conflict 与真实 `_meta` 测试 |
| Residual | Dual 尚未生成/绑定 policy，wrapper env 尚未收窄，真实 Claude sandbox/network 效果尚未验证 |

## 6. 下一 Gate

必须先独立 review 本 fork patch。只有 review `PASS` 后，才允许进入实施计划 §9.3：在 Dual 中先加入
failing fake-acpx、Doctor 与 env-boundary tests，再实现 installer、policy writer 和 transport binding。

fork review 前后均不得调用 provider、恢复 OKX workstream、创建第二个 real-case，或把本结果标为
`E1 POLICY TRANSPORT ENFORCED`。

## 7. Fork Review Delta

独立 fork review 的 `P1-1` 已关闭：manifest repository 修正为公开权威的
`https://github.com/openclaw/acpx.git`。同时吸收两个 P2：manifest 新增 annotated tag object 与 npm tarball
SHA-256；queue owner 对单侧缺失或畸形 binding 改为立即拒绝，不再静默跳过。delta focused tests
`89/0/0/0`，fork `git diff --check` clean。上述 delta 使 patch SHA 更新为本文件 §2 所列值；原 review 报告中的
`a65c050e...` 保留为历史评审对象，不再是当前 manifest authority。

installer 必须把 upstream build/test 输出及其 SHA 落入 Dual-owned 审计目录，供后续 diff review 复核；不得只在
控制台声明 PASS。
