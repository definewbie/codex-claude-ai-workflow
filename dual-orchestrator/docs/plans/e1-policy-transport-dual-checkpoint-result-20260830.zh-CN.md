# E1 Policy Transport — Dual Checkpoint Result

日期：2026-08-30

## 1. 结论

本 checkpoint 把已评审的 acpx fork 接入 Dual execution occurrence。现代 Claude 节点在 provider
side effect 前生成并验证固定 `claude_execution_policy.v1`，session ensure/new、prompt 与 correction
必须携带同一 policy ref/SHA。旧 frozen workflow 的 `approve_all` 不绑定 E1，并在 run metadata 中明确
标记 `NETWORK_AUTHORITY_NOT_CONFIGURED`。

当前成熟度：`E1 POLICY TRANSPORT ENFORCED / SANDBOX EFFECT NOT PROVEN`。本轮没有调用 provider，
不得据此声称 network deny、domain approval、WebSearch coexistence 或 tool env isolation 已闭环。

## 2. Authoritative Facts

- fork upstream commit：`fd173f04aa1b56f9e3f5ca5190c034ddcae28792`；
- fork patch SHA-256：`304577dc136c87b055b5873b0562929cd5c16b1b0d18321e27f851209b3ab811`；
- npm tarball SHA-256：`b483b20ea1db5ffdeb0eb2e3105283ffda0b88ccf37e034d500522aaeef8d12b`；
- adapter：`@agentclientprotocol/claude-agent-acp@0.70.0`；
- occurrence policy：`runs/<run-id>/execution-authority/claude-execution-policy.json`；
- run projection：`run.json.executionAuthority`，只保存 ref/SHA/schema/disposition，不保存 secret value。

## 3. 实现边界

1. `ClaudeExecutionPolicy` 只生成固定字段：sandbox fail-closed、network strict allowlist、workspace/tmp
   filesystem scope、空 setting sources 与固定 subprocess denied-env names。
2. writer 使用原子发布；transport 在每次命令前重验 realpath containment、SHA 与
   workstream/node-instance/run lineage。删除、篡改、symlink 越界或 lineage 漂移均在 acpx 调用前失败。
3. policy 只允许 Claude 消费；Codex 携带 binding 立即拒绝。不同 persistent-session binding 由 fork
   返回 typed conflict，不静默复用。
4. named `permission_profile` 是 E1 activation owner；legacy 无 named profile 不冒充 ENFORCED。
5. installer 双重核对 Git tag/commit 与 npm tarball，应用 reviewed patch 后跑 upstream tests，再注入
   build identity。Doctor 与 `configure-acpx.sh` 对 fork identity fail closed。
6. Claude adapter wrapper 仅从 settings 导出固定 Anthropic provider allowlist，并清除 proxy 变量；不提供
   任意 settings/env passthrough。

## 4. 验证结果

- acpx fork installer：upstream suite `973/0/0/0`，build PASS；日志与 identity 保存在
  `.tools/acpx-e1/audit/`（本地 ignored evidence）。
- Dual focused：policy/transport/handler/Doctor/env tests PASS；包含 deletion/tamper/lineage、non-Claude、
  ensure+prompt+correction 同 binding、legacy `NOT_CONFIGURED` 与 build identity mismatch。
- Dual full reactor：`1294/0/0/0`，9 modules，`BUILD SUCCESS`。
- shell：env allowlist test PASS；installer/configure smoke PASS；`bash -n` PASS。
- `git diff --check`：clean。

首次 sandboxed reactor 的两条进程树测试因 macOS `sysctl` 被环境拒绝而报错；同一 clean reactor 在允许
进程枚举的测试环境中全绿。该环境差异未被计作 E1 PASS 或产品失败。

## 5. End-to-End Invariant

| 维度 | 事实 |
|---|---|
| Declared by | named permission profile + immutable occurrence policy |
| Proven by | fixed writer、realpath/SHA/lineage verifier、pinned fork build identity |
| Consumed by | Dual transport flags → acpx session binding → Claude `session/new._meta` |
| Enforced before | acpx session creation与 prompt process；无效 binding 时 provider side effect 为 0 |
| Failure route | IOException / typed session conflict；legacy 明确 `NETWORK_AUTHORITY_NOT_CONFIGURED` |
| Cannot-bypass test | delete/tamper/path/lineage/non-Claude/approveAll/session conflict/queue reload/build mismatch/env allowlist |
| Residual | sandbox 与网络真实效果未验证；filesystem scratch 当前使用 system tmpdir，须由 proof Q7 判定；official acpx legacy 可运行但不继承成熟度；Codex network 不在本轮范围 |

独立 diff review 通过后保留四项非阻塞输入：official acpx 对 E1 参数的硬失败尚无专门测试；installer
审计工件位于本机 ignored 目录、由 Doctor 复核；`DUAL_ACPX_EXECUTABLE` 是 operator 信任域内的可执行文件
覆盖；system tmpdir 的 filesystem 语义必须在 sandbox capability proof 中实测，不能由 transport 测试外推。

## 6. 下一步

独立 Dual diff review 已 `PASS（P0=0 / P1=0 / P2=4 知会）`。下一步仅离线重放 capability proof
Q1-Q3；Q4-Q15 仍需单独 Verification Gate。不得恢复旧 OKX workstream，也不得把 deterministic
transport PASS 解释为真实网络控制已 PROVEN。
