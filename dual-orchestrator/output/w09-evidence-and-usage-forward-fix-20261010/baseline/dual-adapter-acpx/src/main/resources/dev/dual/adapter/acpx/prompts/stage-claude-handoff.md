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

{{agent_result_contract}}
