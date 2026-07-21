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

Evidence use rule:

- If the package contains `Review Evidence Brief`, review that first.
- Use git evidence and raw artifacts to spot-check high-risk claims or fill explicit gaps.
- If the brief is insufficient for acceptance, put concrete gaps in `missing_evidence` and choose the appropriate blocking status.

## Output Contract

{{agent_result_contract}}
