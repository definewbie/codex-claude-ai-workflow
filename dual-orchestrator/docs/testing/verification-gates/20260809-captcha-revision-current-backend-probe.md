# Verification Gate: Captcha Revision Current-Backend Probe

日期：2026-08-09

状态：`FAIL / CONSUMED`。

- Target: 使用当前 Claude Code 新 session 配置精确重放已捕获的 captcha-auth revision
  prompt，证明该 provider execution 能在不调用工具的情况下返回 exit 0、唯一 strict
  `AgentResultEnvelope`，并通过既有 revision coverage/delta validator；不判断、不比较模型名称。

- Source of truth: captured prompt SHA-256、ACP process exit、stdout/stderr transcript、
  `AgentResultEnvelopeParser`、`TaskContractRevisionContextResolver`、
  `TaskContractProposalValidator` 及原 workstream 中 verified baseline/obligations。

- Trigger precondition: Dual clean SHA 包含 `5580926`；captured prompt 必须为
  `ws-20260807T150938Z-2D5Y` 的 revision prompt，SHA-256
  `d403a5273068bb35f77a8501f167531675712d841703869a5ef751b1983750fb`；旧 workstream
  只读且 sessions 已关闭；新建独立 Claude Code session；permission policy 与 captured run
  相同，deny Terminal，且只允许一次 provider occurrence。

- How precondition is checked before run: 记录 clean Dual SHA、prompt absolute path/hash、
  fresh session name、完整 ACP command 和开始时间；调用前确认旧 workstream 状态仍为
  `WAITING_FOR_RECOVERY` 且不执行 retry/resume；不读取或判断 provider model metadata。

- PASS: ACP exit code 0；transcript 无 tool call / permission request；stdout 只含一个 JSON
  object，无 prose/fence/trailing token；production parser valid；envelope identity 与 captured
  revision node 一致；proposal mechanical validation、typed obligation coverage 和 revision delta
  validation 全部无错误。

- FAIL: 非零 process、tool/permission request、空输出、prose/fence/trailing token、parse/schema
  failure、identity mismatch、coverage/delta error，或出现第二次 provider occurrence。

- INVALID: Dual dirty/错误 SHA、prompt path/hash 不符、复用旧 session、旧 workstream 被修改、
  validator 无法装载真实 baseline/obligations、输出或 transcript 缺失，导致目标不可判定。

- Stop-loss: 只执行一次 fresh-session prompt request；任何 tool/permission request、非零 exit
  或 timeout 立即 FAIL，不 correction、不 retry、不 resume、不修改 prompt/parser/permission。

- Run artifact freshness: probe 报告记录 Dual SHA、Gate SHA、captured prompt path/hash、session
  name、command、started/finished time、exit code、stdout/stderr hashes，以及 production parser、
  coverage、delta 的 typed verdict；不以模型名称作为 freshness 或 PASS 条件。

## Scope

本 Probe 只筛选当前 backend 是否值得消耗一个完整 fresh workflow。PASS 不等于 captcha-auth
交付成功，也不证明 terminal-delivery；FAIL 则阻止新的完整 workstream。

## Result

- Dual SHA：`f1bdf05`；captured prompt SHA-256 与 Gate 预期一致。
- fresh session 只执行了一次 prompt occurrence；ACP process exit code 为 `0`，stop reason 为
  `end_turn`，transcript 未出现 tool call 或 permission request。
- CLI stdout 是增量片段和累计快照的展示层，不能作为候选结果。使用 production
  `AcpxStreamWireLine` + `AcpxSessionStreamProjector` 对 captured session stream 做
  deterministic replay 后，得到唯一最终 assistant candidate：20,285 bytes，SHA-256
  `ed5eeb9118958e43cb2d77915654e8fdd9eb6904d56669ac1d33e807ad5a44a4`。
- 最终 candidate 以 Markdown `json` fence 开始并以 fence 结束。production
  `AgentResultEnvelopeParser` 返回 `AGENT_RESULT_PARSE_FAILED / INVALID_JSON`。
- 因 strict JSON 前置条件失败，mechanical proposal、obligation coverage 与 revision delta
  validator 不得被解释为已通过；未执行 correction、retry、resume 或第二次 provider
  occurrence。
- Probe 结论为 `FAIL`，因此没有启动新的 captcha-auth full workstream。session 已关闭，
  无残留 provider 进程。

### Offline semantic diagnostic

为区分单一 fence 偏差与 proposal 语义/保真问题，使用一次性离线 replay 精确移除首尾
Markdown fence；该解包仅用于诊断，不进入 production acceptance，也不改变 parser。

- 解包后的 candidate 通过 production strict JSON parser、envelope identity/schema validator、
  proposal mechanical validator，以及基于真实 ledger lineage 解析出的 CO-1/CO-2 obligation
  coverage validator。
- revision context 从原 workstream 的 current node、iteration、verified rejected review 和
  previous proposal 解析成功，没有手工构造 baseline 或 obligations。
- revision delta validator 仍返回两个错误：
  `UNDECLARED_CONTRACT_CLAUSE_CHANGE: AC-12` 和
  `UNDECLARED_CONTRACT_CLAUSE_CHANGE: TR-16`。
- 两项差异均为 candidate 删除了 baseline 文本中 `mvn test` 外层的 Markdown backticks；业务
  含义未改变，但不满足现有 character-preserving revision contract，且未在
  `revision_responses` 中声明。

因此该样本不是单一格式偏差。下一 Gate 不得仅加入 bounded syntax correction 后继续 full
workflow；必须先收敛 full-proposal revision 的逐字保真协议，且不得通过 parser 宽松、文本
归一化或 validator 忽略差异来绕过。

后续 deterministic closure 已将字符级保真要求补入唯一
`AgentResultPromptContract` owner，并保持 parser/validator 不变。下一次独立 Gate 可允许最多
一次既有 bounded syntax correction，但 corrected candidate 仍必须通过全部 proposal、coverage
和 delta validator；correction 不得掩盖 semantic drift，且全部调用与资源消耗必须计入。
