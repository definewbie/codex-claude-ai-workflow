# E1 acpx Persistent Policy Binding Replacement Guard 实施计划

日期：2026-09-02  
状态：`IMPLEMENTED / INDEPENDENT DIFF REVIEW REQUIRED`  
基线：`3831b6d4`

独立架构评审：`PASS / IMPLEMENTATION READY（P0=0 / P1=0 / P2=4）`。四项 P2 已随实现吸收：
`reset_on_next_ensure` replacement、direct unbound request、fresh fallback 后磁盘 checkpoint，以及 ERRATUM
排序 manifest 均有明确测试或可复算工件。

## 1. 唯一目标

关闭 reviewed acpx fork 中 persistent session 的 policy binding 绕过：已有同名 record 的 Claude execution policy
与新请求不一致时，当前校验只发生在“可直接复用旧 record”分支；若 cwd、agent argv、resume id 或 reset 状态使
`shouldReuseExistingRecord(...)` 返回 false，manager 会绕过校验，创建新的 ACP session，并以新 options 覆盖同名 record。

本 checkpoint 只修 session policy binding 的生命周期一致性。它不修改 Dual workflow、ledger、permission、WebSearch、
sandbox domain policy、provider 配置或 real-case。

主线归属：E1 `P0` correctness residual。修复的不是某个 allowlist 样本，而是所有带
`claudeExecutionPolicyRef/Sha256` 的 persistent acpx session 的同名替换边界。

## 2. 已确认事实

1. `AcpRuntimeManager.ensureSessionWithOwnership()` 先加载同名 record，再调用 `canReuseRuntimeSession()`；policy binding
   校验位于 `shouldReuseExistingRecord()` 之后。
2. `shouldReuseExistingRecord()` 可因 reset、cwd、agent command/argv 或显式 resume id 不同返回 false；这些是 runtime
   reuse 条件，不是 policy authority。
3. 另一个真实入口 `cli/session/runtime.ts::runSessionPrompt()` 会先把 invocation/queue 的 options 作为 preferred，覆盖
   record options 后创建 client；随后 `connectAndLoadSession()` 在 resume/load 返回 resource-not-found 时 fallback 到
   `session/new`。该路径没有 binding guard，也没有在 fresh fallback 后把 effective options 写回 record。
4. `cli/session/queue-owner-runtime.ts` 的 shared-client 创建路径也执行同样的 preferred-over-record merge；因此只移动
   manager 中的一行不能关闭 sibling bypass。
5. manager reuse=false 后会关闭 retained owner 并调用 `createOwnedRuntimeSession()`；后者把请求的 `sessionOptions`
   送入 client/session-new，并在 `prepareInitialRuntimeRecord()` 中持久化。
6. captured allowlist differential 的有效 occurrence 实际使用 policy SHA `ce796a1b...`：launcher、policy 文件与
   `session/new` `_meta` 三者一致；但归档 `session.json` 的 `session_options` 仍显示此前 record 的 `ea47a3ed...`。
   因此 occurrence 观察有效，但 record 不能作为本轮 binding 证据。
7. 现有回归只覆盖“旧 record 可直接复用且 binding 不同”时拒绝，没有覆盖 manager replacement、direct prompt、
   queue owner 和 reconnect fresh fallback。

## 3. 领域语义与 Owner

### 3.1 不变量

persistent `sessionKey` 是 policy binding 的稳定身份边界，不是可隐式换代的别名：

- 同名 record 已含完整 policy binding 时，新请求必须在任何 client 创建、resume/load/close/new 或 record 写入前，与它做
  ref+SHA 全等比较；不同即 `CLAUDE_EXECUTION_POLICY_CONFLICT`。
- 单侧缺失也属于不同，不允许从 legacy/unbound 静默升级为 E1，也不允许从 E1 静默降级为 unbound。
- cwd、agent argv、resume id、backend resource 是否仍存在，只决定进程/session 能否复用，不得削弱 binding 校验。
- binding 相同但旧 backend session 已不可用时，可以创建新的 ACP session；新 record 必须持久化本次实际送入
  `session/new` 的完整 `sessionOptions`，不得保留旧快照。
- 真正需要更换 policy 时，operator/Dual 必须关闭旧 named session，并使用新的 session identity；本 checkpoint 不发明
  隐式 generation 或迁移协议。

### 3.2 Source of Truth

- 请求侧 authority：本次 `RuntimeEnsureInput.sessionOptions` 中成对出现的 policy ref/SHA。
- 已存在 persistent identity：`SessionRecord.acpx.session_options` 中持久化的 policy ref/SHA。
- 新 ACP session 的执行事实：同一请求传给 client 的 `SessionAgentOptions`，并由 fork 映射到 `session/new` `_meta`。
- record 是 session identity 的持久化事实；stream/`session/new` 是具体 occurrence 的执行事实。两者必须一致，但不能用旧
  record 反向覆盖本次 request。

## 4. 最小实现

### 4.1 唯一 binding comparator

把 binding-key 与 compatibility guard 下沉到现有 `runtime/engine/session-options.ts`，作为 session-options owner 的唯一
实现；manager、direct prompt 与 queue owner 只能调用该 helper，不得各写一份 ref/SHA 判断。

比较对象始终是 persisted record options 与本次 effective requested options。先比较 binding，再做一般 options merge；
不能先 merge，因为 preferred 值会覆盖旧 binding，掩盖冲突。

### 4.2 Manager 守卫顺序

在 `ensureSessionWithOwnership()` 加载 existing record 后、调用 `shouldReuseExistingRecord()` 之前，对 persistent mode
执行 binding compatibility guard。随后：

1. binding 不同：立即抛 `CLAUDE_EXECUTION_POLICY_CONFLICT`；不得关闭 owner，不得创建 client，不得发送 ACP 请求，
   不得修改 record。
2. binding 相同且 reuse 条件满足：沿用现有 `reuseRuntimeSession()`；不改变 system prompt/model 等既有复用语义。
3. binding 相同但 reuse 条件不满足：沿用既有关闭/重建路径；`prepareInitialRuntimeRecord()` 继续以本次完整
   `sessionOptions` 写入 replacement record。
4. one-shot 语义保持原样，继续要求完整 options 全等且 owner 存在。

### 4.3 Direct prompt 与 queue owner

`runSessionPrompt()` 必须在记录 prompt submission、写 session record、创建/启动 client 之前调用同一 guard；通过后才允许
`mergeSessionOptions(requested, persisted)`。`createQueueOwnerSharedClient()` 也必须在 shared child spawn 前使用同一 guard；
per-task options 最终仍由 `runSessionPrompt()` 再验，不能假设 owner-level 验证覆盖后续 queue message。

对全部 `mergeSessionOptions(..., sessionOptionsFromRecord(...))` caller 做 sibling sweep。只有 fresh session、one-shot 或没有
persisted identity 的构造路径可以不走 persistent guard；每个豁免必须有现有语义与测试锚点。

只以 `sessionOptionsFromRecord(record)` 创建 client 的 `runtime/engine/connected-session.ts` 与 manager turn reconnect 路径没有
外部 preferred options，不构成换绑定入口；保持只读消费，但加入 sweep 记录，防止未来新增 requested options 后漏接 guard。

### 4.4 Fresh fallback 持久化

不新增“只刷新两个 policy 字段”的局部 writer：

- manager replacement record 继续通过 `persistSessionOptions(record, input.sessionOptions)` 一次写入完整 options；
- `connectAndLoadSession()` 是唯一知道 resume/load 已 fallback 到 fresh `session/new` 的 transition owner。它必须接收本次
  已验证的 effective session options，并且只在 fresh session 创建成功后完整 replace record 的 `session_options`；
- normal resume/reuse 不改写 persisted options；fallback 失败也不得提前改 record；
- 调用方随后保存的 `acpxState` 必须从更新后的 record 同步，不能在 checkpoint 时把旧 snapshot 写回来。

发现需要新增 generation/version 字段时立即停止，返回架构评审。

### 4.5 证据勘误

在 captured differential 目录增加 `ERRATUM.md`，不修改原始 `session.json`/stream/output：

- 明确 record 中 `ea47a3ed...` 属较早 invocation 的残留；
- 明确有效 occurrence 使用 `ce796a1b...` 的依据是 launcher、policy 文件与 `session/new` `_meta`；
- 修正尝试计数，承认 `2026-09-02T01:41:23Z` 的早期 reviewed-acpx invocation 未被原 result 计入；
- 声明该 evidence 仍证明 bounded allow/deny effect，但不能证明 record binding durability；
- 更新目录 `SHA256SUMS`，保留全部原始工件字节。

## 5. 文件范围

预计只涉及：

1. fork `src/runtime/engine/session-options.ts`；
2. fork `src/runtime/engine/manager.ts`；
3. fork `src/runtime/engine/reconnect.ts`；
4. fork `src/cli/session/runtime.ts` 与 `src/cli/session/queue-owner-runtime.ts`；
5. fork 对应 manager/session/queue lifecycle tests；
6. regenerated reviewed fork patch；
7. patch manifest、configure/Doctor/build identity 中的 patch SHA 与对偶 tests；
8. 本计划、active plan、captured evidence `ERRATUM.md` 与 `SHA256SUMS`；
9. checkpoint result/diff-review 文档。

不得修改 policy schema、Dual transport binding、workflow JSON、ledger schema、permission action、provider adapter 或
WebSearch visibility。若 production diff 超出 patch identity 的机械同步，停止并解释。

## 6. Cannot-Bypass Tests

至少覆盖以下行为，而不是只断言 helper 返回值：

1. manager existing A + request B，且 cwd/agent argv/resume id 任一导致 reuse=false：在 `clientFactory` 前拒绝；store
   字节不变。
2. direct/queue existing A + request B，模拟 backend resource-not-found/fallback-new 链：在 prompt submission/client start
   前拒绝；不得出现 `session/resume` 或 `session/new`，不得覆盖 record。
3. direct/queue existing A + request A、旧 backend session 不可用：允许 resume 失败后创建一次新 ACP session；
   `session/new` `_meta` 与最终 record
   均为 A，完整 session options 无旧值残留。
4. existing unbound + request A，以及 existing A + request unbound：均在副作用前拒绝。
5. existing A + request A、正常 reuse：保持当前零 clientFactory 行为。
6. queue owner A + task B 不得借 preferred merge 绕过；owner A + task A 正常执行。
7. one-shot full-options equality 与 non-Claude/unbound sibling 既有行为不退化。
8. mutation test：把 guard 移回 `shouldReuseExistingRecord()` 之后或删除 direct guard，测试 1/2/6 至少一项必须失败。

focused fork tests 与 full fork suite 通过后，重新生成 patch、重算 identity，执行 Dual focused、full reactor、Doctor、
installer/reverse-apply 与 `git diff --check`。本 checkpoint 不需要 provider。

## 7. End-to-End Invariant

| 维度 | 证明 |
|---|---|
| Declared by | persistent session key + persisted policy ref/SHA |
| Proven by | existing record binding、requested binding、replacement `session/new` `_meta` 与最终 record 全等 |
| Consumed by | manager、direct prompt、queue owner 共用的 session-options compatibility guard |
| Enforced before | prompt record write、clientFactory/start、resume/load/close/new、record overwrite 与 provider prompt |
| Failure route | mismatch -> `CLAUDE_EXECUTION_POLICY_CONFLICT`，零 ACP/record 副作用 |
| Cannot bypass | reuse=false、resource-not-found、单侧缺失、same-binding replacement 与 mutation tests |
| Residual | backend session 丢失仍可导致 same-policy 重建；真正 policy 变更必须换 session identity |

## 8. 实施顺序与成熟度

1. 独立评审本计划与勘误；
2. 先添加 failing fork lifecycle tests；
3. 最小移动 guard，必要时只修完整 options replacement；
4. fork focused/full；
5. 重建 patch identity 与 Dual 对偶验证；
6. 独立 diff review 后 clean commit；
7. 另起 E1 closure Gate。WebSearch visibility authority 继续保持独立计划。

deterministic checkpoint PASS 后可声明：

- persistent policy binding replacement guard：`ENFORCED`；
- captured allowlist differential：effect 仍为 bounded evidence，record durability 该样本 `INVALID`；
- E1 完整 closure 与 WebSearch provider-side cannot-bypass：仍 `NOT PROVEN`。

```text
Boundary Review: PASS
Production implementation: COMPLETE / DIFF REVIEW REQUIRED
Provider execution authorized: NO
Real-case authorized: NO
```
