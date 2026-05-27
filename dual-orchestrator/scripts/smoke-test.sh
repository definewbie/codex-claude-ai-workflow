#!/usr/bin/env bash
#
# smoke-test.sh — Full lifecycle smoke test for dual-orchestrator
#
# Usage:
#   ./scripts/smoke-test.sh                    # Auto-approve all gates
#   ./scripts/smoke-test.sh --interactive      # Pause at each gate for review
#   ./scripts/smoke-test.sh --skip-build       # Skip mvn package
#   ./scripts/smoke-test.sh --cleanup          # Delete fixture repo after test
#   ./scripts/smoke-test.sh --with-github      # Enable GitHub remote repo creation
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
FIXTURE_DIR="/tmp/dual-smoke-fixture-$$"
FIXTURE_REPO_NAME="dual-smoke-fixture-$$"
JAR_PATH="$PROJECT_ROOT/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar"
MAX_APPROVE_ROUNDS=10
TASK_DESCRIPTION="给 main.py 添加一个 goodbye 函数，返回 'goodbye'，并在 README 中添加函数说明。"

INTERACTIVE=false
SKIP_BUILD=false
CLEANUP=false
WITH_GITHUB=false

# ─── Parse args ──────────────────────────────────────────────────────────────
for arg in "$@"; do
  case "$arg" in
    --interactive) INTERACTIVE=true ;;
    --skip-build)  SKIP_BUILD=true ;;
    --cleanup)     CLEANUP=true ;;
    --with-github) WITH_GITHUB=true ;;
    --help|-h)
      head -16 "$0" | tail -13
      exit 0
      ;;
  esac
done

# ─── State ───────────────────────────────────────────────────────────────────
FAILED=false
WARNINGS=0
WORKSTREAM_ID=""
WORKSPACE_DIR=""
WS_DIR=""
HAS_GH_REPO=false
EXIT_CODE=0
EVENT_COUNT=0
NODE_COUNT=0
RUN_COUNT=0
CURRENT_ROUND=0
FINAL_STATUS=""

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

# safe count: returns 0 when dir doesn't exist or has no matches.
fcount() {
  local dir="$1" pattern="$2"
  if [[ ! -d "$dir" ]]; then
    echo 0
    return 0
  fi
  find "$dir" -maxdepth 1 -name "$pattern" 2>/dev/null | wc -l | tr -d ' '
}

# ─── Cleanup trap ────────────────────────────────────────────────────────────
do_cleanup() {
  if [[ "$CLEANUP" != true ]]; then
    return 0
  fi
  step "Cleanup"
  log "Removing worktree and fixture repo..."
  # Remove worktree via git first (while fixture repo still exists),
  # so git worktree metadata is cleaned up properly.
  if [[ -n "$WORKSPACE_DIR" && -d "$FIXTURE_DIR/.git" && -d "$WORKSPACE_DIR" ]]; then
    if git -C "$FIXTURE_DIR" worktree remove --force "$WORKSPACE_DIR" 2>/dev/null; then
      ok "Worktree removed via git"
    else
      # Fallback: force-remove the directory if git command fails
      rm -rf "$WORKSPACE_DIR"
      warn "Worktree force-removed (git worktree remove failed)"
    fi
  elif [[ -n "$WORKSPACE_DIR" ]]; then
    rm -rf "$WORKSPACE_DIR"
  fi
  rm -rf "$FIXTURE_DIR"
  if [[ "$HAS_GH_REPO" == true ]]; then
    gh repo delete "$FIXTURE_REPO_NAME" --yes 2>/dev/null || warn "Could not delete GitHub repo"
  fi
  ok "Cleaned up"
}
trap do_cleanup EXIT

# ─── Phase 0: Setup ──────────────────────────────────────────────────────────
step "Phase 0: Setup"

# 0.1 Build
if [[ "$SKIP_BUILD" == false ]]; then
  log "Building project..."
  (cd "$PROJECT_ROOT" && mvn package -DskipTests -q) || {
    echo -e "${RED}[FATAL] mvn package failed${RESET}" >&2
    exit 1
  }
  ok "Build succeeded"
else
  log "Skipping build (--skip-build)"
  [[ -f "$JAR_PATH" ]] || {
    echo -e "${RED}[FATAL] JAR not found: $JAR_PATH. Run without --skip-build.${RESET}" >&2
    exit 1
  }
  ok "JAR exists"
fi

# 0.2 Verify dualctl
log "Verifying dualctl..."
dualctl --help > /dev/null 2>&1 || {
  echo -e "${RED}[FATAL] dualctl --help failed${RESET}" >&2
  exit 1
}
ok "dualctl is runnable"

# 0.3 Verify acpx (required — agent runs will fail without it)
if command -v acpx &> /dev/null; then
  ok "acpx found: $(acpx --version 2>/dev/null || echo 'version unknown')"
else
  echo -e "${RED}[FATAL] acpx not found. Full lifecycle smoke requires acpx for agent runs.${RESET}" >&2
  echo "  Install acpx or use --dry-run (not yet implemented)." >&2
  exit 1
fi

# 0.4 Verify gh (only matters with --with-github)
if [[ "$WITH_GITHUB" == true ]]; then
  if command -v gh &> /dev/null && gh auth status &> /dev/null 2>&1; then
    ok "gh CLI authenticated"
  else
    echo -e "${RED}[FATAL] --with-github requires gh CLI with auth${RESET}" >&2
    exit 1
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

# ─── Phase 1: Start ─────────────────────────────────────────────────────────
step "Phase 1: Start workstream"

log "Starting: $TASK_DESCRIPTION"
START_OUTPUT=$(dualctl start --run --workdir "$FIXTURE_DIR" --max-steps 20 "$TASK_DESCRIPTION" 2>&1) || {
  echo "$START_OUTPUT"
  echo -e "${RED}[FATAL] dualctl start --run failed${RESET}" >&2
  exit 1
}

# Extract workstream ID from output
WORKSTREAM_ID=$(echo "$START_OUTPUT" | sgrep "^Workstream:" | awk '{print $2}')
if [[ -z "$WORKSTREAM_ID" ]]; then
  echo "$START_OUTPUT"
  echo -e "${RED}[FATAL] Could not extract workstream ID from start output${RESET}" >&2
  exit 1
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

# ─── Phase 1.5: Verify ledger structure ──────────────────────────────────────
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
  if grep -q "goodbye" "$WS_DIR/requirement.md"; then
    ok "requirement.md contains task description"
  else
    warn "requirement.md may not contain full task description"
  fi
fi

# ─── Phase 2: Gate loop ─────────────────────────────────────────────────────
step "Phase 2: Gate approval loop"

# gate.human_next_stage_approval has edge: approved → intake.claude_brainstorm (loops!)
# To actually finish the workflow, we must use "review complete" on that gate.
FINAL_GATE="gate.human_next_stage_approval"
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

  IS_FINAL_GATE=false
  if [[ "$ACTIVE_NODE_DEF" == "$FINAL_GATE" ]]; then
    IS_FINAL_GATE=true
    log "Detected final stage gate: $FINAL_GATE"
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

# ─── Phase 3: Post-run verification ─────────────────────────────────────────
step "Phase 3: Post-run verification"

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

    # Check for actual event types from WorkflowFileLedger
    # Note: node_completed does NOT exist as an event — normal completion
    # writes node_routed for the NEXT node, not for the completing one.
    for event_type in "workflow_started" "node_routed" "iteration_started" "workflow_completed"; do
      COUNT=$(grep -c "\"$event_type\"" "$WS_DIR/events.jsonl" 2>/dev/null || true)
      COUNT=${COUNT:-0}
      if [[ "$COUNT" -gt 0 ]]; then
        ok "  $event_type: $COUNT"
      else
        warn "  $event_type: 0 (may not have been reached)"
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

  # Check each node for completion
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

# 3.5 Workflow definition matches expected
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

# 3.6 Delivery artifacts
log "Checking delivery artifacts..."
DELIVERY_DIR="$WS_DIR/delivery"
if [[ -d "$DELIVERY_DIR" ]]; then
  ok "delivery/ directory exists"
  verify_file "$DELIVERY_DIR/delivery.json"
  if [[ -f "$DELIVERY_DIR/delivery.json" ]]; then
    DEL_STATUS=$(python3 -c "import json; print(json.load(open('$DELIVERY_DIR/delivery.json')).get('status','?'))" 2>/dev/null || echo "?")
    ok "Delivery status: $DEL_STATUS"
  fi
else
  if [[ "$FINAL_STATUS" == "COMPLETED" ]]; then
    fail "delivery/ directory missing (workflow completed)"
  else
    warn "delivery/ directory missing (workflow did not reach delivery phase)"
  fi
fi

# 3.7 Final report — actual path: reports/latest-final-report.md
log "Checking final report..."
REPORT_PATH="$WS_DIR/reports/latest-final-report.md"
if [[ -f "$REPORT_PATH" ]]; then
  REPORT_SIZE=$(wc -c < "$REPORT_PATH" | tr -d ' ')
  ok "Final report: $REPORT_SIZE bytes"

  for section in "User Request" "Acceptance Summary" "Git Evidence" "Recent Agent Outputs"; do
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

# 3.8 Worktree changes
log "Checking worktree changes..."
if [[ -d "$WORKSPACE_DIR" ]]; then
  CHANGES=false
  # Uncommitted changes (most likely during/after Agent runs)
  UNSTAGED=$(cd "$WORKSPACE_DIR" && git status --short 2>/dev/null || echo "")
  if [[ -n "$UNSTAGED" ]]; then
    ok "Uncommitted changes in worktree:"
    echo "$UNSTAGED" | while read -r line; do
      log "  $line"
    done
    CHANGES=true
  fi
  # Committed changes beyond the initial commit
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

# 3.9 Next-stage prompt — actual path: artifacts/next-stage/next-stage-prompt.md
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

# ─── Phase 4: Events timeline ───────────────────────────────────────────────
step "Phase 4: Events timeline"

log "Full event log:"
dualctl events --workdir "$WORKSPACE_DIR" --limit 100 "$WORKSTREAM_ID" 2>&1 || warn "events command failed"

# ─── Report ──────────────────────────────────────────────────────────────────
step "Smoke Test Report"

echo ""
echo "  Workstream:   $WORKSTREAM_ID"
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

if [[ "$FINAL_STATUS" == "COMPLETED" && "$FAILED" == false ]]; then
  echo -e "  ${GREEN}${BOLD}RESULT: PASS${RESET}"
  EXIT_CODE=0
elif [[ "$FINAL_STATUS" == "COMPLETED" ]]; then
  echo -e "  ${YELLOW}${BOLD}RESULT: PASS WITH ISSUES${RESET} (some verifications failed)"
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
