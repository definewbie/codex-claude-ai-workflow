# Codex

- Built-in name: `codex`
- Default command: `npx -y @agentclientprotocol/codex-acp`
- Upstream: https://github.com/agentclientprotocol/codex-acp
- ACPX owns the built-in package range so fresh launches use the repository-selected stable adapter line without requiring a global install.
- Runtime controls exposed by current codex-acp releases include ACP modes plus separate `model` and `reasoning_effort` session config options.
- Use the advertised base model id with `acpx --model <id> codex ...` or `acpx codex set model <id>`, then set reasoning effort separately with `acpx codex set reasoning_effort <value>`.
- Switching models can adjust reasoning effort. ACPX saves the accepted effort for an existing selection, or removes that selection if the new model has no effort control.
- Reconnecting restores the saved model and effort before prompting, even when the adapter resumes the conversation with different defaults.
- Legacy `models` metadata may encode both values in a combined id such as `gpt-5.6-sol[max]`; ACPX uses that form only when the adapter does not advertise the newer model config option.

## Dual online permission replies (maintained adapter patch)

Use `--permission-replies` only for persisted JSON prompt calls with an explicit
finite `--timeout` and zero prompt retries. Existing automatic allow/deny rules
still apply. A genuine ask emits `dual/permission/request` with exact raw request,
RPC/session/prompt/owner generation, SHA-256 and deadline. Only the original live
queue callback accepts `permission-reply -s <name> --reply <json>` once; replies
must identify an offered once option and a committed Dual decision. Do not retry
this command, recreate a callback after restart, or infer tool completion from
`consumed: true`. Cancel, mode/model/config changes and expiry close old callbacks.
Native selection does not approve business scope, independent review or delivery.
