# P0 Closure Report

Date: 2026-06-19

Historical attempt log:
[archive/p0-closure-history-2026-06-18-19.md](../archive/p0-closure-history-2026-06-18-19.md)

## Scope

P0 closure validates the main lifecycle harness:

- terminal-state guards;
- goal loop and semantic repair;
- typed artifact contracts;
- runtime recovery boundaries;
- delivery artifact generation;
- trace/status/summary projection consistency.

It does not claim release-quality UI, public benchmark maturity, or every future
recovery policy is complete.

## Deterministic Evidence

Command:

```bash
mvn -pl dual-cli,dual-server -am package
```

Result:

- Build passed.
- `dual-core`: 51 tests passed.
- `dual-ledger-file`: 111 tests passed.
- `dual-flow-liteflow`: 12 tests passed.
- `dual-runtime`: 323 tests passed.
- `dual-adapter-acpx`: 125 tests passed.
- `dual-adapter-git`: 5 tests passed.
- `dual-cli`: 53 tests passed.
- `dual-server`: 45 tests passed.

Command:

```bash
./scripts/smoke-test.sh --case contract-happy-path --skip-build --cleanup
```

Result:

- Workstream: `ws-20260619T045848Z-XBST`
- Final status: `COMPLETED`
- Gate rounds: 6
- Events: 121
- Node count: 34
- Agent runs: 13
- Warnings: 0
- Result: `PASS`

This smoke also verified that a stale `agent needs reconnect` diagnostic no
longer kills an active Claude run when later acpx stream activity and the
terminal contract prove the Agent is still progressing.

## Private Real-Case Evidence

Command:

```bash
./scripts/run-real-case.sh \
  --repo /Users/kriswang/work/projects/ai/cancer-clinic \
  --task-file /tmp/dual-real-cases/cancer-clinic-p0-closure-business-logic.md \
  --variant full \
  --skip-build
```

Result:

- Workstream: `ws-20260619T051122Z-P837`
- Effective workdir:
  `/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260619T051122Z-P837`
- Final status: `COMPLETED`
- Final acceptance: `ACCEPT_WITH_NOTES`
- Goal outcome: `SATISFIED_WITH_NOTES`
- Benchmark grade: `PASS-WARN`
- Gate rounds: 9
- Agent runs: 24
- Timeline nodes: 67
- Acceptance decisions: `REJECT`, `REJECT`, `ACCEPT_WITH_NOTES`
- Repair loops: 2
- Verification confidence: `MEDIUM`
- Original repo clean: `true`

Generated report:

```text
docs/testing/real-case-reports/20260619T051121Z/report.md
```

The raw real-case report directory is ignored generated output and should not be
committed.

## What The Real Case Proved

- The lifecycle can run a medium-complexity private business task that touches
  backend API logic, backend tests, frontend API code, and frontend pages.
- Codex `REJECT` caused semantic repair instead of manual rerun:
  - first rejection: contract/design gap;
  - second rejection: implementation/verification gap;
  - final result: `ACCEPT_WITH_NOTES`.
- The system kept using the same workstream and worktree instead of opening
  fresh worktrees for each failure.
- `trace` answered the operator questions:
  - where it is: `COMPLETED`;
  - why it stopped: workflow completed, inspect artifacts only;
  - next action: review final artifacts and Git status.
- The original repository remained clean; all business changes stayed in the
  generated worktree.

## Resulting Business Diff

The private worktree contains six modified source files:

```text
backend/app/api/chemo.py
backend/tests/test_chemo.py
miniprogram/src/api/chemo.ts
miniprogram/src/api/request.ts
miniprogram/src/pages/record/index.vue
miniprogram/src/pages/retro/index.vue
```

Diff stat:

```text
backend/app/api/chemo.py               |  34 ++++++-
backend/tests/test_chemo.py            | 158 +++++++++++++++++++++++++++++++++
miniprogram/src/api/chemo.ts           |  24 +++++
miniprogram/src/api/request.ts         |  17 +++-
miniprogram/src/pages/record/index.vue |  20 ++++-
miniprogram/src/pages/retro/index.vue  |  39 ++++----
6 files changed, 268 insertions(+), 24 deletions(-)
```

## Known Limits Accepted After P0

- Final acceptance is `ACCEPT_WITH_NOTES`, not a clean `ACCEPT`, because live
  mini-program runtime smoke was not provided. Build/static/backend evidence was
  sufficient for the gate.
- Human gates are still numerous in `full` lifecycle runs. Gate reduction and a
  smoother operator experience remain P2 product work.
- Context/package and provider metrics are good enough for MVP closure, but
  token/cost governance should keep improving under P1.
- Delivery merge exists as an explicit post-completion command; this closure run
  did not apply the private worktree back to the original repo.

## P0 Decision

Status: `Done with known limits`.

The main lifecycle is now closed for MVP: a goal can move through safe worktree
execution, structured Agent artifacts, verifier rejection, semantic repair,
acceptance, delivery artifact generation, and traceable completion.

Future real-case failures should be classified as:

- regression against this closure boundary; or
- P1/P2 improvement work.

They should not reopen P0 unless they break the core lifecycle invariants above.
