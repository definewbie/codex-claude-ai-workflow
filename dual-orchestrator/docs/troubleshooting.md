# Troubleshooting

中文版本：[troubleshooting.zh-CN.md](troubleshooting.zh-CN.md)

Use this page when a run stops, looks inconsistent, or consumes more time than
expected. Start from the ledger, not from the UI alone.

## First Commands

```bash
dualctl trace --workdir <effective_workdir> <workstream_id>
dualctl recovery show --workdir <effective_workdir> <workstream_id>
dualctl runs --workdir <effective_workdir> <workstream_id>
dualctl events --workdir <effective_workdir> --limit 80 <workstream_id>
```

`trace` is the first read model. It should answer: where it is, why it stopped,
what evidence exists, and what action is safe next.

## acpx / Claude 401

Symptom:

- Claude works in an interactive shell, but `acpx claude ...` fails with 401.

Likely cause:

- The Claude ACP child process did not receive the Anthropic-compatible provider
  environment variables.

Check:

```bash
dualctl doctor --workdir /path/to/repo
```

Fix:

```bash
DUAL_NODE_BIN_DIR=/path/to/node-22.13-or-newer/bin ./scripts/install-acpx-e1.sh
export DUAL_ACPX_EXECUTABLE="$PWD/.tools/acpx-e1/bin/acpx"
./scripts/configure-acpx.sh
```

For GLM, DeepSeek, one-api, or claude-code-router, keep provider settings in
`~/.claude/settings.json` under `env`. The configured Claude wrapper reads them
at runtime; secrets are not written into `~/.acpx/config.json`.

## Reconnect Or Apparent Hang

Symptom:

- Logs mention `agent needs reconnect`.
- The Agent appears slow or stuck, but later output shows it was still working.
- A run stays at `[client] session/resume (running)` for minutes before the
  prompt is accepted.

What the runtime does:

- It treats acpx/Claude reconnect as an adapter lifecycle signal.
- It does not kill an active run only because an old reconnect line exists.
- Later stream activity and a complete terminal contract supersede stale
  reconnect evidence.
- Agent steps use workstream-scoped sessions by default:
  `acpx <agent> -s <base>-<workstream_id> -f <prompt>`. The prompt, context
  package, and ledger remain the source of task context; the ACP session is
  only an execution-continuity cache inside one workstream.
- The acpx queue owner is part of that session lifecycle. Dual should not kill
  it after each node; acpx TTL owns its cleanup. The default TTL is 1800 seconds
  and can be overridden with `DUAL_ACPX_QUEUE_TTL_SECONDS`.

Check:

```bash
dualctl runs --workdir <effective_workdir> <workstream_id>
dualctl trace --workdir <effective_workdir> <workstream_id>
```

If an old run was started with an obsolete session strategy and is stuck in `session/resume`, stop that run and retry with the current CLI build. If the
workflow is in recovery, inspect the recovery assessment before retrying:

```bash
dualctl recovery show --workdir <effective_workdir> <workstream_id>
dualctl retry --workdir <effective_workdir> <workstream_id>
```

## Waiting For Human Review

Symptom:

- Status is `WAITING_FOR_HUMAN`.

Read the current packet first:

```bash
dualctl review show --workdir <effective_workdir> <workstream_id>
```

Then choose one action:

```bash
dualctl approve --run --workdir <effective_workdir> <workstream_id>
dualctl revise --feedback-file feedback.md --run --workdir <effective_workdir> <workstream_id>
dualctl complete --workdir <effective_workdir> <workstream_id>
```

Historical gates are evidence only. Current gates expose actions.

## Completed But UI Looks Running

The ledger is authoritative. Refresh the panel and compare with:

```bash
dualctl trace --workdir <effective_workdir> <workstream_id>
```

If `trace` says `COMPLETED`, the workflow is terminal. Run/resume/retry/approve
must not create new workflow nodes. Only observation, summary, delivery merge,
or cleanup commands should remain available.

## Too Many Worktrees

Each workstream uses an isolated worktree by default. List them from the source
repo:

```bash
git worktree list
```

After a run is reviewed and no longer needed:

```bash
git worktree remove <worktree_dir>
git branch -D <branch>
```

Do not delete a worktree before saving or merging wanted changes.

## Merge Accepted Changes Back

When a workstream is completed and final acceptance is `ACCEPT` or
`ACCEPT_WITH_NOTES`:

```bash
dualctl delivery merge \
  --workdir <source_worktree> \
  --target <original_repo> \
  <workstream_id>
```

This applies the reviewed patch to the target repo index and writes delivery
evidence under the workstream ledger. It does not auto-commit.

## Token Or Context Seems Too Large

Check the run metadata and context package:

```bash
dualctl runs --workdir <effective_workdir> <workstream_id>
```

If one stage repeatedly consumes too much context, treat it as a context package
boundary issue, not an Agent personality issue. The expected direction is to
reduce raw tool transcript carry-over and rely on structured artifacts, compact
handoff, and focused evidence refs.

Token source meanings:

- `acpx_stream`: measured provider usage with input/output split.
- `acpx_usage_update`: measured ACP total usage only; provider split is
  unavailable.
- `artifact_estimate`: local estimate from prompt/output/transcript metadata.

## Public Benchmark Fails

Run:

```bash
rtk proxy mvn -q clean test
dualctl doctor --workdir /path/to/repo
```

If offline regression passes but a separately approved public benchmark fails, inspect the
benchmark report under:

```text
docs/testing/real-case-reports/<timestamp>/
```

Classify the failure as one of:

- provider/session availability;
- artifact contract violation;
- recovery boundary defect;
- product/domain rejection;
- benchmark expectation gap.

Do not fix these by adding ad hoc string parsing to workflow handlers. Convert
external output into typed artifacts, events, or failure signals first.
