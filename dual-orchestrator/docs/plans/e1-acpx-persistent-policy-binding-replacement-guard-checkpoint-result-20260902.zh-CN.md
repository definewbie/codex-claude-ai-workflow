# E1 acpx Persistent Policy Binding Replacement Guard Checkpoint Result

日期：2026-09-02  
基线：`3831b6d4`  
状态：`INDEPENDENT DIFF REVIEW PASS / READY TO COMMIT`

## 1. 结论

本 checkpoint 关闭了 reviewed acpx fork 的 persistent session policy binding replacement 绕过。已有同名 record
时，binding ref/SHA 现在先于 reuse 判断、prompt 记录、client 创建、queue lease、resume/load/new 与 record 覆盖做全等比较；
不一致或单侧缺失均以 `CLAUDE_EXECUTION_POLICY_CONFLICT` fail closed。

binding 相同且 backend session 丢失时仍允许 fresh `session/new`，但 transition owner 会完整替换 record 的
`session_options`，调用方随后从更新后的 record 同步 checkpoint，旧 options 不再回写。

## 2. 实现边界

fork patch 内的 production delta：

1. `runtime/engine/session-options.ts` 提供唯一 compatibility guard；manager、direct prompt 与 queue owner 不再各自解释 binding。
2. manager 在 `shouldReuseExistingRecord()` 与任何 replacement 副作用之前调用 guard，覆盖 cwd/argv/resume/reset 导致的 reuse=false。
3. direct prompt 在 prompt submission 与 record write 前调用 guard；`sendSessionDirect()` 真实转发 requested options。
4. queue owner 在获取 lease 前验证 owner-level binding，每个 queued task 仍由 direct prompt 再验。
5. reconnect 只在 load/resume 确认 fallback 到 fresh session 后完整持久化 effective options；normal reuse/resume 不改 record。

Dual 主仓库仅机械同步 reviewed patch、manifest、configure 与 Doctor identity；未修改 workflow、ledger、permission、WebSearch、
policy schema、provider 或 real-case。

将旧 reviewed patch 与最终 patch 分别应用到同一 upstream 后做全树比较，差异恰为上述 5 个 production 文件与
4 个 test 文件，无其他 fork 文件变化。

## 3. Cannot-Bypass Evidence

新增五个行为测试，fork 总数由 `980` 增至 `985`：

- manager `reset_on_next_ensure` 下 A→B 在 clientFactory/store write 前拒绝；
- persisted A 与 requested unbound、persisted unbound 与 requested A 均拒绝；
- direct unbound request 对 bound record 零字节修改；
- queue owner A→B 在 queue lease 创建前拒绝；
- direct same-binding 在 resource-not-found 后 fresh fallback，并将完整 replacement options checkpoint 到磁盘，旧 tool list 消失。

既有 same-binding reuse、one-shot、non-Claude/unbound 与 queue lifecycle sibling tests 保持通过。

## 4. Identity 与验证

- reviewed upstream：`fd173f04aa1b56f9e3f5ca5190c034ddcae28792`
- reviewed patch SHA：`81acbb7491a17aedfb48b1a73f077b59e06f1d5eceb7cd617e59df86aa5c60ab`
- policy schema：`claude_execution_policy.v2`
- fork full suite（Node `24.19.0`）：`985/0/0`
- Dual reactor：`1294/0/0/0`
- fresh package：PASS
- clean upstream apply、reverse-apply 与 `git diff --check`：PASS
- installer：PASS；installed `build-info` 与 manifest/patch 三方一致
- `configure-acpx.sh`：PASS；用户配置仍锁定 Claude ACP `0.70.0` 与 Codex ACP `0.16.0`
- fresh CLI Doctor：`Result: OK`，实际读取新 patch SHA
- 提交前 fork full suite（Node `22.21.0`，正常本地 socket 权限）：`985/0/0`

一次使用 bundled `pnpm 11` 的 installer 尝试因其新增 ignored-builds supply-chain policy fail closed，未替换旧 binary；同一 lockfile
在历史兼容的 `pnpm 10.34.5` + Node `24.19.0` 下安装、测试和原子替换成功。Node `22.11.0` 低于 fork 声明的
`>=22.13.0`，其旧 queue stderr timing test 失败不计入支持基线；Node 24 下该测试及全量 suite 均通过。
最终 whitespace-clean patch 首次安装还遇到一次 GitHub TLS 临时失败，重试同一 installer 后通过；失败发生在 clone 阶段，
没有替换当时已安装的 binary。

独立 diff review 在一次高负载 `npm test` 中观察到 `984/1`，随后两次直接套件均为 `985/0`；
本轮提交前又在受支持的 Node `22.21.0` 与正常 Unix socket 权限下完整复跑，得到 `985/0/0`。
因失败不可稳定复现且不属于本 delta，本 checkpoint 不修改 upstream timing 语义。验证纪律为：
提交必须存在一次完整 clean pass；若同一失败可连续复现，则阻断提交并单独治理，不允许通过无限重跑挑选绿色结果。

## 5. Evidence Erratum

captured allowlist differential 的原始文件未改写。新增 `ERRATUM.md` 明确：有效 occurrence 的 launcher、policy 文件与
`session/new._meta` 使用 `ce796a1b...`，归档 record 中 `ea47a3ed...` 是较早 invocation 残留；该样本可支持 bounded
allow/deny effect，但其 record durability 结论为 `INVALID`。目录排序 `SHA256SUMS` 已包含 ERRATUM 且可复算。

## 6. End-to-End Invariant

| 维度 | 结果 |
|---|---|
| Declared by | persistent session key + persisted/requested policy ref/SHA |
| Proven by | record/request guard、fresh `session/new` options 与最终磁盘 checkpoint |
| Consumed by | manager、direct prompt、queue owner 共用 comparator |
| Enforced before | prompt/record/client/lease/resume/load/new 副作用 |
| Failure route | `CLAUDE_EXECUTION_POLICY_CONFLICT`，零 ACP 与 record 副作用 |
| Cannot bypass | replacement、单侧缺失、queue、fresh fallback 五条新增行为测试 |
| Residual | 合法 policy 变更必须关闭旧 named session 并使用新 identity |

## 7. 成熟度与下一步

- persistent policy binding replacement guard：`ENFORCED`（deterministic）
- sandbox allow/deny differential effect：bounded evidence 保持有效
- captured record durability：`INVALID`（已勘误）
- E1 完整 closure、WebSearch provider-side visibility/cannot-bypass：`NOT PROVEN`

独立代码级 diff review 结论为 `PASS / READY TO COMMIT（P0=0 / P1=0 / P2=4）`，四条绕过路径均确认在副作用前关闭。
三项未随本轮扩张的测试观察为：`cwd/argv/resume-id` 参数化触发矩阵、resume 不清空
`session_options` 的显式断言，以及 Doctor 常量与 manifest 的机器交叉断言。它们不影响已证明的唯一 guard，
且现在加入会改变已评审 patch identity，因此仅作为下次触及对应文件时的 sibling test 要求，不独立建设 checkpoint。

下一步是选择性提交本 checkpoint；clean commit 后只允许起草新版 E1 closure Verification Gate，要求差分判据、
host-path 双域前后对照与 EPERM 矩阵。仍不授权 OKX resume、real-case、`PUBLIC_NETWORK_DOMAIN`、custom proxy、
第二样本或 WebSearch visibility 实施。
