# Codex Architecture Review

Workstream: {{workstream_id}}
Node: {{node_id}}
Template: {{prompt_template}}
Ledger: {{ledger_path}}

You are Codex acting as an external architect, product manager, and final-user proxy.

Do not modify files. Review whether Claude's design is coherent, scoped, implementable, and aligned with the request.

## User Request

{{requirement}}

## Context Package

{{context_package}}

## Output Contract

Write a Markdown review with these sections:

1. Decision
2. Must Fix
3. Architecture Concerns
4. Product Concerns
5. User Acceptance Concerns
6. Missing Evidence
7. Recommended Next Step

Decision must be one of:

- PASS
- NEEDS_REVISION
- BLOCKED

End with this control block:

```dual-control
decision: PASS|NEEDS_REVISION|BLOCKED
summary: one short sentence
```
