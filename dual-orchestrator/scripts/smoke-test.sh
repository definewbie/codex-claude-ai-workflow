#!/usr/bin/env bash
#
# smoke-test.sh — Full lifecycle smoke test for dual-orchestrator
#
# Usage:
#   ./scripts/smoke-test.sh lifecycle                     # Controlled full lifecycle smoke, no real acpx calls
#   ./scripts/smoke-test.sh                              # Default: baseline-basic case
#   ./scripts/smoke-test.sh --case lifecycle
#   ./scripts/smoke-test.sh --case contract-happy-path
#   ./scripts/smoke-test.sh --case full-reject-classify-path
#   ./scripts/smoke-test.sh --case all                   # Run all cases sequentially
#   ./scripts/smoke-test.sh --list                       # List available cases
#   ./scripts/smoke-test.sh --interactive                # Pause at each gate for review
#   ./scripts/smoke-test.sh --manual-gates               # Pause at each gate for external UI/API approval
#   ./scripts/smoke-test.sh --skip-build                 # Skip mvn package
#   ./scripts/smoke-test.sh --cleanup                    # Delete fixture repo after test
#   ./scripts/smoke-test.sh --with-github                # Enable GitHub remote repo creation
#
# Prerequisites:
#   - Java 17+, Maven
#   - acpx (Claude/Codex CLI) installed and authenticated
#   - gh CLI installed and authenticated (only needed with --with-github)
#

set -euo pipefail

# ─── Colors ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
RESET='\033[0m'

# ─── Config ──────────────────────────────────────────────────────────────────
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR_PATH="$PROJECT_ROOT/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar"
MAX_APPROVE_ROUNDS=10

INTERACTIVE=false
MANUAL_GATES=false
SKIP_BUILD=false
CLEANUP=false
WITH_GITHUB=false
CASE_NAME="baseline-basic"
OPERATOR_URL="${DUAL_OPERATOR_URL:-http://localhost:8080}"

# ─── Parse args ──────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --case)
      shift
      CASE_NAME="${1:-}"
      if [[ -z "$CASE_NAME" ]]; then
        echo -e "${RED}[FATAL] --case requires an argument${RESET}" >&2
        exit 1
      fi
      ;;
    lifecycle|baseline-basic|contract-happy-path|full-reject-classify-path|all)
      CASE_NAME="$1"
      ;;
    --list)
      echo "Available E2E cases:"
      echo "  lifecycle              Controlled baseline lifecycle happy path with fake acpx"
      echo "  baseline-basic           Baseline mainline, full lifecycle happy path"
      echo "  contract-happy-path      Contract variant with contract compilation and review"
      echo "  full-reject-classify-path Full variant, best-effort classify_failure coverage"
      echo "  all                      Run all cases sequentially"
      exit 0
      ;;
    --interactive) INTERACTIVE=true ;;
    --manual-gates) MANUAL_GATES=true ;;
    --skip-build)  SKIP_BUILD=true ;;
    --cleanup)     CLEANUP=true ;;
    --with-github) WITH_GITHUB=true ;;
    --help|-h)
      head -20 "$0" | tail -17
      exit 0
      ;;
    *)
      echo -e "${RED}[FATAL] Unknown argument: $1${RESET}" >&2
      exit 1
      ;;
  esac
  shift
done

# ─── Case configuration ─────────────────────────────────────────────────────
# Per-case config variables (set by configure_case)
CASE_VARIANT=""
CASE_TASK=""
CASE_FINAL_GATE=""
CASE_EXPECTED_ARTIFACTS=()
CASE_EXPECTED_EVENTS=()
CASE_EXPECTED_REPORT_SECTIONS=()
CASE_VARIANT_ASSERT_FN=""
CASE_DUALCTL_EXTRA_ARGS=()
CASE_USE_CONTROLLED_ACPX=false

configure_case() {
  # Reset all case config
  CASE_VARIANT=""
  CASE_TASK=""
  CASE_FINAL_GATE=""
  CASE_EXPECTED_ARTIFACTS=()
  CASE_EXPECTED_EVENTS=()
  CASE_EXPECTED_REPORT_SECTIONS=()
  CASE_VARIANT_ASSERT_FN=""
  CASE_DUALCTL_EXTRA_ARGS=()
  CASE_USE_CONTROLLED_ACPX=false

  case "$CASE_NAME" in
    lifecycle)
      CASE_TASK="给 main.py 添加一个 goodbye 函数，返回 'goodbye'，并在 README 中添加函数说明。不要修改 __main__ 运行输出。"
      CASE_FINAL_GATE="gate.human_next_stage_approval"
      CASE_USE_CONTROLLED_ACPX=true
      CASE_EXPECTED_ARTIFACTS=("reports/latest-final-report.md" "delivery/commit-message.txt" "delivery/pr-body.md" "artifacts/next-stage/next-stage-prompt.md")
      CASE_EXPECTED_EVENTS=("workflow_started" "node_routed" "workflow_completed")
      CASE_EXPECTED_REPORT_SECTIONS=("User Request" "Acceptance Summary" "Developer Self-Test Summary" "Git Evidence" "Run Artifacts")
      CASE_VARIANT_ASSERT_FN="assert_lifecycle_controlled"
      ;;
    baseline-basic)
      CASE_TASK="给 main.py 添加一个 goodbye 函数，返回 'goodbye'，并在 README 中添加函数说明。不要修改 __main__ 运行输出，不要让 python main.py 打印 goodbye。"
      CASE_FINAL_GATE="gate.human_next_stage_approval"
      CASE_EXPECTED_ARTIFACTS=("reports/latest-final-report.md" "delivery/commit-message.txt" "delivery/pr-body.md" "artifacts/next-stage/next-stage-prompt.md")
      CASE_EXPECTED_EVENTS=("workflow_started" "node_routed" "workflow_completed")
      CASE_EXPECTED_REPORT_SECTIONS=("User Request" "Acceptance Summary" "Git Evidence")
      ;;
    contract-happy-path)
      CASE_VARIANT="contract"
      CASE_TASK="给 main.py 添加一个 calculate 函数，接受两个数字参数并返回它们的和，并在 README 中说明。不要修改 __main__ 运行输出。"
      CASE_FINAL_GATE="gate.human_next_stage_approval"
      CASE_DUALCTL_EXTRA_ARGS=("--workflow-variant" "contract")
      CASE_EXPECTED_ARTIFACTS=("artifacts/task-contract.json" "artifacts/task-contract.md" "reports/latest-final-report.md")
      CASE_EXPECTED_EVENTS=("workflow_started" "node_routed" "contract_compilation_completed" "workflow_completed")
      CASE_EXPECTED_REPORT_SECTIONS=("User Request" "TaskContract" "Acceptance Summary")
      CASE_VARIANT_ASSERT_FN="assert_contract_lifecycle"
      ;;
    full-reject-classify-path)
      CASE_VARIANT="full"
      CASE_TASK="给 main.py 添加一个 format_output 函数，接受 text 和 format_type 两个字符串参数。format_type 只支持 json、markdown、table，大小写敏感；json 必须返回紧凑 JSON 字符串 {\"text\":\"<text>\"}，实现建议使用 json.dumps({\"text\": text}, separators=(\",\", \":\"))；markdown 必须返回 '- <text>'；table 必须返回精确三行字符串 '| Text |\\n| --- |\\n| <text> |'；未知格式抛出 ValueError。README 要写完整 API 文档，包括参数、返回值、异常和三个精确返回值示例。不要修改 __main__ 运行输出。"
      CASE_FINAL_GATE="gate.human_next_stage_approval"
      CASE_DUALCTL_EXTRA_ARGS=("--workflow-variant" "full")
      CASE_EXPECTED_ARTIFACTS=("artifacts/task-contract.json")
      CASE_EXPECTED_EVENTS=("workflow_started" "node_routed" "contract_compilation_completed")
      CASE_EXPECTED_REPORT_SECTIONS=("User Request" "TaskContract")
      CASE_VARIANT_ASSERT_FN="assert_full_classify"
      ;;
    *)
      echo "Unknown case: $CASE_NAME. Supported: lifecycle, baseline-basic, contract-happy-path, full-reject-classify-path, all"
      exit 1
      ;;
  esac
}

# ─── Helpers ─────────────────────────────────────────────────────────────────
log()   { echo -e "${CYAN}[SMOKE]${RESET} $*"; }
ok()    { echo -e "  ${GREEN}PASS${RESET} $*"; }
fail()  { echo -e "  ${RED}FAIL${RESET} $*"; FAILED=true; }
warn()  { echo -e "  ${YELLOW}WARN${RESET} $*"; WARNINGS=$((WARNINGS + 1)); }
step()  { echo -e "\n${BOLD}=== $* ===${RESET}"; }

dualctl() {
  java -jar "$JAR_PATH" "$@"
}

# safe grep for print pipelines: returns 0 even when no match, avoids pipefail death.
# Do NOT use this with -q in if/while conditions — it always returns true.
# For conditionals, use plain grep -q (set -e exempts commands inside if/while).
sgrep() { grep "$@" || true; }

# safe recursive delete: only remove directories owned by the current smoke case.
safe_remove_dir() {
  local target="${1:-}"
  [[ -n "$target" ]] || return 0
  case "$target" in
    "$FIXTURE_DIR"|"$FIXTURE_DIR"/*|"${FIXTURE_DIR}-agent-runs"|"${FIXTURE_DIR}-agent-runs"/*|/tmp/dual-smoke-acpx-*)
      rm -rf "$target"
      ;;
    *)
      warn "Refusing to remove unsafe path: $target"
      return 1
      ;;
  esac
}

# safe count: returns 0 when dir doesn't exist or has no matches.
fcount() {
  local dir="$1" pattern="$2"
  if [[ ! -d "$dir" ]]; then
    echo 0
    return 0
  fi
  find "$dir" -maxdepth 1 -name "$pattern" 2>/dev/null | wc -l | tr -d ' '
}

review_packet_ref_for_active_node() {
  local active_node_def="$1"
  local active_node_instance="$2"
  if [[ -z "$active_node_instance" ]]; then
    echo ""
    return 0
  fi
  echo ".agent/workstreams/$WORKSTREAM_ID/reviews/$active_node_instance.md"
}

pause_for_manual_gate() {
  local active_node_def="$1"
  local active_node_instance="$2"
  local is_final_gate="$3"
  local packet_ref
  packet_ref=$(review_packet_ref_for_active_node "$active_node_def" "$active_node_instance")

  echo ""
  log "=== Manual gate mode ==="
  log "The workflow is paused for human review. The smoke script will not approve this gate."
  log "Open operator panel: $OPERATOR_URL"
  log "Effective workdir: $WORKSPACE_DIR"
  log "Workstream: $WORKSTREAM_ID"
  log "Gate: $active_node_def"
  if [[ -n "$packet_ref" ]]; then
    log "Review packet: $WORKSPACE_DIR/$packet_ref"
  fi
  if [[ "$is_final_gate" == true ]]; then
    log "Final gate: use Complete in the UI to finish this workstream."
  else
    log "Use Approve + Run or Revise + Run in the UI, then return here."
  fi
  read -rp "$(echo -e "${BOLD}Press Enter after finishing the gate action in the UI, or type q to stop: ${RESET}")" REPLY
  if [[ "$REPLY" =~ ^[Qq]$ ]]; then
    FINAL_STATUS="USER_STOPPED"
    return 1
  fi
  return 0
}

install_controlled_acpx() {
  local bin_dir="$1"
  mkdir -p "$bin_dir"
  cat > "$bin_dir/acpx" <<'FAKE_ACPX'
#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "--help" ]]; then
  echo "controlled smoke acpx"
  exit 0
fi
if [[ "${1:-}" == "--version" ]]; then
  echo "controlled-smoke-acpx 1.0"
  exit 0
fi

agent=""
prompt=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    claude|codex)
      agent="$1"
      if [[ "${2:-}" == "sessions" && "${3:-}" == "new" ]]; then
        echo "created controlled $agent session"
        exit 0
      fi
      shift
      ;;
    -f)
      shift
      prompt="${1:-}"
      shift
      ;;
    *)
      shift
      ;;
  esac
done

template="$(basename "${prompt:-unknown.md}")"

write_control() {
  local decision="$1"
  local summary="$2"
  cat <<EOF
## Controlled Smoke Output

Agent: ${agent:-unknown}
Template: ${template}

${summary}

\`\`\`dual-control
decision: ${decision}
summary: ${summary}
\`\`\`
EOF
}

case "$template" in
  intake-claude-brainstorm.md)
    write_control "READY_FOR_PRODUCT_REVIEW" "Requirement is clear enough for product review."
    ;;
  intake-codex-product-review.md)
    write_control "READY_FOR_PRODUCT_REVIEW" "Product review passed for controlled smoke."
    ;;
  design-claude.md)
    write_control "READY_FOR_ARCH_REVIEW" "Design is intentionally small and ready for architecture review."
    ;;
  design-codex-review.md)
    write_control "READY_FOR_ARCH_REVIEW" "Architecture review passed for controlled smoke."
    ;;
  plan-claude.md)
    write_control "READY_FOR_PLAN_REVIEW" "Plan keeps the change scoped to main.py and README.md."
    ;;
  plan-codex-review.md)
    write_control "READY_TO_IMPLEMENT" "Plan is ready to implement."
    ;;
  implement-claude.md)
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
    write_control "IMPLEMENTED" "Implemented goodbye() and README documentation."
    ;;
  implement-claude-self-test.md)
    python3 - <<'PY'
import main
assert main.hello() == "world"
assert main.goodbye() == "goodbye"
PY
    write_control "SELF_TEST_COMPLETE" "Self-test passed: hello() and goodbye() behave as expected."
    ;;
  acceptance-codex-review.md)
    write_control "ACCEPT" "Controlled verifier accepted the implementation evidence."
    ;;
  stage-claude-handoff.md)
    cat <<'EOF'
## Handoff

Completed work:
- Added goodbye() to main.py.
- Documented goodbye() in README.md.

Open risks:
- None for controlled smoke.

Recommended next step:
- Use the generated report and next-stage prompt as handoff artifacts.

```dual-control
decision: HANDOFF_READY
summary: Handoff is ready for the next stage.
```
EOF
    ;;
  *)
    write_control "PASS" "Default controlled smoke response."
    ;;
esac
FAKE_ACPX
  chmod +x "$bin_dir/acpx"
}

# ─── Variant-specific assertion functions ────────────────────────────────────

assert_lifecycle_controlled() {
  step "Controlled Lifecycle Assertions"

  if [[ -f "$WORKSPACE_DIR/main.py" ]] && grep -q "def goodbye" "$WORKSPACE_DIR/main.py"; then
    ok "main.py contains goodbye()"
  else
    fail "main.py missing goodbye()"
  fi

  if [[ -f "$WORKSPACE_DIR/README.md" ]] && grep -q "goodbye" "$WORKSPACE_DIR/README.md"; then
    ok "README documents goodbye()"
  else
    fail "README missing goodbye() documentation"
  fi

  if [[ -f "$WS_DIR/artifacts/git-evidence/diff.patch" ]] && grep -q "def goodbye" "$WS_DIR/artifacts/git-evidence/diff.patch"; then
    ok "Git evidence captures goodbye() diff"
  else
    fail "Git evidence diff.patch missing goodbye()"
  fi

  local handoff_json="$WS_DIR/artifacts/next-stage/handoff.json"
  if [[ -f "$handoff_json" ]]; then
    ok "handoff.json exists"
  else
    fail "handoff.json missing"
  fi
}

assert_contract_lifecycle() {
  step "Contract Lifecycle Assertions"

  # task-contract.json exists and is valid JSON with required fields
  local contract_json="$WS_DIR/artifacts/task-contract.json"
  if [[ -f "$contract_json" ]]; then
    ok "task-contract.json exists"
    local contract_status
    contract_status=$(python3 -c "import json; d=json.load(open('$contract_json')); print(d.get('status','?'))" 2>/dev/null || echo "?")
    ok "TaskContract status: $contract_status"
  else
    fail "task-contract.json missing (contract review should produce it)"
  fi

  # task-contract.md exists and has content
  local contract_md="$WS_DIR/artifacts/task-contract.md"
  if [[ -f "$contract_md" ]]; then
    ok "task-contract.md exists"
  else
    fail "task-contract.md missing (ContractCompileHandler should produce it)"
  fi
}

assert_full_classify() {
  step "Full Lifecycle Classification Assertions"

  # Contract must always exist in the full lifecycle
  local contract_json="$WS_DIR/artifacts/task-contract.json"
  if [[ -f "$contract_json" ]]; then
    ok "task-contract.json exists"
  else
    fail "task-contract.json missing (full lifecycle includes contract.compile)"
    return
  fi

  # failure-classification.json — only present if rejection path fired
  local class_json="$WS_DIR/artifacts/failure-classification.json"
  if [[ -f "$class_json" ]]; then
    ok "failure-classification.json exists (classify path exercised)"
    local category
    category=$(python3 -c "import json; print(json.load(open('$class_json')).get('category','?'))" 2>/dev/null || echo "?")
    ok "Failure category: $category"

    # Check events.jsonl for failure_classified domain event
    if grep -q '"failure_classified"' "$WS_DIR/events.jsonl" 2>/dev/null; then
      ok "events.jsonl contains failure_classified event"
    else
      fail "events.jsonl missing failure_classified event (artifact exists but event missing)"
    fi

    local candidate_count
    candidate_count=$(fcount "$WS_DIR/calibration/candidates" "*.json")
    if [[ "$candidate_count" -gt 0 ]]; then
      ok "Calibration candidate(s): $candidate_count"
    else
      warn "No calibration candidate generated (classification category may not be calibratable)"
    fi

    if [[ -f "$WS_DIR/recovery/current-failure.md" ]]; then
      ok "recovery/current-failure.md exists"
    else
      fail "recovery/current-failure.md missing (classify path was exercised)"
    fi

    # Check report contains Failure Classification section if report exists
    local report_path="$WS_DIR/reports/latest-final-report.md"
    if [[ -f "$report_path" ]] && grep -qi "Failure Classification" "$report_path"; then
      ok "Final report contains Failure Classification section"
    else
      fail "Final report missing Failure Classification section (classify path was exercised)"
    fi
  else
    # classify path not exercised — this is PASS-WARN, not FAIL
    warn "failure-classification.json not found (classify path not exercised this run)"
    warn "Acceptance produced ACCEPT — classify_failure did not fire"
    warn "This is PASS-WARN: full lifecycle ran correctly, but classification path was not exercised"
    FULL_CLASSIFY_NOT_EXERCISED=true
  fi
}

# ─── Main smoke case runner ─────────────────────────────────────────────────

run_smoke_case() {
  # Reset state for this case
  FAILED=false
  WARNINGS=0
  WORKSTREAM_ID=""
  WORKSPACE_DIR=""
  WS_DIR=""
  HAS_GH_REPO=false
  EVENT_COUNT=0
  NODE_COUNT=0
  RUN_COUNT=0
  CURRENT_ROUND=0
  FINAL_STATUS=""
  FULL_CLASSIFY_NOT_EXERCISED=""
  CLEANED_UP=false
  PATH_BEFORE_CONTROLLED_ACPX=""

  FIXTURE_DIR="/tmp/dual-smoke-${CASE_NAME}-$$"
  FIXTURE_REPO_NAME="dual-smoke-${CASE_NAME}-$$"
  CONTROLLED_ACPX_DIR="/tmp/dual-smoke-acpx-${CASE_NAME}-$$"

  # ─── Cleanup trap for this case ─────────────────────────────────────────
  do_cleanup() {
    if [[ "$CLEANED_UP" == true ]]; then
      return 0
    fi
    CLEANED_UP=true
    if [[ -n "$PATH_BEFORE_CONTROLLED_ACPX" ]]; then
      export PATH="$PATH_BEFORE_CONTROLLED_ACPX"
    fi
    safe_remove_dir "$CONTROLLED_ACPX_DIR"
    if [[ "$CLEANUP" != true ]]; then
      return 0
    fi
    if [[ -n "$WORKSPACE_DIR" && -d "$FIXTURE_DIR/.git" && -d "$WORKSPACE_DIR" ]]; then
      git -C "$FIXTURE_DIR" worktree remove --force "$WORKSPACE_DIR" 2>/dev/null || safe_remove_dir "$WORKSPACE_DIR"
    elif [[ -n "$WORKSPACE_DIR" ]]; then
      safe_remove_dir "$WORKSPACE_DIR"
    fi
    safe_remove_dir "$FIXTURE_DIR"
    if [[ "$HAS_GH_REPO" == true ]]; then
      gh repo delete "$FIXTURE_REPO_NAME" --yes 2>/dev/null || true
    fi
  }
  trap do_cleanup EXIT
  trap 'do_cleanup; exit 130' INT TERM

  # ─── Phase 0: Setup ─────────────────────────────────────────────────────
  step "Phase 0: Setup [$CASE_NAME]"

  # 0.1 Build
  if [[ "$SKIP_BUILD" == false ]]; then
    log "Building project..."
    (cd "$PROJECT_ROOT" && mvn package -DskipTests -q) || {
      echo -e "${RED}[FATAL] mvn package failed${RESET}" >&2
      return 1
    }
    ok "Build succeeded"
  else
    log "Skipping build (--skip-build)"
    [[ -f "$JAR_PATH" ]] || {
      echo -e "${RED}[FATAL] JAR not found: $JAR_PATH. Run without --skip-build.${RESET}" >&2
      return 1
    }
    ok "JAR exists"
  fi

  # 0.2 Verify dualctl
  log "Verifying dualctl..."
  dualctl --help > /dev/null 2>&1 || {
    echo -e "${RED}[FATAL] dualctl --help failed${RESET}" >&2
    return 1
  }
  ok "dualctl is runnable"

  # 0.3 Verify acpx (real acpx for E2E cases, controlled fake acpx for lifecycle)
  if [[ "$CASE_USE_CONTROLLED_ACPX" == true ]]; then
    install_controlled_acpx "$CONTROLLED_ACPX_DIR"
    PATH_BEFORE_CONTROLLED_ACPX="$PATH"
    export PATH="$CONTROLLED_ACPX_DIR:$PATH"
    ok "controlled acpx installed for lifecycle smoke"
  fi

  if command -v acpx &> /dev/null; then
    ok "acpx found: $(acpx --version 2>/dev/null || echo 'version unknown')"
  else
    echo -e "${RED}[FATAL] acpx not found. Full lifecycle smoke requires acpx for agent runs.${RESET}" >&2
    return 1
  fi

  # 0.4 Verify gh (only matters with --with-github)
  if [[ "$WITH_GITHUB" == true ]]; then
    if command -v gh &> /dev/null && gh auth status &> /dev/null 2>&1; then
      ok "gh CLI authenticated"
    else
      echo -e "${RED}[FATAL] --with-github requires gh CLI with auth${RESET}" >&2
      return 1
    fi
  fi

  # 0.5 Create fixture repo
  log "Creating fixture repo at $FIXTURE_DIR..."
  rm -rf "$FIXTURE_DIR"
  mkdir -p "$FIXTURE_DIR"
  cd "$FIXTURE_DIR"

  git init -q
  git config user.email "smoke@test.local"
  git config user.name "Smoke Test"

  cat > README.md << 'README'
# Smoke Fixture

A minimal project for dual-orchestrator lifecycle smoke test.
README

  cat > main.py << 'PY'
def hello():
    return "world"

if __name__ == "__main__":
    print(hello())
PY

  cat > .gitignore << 'GITIGNORE'
__pycache__/
*.pyc
GITIGNORE

  git add -A
  git commit -q -m "init: fixture project"

  # Create GitHub remote only when explicitly requested
  if [[ "$WITH_GITHUB" == true ]]; then
    if gh repo create "$FIXTURE_REPO_NAME" --public --source=. --push &> /dev/null; then
      ok "GitHub repo created: $FIXTURE_REPO_NAME"
      HAS_GH_REPO=true
    else
      warn "Could not create GitHub repo — PR test will be skipped"
    fi
  fi
  ok "Fixture repo ready"

  # ─── Phase 1: Start ─────────────────────────────────────────────────────
  step "Phase 1: Start workstream [$CASE_NAME]"

  log "Starting [$CASE_NAME]: $CASE_TASK"

  # Build dualctl start args safely using bash array
  local -a START_ARGS=(start --run --workdir "$FIXTURE_DIR" --max-steps 20)
  if [[ ${#CASE_DUALCTL_EXTRA_ARGS[@]} -gt 0 ]]; then
    START_ARGS+=("${CASE_DUALCTL_EXTRA_ARGS[@]}")
  fi
  START_ARGS+=("$CASE_TASK")

  START_OUTPUT=$(dualctl "${START_ARGS[@]}" 2>&1) || {
    echo "$START_OUTPUT"
    echo -e "${RED}[FATAL] dualctl start --run failed${RESET}" >&2
    do_cleanup
    return 1
  }

  # Extract workstream ID from output
  WORKSTREAM_ID=$(echo "$START_OUTPUT" | sgrep "^Workstream:" | awk '{print $2}')
  if [[ -z "$WORKSTREAM_ID" ]]; then
    echo "$START_OUTPUT"
    echo -e "${RED}[FATAL] Could not extract workstream ID from start output${RESET}" >&2
    do_cleanup
    return 1
  fi

  # Extract workspace (worktree) dir — this is where the ledger lives
  WORKSPACE_DIR=$(echo "$START_OUTPUT" | sgrep "^Workspace:" | awk '{print $2}')
  if [[ -z "$WORKSPACE_DIR" ]]; then
    # Fallback: StartCommand pattern is <gitRoot>-agent-runs/<workstreamId>
    WORKSPACE_DIR="${FIXTURE_DIR}-agent-runs/${WORKSTREAM_ID}"
  fi

  # Ledger lives inside the worktree, not the original fixture dir
  WS_DIR="$WORKSPACE_DIR/.agent/workstreams/$WORKSTREAM_ID"

  ok "Workstream: $WORKSTREAM_ID"
  ok "Workspace:  $WORKSPACE_DIR"
  ok "Ledger:     $WS_DIR"

  # Show workflow variant if specified
  if [[ -n "$CASE_VARIANT" ]]; then
    ok "Variant:    $CASE_VARIANT"
  fi

  # Show what happened
  echo "$START_OUTPUT" | sgrep "^Step " | while read -r line; do
    log "  $line"
  done

  # Check final status from start
  if echo "$START_OUTPUT" | grep -q "WAITING_FOR_HUMAN"; then
    ok "Reached first human gate"
  elif echo "$START_OUTPUT" | grep -q "COMPLETED"; then
    warn "Workflow completed in one shot (unexpected for full lifecycle)"
  else
    FINAL_STATUS=$(echo "$START_OUTPUT" | tail -1)
    warn "Unexpected final state: $FINAL_STATUS"
  fi

  # ─── Phase 1.5: Verify ledger structure ──────────────────────────────────
  step "Phase 1.5: Verify ledger structure"

  verify_file() {
    if [[ -f "$1" ]]; then
      ok "Exists: ${1#$WS_DIR/}"
    else
      fail "Missing: ${1#$WS_DIR/}"
    fi
  }

  verify_dir() {
    if [[ -d "$1" ]]; then
      ok "Exists: ${1#$WS_DIR/}/"
    else
      fail "Missing: ${1#$WS_DIR/}/"
    fi
  }

  verify_file "$WS_DIR/workstream.json"
  verify_file "$WS_DIR/requirement.md"
  verify_file "$WS_DIR/workflow/definition.json"
  verify_file "$WS_DIR/workflow/current.json"
  verify_file "$WS_DIR/events.jsonl"
  verify_dir  "$WS_DIR/nodes"
  verify_dir  "$WS_DIR/runs"

  # Verify requirement content
  if [[ -f "$WS_DIR/requirement.md" ]]; then
    if grep -q "goodbye\|calculate\|format_output" "$WS_DIR/requirement.md"; then
      ok "requirement.md contains task description"
    else
      warn "requirement.md may not contain full task description"
    fi
  fi

  # ─── Phase 2: Gate loop ─────────────────────────────────────────────────
  if [[ "$MANUAL_GATES" == true ]]; then
    step "Phase 2: Manual gate loop [$CASE_NAME]"
  else
    step "Phase 2: Gate approval loop [$CASE_NAME]"
  fi

  # gate.human_next_stage_approval is terminal for the current workstream.
  # Use "review complete" there; starting another stage should be an explicit new workstream.
  FINAL_STATUS=""

  while [[ $CURRENT_ROUND -lt $MAX_APPROVE_ROUNDS ]]; do
    CURRENT_ROUND=$((CURRENT_ROUND + 1))
    log "Gate round $CURRENT_ROUND"

    # Get current status — use WORKSPACE_DIR (the worktree) as workdir
    STATUS_OUTPUT=$(dualctl status --workdir "$WORKSPACE_DIR" "$WORKSTREAM_ID" 2>&1) || {
      echo "$STATUS_OUTPUT"
      fail "status command failed at round $CURRENT_ROUND"
      break
    }

    echo "$STATUS_OUTPUT" | sgrep "^  " | while read -r line; do
      log "  $line"
    done

    # Check for terminal states
    if echo "$STATUS_OUTPUT" | grep -q "COMPLETED"; then
      FINAL_STATUS="COMPLETED"
      ok "Workflow completed!"
      break
    fi

    if echo "$STATUS_OUTPUT" | grep -q "FAILED"; then
      FINAL_STATUS="FAILED"
      fail "Workflow failed at round $CURRENT_ROUND"
      echo "$STATUS_OUTPUT"
      break
    fi

    if echo "$STATUS_OUTPUT" | grep -q "WAITING_FOR_RECOVERY"; then
      FINAL_STATUS="WAITING_FOR_RECOVERY"
      warn "Workflow waiting for recovery at round $CURRENT_ROUND"
      echo "$STATUS_OUTPUT"
      break
    fi

    if echo "$STATUS_OUTPUT" | grep -q "BLOCKED"; then
      FINAL_STATUS="BLOCKED"
      fail "Workflow blocked at round $CURRENT_ROUND"
      echo "$STATUS_OUTPUT"
      break
    fi

    # Must be WAITING_FOR_HUMAN — approve
    if ! echo "$STATUS_OUTPUT" | grep -q "WAITING_FOR_HUMAN"; then
      warn "Unexpected status (not HUMAN/COMPLETED/FAILED), attempting approve anyway"
    fi

    # Detect which gate we're at by parsing "Active node definition:" from status output
    ACTIVE_NODE_DEF=$(echo "$STATUS_OUTPUT" | sgrep "Active node definition:" | awk '{print $NF}')
    ACTIVE_NODE_INSTANCE=$(echo "$STATUS_OUTPUT" | sgrep "Active nodes:" | sed -E 's/.*\[([^]]*)\].*/\1/' | cut -d',' -f1 | tr -d ' ')

    IS_FINAL_GATE=false
    if [[ "$ACTIVE_NODE_DEF" == "$CASE_FINAL_GATE" ]]; then
      IS_FINAL_GATE=true
      log "Detected final stage gate: $CASE_FINAL_GATE"
    fi

    if [[ "$MANUAL_GATES" == true ]]; then
      if ! pause_for_manual_gate "$ACTIVE_NODE_DEF" "$ACTIVE_NODE_INSTANCE" "$IS_FINAL_GATE"; then
        break
      fi
      continue
    fi

    if [[ "$INTERACTIVE" == true ]]; then
      echo ""
      log "=== Interactive mode: review the gate before approving ==="
      if [[ "$IS_FINAL_GATE" == true ]]; then
        log "NOTE: This is the final stage gate. Approve = start next iteration. Complete = finish workflow."
      fi
      log "Active gate output:"
      LATEST_RUN=$(ls -t "$WS_DIR/runs/" 2>/dev/null | head -1)
      if [[ -n "$LATEST_RUN" && -d "$WS_DIR/runs/$LATEST_RUN/outputs" ]]; then
        for f in "$WS_DIR/runs/$LATEST_RUN/outputs/"*; do
          [[ -f "$f" ]] || continue
          log "--- $(basename "$f") ---"
          head -50 "$f" 2>/dev/null || true
        done
      fi
      echo ""
      if [[ "$IS_FINAL_GATE" == true ]]; then
        read -rp "$(echo -e "${BOLD}[c]omplete / [a]pprove (loop) / [n]stop?${RESET} ")" -n 1 -r REPLY
        echo ""
        if [[ "$REPLY" =~ ^[Cc]$ ]]; then
          log "Completing workflow..."
          COMPLETE_OUTPUT=$(dualctl review complete --workdir "$WORKSPACE_DIR" "$WORKSTREAM_ID" 2>&1) || {
            echo "$COMPLETE_OUTPUT"
            fail "review complete failed"
            FINAL_STATUS="COMPLETE_FAILED"
            break
          }
          echo "$COMPLETE_OUTPUT"
          FINAL_STATUS="COMPLETED"
          ok "Workflow completed via review complete!"
          break
        elif [[ "$REPLY" =~ ^[Nn]$ ]]; then
          log "User chose to stop."
          FINAL_STATUS="USER_STOPPED"
          break
        fi
        # Fall through to approve
      else
        read -rp "$(echo -e "${BOLD}Approve this gate? [Y/n]${RESET} ")" -n 1 -r REPLY
        echo ""
        if [[ "$REPLY" =~ ^[Nn]$ ]]; then
          log "User chose not to approve. Stopping."
          FINAL_STATUS="USER_STOPPED"
          break
        fi
      fi
    fi

    # Final gate: complete to finish the workflow (approve would loop back to intake)
    if [[ "$IS_FINAL_GATE" == true ]]; then
      log "Completing final stage gate..."
      COMPLETE_OUTPUT=$(dualctl review complete --workdir "$WORKSPACE_DIR" "$WORKSTREAM_ID" 2>&1) || {
        echo "$COMPLETE_OUTPUT"
        fail "review complete failed"
        FINAL_STATUS="COMPLETE_FAILED"
        break
      }
      echo "$COMPLETE_OUTPUT" | sgrep "^" | while read -r line; do
        log "  $line"
      done
      FINAL_STATUS="COMPLETED"
      ok "Workflow completed via review complete!"
      break
    fi

    # Normal gate: approve and continue running
    log "Approving gate and continuing..."
    APPROVE_OUTPUT=$(dualctl review approve --run --workdir "$WORKSPACE_DIR" --max-steps 20 "$WORKSTREAM_ID" 2>&1) || {
      echo "$APPROVE_OUTPUT"
      fail "review approve --run failed at round $CURRENT_ROUND"
      FINAL_STATUS="APPROVE_FAILED"
      break
    }

    # Show steps
    echo "$APPROVE_OUTPUT" | sgrep "^Step " | while read -r line; do
      log "  $line"
    done

    # Check if we hit another gate or terminal
    if echo "$APPROVE_OUTPUT" | grep -q "WAITING_FOR_HUMAN"; then
      ok "Reached next gate"
      continue
    fi

    if echo "$APPROVE_OUTPUT" | grep -q "COMPLETED"; then
      FINAL_STATUS="COMPLETED"
      ok "Workflow completed!"
      break
    fi

    if echo "$APPROVE_OUTPUT" | grep -q "FAILED"; then
      FINAL_STATUS="FAILED"
      fail "Workflow failed"
      echo "$APPROVE_OUTPUT"
      break
    fi

    if echo "$APPROVE_OUTPUT" | grep -q "WAITING_FOR_RECOVERY"; then
      FINAL_STATUS="WAITING_FOR_RECOVERY"
      warn "Workflow waiting for recovery"
      break
    fi

    if echo "$APPROVE_OUTPUT" | grep -q "Stopped after --max-steps"; then
      warn "Hit max-steps limit, continuing..."
      continue
    fi

    warn "Unknown state after approve, continuing..."

  done

  if [[ -z "$FINAL_STATUS" ]]; then
    FINAL_STATUS="MAX_ROUNDS"
    warn "Exceeded $MAX_APPROVE_ROUNDS approval rounds"
  fi

  # ─── Phase 3: Post-run verification ─────────────────────────────────────
  step "Phase 3: Post-run verification [$CASE_NAME]"

  # 3.1 Final status
  log "Final status:"
  dualctl status --workdir "$WORKSPACE_DIR" "$WORKSTREAM_ID" 2>&1 | sgrep "^  " | while read -r line; do
    echo "  $line"
  done

  # 3.2 Events completeness
  log "Checking events.jsonl..."
  if [[ -f "$WS_DIR/events.jsonl" ]]; then
    EVENT_COUNT=$(wc -l < "$WS_DIR/events.jsonl" | tr -d ' ')
    if [[ "$EVENT_COUNT" -gt 0 ]]; then
      ok "events.jsonl has $EVENT_COUNT events"

      for event_type in "${CASE_EXPECTED_EVENTS[@]}"; do
        COUNT=$(grep -c "\"$event_type\"" "$WS_DIR/events.jsonl" 2>/dev/null || true)
        COUNT=${COUNT:-0}
        if [[ "$COUNT" -gt 0 ]]; then
          ok "  $event_type: $COUNT"
        else
          fail "  $event_type: 0 (expected event missing)"
        fi
      done
    else
      fail "events.jsonl is empty"
    fi
  else
    fail "events.jsonl is missing"
  fi

  # 3.3 Nodes completeness
  log "Checking node instances..."
  NODE_COUNT=$(fcount "$WS_DIR/nodes" "*.json")
  if [[ "$NODE_COUNT" -gt 0 ]]; then
    ok "Node instances: $NODE_COUNT"

    for node_file in "$WS_DIR/nodes/"*.json; do
      [[ -f "$node_file" ]] || continue
      node_id=$(basename "$node_file" .json)
      status=$(python3 -c "import json; print(json.load(open('$node_file')).get('status','?'))" 2>/dev/null || echo "?")
      if [[ "$status" == "COMPLETED" ]]; then
        ok "  $node_id: COMPLETED"
      else
        warn "  $node_id: $status"
      fi
    done
  else
    fail "No node instances found"
  fi

  # 3.4 Runs verification
  log "Checking agent runs..."
  RUN_COUNT=$(fcount "$WS_DIR/runs" "*")
  if [[ "$RUN_COUNT" -gt 0 ]]; then
    ok "Agent runs: $RUN_COUNT"

    for run_dir in "$WS_DIR/runs/"*/; do
      [[ -d "$run_dir" ]] || continue
      run_id=$(basename "$run_dir")
      if [[ -f "$run_dir/run.json" ]]; then
        ok "  $run_id: run.json present"
      else
        fail "  $run_id: run.json missing"
      fi

      PROMPT_COUNT=$(fcount "$run_dir/prompts" "*")
      ok "  $run_id: $PROMPT_COUNT prompt file(s)"

      OUTPUT_COUNT=$(fcount "$run_dir/outputs" "*")
      ok "  $run_id: $OUTPUT_COUNT output file(s)"
    done
  else
    fail "No agent runs found"
  fi

  # 3.5 Workflow definition
  log "Checking workflow definition..."
  WF_DEF="$WS_DIR/workflow/definition.json"
  if [[ -f "$WF_DEF" ]]; then
    WF_ID=$(python3 -c "import json; print(json.load(open('$WF_DEF')).get('workflowId','?'))" 2>/dev/null || echo "?")
    WF_VER=$(python3 -c "import json; print(json.load(open('$WF_DEF')).get('version','?'))" 2>/dev/null || echo "?")
    ok "Workflow: $WF_ID v$WF_VER"

    NODE_DEF_COUNT=$(python3 -c "import json; print(len(json.load(open('$WF_DEF')).get('nodes',[])))" 2>/dev/null || echo "?")
    ok "Defined nodes: $NODE_DEF_COUNT"
  else
    fail "workflow/definition.json not found"
  fi

  # 3.6 Case-specific artifact assertions
  log "Checking case-specific artifacts..."
  for artifact in "${CASE_EXPECTED_ARTIFACTS[@]}"; do
    if [[ -f "$WS_DIR/$artifact" ]]; then
      ok "Case artifact: $artifact"
    else
      fail "Missing case artifact: $artifact"
    fi
  done

  # 3.7 Delivery artifacts
  log "Checking delivery artifacts..."
  DELIVERY_DIR="$WS_DIR/delivery"
  if [[ -d "$DELIVERY_DIR" ]]; then
    ok "delivery/ directory exists"
    verify_file "$DELIVERY_DIR/commit-message.txt"
    verify_file "$DELIVERY_DIR/pr-body.md"
    if [[ -f "$DELIVERY_DIR/delivery.json" ]]; then
      DEL_STATUS=$(python3 -c "import json; print(json.load(open('$DELIVERY_DIR/delivery.json')).get('status','?'))" 2>/dev/null || echo "?")
      ok "Delivery status: $DEL_STATUS"
    else
      ok "delivery.json not present before explicit delivery commit/create-pr"
    fi
  else
    if [[ "$FINAL_STATUS" == "COMPLETED" ]]; then
      fail "delivery/ directory missing (workflow completed)"
    else
      warn "delivery/ directory missing (workflow did not reach delivery phase)"
    fi
  fi

  # 3.8 Final report — actual path: reports/latest-final-report.md
  log "Checking final report..."
  REPORT_PATH="$WS_DIR/reports/latest-final-report.md"
  if [[ -f "$REPORT_PATH" ]]; then
    REPORT_SIZE=$(wc -c < "$REPORT_PATH" | tr -d ' ')
    ok "Final report: $REPORT_SIZE bytes"

    for section in "${CASE_EXPECTED_REPORT_SECTIONS[@]}"; do
      if grep -qi "$section" "$REPORT_PATH"; then
        ok "  Report contains '$section' section"
      else
        warn "  Report missing '$section' section"
      fi
    done
  else
    if [[ "$FINAL_STATUS" == "COMPLETED" ]]; then
      fail "No final report found at reports/latest-final-report.md (workflow completed)"
    else
      warn "No final report (workflow did not reach report generation)"
    fi
  fi

  # 3.9 Worktree changes
  log "Checking worktree changes..."
  if [[ -d "$WORKSPACE_DIR" ]]; then
    CHANGES=false
    UNSTAGED=$(cd "$WORKSPACE_DIR" && git status --short 2>/dev/null || echo "")
    if [[ -n "$UNSTAGED" ]]; then
      ok "Uncommitted changes in worktree:"
      echo "$UNSTAGED" | while read -r line; do
        log "  $line"
      done
      CHANGES=true
    fi
    COMMIT_COUNT=$(cd "$WORKSPACE_DIR" && git rev-list --count HEAD 2>/dev/null || echo 1)
    if [[ "$COMMIT_COUNT" -gt 1 ]]; then
      DIFF_STAT=$(cd "$WORKSPACE_DIR" && git diff --stat HEAD~1 2>/dev/null || echo "")
      if [[ -n "$DIFF_STAT" ]]; then
        ok "Committed changes (latest commit):"
        echo "$DIFF_STAT" | while read -r line; do
          log "  $line"
        done
        CHANGES=true
      fi
    fi
    if [[ "$CHANGES" == false ]]; then
      warn "No detectable changes in worktree"
    fi
  else
    fail "Worktree directory not found: $WORKSPACE_DIR"
  fi

  # 3.10 Next-stage prompt — actual path: artifacts/next-stage/next-stage-prompt.md
  log "Checking next-stage prompt..."
  NEXT_PROMPT_PATH="$WS_DIR/artifacts/next-stage/next-stage-prompt.md"
  if [[ -f "$NEXT_PROMPT_PATH" ]]; then
    PROMPT_SIZE=$(wc -c < "$NEXT_PROMPT_PATH" | tr -d ' ')
    ok "Next-stage prompt: $PROMPT_SIZE bytes"
  else
    if [[ "$FINAL_STATUS" == "COMPLETED" ]]; then
      fail "No next-stage prompt found at artifacts/next-stage/next-stage-prompt.md"
    else
      warn "No next-stage prompt (workflow may not have reached that phase)"
    fi
  fi

  # 3.11 Variant-specific assertions
  if [[ -n "$CASE_VARIANT_ASSERT_FN" ]]; then
    $CASE_VARIANT_ASSERT_FN
  fi

  # ─── Phase 4: Events timeline ───────────────────────────────────────────
  step "Phase 4: Events timeline"

  log "Full event log:"
  dualctl events --workdir "$WORKSPACE_DIR" --limit 100 "$WORKSTREAM_ID" 2>&1 || warn "events command failed"

  # ─── Cleanup ────────────────────────────────────────────────────────────
  do_cleanup
  trap - EXIT INT TERM
}

# ─── Report helpers ──────────────────────────────────────────────────────────

print_case_report() {
  echo ""
  echo "  Case:         $CASE_NAME"
  echo "  Variant:      ${CASE_VARIANT:-baseline (default)}"
  echo "  Fixture:      $FIXTURE_DIR"
  echo "  Workspace:    $WORKSPACE_DIR"
  echo "  Ledger:       $WS_DIR"
  echo "  Final status: $FINAL_STATUS"
  echo "  Gate rounds:  $CURRENT_ROUND"
  echo "  Events:       $EVENT_COUNT"
  echo "  Node count:   $NODE_COUNT"
  echo "  Run count:    $RUN_COUNT"
  echo "  Warnings:     $WARNINGS"
  echo ""
}

# ─── --case all mode ─────────────────────────────────────────────────────────
if [[ "$CASE_NAME" == "all" ]]; then
  ALL_CASES=("lifecycle" "baseline-basic" "contract-happy-path" "full-reject-classify-path")
  ALL_RESULTS=()
  ALL_PASS=true

  # Build once for all cases
  if [[ "$SKIP_BUILD" == false ]]; then
    step "Phase 0: Build (shared)"
    log "Building project..."
    (cd "$PROJECT_ROOT" && mvn package -DskipTests -q) || {
      echo -e "${RED}[FATAL] mvn package failed${RESET}" >&2
      exit 1
    }
    ok "Build succeeded"
    SKIP_BUILD=true  # subsequent run_smoke_case calls skip rebuild
  fi

  for case_name in "${ALL_CASES[@]}"; do
    step "Running case: $case_name"

    CASE_NAME="$case_name"
    configure_case
    if ! run_smoke_case; then
      FAILED=true
      [[ -n "$FINAL_STATUS" ]] || FINAL_STATUS="FATAL"
      do_cleanup
      trap - EXIT INT TERM
    fi

    # Capture result — 3-tier: PASS / PASS-WARN / FAIL
    if [[ "$FAILED" == true ]]; then
      ALL_RESULTS+=("$case_name: FAIL ($FINAL_STATUS)")
      ALL_PASS=false
    elif [[ "${FULL_CLASSIFY_NOT_EXERCISED:-}" == true ]]; then
      ALL_RESULTS+=("$case_name: PASS-WARN (classify path not exercised)")
    elif [[ "$FINAL_STATUS" == "COMPLETED" ]]; then
      ALL_RESULTS+=("$case_name: PASS")
    else
      ALL_RESULTS+=("$case_name: FAIL ($FINAL_STATUS)")
      ALL_PASS=false
    fi
  done

  # Final summary
  step "All Cases Summary"
  for result in "${ALL_RESULTS[@]}"; do
    case "$result" in
      *PASS-WARN*) echo -e "  ${YELLOW}${result}${RESET}" ;;
      *PASS*)      echo -e "  ${GREEN}${result}${RESET}" ;;
      *FAIL*)      echo -e "  ${RED}${result}${RESET}" ;;
      *)           echo "  $result" ;;
    esac
  done
  echo ""
  if [[ "$ALL_PASS" == true ]]; then
    echo -e "  ${GREEN}${BOLD}ALL PASSED (some with warnings)${RESET}"
    exit 0
  else
    echo -e "  ${RED}${BOLD}SOME FAILED${RESET}"
    exit 1
  fi
fi

# ─── Single case execution ──────────────────────────────────────────────────

configure_case
run_smoke_case

step "Smoke Test Report [$CASE_NAME]"
print_case_report

if [[ "$FAILED" == true ]]; then
  echo -e "  ${RED}${BOLD}RESULT: FAIL${RESET} (final status: $FINAL_STATUS)"
  EXIT_CODE=1
elif [[ "${FULL_CLASSIFY_NOT_EXERCISED:-}" == true ]]; then
  echo -e "  ${YELLOW}${BOLD}RESULT: PASS-WARN${RESET} (classify path not exercised this run)"
  EXIT_CODE=0
elif [[ "$FINAL_STATUS" == "COMPLETED" ]]; then
  echo -e "  ${GREEN}${BOLD}RESULT: PASS${RESET}"
  EXIT_CODE=0
else
  echo -e "  ${RED}${BOLD}RESULT: FAIL${RESET} (final status: $FINAL_STATUS)"
  EXIT_CODE=1
fi

echo ""
echo "  Inspect: dualctl status --workdir $WORKSPACE_DIR $WORKSTREAM_ID"
echo "  Events:  dualctl events --workdir $WORKSPACE_DIR $WORKSTREAM_ID"
echo ""

exit $EXIT_CODE
