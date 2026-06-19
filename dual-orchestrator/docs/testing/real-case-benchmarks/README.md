# Real-case Benchmarks

Real-case benchmark is the bridge between fixture smoke tests and production
usage. It runs the full lifecycle against a disposable Git repository with real
`acpx` Claude/Codex sessions, then records whether the orchestrator can explain
the result.

It is not a replacement for unit tests. Its job is to answer:

1. Did the workflow reach `COMPLETED`?
2. Did the original repository stay clean?
3. Are final report, handoff, next-stage prompt, and delivery artifacts present?
4. Can `dualctl trace` explain where the workstream stopped or completed?
5. Did Codex acceptance return `ACCEPT`, `ACCEPT_WITH_NOTES`, or `REJECT`?

## Public Case Set

| Case | Type | Purpose | Status |
|------|------|---------|--------|
| `cases/public-issue-flow-review-state.md` | Public sample app feature | Exercises contract, context package, self-test evidence, Codex acceptance, final report, and handoff on a reusable sample repo. | Ready |

The public case registry is tracked in
[benchmark-suite.json](benchmark-suite.json). It defines the default case,
sample repo, task file, workflow variant, max steps, max gates, and known
limits. This keeps benchmark commands repeatable without relying on private task
files.

Private/internal benchmark cases are not included in this public cleanup branch.
Lessons from those runs are summarized in the architecture docs without private
repository names, local paths, or raw reports.

## Run

Preferred public path:

```bash
./scripts/run-public-benchmark.sh
```

List available public cases:

```bash
./scripts/run-public-benchmark.sh --list-cases
```

Run a specific public case:

```bash
./scripts/run-public-benchmark.sh --case public-issue-flow-review-state
```

This command prepares a disposable copy of `examples/issue-flow-app`, runs the
full lifecycle with real Agents, auto-approves benchmark gates, and writes a
timestamped report under:

```text
dual-orchestrator/docs/testing/real-case-reports/<timestamp>/
```

The reports directory is ignored by Git. It is local evidence, not release
documentation.

To choose the sample repo location:

```bash
./scripts/run-public-benchmark.sh --dest /tmp/dual-issue-flow-benchmark
```

To prepare the sample repo without running Agents:

```bash
./scripts/prepare-public-benchmark-repo.sh --dest /tmp/dual-issue-flow-benchmark
```

The script prints an equivalent `run-real-case.sh` command.

## Result Grading

The benchmark writes `summary.json` and `report.md`. The lightweight grade is a
health signal, not a substitute for reading the report:

| Grade | Meaning |
|-------|---------|
| `PASS` | Workflow completed, original repo is clean, key artifacts exist, and Codex accepted without notes. |
| `PASS-WARN` | Workflow completed and key artifacts exist, but there are attention items such as `ACCEPT_WITH_NOTES`, low verification confidence, or repair-loop history. |
| `FAIL` | Workflow did not complete, original repo was polluted, or key artifacts are missing. |

`PASS-WARN` is not automatically a failed run. It means the harness did its job
and surfaced something a human should read before trusting the result.

## Inspect

After a run, start with the report printed by the script. Then use trace:

```bash
dualctl trace --workdir <effective_workdir> <workstream_id>
```

`trace` is the fastest answer to:

- Where is the lifecycle now?
- Why did it stop?
- What should I do next?
- Which artifacts matter?

For deeper inspection:

```bash
dualctl status --workdir <effective_workdir> <workstream_id>
dualctl timeline --workdir <effective_workdir> <workstream_id>
dualctl runs --workdir <effective_workdir> <workstream_id>
dualctl recovery show --workdir <effective_workdir> <workstream_id>
```

## Benchmark Index

When you intentionally want to keep local benchmark summaries comparable, run:

```bash
./scripts/real-case-benchmark-summary.sh
```

It aggregates ignored local reports into:

```text
docs/testing/real-case-benchmarks/benchmark-index.md
```

The committed index stores aggregate metrics only. Detailed `report.md` files
remain ignored local evidence, so public readers do not get broken links to
machine-local paths.

By default the index includes only public cases whose task file starts with
`public-`. Internal reports can still be inspected locally with:

```bash
./scripts/real-case-benchmark-summary.sh --include-private --stdout
```

For the public branch, keep the committed index sanitized. Do not commit private
repository names, absolute local paths, raw prompts, or private product details.

## When To Run

Run the real-case benchmark after changing:

- workflow routing,
- acpx adapter behavior,
- context package assembly,
- artifact contracts,
- failure/recovery handling,
- human review packet or operator overview,
- delivery/final report generation.

Do not run it for every small documentation edit. It uses real Agent quota and
should serve mainline confidence, not ceremony.
