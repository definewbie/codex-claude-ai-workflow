# Feature Status

> Last updated: 2026-06-09

This document summarizes the current public feature shape. It intentionally uses
product-oriented categories instead of internal implementation phase numbers.

## Lifecycle Engine

- [x] Built-in lifecycle variants: `baseline`, `contract`, and `full`.
- [x] Task / Decision / Gate workflow model.
- [x] Default Git worktree isolation.
- [x] File ledger under `.agent/workstreams/<workstream_id>/`.
- [x] Node-level execution guard to avoid duplicate Agent runs.
- [x] Bounded revision and repair loops.
- [x] Conditional edge routing.
- [x] Sequential fan-out execution for multiple active nodes.
- [ ] Cross-workstream dependency coordination.

## Agent Orchestration

- [x] `acpx` Claude and Codex task handler.
- [x] Claude as developer/architect; Codex as external reviewer/verifier.
- [x] Prompt templates for intake, design, plan, implementation, self-test,
  acceptance, and handoff.
- [x] Per-node output contract validation.
- [x] Prompt variable validation at render time.
- [x] Agent status protocol through terminal `dual-control` blocks.
- [ ] Multi-role reviewer workflows beyond Claude/Codex/Human.

## Contracts And Evidence

- [x] TaskContract proposal and canonical TaskContract artifacts.
- [x] Human contract-resolution artifact.
- [x] Strict structured artifact boundary: Markdown for humans, typed artifacts
  for machine facts.
- [x] Self-test evidence contract.
- [x] Codex acceptance evidence contract.
- [x] Stage handoff JSON and next-stage prompt.
- [x] Contract failure diagnostics under `artifacts/contract-failures/`.
- [x] Context package state/evidence boundary.
- [x] Token-aware context budgeting and adaptive compression.
- [ ] Adapter-provided token and cost metrics.

## Failure And Recovery

- [x] Runtime failure classification for recoverable external issues.
- [x] Semantic failure classification for verifier rejection and notes.
- [x] Repair routing back to contract, implementation, or verifier context.
- [x] `recovery/current-failure.md` and structured recovery assessment.
- [x] Calibration candidate generation.
- [x] Human decision flow for calibration candidates.
- [x] Explicit promotion of accepted contract-template candidates to
  project-level principles.
- [ ] Fully automated recovery scheduler for rate-limit windows.

## Operator Experience

- [x] `dualctl trace` one-screen overview.
- [x] `dualctl status`, `timeline`, `summary`, `runs`, `events`, and
  `recovery show`.
- [x] Human review packet JSON/Markdown.
- [x] Local API server for overview, status, timeline, review, retry, and
  scheduler tick.
- [x] Operator panel MVP with overview, attention items, lifecycle progress,
  review packet, recovery summary, metrics, and grouped timeline.
- [x] Polling watch mode.
- [ ] Real-time progress push through SSE or file-watch streaming.
- [ ] Notification hooks for gate/failure/completion events.

## Delivery

- [x] Final report generation.
- [x] Workstream summary report.
- [x] Delivery PR body and commit-message preparation.
- [x] Conservative `delivery commit` and `delivery create-pr` commands.
- [x] Original repo cleanliness checks in benchmark reports.
- [ ] Automatic merge is intentionally out of scope.

## Validation

- [x] Unit tests for workflow routing, ledger behavior, contract registry,
  context governance, control-block parsing, and CLI commands.
- [x] Controlled lifecycle smoke with fake Agent outputs.
- [x] Real `acpx` smoke cases for baseline, contract, and full lifecycles.
- [x] Public reusable benchmark based on `examples/issue-flow-app`.
- [x] Public benchmark report and summary generation.
- [x] Public benchmark index.
- [ ] Deterministic reject fixture for CI-only coverage of the verifier-reject
  branch. Current live runs keep this as technical debt because model rejection
  is intentionally non-deterministic.

## Distribution

- [x] Local install script for `dualctl` and `dual-server`.
- [x] Public quickstart.
- [x] Public benchmark command.
- [ ] Homebrew formula.
- [ ] Docker image.
- [ ] Native image or packaged desktop distribution.

## Current MVP Readiness

The main lifecycle is functionally closed: a bounded task can move through
intake, contract, review gates, implementation, self-test, Codex acceptance,
repair routing, final report, delivery artifacts, and handoff.

The remaining work before a broader open-source release is mostly product
polish around onboarding, operator clarity, public benchmark repeatability, and
packaging. The core runtime should continue to prioritize contract/evidence
correctness over decorative UI or broad integration surface.
