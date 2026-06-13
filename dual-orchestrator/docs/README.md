# dual-orchestrator Docs

中文版本：[README.zh-CN.md](README.zh-CN.md)

> Status: current docs index
> Last updated: 2026-06-13

This directory contains the current product docs and selected implementation
design notes. For open-source readers, start with the current reading path.
Historical handoffs and private benchmark notes were removed from this public
cleanup branch; reusable evidence now starts from the public benchmark sample.

## Current Reading Path

| Purpose | English | Chinese |
|---|---|---|
| Install and first runnable workflow | [quickstart.md](quickstart.md) | [quickstart.zh-CN.md](quickstart.zh-CN.md) |
| Why this project exists | [whitepaper-mapping.md](whitepaper-mapping.md) | Not split yet |
| Overall product and architecture narrative | [architecture-and-execution-flow.en.md](architecture-and-execution-flow.en.md) | [architecture-and-execution-flow.md](architecture-and-execution-flow.md) |
| Runtime state model and operator commands | [workflow-state-machine.md](design/workflow-state-machine.md) | [workflow-state-machine.zh-CN.md](design/workflow-state-machine.zh-CN.md) |
| Artifact contracts and context packages | [artifact-contracts-and-context.md](design/artifact-contracts-and-context.md) | [artifact-contracts-and-context.zh-CN.md](design/artifact-contracts-and-context.zh-CN.md) |
| Failure recovery and calibration | [failure-recovery-and-calibration.md](design/failure-recovery-and-calibration.md) | [failure-recovery-and-calibration.zh-CN.md](design/failure-recovery-and-calibration.zh-CN.md) |
| Operator observability | [operator-observability.md](design/operator-observability.md) | [operator-observability.zh-CN.md](design/operator-observability.zh-CN.md) |
| Design evolution article notes | [design-evolution-notes.md](design/design-evolution-notes.md) | [design-evolution-notes.zh-CN.md](design/design-evolution-notes.zh-CN.md) |
| Current feature status | [features.md](features.md) | Not split yet |
| Public benchmark entry | [testing/real-case-benchmarks/README.md](testing/real-case-benchmarks/README.md) | Not split yet |
| Smoke / E2E usage | [testing/smoke-test-e2e.md](testing/smoke-test-e2e.md) | Not split yet |
| Release checklist | [release-checklist.md](release-checklist.md) | [release-checklist.zh-CN.md](release-checklist.zh-CN.md) |
| Full CLI / runtime reference | [reference.md](reference.md) | Detailed reference is not split yet |

## Public Onboarding Path

Use this path when evaluating the project from a fresh clone:

1. Read the root `README.md`.
2. Follow `quickstart.md`.
3. Run `./scripts/smoke-test.sh --case baseline-basic --cleanup`.
4. Run `./scripts/run-public-benchmark.sh` when real `acpx` Claude/Codex are available.
5. Use `dualctl trace --workdir <effective_workdir> <workstream_id>` to inspect the result.

This path avoids private project details and uses the reusable sample app under
`examples/issue-flow-app`.

## Current Design Path

Use this path when changing the orchestrator itself:

1. `architecture-and-execution-flow.en.md` / `architecture-and-execution-flow.md`
2. `design/workflow-state-machine.md` / `design/workflow-state-machine.zh-CN.md`
3. `design/artifact-contracts-and-context.md`
4. `design/failure-recovery-and-calibration.md`
5. `design/operator-observability.md`
6. `features.md`

These documents describe the current shape of the lifecycle, the state model,
the artifact/context boundary, recovery scheduling, operator projection, and
the remaining MVP gaps.

## Reference Path

`reference.md` is intentionally long. Treat it as a detailed manual, not the
first document to read. Prefer `quickstart.md` and `trace` for day-to-day use.

## Private Evidence Policy

Private real-case runs are valuable engineering evidence, but they are not part
of the public onboarding path. This branch keeps only sanitized summaries and
public benchmark material. Raw local reports remain ignored under
`testing/real-case-reports/` when generated locally.

## Design Evolution Notes

`design/design-evolution-notes.md` preserves the useful story behind the
architecture: how the project moved from a dual-agent script to an engineering
harness. It is article material, not runtime reference.

## Cleanup Notes

As of 2026-06-08, tracked historical handoffs and private benchmark case/report
files have been removed from the public cleanup branch. Remaining historical
design docs may still describe why the system changed, but current behavior is
documented by the reading path above. See `public-cleanup-todo.md` for the
release checklist.
