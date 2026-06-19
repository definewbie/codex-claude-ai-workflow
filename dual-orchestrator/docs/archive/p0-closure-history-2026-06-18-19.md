# P0 Closure History, 2026-06-18 to 2026-06-19

This is a sanitized process archive. Raw private real-case logs remain ignored
under `docs/testing/real-case-reports/` and are not part of the public reading
path.

## Why This Archive Exists

P0 closure did not succeed in one straight run. Several real-case attempts
exposed boundary bugs that were worth preserving as engineering lessons:

- generated Git evidence initially missed committed worktree changes;
- acceptance `REJECT + INSUFFICIENT_EVIDENCE` was too easy to misclassify as a
  contract violation;
- acpx/Claude could emit a terminal contract and then keep the process alive
  with reconnect diagnostics;
- implementation prompts could drift into broad self-test or dependency
  installation work;
- Agent output occasionally appeared in side files instead of stdout contract
  artifacts;
- reconnect/session diagnostics needed to become typed runtime recovery facts,
  not natural-language guesses.

## Boundary Fixes That Came Out Of The Attempts

- Git evidence now compares the workstream base commit to the current head and
  includes committed, staged, working-tree, and untracked evidence.
- Acceptance review semantics allow valid `REJECT` decisions with insufficient
  evidence while still rejecting contradictory positive decisions.
- acpx transport observes terminal Agent contracts and stream lifecycle events
  instead of treating raw stdout progress as lifecycle state.
- Persistent reconnect/session diagnostics become typed recoverable runtime
  failures unless a complete terminal contract wins first.
- Implementation prompts are scoped to code changes; self-test prompts own
  validation evidence.
- Context packages prefer structured sidecars over raw Agent stdout, preventing
  tool logs and transcripts from recursively entering later prompts.
- Observe/review profiles are read/search oriented; write-heavy implementation
  stays visible as a higher-risk phase.

## Final Closure Evidence

The final P0 closure evidence is summarized in
[`../testing/p0-closure-report.md`](../testing/p0-closure-report.md).

That report records:

- deterministic Maven/package evidence;
- a controlled acpx smoke that completed end to end;
- a private medium-complexity real case that completed after two semantic
  repair loops;
- known limits accepted after P0.

## Lesson

The main lesson was not "add more retry." It was to preserve domain boundaries:

- runtime recovery is not business rejection;
- semantic repair is not process retry;
- stdout is evidence, not lifecycle state;
- Agent claims must become typed artifacts before routing;
- process logs are useful for diagnosis but should not become public product
  documentation.
