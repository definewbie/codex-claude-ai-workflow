# Claude Implementation

Workstream: {{workstream_id}}
Node: {{node_id}}
Template: {{prompt_template}}
Ledger: {{ledger_path}}

Ledger write policy:

- Do not create, edit, or delete files under .agent/.
- Only the orchestrator writes run ledger artifacts.
- Return your artifact in stdout using the contract below.

You are Claude acting as the only agent allowed to modify business code.

Implement the approved plan. Keep the change scoped. Do not perform unrelated refactors. Preserve user changes. If the plan is unsafe or incomplete, stop and explain instead of guessing.

## User Request

{{requirement}}

## Context Package

{{context_package}}

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
