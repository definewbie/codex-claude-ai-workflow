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

Before the final `dual-control` block, include this exact structured artifact envelope. Fill it with enough state for a fresh session to continue safely. Use empty arrays when a list has no items. Keep `decision` consistent with the final control block.

<dual-artifact type="stage_handoff" version="1">
{
  "artifact_type": "stage_handoff",
  "schema_version": 1,
  "decision": "{{decision_example}}",
  "summary": "one short sentence",
  "completed_work": [],
  "important_decisions": [],
  "changed_files": [],
  "verification_state": "short verification state",
  "open_risks": [],
  "recommended_next_stage": "short next-stage recommendation",
  "suggested_next_prompt": "prompt text for the next stage"
}
</dual-artifact>

Allowed decisions:

{{allowed_decisions}}

Required control keys:

{{required_control_keys}}

End with this control block:

```dual-control
decision: {{decision_example}}
summary: one short sentence
```
