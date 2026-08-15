# Result: Design Review Convergence Captured Exact Replay

日期：2026-08-15

状态：`FAIL / CONSUMED`

Gate：`20260815-design-review-convergence-captured-replay.md`

执行 SHA：`5499f4e203fb94f381f6168b9bc370af672120d3`

## 结论

唯一授权 replay 已执行并消费。process 正常退出，ACP completion 为 `end_turn`，但 production strict contract 拒绝
final assistant message；同一输出也未覆盖 Gate 要求的 B1-B4。因此 checkpoint `44e72306` 仍为
`PROJECTED / VALIDATED / NOT PROVEN`，不得启动第二次 replay 或 fresh captcha-auth workstream。

## Execution Facts

| Fact | Value |
|---|---|
| session | `codex-design-review-replay-44e7230` |
| record id | `01a002c4-13bb-7070-967d-482d6bd4c0f6` |
| prompt occurrences | `1` |
| permission requests / tool calls | `0 / 0` |
| correction / retry | `0 / 0` |
| process exit | `0` |
| ACP stop reason | `end_turn` |
| duration | `75.73s` |
| session closure | `closed` |

ACP stream SHA 为 `517518ba222957f7ba00bb3e87537a8e9a721b3e7aa43830f1624d2dbb47b3a5`；final
assistant message SHA 为 `c1edee671ed785e7ae68e122bdb55c0ae87a03a4c6f97b50bdb53056db23bd54`。

## Strict Contract Failure

Production `AgentLifecycleEventReader` 从唯一 session stream 生成 terminal `AcpxPromptCompletion.finalAssistantMessage()`；
冻结 validator 再调用 production presentation normalization、`AgentResultEnvelopeParser`、validator 与 projector。

final assistant message 是完整且合法的 JSON object，但
`result.failure.verification_remediation` 被输出为自由文本：

```text
Revise the resend ownership protocol and prove the vulnerable interleaving with a deterministic PostgreSQL-backed concurrency test.
```

typed contract 只允许 `NONE`、`TEST_CODE_CHANGE`、`EVIDENCE_COLLECTION`，且冻结 prompt 示例明确给出
`"verification_remediation": "NONE"`。因此这是模型输出违反显式 schema，不是 parser 放宽问题、ACP 截断或 transport
丢失。

第一次把 acpx console stdout 直接送入 validator 得到 array-start parse error，只是 operator 对 evidence 层级的错误
尝试，不是最终样本判定。Console stdout 包含 `[client]`/`[thinking]` presentation；production parser 的 authoritative
输入是上述 ACP stream 投影出的 final assistant message。最终 FAIL 以该 production 路径为准。

## Semantic Coverage

模型返回 typed intent `NEEDS_REVISION / blocking=true`，并发现一个新的有效 blocker：后发 resend 可在先发 OTP 已
commit、尚未 activation 的窗口中 invalidate/replace 它，使第一个已投递 code 不可验证；同时要求 deterministic
barrier/latch PostgreSQL concurrency test。

但 Gate 要求的完整当前 blocker 集没有被覆盖：

| Boundary | Result | Evidence |
|---|---|---|
| B1 stale PENDING / expired ACTIVE 在 replacement 前退出 current set，并支持 restart resend | `MISS` | 输出只讨论 in-flight committed OTP，不讨论 stale/expired recovery。 |
| B2 activation 必须带 `expires_at > now()`，late confirmation 不得激活过期 OTP | `MISS` | 输出没有 expiry predicate 或 delayed confirmation。 |
| B3 unique conflict 必须 rollback-safe | `MISS` | checks 仅泛称 transaction conflict，没有 rollback-only/transaction-boundary 方案。 |
| B4 stale/restart/late-confirmation/real-conflict tests | `MISS` | 只提出新的 forced concurrent resend interleaving test。 |

新 blocker 不替代 B1-B4。Gate 明确要求在同一 turn 报告完整可推导 blocker set，因此 semantic target 也独立 FAIL。

## Boundary Assessment

- Declared by：冻结 final typed design、TaskContract、`iter-003..005`。
- Proven by：唯一 ACP stream、terminal completion、production reader/parser/validator。
- Consumed by：strict AgentResultEnvelope boundary；没有进入 workflow routing。
- Enforced before：schema 失败阻止 projector 产生 authoritative sidecar。
- Failure route：Gate 立即停止并关闭 session；没有 correction/retry/第二 turn。
- Cannot-bypass：stream 中恰好一个 `session/prompt`，0 permission request，0 tool call；run id 正确也不能绕过 enum。
- Residual raw path：console stdout 仅为 presentation evidence，不作为最终控制输入。

## Next Decision Boundary

本结果同时证明两点：完整 typed subject/iteration history 已成功投影，但它们不足以保证单 turn 完整 blocker convergence；
模型还会在清晰 schema 下产生局部字段漂移。下一步必须先做只读归因，判断最大贡献项是 architecture-review prompt 的
扫描策略、输出 contract 可执行性，还是固定单 turn 目标本身不现实。不得直接放宽 parser、自动改 enum、增加 retry 或
再次运行 provider。
