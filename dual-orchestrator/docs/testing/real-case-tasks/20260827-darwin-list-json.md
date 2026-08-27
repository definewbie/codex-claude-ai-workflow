# Darwin CLI: Machine-Readable Run Listing

## Goal

Add an opt-in JSON output mode to `darwin list` so scripts can consume the run inventory without parsing the existing human-readable table.

## Requirements

1. `python3 -m darwin.cli --output-dir <dir> list --json` must write exactly one valid JSON array to stdout.
2. Every successfully loaded run must be represented by one object containing exactly these fields:
   - `run_id`
   - `asset_id`
   - `status`
   - `round_count`
3. Array order must match the existing `ManifestStore.list_runs()` order.
4. When there are no runs, JSON mode must emit `[]`.
5. If an entry cannot be loaded, JSON mode must preserve its visibility with an object containing exactly:
   - `run_id`
   - `error`
   It must continue listing the remaining entries.
6. JSON mode must not create or modify run artifacts.
7. Existing `darwin list` output and behavior without `--json` must remain unchanged.
8. Add focused CLI tests for populated, empty, malformed-entry, ordering, no-write, and unchanged default-output behavior.
9. Update `README.md` with one concise JSON-mode usage example and its output contract.

## Constraints

- Keep the change within the existing CLI and storage model.
- Do not add dependencies, a new persistence format, a new command, or a general output framework.
- Do not change `RunManifest` serialization or `ManifestStore.list_runs()` semantics.
- Do not alter `eval`, `run`, or `report` behavior.

## Verification

Run all repository tests except the three pre-existing collection-blocked modules listed below, and require every runnable test plus the new focused tests to pass:

- `tests/test_e2e_phase1.py`
- `tests/test_e2e_phase2.py`
- `tests/test_evaluator.py`

Those three modules depend on a frozen external asset path under `/Users/krisdwang/.hermes/skills/` that is absent from the target baseline. A full unfiltered collection must still produce only the same three `FileNotFoundError` signatures; no new collection error may be introduced. Do not repair or suppress this pre-existing portability issue as part of this task.

Independently parse captured JSON stdout and verify the exact object keys, list ordering, empty-list behavior, malformed-entry representation, and absence of filesystem changes.
