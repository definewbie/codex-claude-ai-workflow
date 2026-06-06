#!/usr/bin/env bash
#
# prepare-public-benchmark-repo.sh — Copy a public benchmark sample into a
# disposable git repository that can be passed to run-real-case.sh.
#
# Usage:
#   ./scripts/prepare-public-benchmark-repo.sh
#   ./scripts/prepare-public-benchmark-repo.sh --dest /tmp/issue-flow-benchmark
#

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_DIR="$PROJECT_ROOT/examples/issue-flow-app"
DEST_DIR=""

usage() {
  cat <<'EOF'
Usage:
  prepare-public-benchmark-repo.sh [--dest <path>]

Options:
  --dest <path>  Destination directory. Defaults to a timestamped directory
                 under /tmp.
  -h, --help     Show this help.
EOF
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dest)
        shift
        DEST_DIR="${1:-}"
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        printf 'Unknown argument: %s\n' "$1" >&2
        usage
        exit 1
        ;;
    esac
    shift
  done
}

default_dest() {
  local stamp
  stamp="$(date -u '+%Y%m%dT%H%M%SZ')"
  printf '/tmp/dual-public-issue-flow-%s\n' "$stamp"
}

copy_sample() {
  [[ -d "$SOURCE_DIR" ]] || {
    printf 'Sample source not found: %s\n' "$SOURCE_DIR" >&2
    exit 1
  }

  if [[ -e "$DEST_DIR" ]]; then
    printf 'Destination already exists: %s\n' "$DEST_DIR" >&2
    exit 1
  fi

  mkdir -p "$DEST_DIR"
  cp -R "$SOURCE_DIR"/. "$DEST_DIR"/
}

init_git_repo() {
  git -C "$DEST_DIR" init -b main >/dev/null
  git -C "$DEST_DIR" config user.name "dual benchmark"
  git -C "$DEST_DIR" config user.email "dual-benchmark@example.invalid"
  git -C "$DEST_DIR" add .
  git -C "$DEST_DIR" commit -m "chore: seed issue flow benchmark app" >/dev/null
}

print_next_steps() {
  cat <<EOF
Prepared public benchmark repo:
  $DEST_DIR

Run:
  ./scripts/run-real-case.sh \\
    --repo "$DEST_DIR" \\
    --task-file "$PROJECT_ROOT/docs/testing/real-case-benchmarks/cases/public-issue-flow-review-state.md" \\
    --variant full
EOF
}

main() {
  parse_args "$@"
  if [[ -z "$DEST_DIR" ]]; then
    DEST_DIR="$(default_dest)"
  fi
  copy_sample
  init_git_repo
  print_next_steps
}

main "$@"
