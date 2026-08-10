# Verification Gate: Captcha Revision Captured Offline Replay v3.1

日期：2026-08-10

状态：`READY / NOT RUN`。

本 Gate 只闭合 v3 一次性 runner 的 lineage path 组装缺口。它使用 v3 已产生的
fresh captured candidate 做纯离线 replay，不调用 provider，不修改 production，不改写
v3 `INVALID / CONSUMED` 结论。

## Verification Gate

- Target: 在 clean SHA `88e0874f95abd0db5aac78d7db5710cef3905ae8` 的后代上，将 v3
  captured candidate 放入与 envelope `run_id=run-1786115545628` 一致的隔离
  `runs/run-1786115545628/...` 路径，证明 production `ArtifactContractValidator`
  产生 `NodeStatus.COMPLETED`、validated sidecar 和 verified task-contract proposal reference。

- Source of truth: captured candidate SHA-256；production `AgentResultEnvelopeParser`、
  `AgentResultEnvelopeValidator`、`TaskContractProposalValidator`、
  `TaskContractRevisionContextResolver`、`ArtifactContractValidator` 的 `NodeResult`；新生成的
  sidecar/reference 及其 hash/lineage。

- Trigger precondition: worktree clean；`8992824d68800ca1c1e1ecfae236b3d7b55a8a68` 是
  HEAD 祖先；v3 Gate 已固定为 `INVALID / CONSUMED`；captured candidate 路径为
  `/private/tmp/captcha-revision-presentation-probe-v3-workspace-ded6e77/.agent/workstreams/ws-20260807T150938Z-2D5Y/runs/probe-v3/outputs/intake-claude-brainstorm.json`，
  bytes 为 `17782`，SHA-256 为
  `3edf94b9d0eb1e354c252dbc1d47f773c000c0052b3c19ea3c820ccf07057bd9`；历史
  source workspace tree SHA-256 为
  `ace6b73e2c3af0c774c004b20fdf8844fcbc0c8aba4583aba21ade9c73b3a80b`；新
  clone/report path 运行前不存在。

- How precondition is checked before run: 记录 HEAD、ancestor、worktree、candidate bytes/hash、
  source tree hash、offline runner source hash、fresh package jar hash/mtime、clone/report path。runner
  只复制 source workspace，再把 captured candidate 写入 clone 的
  `.agent/workstreams/ws-20260807T150938Z-2D5Y/runs/run-1786115545628/outputs/`
  下的新文件，并使用真实 node input refs 调用 production validator。

- PASS: validator 返回 `NodeStatus.COMPLETED`；typed agent-result contract 与 required artifact
  均 valid；decision 为 `READY_FOR_PRODUCT_REVIEW`；strict parse、schema、mechanical、
  CO-1/CO-2 coverage、revision-delta 无错误；新 sidecar 的 `source_output_ref`
  包含 `/runs/run-1786115545628/`；verified proposal reference 能解析回同一 sidecar，
  hash、workstream、producer node/node instance 一致；source workspace tree hash 不变。

- FAIL: parser/schema/mechanical/coverage/delta 任一失败；validator 非 `COMPLETED`；
  sidecar/reference 未生成、无法解析或 lineage/hash/identity 不一致。`FAIL` 阻止
  fresh captcha-auth full workstream。

- INVALID: candidate/hash/path、source tree、HEAD/ancestor、fresh package、runner hash 或
  clone freshness 不符；runner 未调用 production validator；报告缺失 typed verdict 或
  sidecar/reference evidence。`INVALID` 不得解释成部分 PASS。

- Stop-loss: 禁止调用 Claude/Codex/ACPX；禁止 correction、retry、resume 或修改
  captured candidate；任一 precondition 不符立即停止。只运行一次 offline validator
  replay。

- Run artifact freshness: Gate 先独立提交并取得 clean SHA；随后 fresh package，
  重新编译固定 runner，并在报告中记录 Gate/implementation/candidate/runner/jar
  hashes。该 replay PASS 只与 `8992824d` 的 deterministic presentation-normalization
  `ENFORCED` 证据合并解锁下一个 full-workstream Gate；不单独声称真实 fence
  normalization `PROVEN`。

## Preflight Inputs

- offline runner：`/private/tmp/CaptchaRevisionCapturedOfflineReplayV31.java`。
- runner bytes：`6543`。
- runner SHA-256：`4a563fcbdb5969216551b42f20835665af7b701b1fa2215a46109ffb7ea44d6f`。
- clone workspace：`/private/tmp/captcha-revision-offline-replay-v31-workspace-<gate-short-sha>`。
- report：`/private/tmp/captcha-revision-offline-replay-v31-<gate-short-sha>.report.json`。

## Result

尚未运行。
