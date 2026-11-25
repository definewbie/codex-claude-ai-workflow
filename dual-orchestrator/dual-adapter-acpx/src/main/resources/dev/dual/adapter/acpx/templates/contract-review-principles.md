# Contract Review Principles

- Intent fidelity: the proposal must preserve the user's original intent.
- Explicit-requirement coverage: every user-stated objective, scope boundary, behavior constraint, acceptance criterion, and verification requirement must appear in a binding proposal field; `risk_areas`, `open_questions`, and `evidence_refs` do not preserve requirements.
- Protocol ownership: harness output and routing protocols belong to the orchestrator's node Output Contract and must not be duplicated into the product task proposal.
- Semantic neutrality: the proposal must not broaden or narrow accepted input, output, scope, or observable behavior based on an unstated implementation preference or language-specific edge case.
- Typed scope authority: intake file entries are proposals, not authorization. GOAL approval fixes the objective, acceptance and exclusions, not an implementation file set. Design and plan review produce the candidate for later exact EXECUTION approval. Only the approved EXECUTION `scope.included` is the exhaustive authorization set for permitted repository changes; each entry names one exact repository-relative path and `CREATE` or `MODIFY`. It does not require every declared path to be changed. Acceptance criteria, constraints, risks, and other prose cannot authorize an undeclared path. Never request an illustrative or non-exhaustive EXECUTION scope. A scope gap must request concrete typed path-and-operation changes.
- Non-goal clarity: boundaries must prevent unrelated refactors and scope creep.
- Verifiability: acceptance criteria must be observable by self-test, reviewer inspection, or user acceptance.
- Evidence grounding: important claims should point to user request, repo files, or prior artifacts.
- Risk coverage: risk areas should expose product, architecture, user, and data-safety risks relevant to the task.
- Human boundary: ask for human input only for real product/risk decisions, not JSON formatting or process validation.
