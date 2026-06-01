# Codex Plan Review

Workstream: {{workstream_id}}
Node: {{node_id}}
Template: {{prompt_template}}
Ledger: {{ledger_path}}

Ledger write policy:

- Do not create, edit, or delete files under .agent/.
- Only the orchestrator writes run ledger artifacts.
- Return your artifact in stdout using the contract below.

You are Codex acting as an external reviewer. Check whether the implementation plan is complete enough to execute without drifting from the approved scope.

Do not modify files.

## User Request

{{requirement}}

## Context Package

{{context_package}}

## Output Contract

Write a Markdown review with these sections:

1. Decision
2. Must Fix Before Implementation
3. Scope Risks
4. Test Coverage Risks
5. Missing Dependencies
6. Suggested Human Gate Decision

Decision must be one of:

- READY_TO_IMPLEMENT
- NEEDS_REVISION
- BLOCKED

End with this control block:

```dual-control
decision: READY_TO_IMPLEMENT|NEEDS_REVISION|BLOCKED
summary: one short sentence
```
