# Occurrence-Policy Session Lifetime Checkpoint Result

日期：2026-09-02
状态：`IMPLEMENTED / INDEPENDENT DIFF REVIEW PASS / READY TO COMMIT`

## 1. 目标

关闭 occurrence-scoped Claude execution policy 与 workstream-scoped persistent session 的 lifetime 冲突。相邻合法 Agent run
不再要求 operator 手工关闭 session，同时不放宽 reviewed acpx 的 policy-binding replacement guard。

本 checkpoint 不修改 sandbox local-binding、external network、workflow JSON、provider permission 或产品代码，不调用 provider。

## 2. 实现

1. `SessionScopePolicy.occurrenceBound()` 使用既有 `RUN` scope 生成
   `<base>-<workstreamId>-<runId>`；reason 固定为 `execution-policy:occurrence-bound`。
2. `AcpxAgentTaskHandler` 在 session 创建前识别 E1-bound Claude execution，并让 occurrence policy lifetime 优先于
   env/node-config session scope。
3. experimental compaction 仍可生成原有 seed，但不得改写 occurrence-bound physical session name/segment。
   因 segment 同步收敛为 run scope，seed 仍作为当前 prompt 输入保留，但不再承诺跨 occurrence 的 session seed 连续性。
4. contract correction 与 reconnect 继续消费同一 `AcpxRunRequest`，因此同一 run 内仍复用同一 session。
5. 终态 cleanup 继续从每个 run.json 读取实际 session，无需新增 reaper 或命名反推。

## 3. 测试

- `SessionScopePolicyTest`：两个 run 得到不同 session，并记录 occurrence-bound reason；
- `AcpxAgentTaskHandlerTest`：named Claude profile 写入 run scope；同 run initial/correction 使用同一 session；compaction seed
  保留但不能覆盖 physical session；相邻 execute 使用不同 session/binding，不再触发 replacement conflict；captured large-stream
  fixture 绑定本次动态 session identity；
- `AcpxSessionLifecycleTest`：多个 run-scoped Claude session 均由既有 ledger cleanup 收集并关闭。
- `SessionScopePolicyTest` 另以 `DUAL_SESSION_SCOPE_CLAUDE=workstream` 证明 legacy env 只影响 legacy resolve，不能降级
  occurrence-bound policy。

验证结果：

- focused：`61 tests / 0 failures / 0 errors / 0 skipped`；
- full Maven reactor：`1297 tests / 0 failures / 0 errors / 0 skipped`；
- `git diff --check`：clean。

## 4. Boundary Closure

| 维度 | 结果 |
|---|---|
| Declared by | occurrence-scoped `ClaudeExecutionPolicy.Binding` |
| Proven by | binding lineage 的 run id 与 run-scoped session identity |
| Consumed by | handler → transport ensure/prompt/correction/reconnect |
| Enforced before | session bootstrap 与 provider prompt |
| Failure route | policy/session 装配失败沿既有 typed execution failure 返回 |
| Cannot-bypass test | env/node-config/compaction 不得产生共享 session；相邻 run 不同、同 run correction 相同 |
| Residual | provider real-case 尚未复验；S2 localhost capability 仍未实施 |

## 5. Review Reflection

首轮 focused test 暴露 captured large-stream fixture 把旧 session 名写死在 `index.json`。修订后 fake acpx 从真实
`sessions ... --name` 参数生成 record，既适配 run-scoped identity，也继续证明 projector 不接受未匹配 session evidence。

独立代码级 diff review 判定 `PASS / P0=0 / P1=0 / P2=4`。其中 env override 与相邻 execute 两条廉价回归测试已随
checkpoint 补齐；compaction seed 的 occurrence 语义已在 §2 明示；评审/CI 使用 `-am` 或 full reactor，避免读取本地陈旧模块工件。

本 checkpoint 的成熟度只能标记为 `SESSION LIFETIME ENFORCED / REAL-CASE NOT REPROVEN`，现允许选择性提交。S2 必须先完成
agent-owned 与 host-owned loopback connect 方向测量和 operator 裁定，不能随本提交实施。
