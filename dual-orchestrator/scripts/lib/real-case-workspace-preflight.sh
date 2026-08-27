#!/usr/bin/env bash
# Workspace-bound eligibility evidence for real-case runs.

real_case_workspace_sha256_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

real_case_workspace_sha256_text() {
  printf '%s' "$1" | shasum -a 256 | awk '{print $1}'
}

real_case_workspace_canonical_dir() {
  (cd "$1" >/dev/null 2>&1 && pwd -P)
}

real_case_workspace_terminal_digest() {
  local workstream_id="$1"
  local workspace_id="$2"
  local workspace="$3"
  local head="$4"
  local tree="$5"
  local check_sha="$6"
  local exit_code="$7"
  local pre_status_sha="$8"
  local post_status_sha="$9"
  local stdout_sha="${10}"
  local stderr_sha="${11}"
  real_case_workspace_sha256_text "$(printf '%s\n' \
    "$workstream_id" \
    "$workspace_id" \
    "$workspace" \
    "$head" \
    "$tree" \
    "$check_sha" \
    "$exit_code" \
    "$pre_status_sha" \
    "$post_status_sha" \
    "$stdout_sha" \
    "$stderr_sha")"
}

real_case_workspace_resolve_identity() {
  local source_root="$1"
  local candidate_workspace="$2"
  local expected_workstream_id="$3"
  local canonical_source canonical_candidate workstream_json primary_workspace_id workspace_json
  local persisted_workstream_id persisted_git_root persisted_workspace_id workspace_workstream_id
  local original_workdir effective_workdir canonical_git_root canonical_original canonical_effective

  canonical_source="$(real_case_workspace_canonical_dir "$source_root")" || return 1
  canonical_candidate="$(real_case_workspace_canonical_dir "$candidate_workspace")" || return 1
  workstream_json="$canonical_candidate/.agent/workstreams/$expected_workstream_id/workstream.json"
  [[ -f "$workstream_json" ]] || return 1

  persisted_workstream_id="$(jq -er '.workstreamId | select(type == "string" and length > 0)' "$workstream_json" 2>/dev/null)" || return 1
  persisted_git_root="$(jq -er '.repository.gitRoot | select(type == "string" and length > 0)' "$workstream_json" 2>/dev/null)" || return 1
  primary_workspace_id="$(jq -er '.primaryWorkspaceId | select(type == "string" and length > 0)' "$workstream_json" 2>/dev/null)" || return 1
  [[ "$persisted_workstream_id" == "$expected_workstream_id" ]] || return 1
  canonical_git_root="$(real_case_workspace_canonical_dir "$persisted_git_root")" || return 1
  [[ "$canonical_git_root" == "$canonical_source" ]] || return 1

  workspace_json="$canonical_candidate/.agent/workstreams/$expected_workstream_id/workspaces/$primary_workspace_id.json"
  [[ -f "$workspace_json" ]] || return 1
  persisted_workspace_id="$(jq -er '.workspaceId | select(type == "string" and length > 0)' "$workspace_json" 2>/dev/null)" || return 1
  workspace_workstream_id="$(jq -er '.workstreamId | select(type == "string" and length > 0)' "$workspace_json" 2>/dev/null)" || return 1
  original_workdir="$(jq -er '.originalWorkdir | select(type == "string" and length > 0)' "$workspace_json" 2>/dev/null)" || return 1
  effective_workdir="$(jq -er '.effectiveWorkdir | select(type == "string" and length > 0)' "$workspace_json" 2>/dev/null)" || return 1
  [[ "$persisted_workspace_id" == "$primary_workspace_id" ]] || return 1
  [[ "$workspace_workstream_id" == "$expected_workstream_id" ]] || return 1
  canonical_original="$(real_case_workspace_canonical_dir "$original_workdir")" || return 1
  canonical_effective="$(real_case_workspace_canonical_dir "$effective_workdir")" || return 1
  [[ "$canonical_original" == "$canonical_source" ]] || return 1
  [[ "$canonical_effective" == "$canonical_candidate" ]] || return 1
  [[ "$(git -C "$canonical_effective" rev-parse --show-toplevel 2>/dev/null)" == "$canonical_effective" ]] || return 1

  REAL_CASE_RESOLVED_SOURCE_ROOT="$canonical_source"
  REAL_CASE_RESOLVED_WORKSPACE="$canonical_effective"
  REAL_CASE_RESOLVED_WORKSTREAM_ID="$persisted_workstream_id"
  REAL_CASE_RESOLVED_WORKSPACE_ID="$persisted_workspace_id"
  REAL_CASE_RESOLVED_WORKSTREAM_JSON="$workstream_json"
  REAL_CASE_RESOLVED_WORKSPACE_JSON="$workspace_json"
}

real_case_workspace_write_evidence() {
  local evidence="$1"
  local status="$2"
  local reason="$3"
  local verification_status="$4"
  local source_root="$5"
  local workstream_id="$6"
  local workspace_id="$7"
  local workspace="$8"
  local expected_head="$9"
  local expected_tree="${10}"
  local workspace_head="${11}"
  local workspace_tree="${12}"
  local check_path="${13}"
  local expected_check_sha="${14}"
  local actual_check_sha="${15}"
  local started_at="${16}"
  local finished_at="${17}"
  local exit_code="${18}"
  local stdout_path="${19}"
  local stderr_path="${20}"
  local pre_status_path="${21}"
  local post_status_path="${22}"
  local untracked_status_path="${23}"
  local terminal_digest="${24}"
  local tmp="$evidence.tmp"
  local stdout_sha="" stderr_sha="" pre_sha="" post_sha="" untracked_sha=""

  [[ -f "$stdout_path" ]] && stdout_sha="$(real_case_workspace_sha256_file "$stdout_path")"
  [[ -f "$stderr_path" ]] && stderr_sha="$(real_case_workspace_sha256_file "$stderr_path")"
  [[ -f "$pre_status_path" ]] && pre_sha="$(real_case_workspace_sha256_file "$pre_status_path")"
  [[ -f "$post_status_path" ]] && post_sha="$(real_case_workspace_sha256_file "$post_status_path")"
  [[ -f "$untracked_status_path" ]] && untracked_sha="$(real_case_workspace_sha256_file "$untracked_status_path")"

  jq -n \
    --arg status "$status" \
    --arg reason "$reason" \
    --arg verificationStatus "$verification_status" \
    --arg sourceRoot "$source_root" \
    --arg workstreamId "$workstream_id" \
    --arg workspaceId "$workspace_id" \
    --arg workspace "$workspace" \
    --arg expectedHead "$expected_head" \
    --arg expectedTree "$expected_tree" \
    --arg workspaceHead "$workspace_head" \
    --arg workspaceTree "$workspace_tree" \
    --arg checkPath "$check_path" \
    --arg expectedCheckSha256 "$expected_check_sha" \
    --arg checkSha256 "$actual_check_sha" \
    --arg startedAt "$started_at" \
    --arg finishedAt "$finished_at" \
    --argjson exitCode "$exit_code" \
    --arg stdoutPath "$stdout_path" \
    --arg stdoutSha256 "$stdout_sha" \
    --arg stderrPath "$stderr_path" \
    --arg stderrSha256 "$stderr_sha" \
    --arg preTrackedStatusPath "$pre_status_path" \
    --arg preTrackedStatusSha256 "$pre_sha" \
    --arg postTrackedStatusPath "$post_status_path" \
    --arg postTrackedStatusSha256 "$post_sha" \
    --arg postUntrackedStatusPath "$untracked_status_path" \
    --arg postUntrackedStatusSha256 "$untracked_sha" \
    --arg terminalDigest "$terminal_digest" \
    '{
      schemaVersion: 1,
      status: $status,
      reason: $reason,
      verificationStatus: $verificationStatus,
      sourceRoot: $sourceRoot,
      workstreamId: $workstreamId,
      workspaceId: $workspaceId,
      effectiveWorkdir: $workspace,
      expectedHead: $expectedHead,
      expectedTree: $expectedTree,
      workspaceHead: $workspaceHead,
      workspaceTree: $workspaceTree,
      checkPath: $checkPath,
      expectedCheckSha256: $expectedCheckSha256,
      checkSha256: $checkSha256,
      startedAt: $startedAt,
      finishedAt: $finishedAt,
      exitCode: $exitCode,
      stdoutPath: $stdoutPath,
      stdoutSha256: $stdoutSha256,
      stderrPath: $stderrPath,
      stderrSha256: $stderrSha256,
      preTrackedStatusPath: $preTrackedStatusPath,
      preTrackedStatusSha256: $preTrackedStatusSha256,
      postTrackedStatusPath: $postTrackedStatusPath,
      postTrackedStatusSha256: $postTrackedStatusSha256,
      postUntrackedStatusPath: $postUntrackedStatusPath,
      postUntrackedStatusSha256: $postUntrackedStatusSha256,
      terminalDigest: $terminalDigest,
      verifiedAt: ""
    }' >"$tmp" || return 1
  mv "$tmp" "$evidence"
}

real_case_workspace_preflight_capture() {
  local source_root="$1"
  local candidate_workspace="$2"
  local workstream_id="$3"
  local expected_head="$4"
  local expected_tree="$5"
  local check_path="$6"
  local expected_check_sha="$7"
  local evidence_dir="$8"
  local evidence="$evidence_dir/workspace-preflight.json"
  local stdout_path="$evidence_dir/workspace-preflight.stdout.log"
  local stderr_path="$evidence_dir/workspace-preflight.stderr.log"
  local pre_status_path="$evidence_dir/workspace-preflight.pre-tracked-status.txt"
  local post_status_path="$evidence_dir/workspace-preflight.post-tracked-status.txt"
  local untracked_status_path="$evidence_dir/workspace-preflight.post-untracked-status.txt"
  local started_at finished_at status="failed" reason="unknown" verification_status="not_run"
  local workspace="" workspace_id="" workspace_head="" workspace_tree="" actual_check_sha=""
  local exit_code=1 terminal_digest="" pre_status="" post_status=""

  mkdir -p "$evidence_dir" || return 1
  : >"$stdout_path"
  : >"$stderr_path"
  : >"$pre_status_path"
  : >"$post_status_path"
  : >"$untracked_status_path"
  started_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"

  if ! real_case_workspace_resolve_identity "$source_root" "$candidate_workspace" "$workstream_id"; then
    reason="workspace_identity_invalid"
  else
    workspace="$REAL_CASE_RESOLVED_WORKSPACE"
    workspace_id="$REAL_CASE_RESOLVED_WORKSPACE_ID"
    workspace_head="$(git -C "$workspace" rev-parse HEAD 2>/dev/null || true)"
    workspace_tree="$(git -C "$workspace" rev-parse 'HEAD^{tree}' 2>/dev/null || true)"
    if [[ "$workspace_head" != "$expected_head" || "$workspace_tree" != "$expected_tree" ]]; then
      reason="workspace_git_identity_mismatch"
    elif [[ ! -f "$check_path" || ! -x "$check_path" ]]; then
      reason="check_missing_or_not_executable"
    else
      actual_check_sha="$(real_case_workspace_sha256_file "$check_path" 2>/dev/null || true)"
      if [[ -z "$actual_check_sha" || "$actual_check_sha" != "$expected_check_sha" ]]; then
        reason="check_sha_mismatch"
      else
        pre_status="$(git -C "$workspace" status --porcelain=v1 --untracked-files=no 2>/dev/null || true)"
        printf '%s' "$pre_status" >"$pre_status_path"
        if [[ -n "$pre_status" ]]; then
          reason="pre_check_tracked_dirty"
        else
          if (
            cd "$workspace" || exit 125
            DUAL_WORKSTREAM_ID="$workstream_id" \
              DUAL_EXECUTION_WORKSPACE="$workspace" \
              "$check_path"
          ) >"$stdout_path" 2>"$stderr_path"; then
            exit_code=0
          else
            exit_code="$?"
          fi
          post_status="$(git -C "$workspace" status --porcelain=v1 --untracked-files=no 2>/dev/null || true)"
          printf '%s' "$post_status" >"$post_status_path"
          git -C "$workspace" status --porcelain=v1 --untracked-files=all --ignored=matching \
            >"$untracked_status_path" 2>/dev/null || true
          if [[ "$exit_code" -ne 0 ]]; then
            reason="check_nonzero_exit"
          elif [[ -n "$post_status" ]]; then
            reason="post_check_tracked_dirty"
          else
            status="passed"
            reason="workspace_check_passed"
            verification_status="pending"
          fi
        fi
      fi
    fi
  fi

  finished_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  terminal_digest="$(real_case_workspace_terminal_digest \
    "$workstream_id" "$workspace_id" "$workspace" "$workspace_head" "$workspace_tree" \
    "$actual_check_sha" "$exit_code" \
    "$(real_case_workspace_sha256_file "$pre_status_path")" \
    "$(real_case_workspace_sha256_file "$post_status_path")" \
    "$(real_case_workspace_sha256_file "$stdout_path")" \
    "$(real_case_workspace_sha256_file "$stderr_path")")"
  real_case_workspace_write_evidence \
    "$evidence" "$status" "$reason" "$verification_status" "$source_root" "$workstream_id" \
    "$workspace_id" "$workspace" "$expected_head" "$expected_tree" "$workspace_head" "$workspace_tree" \
    "$check_path" "$expected_check_sha" "$actual_check_sha" "$started_at" "$finished_at" "$exit_code" \
    "$stdout_path" "$stderr_path" "$pre_status_path" "$post_status_path" "$untracked_status_path" "$terminal_digest" || return 1
  [[ "$status" == "passed" ]]
}

real_case_workspace_preflight_mark_verify() {
  local evidence="$1"
  local status="$2"
  local reason="$3"
  local verification_status="$4"
  local verified_at tmp
  verified_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  tmp="$evidence.tmp"
  jq \
    --arg status "$status" \
    --arg reason "$reason" \
    --arg verificationStatus "$verification_status" \
    --arg verifiedAt "$verified_at" \
    '.status = $status | .reason = $reason | .verificationStatus = $verificationStatus | .verifiedAt = $verifiedAt' \
    "$evidence" >"$tmp" || return 1
  mv "$tmp" "$evidence"
}

real_case_workspace_preflight_verify() {
  local evidence="$1"
  local source_root candidate_workspace workstream_id expected_head expected_tree check_path expected_check_sha
  local expected_workspace_id expected_workspace expected_terminal expected_post_sha expected_exit
  local workspace_head workspace_tree current_status current_status_sha actual_check_sha terminal_digest reason=""
  local expected_stdout_sha expected_stderr_sha actual_stdout_sha actual_stderr_sha stdout_path stderr_path

  [[ -f "$evidence" ]] || return 1
  [[ "$(jq -r '.status // ""' "$evidence" 2>/dev/null)" == "passed" ]] || return 1
  source_root="$(jq -er '.sourceRoot' "$evidence" 2>/dev/null)" || return 1
  candidate_workspace="$(jq -er '.effectiveWorkdir' "$evidence" 2>/dev/null)" || return 1
  workstream_id="$(jq -er '.workstreamId' "$evidence" 2>/dev/null)" || return 1
  expected_workspace_id="$(jq -er '.workspaceId' "$evidence" 2>/dev/null)" || return 1
  expected_workspace="$candidate_workspace"
  expected_head="$(jq -er '.expectedHead' "$evidence" 2>/dev/null)" || return 1
  expected_tree="$(jq -er '.expectedTree' "$evidence" 2>/dev/null)" || return 1
  check_path="$(jq -er '.checkPath' "$evidence" 2>/dev/null)" || return 1
  expected_check_sha="$(jq -er '.expectedCheckSha256' "$evidence" 2>/dev/null)" || return 1
  expected_terminal="$(jq -er '.terminalDigest' "$evidence" 2>/dev/null)" || return 1
  expected_post_sha="$(jq -er '.postTrackedStatusSha256' "$evidence" 2>/dev/null)" || return 1
  expected_exit="$(jq -er '.exitCode' "$evidence" 2>/dev/null)" || return 1
  stdout_path="$(jq -er '.stdoutPath' "$evidence" 2>/dev/null)" || return 1
  stderr_path="$(jq -er '.stderrPath' "$evidence" 2>/dev/null)" || return 1
  expected_stdout_sha="$(jq -er '.stdoutSha256' "$evidence" 2>/dev/null)" || return 1
  expected_stderr_sha="$(jq -er '.stderrSha256' "$evidence" 2>/dev/null)" || return 1

  if ! real_case_workspace_resolve_identity "$source_root" "$candidate_workspace" "$workstream_id"; then
    reason="pre_run_workspace_identity_changed"
  elif [[ "$REAL_CASE_RESOLVED_WORKSPACE_ID" != "$expected_workspace_id" || "$REAL_CASE_RESOLVED_WORKSPACE" != "$expected_workspace" ]]; then
    reason="pre_run_workspace_identity_changed"
  elif [[ ! -f "$check_path" ]]; then
    reason="pre_run_check_missing"
  else
    workspace_head="$(git -C "$expected_workspace" rev-parse HEAD 2>/dev/null || true)"
    workspace_tree="$(git -C "$expected_workspace" rev-parse 'HEAD^{tree}' 2>/dev/null || true)"
    current_status="$(git -C "$expected_workspace" status --porcelain=v1 --untracked-files=no 2>/dev/null || true)"
    current_status_sha="$(real_case_workspace_sha256_text "$current_status")"
    actual_check_sha="$(real_case_workspace_sha256_file "$check_path" 2>/dev/null || true)"
    actual_stdout_sha="$(real_case_workspace_sha256_file "$stdout_path" 2>/dev/null || true)"
    actual_stderr_sha="$(real_case_workspace_sha256_file "$stderr_path" 2>/dev/null || true)"
    terminal_digest="$(real_case_workspace_terminal_digest \
      "$workstream_id" "$expected_workspace_id" "$expected_workspace" "$workspace_head" "$workspace_tree" \
      "$actual_check_sha" "$expected_exit" \
      "$(jq -r '.preTrackedStatusSha256' "$evidence")" "$current_status_sha" \
      "$actual_stdout_sha" "$actual_stderr_sha")"
    if [[ "$workspace_head" != "$expected_head" || "$workspace_tree" != "$expected_tree" ]]; then
      reason="pre_run_git_identity_changed"
    elif [[ -n "$current_status" || "$current_status_sha" != "$expected_post_sha" ]]; then
      reason="pre_run_tracked_status_changed"
    elif [[ "$actual_check_sha" != "$expected_check_sha" ]]; then
      reason="pre_run_check_sha_changed"
    elif [[ "$actual_stdout_sha" != "$expected_stdout_sha" || "$actual_stderr_sha" != "$expected_stderr_sha" ]]; then
      reason="pre_run_output_evidence_changed"
    elif [[ "$terminal_digest" != "$expected_terminal" ]]; then
      reason="pre_run_terminal_digest_changed"
    fi
  fi

  if [[ -n "$reason" ]]; then
    real_case_workspace_preflight_mark_verify "$evidence" "failed" "$reason" "failed" || true
    return 1
  fi
  real_case_workspace_preflight_mark_verify "$evidence" "passed" "workspace_check_verified_before_run" "passed"
}

real_case_workspace_preflight_usage() {
  cat <<'EOF'
Usage:
  real-case-workspace-preflight.sh capture --source-root <path> --workspace <path> --workstream <id> --expected-head <sha> --expected-tree <sha> --check <path> --expected-check-sha <sha> --evidence-dir <path>
  real-case-workspace-preflight.sh verify --evidence <workspace-preflight.json>
EOF
}

real_case_workspace_preflight_main() {
  local action="${1:-}"
  [[ -n "$action" ]] || { real_case_workspace_preflight_usage >&2; return 2; }
  shift
  local source_root="" workspace="" workstream="" expected_head="" expected_tree=""
  local check="" expected_check_sha="" evidence_dir="" evidence=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --source-root) shift; source_root="${1:-}" ;;
      --workspace) shift; workspace="${1:-}" ;;
      --workstream) shift; workstream="${1:-}" ;;
      --expected-head) shift; expected_head="${1:-}" ;;
      --expected-tree) shift; expected_tree="${1:-}" ;;
      --check) shift; check="${1:-}" ;;
      --expected-check-sha) shift; expected_check_sha="${1:-}" ;;
      --evidence-dir) shift; evidence_dir="${1:-}" ;;
      --evidence) shift; evidence="${1:-}" ;;
      *) real_case_workspace_preflight_usage >&2; return 2 ;;
    esac
    shift
  done
  case "$action" in
    capture)
      [[ -n "$source_root" && -n "$workspace" && -n "$workstream" && -n "$expected_head" \
        && -n "$expected_tree" && -n "$check" && -n "$expected_check_sha" && -n "$evidence_dir" ]] || return 2
      real_case_workspace_preflight_capture "$source_root" "$workspace" "$workstream" "$expected_head" \
        "$expected_tree" "$check" "$expected_check_sha" "$evidence_dir"
      ;;
    verify)
      [[ -n "$evidence" ]] || return 2
      real_case_workspace_preflight_verify "$evidence"
      ;;
    *) real_case_workspace_preflight_usage >&2; return 2 ;;
  esac
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  set -euo pipefail
  real_case_workspace_preflight_main "$@"
fi
