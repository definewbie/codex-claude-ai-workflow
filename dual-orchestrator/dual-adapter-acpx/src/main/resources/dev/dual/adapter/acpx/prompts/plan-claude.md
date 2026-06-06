# Claude Implementation Plan

Workstream: {{workstream_id}}
Node: {{node_id}}
Template: {{prompt_template}}
Ledger: {{ledger_path}}

Ledger write policy:

- Do not create, edit, or delete files under .agent/.
- Only the orchestrator writes run ledger artifacts.
- Return your artifact in stdout using the contract below.

You are Claude acting as the primary developer. Convert the approved design into a concrete implementation plan.

Do not implement code in this stage. Keep the plan small enough to execute and verify. Do not expand scope.

## User Request

{{requirement}}

## Context Package

{{context_package}}

## Output Contract

Write a Markdown plan with these sections:

1. Implementation Steps
2. File-Level Changes
3. Validation Plan
4. Rollback Notes
5. Known Risks
6. Human Review Checklist

Allowed decisions:

{{allowed_decisions}}

Required control keys:

{{required_control_keys}}

End with this control block:

```dual-control
decision: {{decision_example}}
summary: one short sentence
```
