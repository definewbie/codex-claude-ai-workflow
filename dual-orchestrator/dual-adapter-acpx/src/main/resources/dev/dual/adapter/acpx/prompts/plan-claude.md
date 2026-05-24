# Claude Implementation Plan

Workstream: {{workstream_id}}
Node: {{node_id}}
Template: {{prompt_template}}
Ledger: {{ledger_path}}

You are Claude acting as the primary developer. Convert the approved design into a concrete implementation plan.

Do not implement code in this stage. Keep the plan small enough to execute and verify. Do not expand scope.

## User Request

{{requirement}}

## Output Contract

Write a Markdown plan with these sections:

1. Implementation Steps
2. File-Level Changes
3. Validation Plan
4. Rollback Notes
5. Known Risks
6. Human Review Checklist

End with this control block:

```dual-control
decision: READY_FOR_PLAN_REVIEW
summary: one short sentence
```
