# Schema-Level Bounded Correction Owner Assessment

日期：2026-07-26

## 结论

Boundary Review：**PASS / IMPLEMENTATION READY**

这不是 Phase B 重开。Phase B 的 change-boundary 已闭环；本缺口属于 Agent
terminal output contract，并阻塞 Phase C controlled verification。

## 已证明的问题

README controlled real-case `ws-20260726T111801Z-CM6B` 中：

- ACP assistant message 是纯 JSON，`stopReason=end_turn`，process exit code 为 `0`；
- strict parser 解码成功，`contractDecodable=true`；
- `AgentResultEnvelopeValidator` 拒绝结果，因为缺少
  `result.missing_evidence`；
- `AcpxAgentResultAcquirer.mayCorrect()` 只接受
  `AGENT_RESULT_PARSE_FAILED`，因此没有执行已有的一次同 session correction；
- workflow 停在 `implement.claude_self_test_report`，A3 未启动。

## Owner

| Fact / Action | 唯一 owner |
| --- | --- |
| JSON grammar | `AgentResultEnvelopeParser` |
| Schema、identity 与 node contract validation | `AgentResultEnvelopeValidator` |
| 是否属于可重新序列化的 schema failure | `AgentResultEnvelopeValidator.ValidationResult` |
| 最多一次、同 session、deny-all correction | `AcpxAgentResultAcquirer` |
| 最终 node 准入 | `ArtifactContractValidator`，由 `AgentWorker` 强制调用 |

第一次 validation 只决定是否允许 correction，不决定 node 成功。corrected candidate
必须再次经过 `ArtifactContractValidator` 的完整 validation。

## Correction Matrix

允许：

- JSON grammar failure（保留现有行为）；
- schema version 或 execution identity 字段缺失；
- nullable、允许为空的结构列表字段缺失，例如
  `checks_skipped`、`missing_evidence`、`acceptance_notes`。

禁止：

- workstream/run/node identity 不匹配；
- artifact type 或 decision 不允许；
- `decision`、`summary`、objective 等业务内容缺失；
- 必须非空的 acceptance、plan 或 handoff 内容为空；
- lineage/hash、task scope、change boundary、reread policy 或其他 semantic failure；
- provider/runtime failure；
- correction 后再次失败时启动第三次 prompt。

禁止根据错误字符串决定是否 correction。错误文本只能作为 correction prompt 和 evidence，
routing 必须消费 typed disposition。

## 最小实现范围

- 不新增 module、pipeline、recovery framework 或通用 middleware；
- 扩展现有 `AgentResultEnvelopeValidator.ValidationResult`；
- 扩展现有 `AcpxAgentResultAcquirer`；
- `AcpxAgentTaskHandler` 只传入当前 workstream/run/node contract；
- occurrence metadata 记录 parse、schema validation 与 correction reason；
- strict parser、prompt JSON contract 与 runtime 最终 validator 保持严格。

## Cannot-Bypass Gate

1. 缺少 `result.missing_evidence`：同 session correction 恰好一次，第二份合法结果通过。
2. correction 后仍缺字段：不发第三次 prompt，runtime 最终拒绝。
3. wrong run/workstream/node：不 correction。
4. illegal decision/artifact type：不 correction。
5. semantic required content 为空：不 correction。
6. provider failure：不 correction。
7. correction occurrence 使用 deny-all profile，保留两次 prompt/output/usage evidence。
8. `ArtifactContractValidator` 仍对最终 candidate 完整复验；直接构造 handler result
   不能绕过。
9. 第二份 schema correction 只能补写 validator 明确返回的 typed targets；decision、
   summary、evidence 或其他既有字段发生变化时直接拒绝。

通过 deterministic gates 和独立 boundary review 后，重新运行同一个 README
controlled real-case。只有 README PASS 才允许 A3。

## Implementation Result

状态：**PASS / ENFORCED + DETERMINISTICALLY PROVEN**

- `AgentResultEnvelopeParser` 未放宽；
- `AgentResultEnvelopeValidator` 是唯一 schema 与 correction-target owner；
- `AcpxAgentResultAcquirer` 最多执行一次同 session、deny-all correction；
- 缺失可空列表只能补成空列表，缺失 identity 只能补成当前 execution identity，
  缺失 schema version 只能补成 `1`；
- 显式不兼容 schema version、identity mismatch、illegal decision、semantic failure
  不触发 correction；
- correction 后仍无效或改变既有字段时，不会产生第三次 prompt，最终 node validation
  拒绝；
- targeted tests 与完整 Maven reactor 均通过。
