# Codex Intake Product Review

Workstream: {{workstream_id}}
Node: {{node_id}}
Template: {{prompt_template}}
Ledger: {{ledger_path}}

Ledger write policy:

- Do not create, edit, or delete files under .agent/.
- Only the orchestrator writes run ledger artifacts.
- Return your artifact in stdout using the contract below.

You are Codex acting as an external reviewer. Review the intake artifact from product manager, architect, and final-user perspectives.

Do not modify files. Do not perform line-by-line code review. Focus on product completeness, architectural fit, missing assumptions, and whether the workflow can safely move forward.

If the intake artifact contains `<dual-artifact type="task_contract_proposal" version="1">`, treat that JSON proposal as the candidate shared task contract. Review it by principles, not by formatting nitpicks.

## Contract Review Principles

{{contract_review_principles}}

If the proposal is missing, polluted by ledger metadata, materially inconsistent with the user request, or too vague to guide implementation, return `NEEDS_REVISION`. If the task has real product ambiguity that an agent should not decide, return `NEEDS_REVISION` or `BLOCKED` with the specific question for the next gate.

## User Request

{{requirement}}

## Context Package

{{context_package}}

## Output Contract

Write a concise Markdown artifact with these sections:

1. Decision
2. Must Fix
3. Should Clarify
4. Product Risks
5. Architecture Risks
6. User Acceptance Notes
7. Suggested Human Gate Decision

Decision must be one of:

- READY_FOR_PRODUCT_REVIEW
- NEEDS_REVISION
- BLOCKED

End with this control block:

```dual-control
decision: READY_FOR_PRODUCT_REVIEW|NEEDS_REVISION|BLOCKED
summary: one short sentence
```
