# Verification Gate: Captcha Revision Presentation Normalization Probe v3

日期：2026-08-10

状态：`INVALID / CONSUMED`。

该 Gate 是一个新样本。它只把已消费 v2 的 verified baseline、CO-1/CO-2 obligations 和 captured
revision prompt 当作只读输入；不复用 v2 session、candidate、correction 或 PASS/FAIL 结论，也不
修改 v2 Gate。

## Verification Gate

- Target: 在 implementation checkpoint
  `8992824d68800ca1c1e1ecfae236b3d7b55a8a68` 上执行一次且仅一次 fresh-session revision
  probe，证明 ACP 合法的完整单一 `json` fence 可由 production acquisition boundary 归一化，且
  final candidate 仍通过原 strict parser、envelope/schema、task-contract mechanical、真实
  CO-1/CO-2 coverage 和 revision-delta validation。该 Probe 不比较、不判断 provider/model。

- Source of truth: production `AcpxAgentResultAcquirer` 的 occurrence schema v2、
  `presentationDisposition`、raw/candidate SHA-256、process result、ACP transcript、usage；
  `AgentResultEnvelopeParser`、`AgentResultEnvelopeValidator` 与
  `ArtifactContractValidator` 的最终 `NodeResult`；旧 workstream 中 verified previous proposal、
  rejected review obligations 和 revision lineage。

- Trigger precondition: Dual implementation SHA 必须为 `8992824d68800ca1c1e1ecfae236b3d7b55a8a68`
  的后代且 Gate 提交后 worktree clean；captured source prompt 必须为
  `ws-20260807T150938Z-2D5Y/run-1786115545628` 的原始 revision prompt，SHA-256 为
  `d403a5273068bb35f77a8501f167531675712d841703869a5ef751b1983750fb`；production-rendered
  probe prompt 必须为 `34870` bytes、SHA-256
  `f3d686fa84a1fcfe14a7b97b6489ac7900da3d90a0dc94172cd013818261329f`，只含一个
  `## Output Contract`，包含 verified baseline 和 `character-for-character` 指令；旧 workstream
  运行前树指纹必须为 `ace6b73e2c3af0c774c004b20fdf8844fcbc0c8aba4583aba21ade9c73b3a80b`；
  使用一个从未存在的 fresh session 和一个运行前不存在的 `/private/tmp` clone workspace。

- How precondition is checked before run: 记录 clean Gate SHA、implementation ancestor、prompt
  path/hash/bytes/marker count、旧 workstream tree hash、一次性 runner path/hash、fresh package jar
  path/hash/mtime、session name、clone/report path和开始时间。runner 固定为
  `/private/tmp/CaptchaRevisionPresentationProbeV3.java`，SHA-256
  `4470f6951824c67093d749139b4a6ad69e6e01e81a1994ebc20f95acce836ac7`；它必须编译并只链接
  fresh package。旧 workstream 只读复制到 clone；provider execution 和 validator 只写 clone。

- PASS: 恰好一个 initial occurrence，只有 production acquirer 判定 initial parse failure 时才允许
  同 session 唯一 correction；总 occurrence 不超过两个。每个 occurrence process exit `0`、
  completed、无 active blocking adapter signal、无 tool/permission request。最终 presentation
  disposition 只能是 `NOT_NEEDED` 或 `SINGLE_JSON_FENCE_REMOVED`；若 raw 为 fenced JSON，必须
  为后者且 raw/candidate hash 不同。最终 candidate 必须通过未修改的 strict parser 和 envelope
  contract；production `ArtifactContractValidator` 必须返回 `NodeStatus.COMPLETED`，其 artifact
  contract valid，从而同时证明 mechanical、真实 CO-1/CO-2 coverage 与 revision delta 通过；
  `AC-12`、`TR-16` 等未声明 clauses 保持 verified baseline 字符级一致。usage、duration 和全部
  occurrence 必须计入报告。session 被关闭、无残留 provider 进程，旧 workstream 运行后树指纹
  与运行前完全相同。

- FAIL: 任一非零 process、timeout、tool/permission request、active blocking signal、
  `REJECTED_AMBIGUOUS_PRESENTATION`、第二 occurrence 后仍 parse/schema invalid、
  `SCHEMA_FAILURE` correction、任何 mechanical/coverage/delta error、未声明 clause 改写/新增/删除、
  第三个 occurrence、session 关闭失败或残留 provider 进程。FAIL 阻止 fresh captcha-auth full
  workstream。

- INVALID: Gate/implementation/runner/prompt/hash/path/marker/fresh-package 不符；worktree dirty；
  clone path 运行前已存在；旧 workstream tree hash 不符或运行后发生变化；session 不是 fresh；
  runner 未走 production acquirer 和 `ArtifactContractValidator`；transcript、occurrence schema v2、
  usage 或 typed validation result 缺失，导致目标不可判定。`INVALID` 不得解释成部分成功，也不得
  启动 full workflow。

- Stop-loss: 只启动一个 fresh session 和一个 initial prompt。只有 production acquirer 自身允许时
  才执行同 session 最多一次 bounded correction；不得手工 correction、retry、resume 或第三次
  prompt。发现 process/tool/permission/transport failure 立即停止并关闭 session。Probe 未 PASS 前
  不生成或运行 fresh captcha-auth workstream；同类 Gate 不连续重跑。

- Run artifact freshness: 本 Gate 先独立提交并取得 clean SHA；随后在该 SHA 上执行 fresh package，
  记录 adapter/runtime jar SHA-256 与 mtime，再重新编译固定 runner。报告必须记录 Gate SHA、
  implementation ancestor、prompt/runner/jar hashes、session、clone、所有 occurrence、usage、
  presentation audit 和 final typed validator verdict。deterministic `ENFORCED` 不等于 real-case
  `PROVEN`；只有本 Probe PASS 才解锁一个 fresh captcha-auth full workstream。

## Preflight Inputs

- implementation checkpoint：`8992824d68800ca1c1e1ecfae236b3d7b55a8a68`。
- captured source prompt：
  `/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/ws-20260807T150938Z-2D5Y/.agent/workstreams/ws-20260807T150938Z-2D5Y/runs/run-1786115545628/prompts/intake-claude-brainstorm.md`。
- generated prompt：
  `/private/tmp/captcha-revision-presentation-probe-v3-8992824d.prompt.md`。
- generated prompt：`34870` bytes；SHA-256 `f3d686fa84a1fcfe14a7b97b6489ac7900da3d90a0dc94172cd013818261329f`。
- Output Contract marker count：`1`。
- verified baseline marker：present。
- character-for-character instruction：present。
- runner：`/private/tmp/CaptchaRevisionPresentationProbeV3.java`；SHA-256
  `4470f6951824c67093d749139b4a6ad69e6e01e81a1994ebc20f95acce836ac7`；compile preflight PASS。
- old workstream pre-run tree SHA-256：
  `ace6b73e2c3af0c774c004b20fdf8844fcbc0c8aba4583aba21ade9c73b3a80b`。

## Result

本 Gate 已于 2026-08-10 在 Gate SHA
`ded6e7796100a0a47e6a674dd31380c08d91e6de` 上执行一次，结论为
`INVALID / CONSUMED`。不得重用该 session 或把本轮解释为部分 PASS。

### Preflight

- worktree clean；implementation checkpoint `8992824d68800ca1c1e1ecfae236b3d7b55a8a68`
  是 Gate SHA 的祖先。
- fresh package PASS；runtime jar SHA-256
  `fd348ef372e0a147f37b7bee5f0fda1257a801d32fa17ed844fb0fa8f706ee46`，adapter jar
  SHA-256 `63d0ac5d69fce3e5665d636d741dc9d6cb856b09f8da67a63343a1a7b4536fff`。
- prompt、runner、captured source prompt 与旧 workstream tree hash 均与 Gate 一致。
- fresh session：`dual-captcha-revision-presentation-v3-ded6e77`；clone/report path 运行前不存在。

### Captured Facts

- 仅执行一个 initial occurrence，ACP transcript 以 `[done] end_turn` 结束；没有
  correction artifact。
- captured assistant result 是单个无 Markdown fence 的完整 JSON object；production
  acquirer 写出 final candidate。
- final candidate 进入 production `ArtifactContractValidator`，且已经过 strict parse、
  envelope/schema、task-contract mechanical、CO-1/CO-2 coverage 和 revision-delta validation。
  如果任一边界失败，validator 会在 `withResultSidecars` 之前返回 typed contract
  violation；本轮已进入 sidecar publication。
- session `eef6a540-a9c9-4937-a7ac-60b0843264f2` 已关闭，无残留 provider
  进程。旧 workstream 运行后 tree SHA-256 仍为
  `ace6b73e2c3af0c774c004b20fdf8844fcbc0c8aba4583aba21ade9c73b3a80b`。

### Invalid Reason

一次性 runner 把 captured output 写入 `runs/probe-v3/...`，但 envelope 的 authoritative
`run_id` 为 `run-1786115545628`。`AgentOutputContractDocument.hasValidatedLineage(...)`
要求 `source_output_ref` 包含 `/runs/<run_id>/`，因此
`TaskContractProposalReference.publish(...)` 正确 fail closed，报错：

```text
validated task-contract proposal lineage is required
```

该异常发生在 runner 的 typed report 持久化之前，导致 Gate 要求的 production
validator `COMPLETED` verdict、presentation audit 和 usage 报告不完整。这是验证
harness 的 lineage path 组装错误，不是 provider/model、prompt、strict parser 或
production lineage validator 错误。

### Decision

- 不启动 fresh captcha-auth full workstream。
- 不再调用 provider 补跑本 Gate。
- 后续若继续，只能先用本次 captured candidate 做一次纯离线、正确
  lineage path 的 production validator replay；该 replay 必须单独预定 Gate，且不得
  改写本 v3 的 `INVALID` 结论。
