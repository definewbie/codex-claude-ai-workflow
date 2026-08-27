#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

# shellcheck source=scripts/lib/real-case-workspace-preflight.sh
source "$ROOT/scripts/lib/real-case-workspace-preflight.sh"

assert_eq() {
  local expected="$1"
  local actual="$2"
  local label="$3"
  if [[ "$expected" != "$actual" ]]; then
    printf '[FAIL] %s: expected %s, got %s\n' "$label" "$expected" "$actual" >&2
    exit 1
  fi
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

source_root="$tmp/source repo"
workspace="$tmp/execution workspace"
check="$tmp/check scripts/workspace check.sh"
workstream_id="ws-space-path"
workspace_id="wsp-space-path"
mkdir -p "$source_root" "$(dirname "$check")"
git -C "$source_root" init -q
git -C "$source_root" config user.email test@example.com
git -C "$source_root" config user.name Test
printf 'baseline\n' >"$source_root/tracked.txt"
git -C "$source_root" add tracked.txt
git -C "$source_root" commit -qm baseline
git -C "$source_root" worktree add -q -b "test/$workstream_id" "$workspace"

ledger="$workspace/.agent/workstreams/$workstream_id"
mkdir -p "$ledger/workspaces"
jq -n \
  --arg id "$workstream_id" \
  --arg root "$source_root" \
  --arg workspaceId "$workspace_id" \
  '{schemaVersion:1, workstreamId:$id, repository:{originalWorkdir:$root, gitRoot:$root}, primaryWorkspaceId:$workspaceId}' \
  >"$ledger/workstream.json"
jq -n \
  --arg workspaceId "$workspace_id" \
  --arg workstreamId "$workstream_id" \
  --arg original "$source_root" \
  --arg effective "$workspace" \
  '{schemaVersion:1, workspaceId:$workspaceId, workstreamId:$workstreamId, originalWorkdir:$original, effectiveWorkdir:$effective}' \
  >"$ledger/workspaces/$workspace_id.json"

cat >"$check" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
[[ "$PWD" == "$DUAL_EXECUTION_WORKSPACE" ]]
[[ -n "$DUAL_WORKSTREAM_ID" ]]
printf 'workspace check passed\n'
printf 'diagnostic only\n' >&2
touch .workspace-check-cache
EOF
chmod +x "$check"

head_sha="$(git -C "$source_root" rev-parse HEAD)"
tree_sha="$(git -C "$source_root" rev-parse 'HEAD^{tree}')"
check_sha="$(real_case_workspace_sha256_file "$check")"
evidence_dir="$tmp/evidence with spaces"

"$ROOT/scripts/lib/real-case-workspace-preflight.sh" capture \
  --source-root "$source_root" \
  --workspace "$workspace" \
  --workstream "$workstream_id" \
  --expected-head "$head_sha" \
  --expected-tree "$tree_sha" \
  --check "$check" \
  --expected-check-sha "$check_sha" \
  --evidence-dir "$evidence_dir"
evidence="$evidence_dir/workspace-preflight.json"
assert_eq "passed" "$(jq -r '.status' "$evidence")" "space-path capture status"
canonical_workspace="$(real_case_workspace_canonical_dir "$workspace")"
assert_eq "$canonical_workspace" "$(jq -r '.effectiveWorkdir' "$evidence")" "persisted workspace path"
assert_eq "$check" "$(jq -r '.checkPath' "$evidence")" "space-path check identity"
assert_eq "pending" "$(jq -r '.verificationStatus' "$evidence")" "capture awaits pre-run verify"
grep -q 'workspace check passed' "$evidence_dir/workspace-preflight.stdout.log"
grep -q '.workspace-check-cache' "$evidence_dir/workspace-preflight.post-untracked-status.txt"
"$ROOT/scripts/lib/real-case-workspace-preflight.sh" verify --evidence "$evidence"
assert_eq "passed" "$(jq -r '.verificationStatus' "$evidence")" "pre-run verification status"
assert_eq "workspace_check_verified_before_run" "$(jq -r '.reason' "$evidence")" "pre-run verification reason"

nonzero_check="$tmp/nonzero-check.sh"
cat >"$nonzero_check" <<'EOF'
#!/usr/bin/env bash
printf 'PASS\n'
exit 9
EOF
chmod +x "$nonzero_check"
nonzero_dir="$tmp/nonzero-evidence"
if real_case_workspace_preflight_capture \
  "$source_root" "$workspace" "$workstream_id" "$head_sha" "$tree_sha" "$nonzero_check" \
  "$(real_case_workspace_sha256_file "$nonzero_check")" "$nonzero_dir"; then
  printf '[FAIL] nonzero check was accepted because stdout said PASS\n' >&2
  exit 1
fi
assert_eq "check_nonzero_exit" "$(jq -r '.reason' "$nonzero_dir/workspace-preflight.json")" \
  "exit code owns check result"

missing_check="$tmp/missing-check.sh"
missing_dir="$tmp/missing-evidence"
if real_case_workspace_preflight_capture \
  "$source_root" "$workspace" "$workstream_id" "$head_sha" "$tree_sha" "$missing_check" "missing" "$missing_dir"; then
  printf '[FAIL] missing check was accepted\n' >&2
  exit 1
fi
assert_eq "check_missing_or_not_executable" "$(jq -r '.reason' "$missing_dir/workspace-preflight.json")" \
  "missing check reason"

printf 'dirty\n' >>"$workspace/tracked.txt"
dirty_dir="$tmp/dirty-evidence"
if real_case_workspace_preflight_capture \
  "$source_root" "$workspace" "$workstream_id" "$head_sha" "$tree_sha" "$check" "$check_sha" "$dirty_dir"; then
  printf '[FAIL] tracked-dirty workspace was accepted\n' >&2
  exit 1
fi
assert_eq "pre_check_tracked_dirty" "$(jq -r '.reason' "$dirty_dir/workspace-preflight.json")" \
  "pre-check tracked dirty reason"
git -C "$workspace" restore tracked.txt

dirtying_check="$tmp/dirtying-check.sh"
cat >"$dirtying_check" <<'EOF'
#!/usr/bin/env bash
printf 'changed by check\n' >> tracked.txt
EOF
chmod +x "$dirtying_check"
post_dirty_dir="$tmp/post-dirty-evidence"
if real_case_workspace_preflight_capture \
  "$source_root" "$workspace" "$workstream_id" "$head_sha" "$tree_sha" "$dirtying_check" \
  "$(real_case_workspace_sha256_file "$dirtying_check")" "$post_dirty_dir"; then
  printf '[FAIL] check that dirtied tracked files was accepted\n' >&2
  exit 1
fi
assert_eq "post_check_tracked_dirty" "$(jq -r '.reason' "$post_dirty_dir/workspace-preflight.json")" \
  "post-check tracked dirty reason"
git -C "$workspace" restore tracked.txt

identity_dir="$tmp/identity-evidence"
jq '.workstreamId = "ws-other"' "$ledger/workspaces/$workspace_id.json" >"$tmp/workspace-mutated.json"
mv "$tmp/workspace-mutated.json" "$ledger/workspaces/$workspace_id.json"
if real_case_workspace_preflight_capture \
  "$source_root" "$workspace" "$workstream_id" "$head_sha" "$tree_sha" "$check" "$check_sha" "$identity_dir"; then
  printf '[FAIL] mismatched persisted identity was accepted\n' >&2
  exit 1
fi
assert_eq "workspace_identity_invalid" "$(jq -r '.reason' "$identity_dir/workspace-preflight.json")" \
  "persisted identity mismatch"
jq --arg id "$workstream_id" '.workstreamId = $id' "$ledger/workspaces/$workspace_id.json" >"$tmp/workspace-restored.json"
mv "$tmp/workspace-restored.json" "$ledger/workspaces/$workspace_id.json"

drift_dir="$tmp/drift-evidence"
real_case_workspace_preflight_capture \
  "$source_root" "$workspace" "$workstream_id" "$head_sha" "$tree_sha" "$check" "$check_sha" "$drift_dir"
printf 'changed before run\n' >>"$workspace/tracked.txt"
if real_case_workspace_preflight_verify "$drift_dir/workspace-preflight.json"; then
  printf '[FAIL] pre-run tracked drift was accepted\n' >&2
  exit 1
fi
assert_eq "failed" "$(jq -r '.verificationStatus' "$drift_dir/workspace-preflight.json")" \
  "pre-run drift verification status"
assert_eq "pre_run_tracked_status_changed" "$(jq -r '.reason' "$drift_dir/workspace-preflight.json")" \
  "pre-run drift reason"
git -C "$workspace" restore tracked.txt

ledger_drift_dir="$tmp/ledger-drift-evidence"
real_case_workspace_preflight_capture \
  "$source_root" "$workspace" "$workstream_id" "$head_sha" "$tree_sha" "$check" "$check_sha" "$ledger_drift_dir"
jq '.primaryWorkspaceId = "wsp-other"' "$ledger/workstream.json" >"$tmp/workstream-mutated.json"
mv "$tmp/workstream-mutated.json" "$ledger/workstream.json"
if real_case_workspace_preflight_verify "$ledger_drift_dir/workspace-preflight.json"; then
  printf '[FAIL] pre-run primary workspace drift was accepted\n' >&2
  exit 1
fi
assert_eq "pre_run_workspace_identity_changed" "$(jq -r '.reason' "$ledger_drift_dir/workspace-preflight.json")" \
  "pre-run persisted identity drift reason"
jq --arg id "$workspace_id" '.primaryWorkspaceId = $id' "$ledger/workstream.json" >"$tmp/workstream-restored.json"
mv "$tmp/workstream-restored.json" "$ledger/workstream.json"

check_drift_dir="$tmp/check-drift-evidence"
real_case_workspace_preflight_capture \
  "$source_root" "$workspace" "$workstream_id" "$head_sha" "$tree_sha" "$check" "$check_sha" "$check_drift_dir"
cp "$check" "$tmp/original-check.sh"
printf '# changed before run\n' >>"$check"
if real_case_workspace_preflight_verify "$check_drift_dir/workspace-preflight.json"; then
  printf '[FAIL] pre-run check script drift was accepted\n' >&2
  exit 1
fi
assert_eq "pre_run_check_sha_changed" "$(jq -r '.reason' "$check_drift_dir/workspace-preflight.json")" \
  "pre-run check SHA drift reason"
cp "$tmp/original-check.sh" "$check"
chmod +x "$check"

output_drift_dir="$tmp/output-drift-evidence"
real_case_workspace_preflight_capture \
  "$source_root" "$workspace" "$workstream_id" "$head_sha" "$tree_sha" "$check" "$check_sha" "$output_drift_dir"
printf 'tampered before run\n' >>"$output_drift_dir/workspace-preflight.stdout.log"
if real_case_workspace_preflight_verify "$output_drift_dir/workspace-preflight.json"; then
  printf '[FAIL] pre-run output evidence drift was accepted\n' >&2
  exit 1
fi
assert_eq "pre_run_output_evidence_changed" "$(jq -r '.reason' "$output_drift_dir/workspace-preflight.json")" \
  "pre-run output evidence drift reason"

head_drift_dir="$tmp/head-drift-evidence"
real_case_workspace_preflight_capture \
  "$source_root" "$workspace" "$workstream_id" "$head_sha" "$tree_sha" "$check" "$check_sha" "$head_drift_dir"
printf 'new commit\n' >"$workspace/new-file.txt"
git -C "$workspace" add new-file.txt
git -C "$workspace" commit -qm 'move head before run'
if real_case_workspace_preflight_verify "$head_drift_dir/workspace-preflight.json"; then
  printf '[FAIL] pre-run HEAD/tree drift was accepted\n' >&2
  exit 1
fi
assert_eq "pre_run_git_identity_changed" "$(jq -r '.reason' "$head_drift_dir/workspace-preflight.json")" \
  "pre-run HEAD/tree drift reason"

printf '[PASS] real-case workspace preflight tests\n'
