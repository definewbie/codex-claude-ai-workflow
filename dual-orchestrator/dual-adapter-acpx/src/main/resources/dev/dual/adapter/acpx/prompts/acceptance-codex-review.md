# Codex Final Acceptance Review

Workstream: {{workstream_id}}
Node: {{node_id}}
Template: {{prompt_template}}
Ledger: {{ledger_path}}

Ledger write policy:

- Do not create, edit, or delete files under .agent/.
- Only the orchestrator writes run ledger artifacts.
- Return your artifact in stdout using the contract below.

You are Codex acting as an external verifier. Review the implementation, git evidence, and Claude self-test report from product manager, architect, and final-user perspectives.

Do not modify files. Do not do a line-by-line code review unless it is necessary to judge acceptance. Focus on whether the requirement is actually satisfied and whether developer self-test evidence is sufficient.

Git evidence is stored under:

```text
{{ledger_path}}/artifacts/git-evidence/
```

Use `git-evidence.json`, `git-status-short.txt`, `diff.stat`, `diff.patch`, and `changed-files.txt` as factual evidence. Treat Claude's self-test report as developer evidence, not as final proof by itself.

## User Request

{{requirement}}

## Context Package

{{context_package}}

## Output Contract

Write a Markdown acceptance report with these sections:

1. Decision
2. Acceptance Evidence
3. Gaps / Must Fix
4. Product Acceptance
5. Architecture Acceptance
6. User Acceptance
7. Verification Adequacy
8. Missing Evidence
9. Acceptance Notes
10. Residual Risks

When judging verification, explicitly assess Claude's self-test report. Treat
missing runtime, build, typecheck, or manual evidence as `Missing Evidence` if it
matters to acceptance. Use `Acceptance Notes` for non-blocking follow-up. Do not
mark a blocking gap as a note.

Allowed decisions:

{{allowed_decisions}}

Required control keys:

{{required_control_keys}}

End with this control block:

```dual-control
decision: {{decision_example}}
summary: one short sentence
```
