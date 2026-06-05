# Cleanup Todo Before Public Release

This file tracks cleanup work that should happen after the project stabilizes.
Do not clean these files opportunistically during feature development. The goal
is to keep current development evidence available while preventing private
project details from leaking into the public release branch.

## Principle

- Keep development evidence until the main lifecycle is stable.
- Do cleanup on a dedicated branch, not mixed into feature branches.
- Prefer archiving through a git tag before removing or rewriting files.
- Do not publish private project names, local filesystem paths, domain-specific
  product details, or real workstream ledgers.

## Suggested Cleanup Branch

```bash
git tag archive/pre-opensource-cleanup-20260605
git switch -c codex/open-source-doc-cleanup
```

The tag preserves the full development history before any public-facing cleanup.

## Private Benchmark Material To Archive Or Remove

These files are useful for development history but should not remain in the
public-facing tree as-is:

- `dual-orchestrator/docs/testing/real-case-benchmarks/cases/cancer-clinic-*.md`
- `dual-orchestrator/docs/testing/real-case-benchmarks/reports/*cancer-clinic*.md`
- `dual-orchestrator/docs/testing/real-case-benchmarks/benchmark-index.md`
- `dual-orchestrator/docs/testing/real-case-reports/**`

Expected cleanup:

- Move private reports to a non-public archive branch or rely on the archive tag.
- Replace the public benchmark index with runs from public benchmark cases only.
- Keep the benchmark mechanism, but remove private case content.

## Development Handoff Material To Archive Or Remove

These files record useful development context, but are too process-heavy for a
public README path:

- `dual-orchestrator/docs/handoff-2026-05-27.md`
- `dual-orchestrator/docs/handoff-2026-05-28.md`
- `dual-orchestrator/docs/handoff-2026-05-28-v2.md`
- `dual-orchestrator/docs/handoff-2026-05-28-v3.md`
- `dual-orchestrator/docs/handoff-2026-05-28-v4.md`
- `dual-orchestrator/docs/handoff-2026-05-28-v5.md`
- `dual-orchestrator/docs/handoff-2026-05-29.md`

Expected cleanup:

- Archive them outside the public documentation tree.
- Keep only durable architecture and operation docs.

## Documents That Need Redaction Or Rewording

These documents are valuable, but currently include private case names, local
paths, or development-stage claims:

- `dual-agent-workflow-article.md`
- `dual-orchestrator/docs/architecture-and-execution-flow.md`
- `dual-orchestrator/docs/features.md`
- `dual-orchestrator/docs/mvp-mainline-validation.md`
- `dual-orchestrator/docs/quickstart.md`
- `dual-orchestrator/docs/reference.md`
- `dual-orchestrator/docs/design/2026-06-03-artifact-driven-contract-negotiation.md`
- `dual-orchestrator/docs/design/workflow-state-machine.md`
- `dual-orchestrator/docs/design/P5-task-contract-and-contract-compiler.md`
- `dual-orchestrator/docs/long-term-plan/*.md`

Expected cleanup:

- Replace private repository names with neutral terms such as `private-real-world-app`.
- Remove local paths like `/Users/...`.
- Keep lessons learned, but avoid exposing private product requirements.
- Make sure public claims are backed by public smoke or benchmark evidence.

## Legacy Prototype Decision

`dual-agent-runner/` is the original Bash prototype. Before public release,
decide whether to:

1. Keep it and clearly label it as `legacy prototype`.
2. Move it under an archive directory.
3. Remove it from the public branch and rely on git history.

Recommendation: keep the Java orchestrator as the main public entry point. If
the Bash prototype stays, the README must make its status unambiguous.

## Final Cleanup Checks

Run these scans before publishing:

```bash
rg "cancer-clinic|cancer_clinic|/Users/kriswang|miniprogram|治疗|肿瘤|化疗"
rg "private/tmp|Desktop|work/projects"
```

The public branch should either have no matches, or matches only inside this
cleanup checklist with intentional examples.
