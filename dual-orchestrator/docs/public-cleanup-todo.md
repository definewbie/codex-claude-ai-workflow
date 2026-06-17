# Public Cleanup TODO

> Status: public cleanup branch checklist
> Last updated: 2026-06-08

This checklist tracks what was cleaned on the public release branch and what
still needs a final pass before publishing. It intentionally avoids forcing the
main development branch to lose internal evidence.

## Goal

Make the repository readable for open-source users without losing the internal
evidence that helped harden the orchestrator.

The public branch should answer three questions quickly:

1. What problem does the project solve?
2. How do I install it and run the first workflow?
3. Where do I look when a workstream pauses, fails, or completes?

## Keep In Public Path

These files are safe and useful as public entry points:

| Path | Reason |
|---|---|
| `README.md` | Main product entry. |
| `docs/README.md` | Documentation map. |
| `docs/quickstart.md` | First-run path. |
| `docs/whitepaper-mapping.md` | Explains why the project exists. |
| `docs/architecture-and-execution-flow.md` | Overall architecture and lifecycle narrative. |
| `docs/design/workflow-state-machine.md` | State model and operator commands. |
| `docs/features.md` | Current MVP status and limits. |
| `docs/testing/public-benchmark-plan.md` | Public benchmark strategy. |
| `docs/testing/real-case-benchmarks/cases/public-*.md` | Public reusable benchmark cases. |
| `examples/issue-flow-app/` | Public sample app for benchmark runs. |
| `scripts/run-public-benchmark.sh` | One-command public real-case runner. |
| `scripts/smoke-test.sh` | Local smoke validation. |

## Cleaned In This Branch

These tracked files were removed from the public cleanup branch:

| Path | Suggested action |
|---|---|
| `docs/handoff-*.md` | Removed from public branch; keep in internal history/branches if needed. |
| `docs/testing/real-case-benchmarks/cases/*private*.md` | Removed private benchmark cases; public benchmark uses `public-*.md`. |
| `docs/testing/real-case-benchmarks/reports/*private*.md` | Removed private benchmark reports; public evidence is summarized generically. |

`docs/testing/real-case-reports/` is ignored generated output. Local runs may
create reports there, but they should not be committed.

## Review Before Public Release

These files are useful internally but should be reviewed before publishing:

| Path | Suggested action |
|---|---|
| `docs/superpowers/plans/*.md` | Keep only if still useful as design history; otherwise archive. |
| `docs/superpowers/specs/*.md` | Keep current specs only; archive superseded ones. |

## Sanitize If Kept

If any internal evidence remains in public docs, remove or generalize:

- Absolute local paths such as `/Users/...`.
- Private repository names.
- Domain-specific product details that are not needed to explain the
  orchestrator.
- Workstream IDs that point to local ledgers.
- Raw agent logs or prompts that may contain private context.

Prefer generic phrasing such as "a private production-like repository" when the
exact project name does not matter.

## Do Not Do Yet

These are tempting but not worth doing before the public MVP shape is stable:

- Do not rewrite all historical design docs.
- Do not migrate old ledger schemas.
- Do not create a complex docs site.
- Do not build a large benchmark suite before the public sample path is stable.
- Do not delete internal evidence from the development branch merely to make it
  look tidy.

## Release Branch Checklist

Before cutting a public release branch:

1. Run `rtk git grep "private-repo-name|/Users/|local-user-name" -- dual-orchestrator/README.md dual-orchestrator/docs`.
2. Move or sanitize the matches that are not explicitly marked as internal.
3. Run `./scripts/smoke-test.sh --case lifecycle --cleanup`.
4. Run `./scripts/run-public-benchmark.sh` if real `acpx` quota is available.
5. Verify `dualctl trace` can explain the final public benchmark workstream.
6. Re-read `README.md`, `docs/README.md`, and `docs/quickstart.md` as a new user.
