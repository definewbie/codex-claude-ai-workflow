# Quickstart

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

## 2. Install

```bash
cd dual-orchestrator
./scripts/install.sh
```

The default prefix is `$HOME/.local`.

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
Claude/Codex sessions; missing sessions are usually created by acpx on demand.

## 4. Run A Small Smoke

From `dual-orchestrator`:

```bash
./scripts/smoke-test.sh --case thin-basic --cleanup
```

This is the quickest sanity check for the CLI/workflow path.

## 5. Run A Real Lifecycle Case

Use a real Git repo and a bounded task file:

```bash
./scripts/run-real-case.sh \
  --repo /path/to/your/repo \
  --task-file docs/testing/real-case-benchmarks/cases/cancer-clinic-feature-slice.md \
  --variant full
```

For your own project, replace `--task-file` with a small markdown requirement.

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
- `PASS-WARN`: lifecycle completed, but evidence still needs attention.
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

If the workflow is waiting for human review:

```bash
dualctl review show --workdir <effective_workdir> <workstream_id>
dualctl approve --run --workdir <effective_workdir> <workstream_id>
```

`dualctl approve`, `dualctl revise`, and `dualctl complete` are shortcuts for
the longer `dualctl review ...` commands.

For deeper inspection:

```bash
dualctl status --workdir <effective_workdir> <workstream_id>
dualctl timeline --workdir <effective_workdir> <workstream_id>
dualctl runs --workdir <effective_workdir> <workstream_id>
dualctl events --workdir <effective_workdir> --limit 50 <workstream_id>
dualctl summary --workdir <effective_workdir> <workstream_id>
```

For failures:

```bash
dualctl recovery show --workdir <effective_workdir> <workstream_id>
```

The ledger lives under:

```text
<effective_workdir>/.agent/workstreams/<workstream_id>/
```
