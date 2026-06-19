# Quickstart

中文版本：[quickstart.zh-CN.md](quickstart.zh-CN.md)

This guide gets `dual-orchestrator` from a fresh checkout to a runnable local
CLI. It focuses on the smallest open-source path, not full production setup.

## 1. Prerequisites

Install or verify:

```bash
java -version       # JDK 21+
mvn -version        # Maven 3.9+
git --version
acpx --version
```

`acpx` must be able to call your Claude and Codex runtimes for real lifecycle
runs. The fixture/smoke path can verify the CLI without touching your own repo.

Use pinned adapter versions for real runs. `acpx` may otherwise resolve
floating Claude/Codex ACP adapters and change behavior between runs:

```bash
npm install -g acpx@0.11.0
./scripts/configure-acpx.sh
```

If your Claude Code uses a third-party Anthropic-compatible backend such as
GLM, DeepSeek, one-api, or claude-code-router, keep the provider variables in
`~/.claude/settings.json` under `env`. `configure-acpx.sh` installs a small
Claude adapter wrapper that reads those variables at runtime, so secrets are not
written into `~/.acpx/config.json`.

## 2. Install

```bash
cd dual-orchestrator
./scripts/install.sh
```

The default prefix is `$HOME/.local`.

To install wrappers and write the pinned acpx Claude/Codex adapter config in one step:

```bash
./scripts/install.sh --configure-acpx
```

Add it to `PATH` if needed:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

Verify:

```bash
dualctl --help
```

## 3. Check A Target Repo

Before a real run, check the target Git repo:

```bash
dualctl doctor --workdir /path/to/git/repo
```

`doctor` checks basic prerequisites. Real agent runs also need usable acpx
Claude/Codex sessions. It also checks that `acpx` is the expected version and
that both ACP adapters are pinned instead of using floating npm ranges. When
Claude settings define `ANTHROPIC_BASE_URL` plus a token, it also checks that
the Claude ACP command can receive those variables.

## 4. Run A Small Smoke

From `dual-orchestrator`:

```bash
./scripts/smoke-test.sh --case lifecycle --cleanup
```

This is the quickest sanity check for the CLI/workflow path.

## 5. Run A Real Lifecycle Case

The easiest reusable real run is the public benchmark:

```bash
./scripts/run-public-benchmark.sh
```

It creates a disposable issue-flow sample repo under `/tmp`, runs the full
lifecycle with real `acpx` Claude/Codex, auto-approves intermediate benchmark
gates, and writes a local ignored report under
`docs/testing/real-case-reports/<timestamp>/`.

To choose the sample repo location:

```bash
./scripts/run-public-benchmark.sh --dest /tmp/dual-issue-flow-benchmark
```

For your own repository, use `run-real-case.sh` directly.

Use a real Git repo and a bounded task file:

```bash
cat > /tmp/dual-task.md <<'EOF'
Update the README with a short "Local Development" section.
Keep the change documentation-only. Do not modify source code.
EOF

./scripts/run-real-case.sh \
  --repo /path/to/your/repo \
  --task-file /tmp/dual-task.md
```

`full` lifecycle is the default. Use `--variant contract` only when you
intentionally want the smaller contract-only workflow.

Use a small task first. Public reusable benchmark cases are named `public-*.md`.
Internal/private benchmark cases are not part of this public cleanup branch.

The script creates a safe worktree, starts the lifecycle, auto-approves
intermediate gates for benchmark purposes, and writes a report.

## 6. Inspect Results

After the run:

```bash
cat docs/testing/real-case-reports/<timestamp>/report.md
jq . docs/testing/real-case-reports/<timestamp>/summary.json
cat docs/testing/real-case-benchmarks/benchmark-index.md
```

Important fields:

- `final_status`
- `final_acceptance_decision`
- `repair_loop_count`
- `verification_confidence`
- `original_repo_clean`
- `artifacts`

Interpretation:

- `PASS`: lifecycle completed with strong evidence.
- `PASS-WARN`: lifecycle completed, but evidence still needs attention, often
  because Codex returned `ACCEPT_WITH_NOTES` or verification confidence is not
  high enough yet.
- `FAIL`: workflow stopped, original repo was polluted, or key artifacts are missing.

## 7. Trace A Running Workstream

Use the effective workdir and workstream id printed by `start` or
`run-real-case.sh`:

```bash
dualctl trace --workdir <effective_workdir> <workstream_id>
```

`trace` is the easiest first read. It prints the current status, active node,
why the workflow stopped, attention items, recent timeline, final acceptance
decision, repair loops, important artifacts, and the next command to run.

If a historical Agent run has a missing or unreadable `run.json`, `trace`
prints `Run Metadata Warnings`. That means observation data is incomplete; it
does not turn the workflow terminal state into a failure. Use
`dualctl inspect --run <run_id>` when you intentionally want strict inspection
of a specific run.

If the workflow is waiting for human review:

```bash
dualctl review show --workdir <effective_workdir> <workstream_id>
dualctl approve --run --workdir <effective_workdir> <workstream_id>
```

`dualctl approve`, `dualctl revise`, and `dualctl complete` are shortcuts for
the longer `dualctl review ...` commands.

## 8. Optional Local Worker Mode

Default runs execute the active node inline:

```bash
dualctl run --workdir <effective_workdir> <workstream_id>
```

For the AgentTask/worker MVP, queue one active Agent task and let a local worker
execute exactly one queued task:

```bash
dualctl run --dispatch-mode WORKER --workdir <effective_workdir> <workstream_id>
dualctl worker run-once --workdir <effective_workdir> <workstream_id>
```

This mode keeps the workflow definition and routing in the runtime ledger. The
worker is only the local executor for one queued AgentTask; it is not a
multi-worker platform yet.

## 9. Merge Accepted Worktree Changes

When a workstream is completed and accepted, merge the reviewed worktree diff
back to the original repository explicitly:

```bash
dualctl delivery merge \
  --workdir <source_worktree> \
  --target <original_repo> \
  <workstream_id>
```

The command requires a completed workstream, final `ACCEPT` or
`ACCEPT_WITH_NOTES`, and a clean target repo. It writes
`delivery/merge-plan.json`, `delivery/merge.patch`, changed file lists, and
apply logs under the workstream ledger. It applies the patch to the target repo
index but does not auto-commit.

After reviewing the staged diff:

```bash
cd <original_repo>
git diff --cached
git commit -m "feat: dual delivery <workstream_id>"
```

## 10. Promote Reusable Learnings

When a real failure is classified as a reusable calibration candidate, review it
before changing any project principles:

```bash
dualctl calibration list --workdir <effective_workdir> <workstream_id>
dualctl calibration show --workdir <effective_workdir> <workstream_id> <candidate_id>
dualctl calibration decide --status accepted --workdir <effective_workdir> <workstream_id> <candidate_id>
dualctl calibration plan --workdir <effective_workdir> <workstream_id> <candidate_id>
dualctl calibration promote --workdir <effective_workdir> <workstream_id> <candidate_id>
```

Promotion is intentionally narrow in the MVP: it only appends an accepted
`CONTRACT_TEMPLATE` plan to `.agent/templates/contract-review-principles.md`.
It does not mutate built-in prompts, workflow definitions, context rules, or
tests.

For deeper inspection:

```bash
dualctl status --workdir <effective_workdir> <workstream_id>
dualctl timeline --workdir <effective_workdir> <workstream_id>
dualctl runs --workdir <effective_workdir> <workstream_id>
dualctl events --workdir <effective_workdir> --limit 50 <workstream_id>
dualctl summary --workdir <effective_workdir> <workstream_id>
```

`dualctl runs` is also a read-only observation command. It lists readable Agent
runs and reports missing or damaged run metadata as warnings, so one historical
file problem does not hide the current workflow state.

For failures:

```bash
dualctl recovery show --workdir <effective_workdir> <workstream_id>
```

The ledger lives under:

```text
<effective_workdir>/.agent/workstreams/<workstream_id>/
```
