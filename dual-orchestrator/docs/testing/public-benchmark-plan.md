# Public Benchmark Plan

This document separates three different validation layers so the project does
not confuse public onboarding smoke tests with real lifecycle benchmarks.

## Why This Exists

The current real-case benchmark mechanism was validated against a private
project. That helped expose real orchestration problems, but the private project
is not suitable for public documentation or open-source benchmark cases.

The benchmark strategy should keep the value of real cases without leaking
private product details.

## Validation Layers

| Layer | Purpose | Public? | Expected Complexity |
| --- | --- | --- | --- |
| Smoke fixture | Prove install, CLI, ledger, workflow, and operator panel can run. | Yes | Small and deterministic |
| Public real-case benchmark | Prove the lifecycle can handle meaningful product work. | Yes | Moderate, realistic, repeatable |
| Private real-case benchmark | Stress the system with real internal product work. | No | High, messy, domain-specific |

## Smoke Fixture

The smoke fixture should be intentionally small. It is allowed to be a toy
repository because its job is onboarding and regression detection, not product
quality proof.

Good smoke fixture properties:

- Fits in a temporary directory.
- Has one or two source files.
- Has a simple test command.
- Can run quickly.
- Produces easy-to-inspect ledger artifacts.

It should answer:

- Can `dualctl start --run` create a workstream?
- Can human gates be approved?
- Can the workflow reach the next gate or completion?
- Can `trace`, `summary`, and the operator panel explain the state?

## Public Real-case Benchmark

The public real-case benchmark should not be a toy fixture. It should be a
small but real application designed to exercise the workflow:

- Product requirement clarification.
- Design review.
- Plan review.
- Implementation.
- Self-test report.
- Final acceptance.
- Handoff and next-stage prompt generation.
- Delivery artifact generation.

Recommended benchmark app shape:

- A small Spring Boot API plus a lightweight frontend, or a single-stack app if
  that is cheaper to maintain.
- Real domain state transitions, not only a single pure function.
- A few existing tests so Claude can run meaningful self-test steps.
- Clear product docs so Codex can review against user intent.
- Tasks that are safe to repeat in disposable worktrees.

Example public benchmark tasks:

- Add a status transition to an issue tracker sample app.
- Add a review-required state to a publishing workflow sample app.
- Add a small dashboard card with backend-derived data.
- Add validation rules plus UI copy for a form-heavy workflow.

These cases should live under:

```text
dual-orchestrator/docs/testing/real-case-benchmarks/cases/public-*.md
```

Reports generated from public runs may be committed if they do not include local
machine paths or private tokens.

## Private Real-case Benchmark

Private real cases remain valuable because they reveal problems that small
fixtures miss:

- Long context pressure.
- Ambiguous requirements.
- Domain constraints that are easy to violate.
- Non-obvious testing expectations.
- Real repair and recovery behavior.

Private cases should stay out of the public branch. If their lessons matter,
summarize them as generic findings in design docs without naming the project or
including paths, business details, or workstream ledgers.

## Current Policy

- Do not delete existing private benchmark evidence during active development.
- Do not add more private project details to public-facing docs.
- New reusable benchmark work should target a public sample app, not the private
  project.
- Keep `run-real-case.sh` generic. It can run both public and private repos; the
  difference is where reports are stored and whether they are publishable.

## Next Implementation Step

Create a public sample app only when the orchestrator lifecycle is stable enough
that the benchmark will test product behavior instead of constantly chasing
framework churn.

The first public benchmark should be modest but real:

1. Create or choose a public sample app.
2. Add one public benchmark case with clear acceptance criteria.
3. Run it with `run-real-case.sh`.
4. Commit the sanitized benchmark report and index.
5. Keep private benchmark reports out of the public release branch.
