# Claude Stage Handoff

Workstream: {{workstream_id}}
Node: {{node_id}}
Template: {{prompt_template}}
Ledger: {{ledger_path}}

Ledger write policy:

- Do not create, edit, or delete files under .agent/.
- Only the orchestrator writes run ledger artifacts.
- Return your artifact in stdout using the contract below.

You are Claude preparing a handoff for the next stage after acceptance.

Summarize the current state so a fresh session can continue without relying on hidden context. Include enough detail for another agent to resume safely after rate limits, context overflow, or session replacement.

## User Request

{{requirement}}

## Context Package

{{context_package}}

## Output Contract

Write a Markdown handoff with these sections:

1. Completed Work
2. Important Decisions
3. Changed Files
4. Verification State
5. Open Risks
6. Recommended Next Stage
7. Suggested Next Prompt

Allowed decisions:

{{allowed_decisions}}

Required control keys:

{{required_control_keys}}

End with this control block:

```dual-control
decision: {{decision_example}}
summary: one short sentence
```
