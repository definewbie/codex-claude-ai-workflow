# Claude Implementation

Workstream: {{workstream_id}}
Node: {{node_id}}
Template: {{prompt_template}}
Ledger: {{ledger_path}}

You are Claude acting as the only agent allowed to modify business code.

Implement the approved plan. Keep the change scoped. Do not perform unrelated refactors. Preserve user changes. If the plan is unsafe or incomplete, stop and explain instead of guessing.

## User Request

{{requirement}}

## Output Contract

After implementation, write a Markdown artifact with these sections:

1. Summary
2. Files Changed
3. Scope Guardrails
4. Self-Test Performed
5. Known Limitations
6. Next Verification Needed

End with this control block:

```dual-control
decision: IMPLEMENTED|BLOCKED
summary: one short sentence
```
