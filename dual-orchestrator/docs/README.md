# dual-orchestrator Docs

> Status: current docs index
> Last updated: 2026-06-08

This directory contains the current product docs and selected implementation
design notes. For open-source readers, start with the current reading path.
Historical handoffs and private benchmark notes were removed from this public
cleanup branch; reusable evidence now starts from the public benchmark sample.

## Current Reading Path

| Purpose | Document |
|---|---|
| Install and first runnable workflow | `quickstart.md` |
| Why this project exists | `whitepaper-mapping.md` |
| Overall product and architecture narrative | `architecture-and-execution-flow.md` |
| Runtime state model and operator commands | `design/workflow-state-machine.md` |
| Artifact/context contract design | `design/2026-06-03-artifact-driven-contract-negotiation.md` |
| Contract boundary hardening | `design/2026-06-06-contract-boundary-hardening.md` |
| Current roadmap / feature status | `features.md` |
| Public benchmark direction | `testing/public-benchmark-plan.md` |
| Public benchmark entry | `testing/real-case-benchmarks/README.md` |
| Smoke / E2E usage | `testing/smoke-test-e2e.md` |
| Full CLI / runtime reference | `reference.md` |

## Public Onboarding Path

Use this path when evaluating the project from a fresh clone:

1. Read the root `README.md`.
2. Follow `quickstart.md`.
3. Run `./scripts/smoke-test.sh --case thin-basic --cleanup`.
4. Run `./scripts/run-public-benchmark.sh` when real `acpx` Claude/Codex are available.
5. Use `dualctl trace --workdir <effective_workdir> <workstream_id>` to inspect the result.

This path avoids private project details and uses the reusable sample app under
`examples/issue-flow-app`.

## Current Design Path

Use this path when changing the orchestrator itself:

1. `architecture-and-execution-flow.md`
2. `design/workflow-state-machine.md`
3. `design/2026-06-03-artifact-driven-contract-negotiation.md`
4. `design/2026-06-06-contract-boundary-hardening.md`
5. `features.md`

These documents describe the current shape of the lifecycle, the state model,
the artifact/context boundary, and the remaining MVP gaps.

## Reference Path

`reference.md` is intentionally long. Treat it as a detailed manual, not the
first document to read. Prefer `quickstart.md` and `trace` for day-to-day use.

## Private Evidence Policy

Private real-case runs are valuable engineering evidence, but they are not part
of the public onboarding path. This branch keeps only sanitized summaries and
public benchmark material. Raw local reports remain ignored under
`testing/real-case-reports/` when generated locally.

## Historical / Reference Documents

These documents are useful for understanding why the system evolved, but they
should not be treated as current implementation truth:

| Document group | Status |
|---|---|
| `superpowers/plans/*.md` | Implementation plans from earlier phases. Useful as background. |
| `superpowers/specs/*.md` | Earlier design specs. Some have been superseded by current runtime docs. |
| `design/P5-task-contract-and-contract-compiler.md` | Initial P5 design. Superseded for contract source-of-truth by artifact-driven negotiation. |
| `designs/T1.1-T2.2-contract-and-prompt-regression.md` | Historical test/contract design; much of it has already been implemented. |

## Cleanup Notes

As of 2026-06-08, tracked historical handoffs and private benchmark case/report
files have been removed from the public cleanup branch. Remaining historical
design docs may still describe why the system changed, but current behavior is
documented by the reading path above. See `public-cleanup-todo.md` for the
release checklist.
