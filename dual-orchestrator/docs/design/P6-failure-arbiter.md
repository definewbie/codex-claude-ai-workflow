# P6: FailureArbiter — Semantic Failure Classification

> Status: **In Progress** | Branch: `dev-p6-failure-arbiter`

## Problem

P5 TaskContract provides structured contract artifacts and Codex review optional keys (`contract_gaps`, `out_of_scope_files`, `missing_tests`). However, when acceptance review produces REJECT or ACCEPT_WITH_NOTES, the workflow has no mechanism to classify *why* the failure occurred. This leads to blind re-implementation regardless of root cause.

## Solution

Build a deterministic FailureArbiter that classifies review failures into 6 semantic categories and routes the workflow to category-specific recovery paths.

## Classification Categories (priority order)

| Priority | Category | Trigger |
|----------|----------|---------|
| 1 | CONTRACT_GAP | `contract_gaps` non-empty |
| 2 | CONTRACT_AMBIGUITY | ambiguityNotes non-empty OR scope.included empty OR acceptanceCriteria < 2 |
| 3 | IMPLEMENTATION_BUG | `out_of_scope_files` non-empty |
| 4 | CONTEXT_INSUFFICIENT | Review summary contains context-lack keywords |
| 5 | TOOL_LIMITATION | Git evidence shows 0 changed files |
| 6 | REVIEW_NOISE | All optional fields empty AND no ambiguity indicators |
| 7 (fallback) | IMPLEMENTATION_BUG | Default |

## Routing

- REJECT produces `REJECT_{CATEGORY}` compound outcomes → category-specific recovery
- ACCEPT_WITH_NOTES produces `NOTED_{CATEGORY}` → report (data preserved for P7 calibration)
- REJECT_REVIEW_NOISE → `context.acceptance_codex_verifier_review` (auto re-review)

## Components

| Component | Module | Description |
|-----------|--------|-------------|
| FailureCategory | dual-core | 6-value enum |
| FailureClassification | dual-core | record(category, reasoning, suggestedAction, source, classifiedAt) |
| FailureArbiter | dual-runtime | Deterministic rule-based classifier |
| FailureClassifyHandler | dual-runtime | TaskHandler (type: acceptance.failure_classify) |
| p6Mainline() | dual-runtime | Workflow with classify node + 12 recovery edges |

## Decisions

1. ACCEPT_WITH_NOTES also classified → NOTED_* → report (calibration data for P7)
2. REJECT_REVIEW_NOISE → auto re-review (not re-implementation)
3. TOOL_LIMITATION evaluated before REVIEW_NOISE (objective fact before subjective judgment)

## Out of Scope

- CalibrationCandidate / pattern detection (P7)
- Multi-role review / adversarial test agent (P8)
- Claude-based classification (MVP is deterministic)
- Web UI / metrics
