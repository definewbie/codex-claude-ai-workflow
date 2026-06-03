# dual-orchestrator Docs

> Status: current docs index
> Last updated: 2026-06-03

This directory contains both current design documents and historical handoff notes. Prefer the current documents below when trying to understand the system.

## Current Reading Path

| Purpose | Document |
|---|---|
| Overall product and architecture narrative | `architecture-and-execution-flow.md` |
| Runtime state model and operator commands | `design/workflow-state-machine.md` |
| Current roadmap / feature status | `features.md` |
| Artifact-driven TaskContract design | `design/2026-06-03-artifact-driven-contract-negotiation.md` |
| Failure classification design | `design/P6-failure-arbiter.md` |
| Real-case benchmark entry | `testing/real-case-benchmarks/README.md` |
| Smoke / E2E usage | `testing/smoke-test-e2e.md` |

## Historical / Reference Documents

These documents are useful for understanding why the system evolved, but they should not be treated as current implementation truth:

| Document group | Status |
|---|---|
| `handoff-*.md` | Historical session handoffs. Many branch/test counts are old. |
| `superpowers/plans/*.md` | Implementation plans from earlier phases. Useful as background. |
| `superpowers/specs/*.md` | Earlier design specs. Some have been superseded by current runtime docs. |
| `design/P5-task-contract-and-contract-compiler.md` | Initial P5 design. Superseded for contract source-of-truth by artifact-driven negotiation. |
| `designs/T1.1-T2.2-contract-and-prompt-regression.md` | Historical test/contract design; much of it has already been implemented. |

## Cleanup Notes

As of 2026-06-03, the docs that were most likely to confuse new readers were:

- `design/P5-task-contract-and-contract-compiler.md`: still valuable, but its Markdown heuristic compiler design is superseded.
- Ignored local scratch files such as `smoke-test-full.md`, `todo.md`, or `note.md` if present in a working copy: these are not formal project docs; prefer tracked docs under this directory.
- `handoff-*.md`: historical handoff chain; do not use for current status.

The cleanup strategy is conservative: keep historical records, but route readers through this index and the current state machine / architecture docs first.
