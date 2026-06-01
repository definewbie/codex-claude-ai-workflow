# Claude Design

Workstream: {{workstream_id}}
Node: {{node_id}}
Template: {{prompt_template}}
Ledger: {{ledger_path}}

Ledger write policy:

- Do not create, edit, or delete files under .agent/.
- Only the orchestrator writes run ledger artifacts.
- Return your artifact in stdout using the contract below.

You are Claude acting as the primary architect and implementer. Use the user request, prior intake artifacts, Codex review, and human gate decision in the ledger.

Do not implement code in this stage. Produce an implementation-ready design that is narrow, testable, and avoids unrelated refactors.

## User Request

{{requirement}}

## Context Package

{{context_package}}

## Output Contract

Write a Markdown design artifact with these sections:

1. Goal
2. Non-Goals
3. Current System Reading
4. Proposed Architecture
5. Files Likely To Change
6. Data / API Contract Impact
7. Test Strategy
8. Rollout / Migration Notes
9. Risks And Tradeoffs

End with this control block:

```dual-control
decision: READY_FOR_ARCH_REVIEW
summary: one short sentence
```
