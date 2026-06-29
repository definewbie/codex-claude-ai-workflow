#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR_PATH="$PROJECT_ROOT/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar"
CASE_NAME="${1:-all}"
SKIP_BUILD="${SKIP_BUILD:-false}"
KEEP="${KEEP:-false}"

pass() { printf 'PASS %s\n' "$*"; }
fail() { printf 'FAIL %s\n' "$*" >&2; exit 1; }
log() { printf '[SEMANTIC] %s\n' "$*"; }

usage() {
  cat <<'EOF'
Usage:
  scripts/semantic-boundary-smoke.sh [all|plan-body|review-noise|reread-reason]

This smoke uses a controlled fake acpx but drives the real dualctl/worktree/ledger
path. It validates semantic boundary failures:
  plan-body     plan.claude_plan missing formal plan body is rejected
  review-noise  prose "minor stylistic issue" does not classify as REVIEW_NOISE
  reread-reason reusable file re-read outside the computed reread policy is rejected
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

if [[ "$SKIP_BUILD" != "true" ]]; then
  log "Building dual-cli jar"
  (cd "$PROJECT_ROOT" && mvn -q -pl dual-cli -am package -DskipTests)
fi
[[ -f "$JAR_PATH" ]] || fail "dual-cli jar missing: $JAR_PATH"

dualctl() {
  java -jar "$JAR_PATH" "$@"
}

make_fake_acpx() {
  local bin_dir="$1"
  mkdir -p "$bin_dir"
  cat > "$bin_dir/claude" <<'FAKE_CLAUDE'
#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "--version" ]]; then
  echo "semantic-boundary-fake-claude 1.0"
  exit 0
fi

case_name="${DUAL_SEMANTIC_CASE:-}"

tool_read_readme() {
  cat <<'EOF'
[tool] Read README.md (completed)
  input: {"file_path":"README.md"}
EOF
}

tool_read_notes() {
  cat <<'EOF'
[tool] Read NOTES.md (completed)
  input: {"file_path":"NOTES.md"}
EOF
}

task_contract_proposal() {
  cat <<'EOF'
<dual-artifact type="task_contract_proposal" version="1">
{
  "artifact_type": "task_contract_proposal",
  "schema_version": 1,
  "objective": "Add goodbye() and document it.",
  "scope": {
    "included": ["main.py", "README.md"],
    "excluded": ["Do not change __main__ output"]
  },
  "acceptance_criteria": [
    "main.py defines goodbye() returning goodbye",
    "README.md documents goodbye()"
  ],
  "constraints": ["Keep changes scoped"],
  "non_goals": ["No dependencies"],
  "risk_areas": ["Missing docs"],
  "test_requirements": ["Import main and check goodbye"],
  "evidence_refs": ["main.py", "README.md"],
  "open_questions": []
}
</dual-artifact>

```dual-control
decision: READY_FOR_PRODUCT_REVIEW
summary: Requirement is clear.
```
EOF
}

task_contract_proposal
FAKE_CLAUDE
  chmod +x "$bin_dir/claude"

  cat > "$bin_dir/acpx" <<'FAKE'
#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "--version" ]]; then
  echo "semantic-boundary-fake-acpx 1.0"
  exit 0
fi

agent=""
prompt=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    claude|codex) agent="$1"; shift ;;
    -f) shift; prompt="${1:-}"; shift ;;
    *) shift ;;
  esac
done

template="$(basename "${prompt:-unknown.md}")"
case_name="${DUAL_SEMANTIC_CASE:-}"

tool_read_readme() {
  cat <<'EOF'
[tool] Read README.md (completed)
  input: {"file_path":"README.md"}
EOF
}

tool_read_notes() {
  cat <<'EOF'
[tool] Read NOTES.md (completed)
  input: {"file_path":"NOTES.md"}
EOF
}

agent_contract() {
  local decision="$1"
  local summary="$2"
  local category_hint="${3:-}"
  cat <<EOF
<dual-artifact type="agent_output_contract" version="1">
{
  "artifact_type": "agent_output_contract",
  "schema_version": 1,
  "decision": "${decision}",
  "summary": "${summary}",
  "blocking": false,
  "checks": ["controlled semantic smoke"],
  "evidence": ["controlled output"],
  "missing_evidence": [],
  "must_fix": [],
  "open_risks": [],
  "verification_summary": "controlled semantic smoke",
  "reread_reasons": [],
  "failure": {
    "category_hint": "${category_hint}",
    "contract_gaps": [],
    "out_of_scope_files": [],
    "missing_tests": [],
    "context_gaps": [],
    "reason": ""
  }
}
</dual-artifact>

\`\`\`dual-control
decision: ${decision}
summary: ${summary}
\`\`\`
EOF
}

task_contract_proposal() {
  cat <<'EOF'
<dual-artifact type="task_contract_proposal" version="1">
{
  "artifact_type": "task_contract_proposal",
  "schema_version": 1,
  "objective": "Add goodbye() and document it.",
  "scope": {
    "included": ["main.py", "README.md"],
    "excluded": ["Do not change __main__ output"]
  },
  "acceptance_criteria": [
    "main.py defines goodbye() returning goodbye",
    "README.md documents goodbye()"
  ],
  "constraints": ["Keep changes scoped"],
  "non_goals": ["No dependencies"],
  "risk_areas": ["Missing docs"],
  "test_requirements": ["Import main and check goodbye"],
  "evidence_refs": ["main.py", "README.md"],
  "open_questions": []
}
</dual-artifact>

```dual-control
decision: READY_FOR_PRODUCT_REVIEW
summary: Requirement is clear.
```
EOF
}

valid_plan() {
  cat <<'EOF'
# Plan

## Implementation Steps

1. Update main.py with goodbye().

## File-Level Changes

- main.py
- README.md

## Validation Plan

- Import main and assert goodbye().

## Rollback Notes

- Revert main.py and README.md.

## Known Risks

- Missing documentation.

## Human Review Checklist

- Confirm function and docs.

<dual-artifact type="plan_artifact" version="1">
{
  "artifact_type": "plan_artifact",
  "schema_version": 1,
  "implementation_steps": ["Update main.py with goodbye().", "Document goodbye() in README.md."],
  "edit_files": ["main.py", "README.md"],
  "reference_files": [],
  "file_level_changes": ["main.py", "README.md"],
  "validation_plan": ["Import main and assert goodbye()."],
  "rollback_notes": "Revert main.py and README.md.",
  "known_risks": ["Missing documentation."],
  "human_review_checklist": ["Confirm function and docs."]
}
</dual-artifact>

EOF
  agent_contract "READY_FOR_PLAN_REVIEW" "Plan body is complete."
}

case "$template" in
  intake-claude-brainstorm.md)
    tool_read_readme
    if [[ "$case_name" == "reread-reason" ]]; then
      tool_read_notes
    fi
    task_contract_proposal
    ;;
  intake-codex-product-review.md)
    agent_contract "READY_FOR_PRODUCT_REVIEW" "Product review passed."
    ;;
  design-claude.md)
    if [[ "$case_name" == "reread-reason" ]]; then
      tool_read_notes
    fi
    agent_contract "READY_FOR_ARCH_REVIEW" "Design passed."
    ;;
  design-codex-review.md)
    agent_contract "READY_FOR_ARCH_REVIEW" "Architecture review passed."
    ;;
  plan-claude.md)
    if [[ "$case_name" == "plan-body" ]]; then
      agent_contract "READY_FOR_PLAN_REVIEW" "Plan only exists in transcript prose, not formal artifact body."
    else
      valid_plan
    fi
    ;;
  plan-codex-review.md)
    agent_contract "READY_TO_IMPLEMENT" "Plan review passed."
    ;;
  implement-claude.md)
    if [[ "$case_name" == "reread-reason" ]]; then
      tool_read_notes
    fi
    if [[ -f main.py ]] && ! grep -q "def goodbye" main.py; then
      cat >> main.py <<'PY'

def goodbye():
    return "goodbye"
PY
    fi
    if [[ -f README.md ]] && ! grep -q "goodbye" README.md; then
      cat >> README.md <<'README'

## goodbye

`goodbye()` returns `goodbye`.
README
    fi
    agent_contract "IMPLEMENTED" "Implemented goodbye without declaring reread reasons."
    ;;
  implement-claude-self-test.md)
    cat <<'EOF'
## Verification Summary

Self-test passed.

## Checks Run

- Import main and assert goodbye.

## Checks Skipped

- None.

## Evidence

- Controlled smoke.

## Repository Hygiene

- Clean.

## Missing Evidence

- None.

## Residual Risk

- Controlled smoke only.

<dual-artifact type="self_test_report" version="1">
{
  "artifact_type": "self_test_report",
  "schema_version": 1,
  "decision": "SELF_TEST_COMPLETE",
  "summary": "Self-test passed.",
  "checks_run": ["controlled self-test"],
  "checks_skipped": [],
  "evidence": ["controlled smoke"],
  "missing_evidence": [],
  "verification_summary": "controlled smoke"
}
</dual-artifact>

```dual-control
decision: SELF_TEST_COMPLETE
summary: Self-test passed.
```
EOF
    ;;
  acceptance-codex-review.md)
    if [[ "$case_name" == "review-noise" ]]; then
      cat <<'EOF'
<dual-artifact type="acceptance_review" version="1">
{
  "artifact_type": "acceptance_review",
  "schema_version": 1,
  "decision": "REJECT",
  "summary": "minor stylistic issue only; should not block",
  "blocking": true,
  "checks": ["controlled acceptance"],
  "evidence": ["controlled acceptance"],
  "missing_evidence": [],
  "must_fix": [],
  "open_risks": [],
  "changed_files": ["main.py", "README.md"],
  "verification_summary": "minor stylistic issue only; should not block",
  "failure": {
    "category_hint": "",
    "contract_gaps": [],
    "out_of_scope_files": [],
    "missing_tests": [],
    "context_gaps": [],
    "reason": ""
  }
}
</dual-artifact>

```dual-control
decision: REJECT
summary: minor stylistic issue only; should not block
```
EOF
    else
      cat <<'EOF'
<dual-artifact type="acceptance_review" version="1">
{
  "artifact_type": "acceptance_review",
  "schema_version": 1,
  "decision": "ACCEPT",
  "summary": "Accepted.",
  "blocking": false,
  "checks": ["controlled acceptance"],
  "evidence": ["controlled acceptance"],
  "missing_evidence": [],
  "must_fix": [],
  "open_risks": [],
  "changed_files": ["main.py", "README.md"],
  "verification_summary": "Accepted.",
  "failure": {
    "category_hint": "",
    "contract_gaps": [],
    "out_of_scope_files": [],
    "missing_tests": [],
    "context_gaps": [],
    "reason": ""
  }
}
</dual-artifact>

```dual-control
decision: ACCEPT
summary: Accepted.
```
EOF
    fi
    ;;
  stage-claude-handoff.md)
    cat <<'EOF'
<dual-artifact type="stage_handoff" version="1">
{
  "artifact_type": "stage_handoff",
  "schema_version": 1,
  "decision": "HANDOFF_READY",
  "summary": "Handoff ready.",
  "completed_work": ["goodbye"],
  "important_decisions": [],
  "changed_files": ["main.py", "README.md"],
  "verification_state": "accepted",
  "open_risks": [],
  "recommended_next_stage": "none",
  "suggested_next_prompt": "none"
}
</dual-artifact>

```dual-control
decision: HANDOFF_READY
summary: Handoff ready.
```
EOF
    ;;
  *)
    agent_contract "PASS" "Default controlled response."
    ;;
esac
FAKE
  chmod +x "$bin_dir/acpx"
}

setup_repo() {
  local repo="$1"
  rm -rf "$repo" "${repo}-agent-runs"
  mkdir -p "$repo"
  (
    cd "$repo"
    git init -q
    git config user.email "semantic@test.local"
    git config user.name "Semantic Smoke"
    cat > README.md <<'EOF'
# Semantic Fixture
EOF
    cat > NOTES.md <<'EOF'
# Internal Notes

This file is intentionally outside the implementation plan.
EOF
    cat > main.py <<'EOF'
def hello():
    return "world"

if __name__ == "__main__":
    print(hello())
EOF
    git add .
    git commit -q -m "initial"
  )
}

run_until_boundary() {
  local repo="$1"
  local case_name="$2"
  local fake_bin="$3"
  local task="$repo/task.md"
  echo "Add goodbye() returning goodbye and document it." > "$task"
  local output
  output=$(PATH="$fake_bin:$PATH" DUAL_SEMANTIC_CASE="$case_name" dualctl start --run --max-steps 20 --workdir "$repo" --workflow-variant full --task-file "$task" 2>&1 || true)
  local ws workdir
  ws=$(printf '%s\n' "$output" | awk '/^Workstream:/ {print $2; exit}')
  workdir=$(printf '%s\n' "$output" | awk '/^Workspace:/ {print $2; exit}')
  [[ -n "$ws" && -n "$workdir" ]] || { printf '%s\n' "$output"; fail "$case_name: could not start workstream"; }

  local round status approve
  for round in 1 2 3 4 5 6 7 8; do
    status=$(dualctl status --workdir "$workdir" "$ws" 2>&1 || true)
    if printf '%s\n' "$status" | grep -q "WAITING_FOR_RECOVERY\|FAILED\|COMPLETED"; then
      break
    fi
    if printf '%s\n' "$status" | grep -q "WAITING_FOR_HUMAN"; then
      approve=$(PATH="$fake_bin:$PATH" DUAL_SEMANTIC_CASE="$case_name" dualctl review approve --run --max-steps 20 --workdir "$workdir" "$ws" 2>&1 || true)
      if printf '%s\n' "$approve" | grep -q "WAITING_FOR_RECOVERY\|FAILED\|COMPLETED"; then
        break
      fi
    else
      approve=$(PATH="$fake_bin:$PATH" DUAL_SEMANTIC_CASE="$case_name" dualctl run --workdir "$workdir" "$ws" 2>&1 || true)
    fi
  done
  printf '%s\t%s\n' "$workdir" "$ws"
}

assert_plan_body() {
  local workdir="$1" ws="$2"
  dualctl recovery show --workdir "$workdir" "$ws" > "$workdir/recovery-show.txt" 2>&1 || true
  grep -R "PLAN_ARTIFACT_MISSING" "$workdir/.agent/workstreams/$ws" >/dev/null \
    || fail "plan-body: expected PLAN_ARTIFACT_MISSING"
  pass "plan-body: missing typed plan_artifact is blocked"
}

assert_review_noise() {
  local workdir="$1" ws="$2"
  local class_json="$workdir/.agent/workstreams/$ws/artifacts/failure-classification.json"
  [[ -f "$class_json" ]] || fail "review-noise: expected failure-classification.json"
  local category
  category=$(python3 -c "import json; print(json.load(open('$class_json')).get('category',''))")
  [[ "$category" != "REVIEW_NOISE" ]] || fail "review-noise: summary text incorrectly classified as REVIEW_NOISE"
  [[ "$category" == "IMPLEMENTATION_BUG" ]] || fail "review-noise: expected IMPLEMENTATION_BUG fallback, got $category"
  pass "review-noise: prose summary did not trigger REVIEW_NOISE"
}

assert_reread_reason() {
  local workdir="$1" ws="$2"
  grep -R "REREAD_POLICY_DENIED" "$workdir/.agent/workstreams/$ws" >/dev/null \
    || fail "reread-reason: expected REREAD_POLICY_DENIED"
  pass "reread-reason: reusable re-read outside computed policy is blocked"
}

run_case() {
  local case_name="$1"
  local root="/tmp/dual-semantic-${case_name}-$$"
  local repo="$root/repo"
  local fake_bin="$root/bin"
  setup_repo "$repo"
  make_fake_acpx "$fake_bin"
  log "Running $case_name"
  local result workdir ws
  result=$(run_until_boundary "$repo" "$case_name" "$fake_bin")
  workdir="${result%%$'\t'*}"
  ws="${result##*$'\t'}"
  case "$case_name" in
    plan-body) assert_plan_body "$workdir" "$ws" ;;
    review-noise) assert_review_noise "$workdir" "$ws" ;;
    reread-reason) assert_reread_reason "$workdir" "$ws" ;;
    *) fail "unknown semantic case: $case_name" ;;
  esac
  if [[ "$KEEP" != "true" ]]; then
    rm -rf "$root" "${repo}-agent-runs"
  else
    log "Kept $root"
  fi
}

case "$CASE_NAME" in
  all)
    run_case plan-body
    run_case review-noise
    run_case reread-reason
    ;;
  plan-body|review-noise|reread-reason)
    run_case "$CASE_NAME"
    ;;
  *)
    usage
    fail "unknown case: $CASE_NAME"
    ;;
esac

pass "semantic boundary smoke complete"
