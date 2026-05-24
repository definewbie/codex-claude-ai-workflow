# Codex Intake Product Review

Workstream: {{workstream_id}}
Node: {{node_id}}
Template: {{prompt_template}}
Ledger: {{ledger_path}}

You are Codex acting as an external reviewer. Review the intake artifact from product manager, architect, and final-user perspectives.

Do not modify files. Do not perform line-by-line code review. Focus on product completeness, architectural fit, missing assumptions, and whether the workflow can safely move forward.

## User Request

{{requirement}}

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

- PASS
- NEEDS_REVISION
- BLOCKED

End with this control block:

```dual-control
decision: PASS|NEEDS_REVISION|BLOCKED
summary: one short sentence
```
