# Verification Gate: Captcha Revision Verbatim Preservation Probe v2

日期：2026-08-09

状态：`READY / NOT RUN`。

- Target: 使用 clean SHA `bc08027` 的 production `AgentResultPromptContract` 重新渲染
  captured captcha-auth revision prompt，证明一个 fresh Claude session 能产生逐字保留未声明
  clauses 的最终 `TaskContractProposal`。允许因 strict JSON parse failure 在同一 session 执行
  最多一次既有 bounded syntax correction；不判断、不比较模型名称。

- Source of truth: generated prompt path/hash、ACP process results、session stream、
  `AcpxAgentResultAcquirer` 的 typed correction reason/disposition、
  `AgentResultEnvelopeParser`、`AgentResultEnvelopeValidator`、
  `TaskContractRevisionContextResolver`、`TaskContractProposalValidator`，以及原 workstream 的
  verified previous proposal 和 CO-1/CO-2 obligations。

- Trigger precondition: implementation baseline 必须包含 `bc08027` 且 worktree clean；generated
  prompt 必须由 production `AgentResultPromptContract.render` 替换 captured prompt 的唯一
  `Output Contract` 区段，路径为
  `/private/tmp/captcha-revision-probe-v2-bc08027.prompt.md`，SHA-256 必须为
  `f3d686fa84a1fcfe14a7b97b6489ac7900da3d90a0dc94172cd013818261329f`；prompt 必须只含一个
  Output Contract、包含 verified baseline marker 和 character-for-character instruction；原
  `ws-20260807T150938Z-2D5Y` 保持只读；使用独立 fresh session。

- How precondition is checked before run: 记录 clean Dual SHA、Gate SHA、prompt absolute
  path/hash/bytes、production-render checks、fresh session name 和开始时间。执行入口直接使用
  production `AcpxAgentResultAcquirer`、`AcpxAgentTransport`、`contractDiscovery` initial
  permission 和 `contractCorrection` correction permission；不得手工拼 correction prompt，不读取
  provider model metadata。

- PASS: initial occurrence exit 0、completed 且无 active blocking adapter signal；correction
  disposition 只能为 `NOT_NEEDED`，或在 initial `PARSE_FAILURE` 时为 `ATTEMPTED`；最多两个
  occurrence，且 correction occurrence exit 0、completed、无 tool/permission request。最终
  candidate 必须无 prose/fence/trailing token，通过 production strict parser、envelope
  identity/schema、proposal mechanical validation、真实 CO-1/CO-2 coverage 和 revision delta；
  `AC-12`、`TR-16` 等未声明 clauses 必须与 verified baseline 逐字一致。记录所有 occurrence
  的调用、token 和耗时，不因 correction 剔除成本。

- FAIL: 非零 process、timeout、tool/permission request、active blocking signal、第二次之后仍
  parse/schema invalid、correction reason 为 `SCHEMA_FAILURE`、任何 mechanical/coverage/delta
  error、未声明 clause 改写/新增/删除，或出现第三次 occurrence。FAIL 阻止 fresh captcha-auth
  full workstream。

- INVALID: SHA/路径/hash 不符、prompt 不是 production renderer 生成、worktree dirty、旧
  workstream 被修改、session 非 fresh、真实 baseline/obligations 无法解析、transcript/usage
  缺失或执行未走 production acquirer，导致目标不可判定。

- Stop-loss: 只允许一次 initial occurrence；只有 production acquirer 判定
  `PARSE_FAILURE + ATTEMPTED` 才允许同 session 一次 correction。任何 process/tool/permission
  failure 立即停止；不 retry、不 resume、不启动第三次 prompt、不修改 parser/validator/prompt，
  不启动 full workflow。

- Run artifact freshness: Gate 先作为独立 checkpoint 提交；运行报告记录 implementation SHA
  `bc08027`、Gate SHA、prompt hash、session/stream identity、每个 occurrence 的 process/parse/
  correction/usage facts、最终四类 validator verdict。Probe PASS 后才允许生成 fresh package 并
  启动一个新的 captcha-auth full workstream；Probe 本身不等于 terminal-delivery PROVEN。

## Preflight Evidence

- generated prompt bytes：`34870`。
- generated prompt SHA-256：
  `f3d686fa84a1fcfe14a7b97b6489ac7900da3d90a0dc94172cd013818261329f`。
- Output Contract marker count：`1`。
- verified baseline marker：present。
- character-for-character instruction：present。
- production implementation checkpoint：`bc08027`。
