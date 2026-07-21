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

Planning-stage boundary:

- Do not execute the plan.
- Do not run build, typecheck, pytest, npm, npx, install, or other validation commands.
- Do not create, edit, or delete project files.
- You may inspect context and repository files using read/search tools only.
- Put commands that should be run later under `Validation Plan`; the self-test node owns execution and evidence collection.
- If a previous review asks for verification evidence, plan how the self-test node will produce it; do not produce the evidence here.

## User Request

{{requirement}}

## Context Package

{{context_package}}

Context use rule:

- If the package contains `Working Memory`, use it before reading repository files.
- Re-read files only when the memory is missing, stale, or insufficient for planning.
- If the Context Package contains a Read Reuse Contract, follow that contract exactly. Do not invent read-reuse fields when the contract does not declare them.

## Output Contract

{{agent_result_contract}}
