# dual-orchestrator Docs

> Status: current docs index
> Last updated: 2026-06-08

This directory contains current product docs, implementation design notes, and
historical development records. For open-source readers, start with the current
reading path. Historical handoffs and private benchmark notes are retained for
project traceability, but they are not the public onboarding path.

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
| Public and private benchmark entry | `testing/real-case-benchmarks/README.md` |
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

## Internal / Private Evidence

Some documents mention private real-case runs that were used to harden the
runtime. They are useful engineering evidence, but should not be copied into
public release material without sanitization.

| Private evidence | Public policy |
|---|---|
| Private benchmark case files under `testing/real-case-benchmarks/cases/` | Internal benchmark cases. Keep for development, exclude from public narrative. |
| Private benchmark reports under `testing/real-case-benchmarks/reports/` | Internal benchmark reports. Summarize lessons generically when needed. |
| `testing/real-case-reports/` | Raw local run reports. These often contain absolute paths and should not be treated as public docs. |
| `handoff-*.md` | Historical session handoffs. Keep as archive only. |

## Historical / Reference Documents

These documents are useful for understanding why the system evolved, but they
should not be treated as current implementation truth:

| Document group | Status |
|---|---|
| `handoff-*.md` | Historical session handoffs. Many branch/test counts are old. |
| `superpowers/plans/*.md` | Implementation plans from earlier phases. Useful as background. |
| `superpowers/specs/*.md` | Earlier design specs. Some have been superseded by current runtime docs. |
| `design/P5-task-contract-and-contract-compiler.md` | Initial P5 design. Superseded for contract source-of-truth by artifact-driven negotiation. |
| `designs/T1.1-T2.2-contract-and-prompt-regression.md` | Historical test/contract design; much of it has already been implemented. |

## Cleanup Notes

As of 2026-06-08, the docs that are most likely to confuse new readers are:

- `design/P5-task-contract-and-contract-compiler.md`: still valuable, but its Markdown heuristic compiler design is superseded by structured artifact contracts.
- `testing/real-case-reports/`: useful raw evidence, but not public narrative material.
- Private benchmark case files under `testing/real-case-benchmarks/cases/`: internal hardening cases, not reusable open-source fixtures.
- `handoff-*.md`: historical handoff chain; do not use for current status.

The cleanup strategy is conservative: keep historical records until the public
release branch is prepared, but route readers through this index and the current
architecture/state-machine docs first. See `public-cleanup-todo.md` for the
release cleanup checklist.
