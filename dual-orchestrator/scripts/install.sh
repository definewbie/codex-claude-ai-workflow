#!/usr/bin/env bash
#
# install.sh — Install local dual-orchestrator command wrappers.
#
# This is the first open-source-friendly installer. It builds the CLI/server
# jars, copies them under a user-writable prefix, and writes small wrapper
# scripts. It does not require root and does not modify shell profiles.
#
# Usage:
#   ./scripts/install.sh
#   ./scripts/install.sh --prefix "$HOME/.local"
#   ./scripts/install.sh --skip-build
#   ./scripts/install.sh --dry-run
#

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PREFIX="${DUAL_PREFIX:-$HOME/.local}"
SKIP_BUILD=false
INSTALL_SERVER=true
DRY_RUN=false
CONFIGURE_ACPX=false

CLI_JAR="$PROJECT_ROOT/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar"
SERVER_JAR="$PROJECT_ROOT/dual-server/target/dual-server-0.1.0-SNAPSHOT.jar"

usage() {
  cat <<'EOF'
Usage:
  install.sh [options]

Options:
  --prefix <path>   Install prefix. Default: $DUAL_PREFIX or $HOME/.local
  --skip-build      Reuse existing target jars instead of running Maven.
  --no-server       Install only dualctl, not dual-server.
  --configure-acpx  Write a pinned acpx Codex adapter command into ~/.acpx/config.json.
  --dry-run         Print planned actions without writing files.
  -h, --help        Show this help.

Installed files:
  <prefix>/bin/dualctl
  <prefix>/bin/dual-server       unless --no-server
  <prefix>/lib/dual-orchestrator/dual-cli.jar
  <prefix>/lib/dual-orchestrator/dual-server.jar
EOF
}

log() {
  printf '[INSTALL] %s\n' "$*"
}

warn() {
  printf '[INSTALL][WARN] %s\n' "$*" >&2
}

fail() {
  printf '[INSTALL][FAIL] %s\n' "$*" >&2
  exit 1
}

run() {
  if [[ "$DRY_RUN" == true ]]; then
    printf '[INSTALL][DRY-RUN] %q' "$1"
    shift
    for arg in "$@"; do
      printf ' %q' "$arg"
    done
    printf '\n'
    return 0
  fi
  "$@"
}

abs_path() {
  local path="$1"
  if [[ "$path" = /* ]]; then
    printf '%s\n' "$path"
  else
    printf '%s/%s\n' "$(pwd)" "$path"
  fi
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --prefix)
        shift
        PREFIX="${1:-}"
        ;;
      --skip-build)
        SKIP_BUILD=true
        ;;
      --no-server)
        INSTALL_SERVER=false
        ;;
      --configure-acpx)
        CONFIGURE_ACPX=true
        ;;
      --dry-run)
        DRY_RUN=true
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        fail "Unknown argument: $1"
        ;;
    esac
    shift
  done

  [[ -n "$PREFIX" ]] || fail "--prefix cannot be empty"
  PREFIX="$(abs_path "$PREFIX")"
}

require_command() {
  local command_name="$1"
  command -v "$command_name" >/dev/null 2>&1 || fail "$command_name is required"
}

check_dependencies() {
  require_command java
  require_command mvn
  require_command git
  if ! command -v acpx >/dev/null 2>&1; then
    warn "acpx was not found. dualctl can be installed, but real agent runs will fail until acpx is installed and initialized."
  fi
}

configure_acpx() {
  if [[ "$CONFIGURE_ACPX" != true ]]; then
    return
  fi
  log "Configuring pinned acpx adapter"
  run "$PROJECT_ROOT/scripts/configure-acpx.sh"
}

build_jars() {
  if [[ "$SKIP_BUILD" == true ]]; then
    log "Skipping Maven build"
  else
    log "Building dual-cli and dual-server"
    run mvn -f "$PROJECT_ROOT/pom.xml" -pl dual-cli,dual-server -am package
  fi

  [[ -f "$CLI_JAR" ]] || fail "dual-cli jar not found: $CLI_JAR"
  if [[ "$INSTALL_SERVER" == true ]]; then
    [[ -f "$SERVER_JAR" ]] || fail "dual-server jar not found: $SERVER_JAR"
  fi
}

install_jar() {
  local source="$1"
  local target="$2"
  log "Installing $(basename "$target")"
  run mkdir -p "$(dirname "$target")"
  run cp "$source" "$target"
}

write_wrapper() {
  local target="$1"
  local jar="$2"
  local name="$3"
  log "Writing $name wrapper"
  run mkdir -p "$(dirname "$target")"
  if [[ "$DRY_RUN" == true ]]; then
    log "Would write $target -> java -jar $jar"
    return
  fi
  cat >"$target" <<EOF
#!/usr/bin/env bash
set -euo pipefail
exec java -jar "$jar" "\$@"
EOF
  chmod +x "$target"
}

install_files() {
  local lib_dir="$PREFIX/lib/dual-orchestrator"
  local bin_dir="$PREFIX/bin"
  local installed_cli_jar="$lib_dir/dual-cli.jar"
  local installed_server_jar="$lib_dir/dual-server.jar"

  install_jar "$CLI_JAR" "$installed_cli_jar"
  write_wrapper "$bin_dir/dualctl" "$installed_cli_jar" "dualctl"

  if [[ "$INSTALL_SERVER" == true ]]; then
    install_jar "$SERVER_JAR" "$installed_server_jar"
    write_wrapper "$bin_dir/dual-server" "$installed_server_jar" "dual-server"
  fi
}

print_next_steps() {
  cat <<EOF

Installed dual-orchestrator under:
  $PREFIX

Try:
  $PREFIX/bin/dualctl --help

If $PREFIX/bin is not on PATH, add it to your shell profile:
  export PATH="$PREFIX/bin:\$PATH"

Before real agent runs, verify:
  java -version
  mvn -version
  acpx --version
  $PROJECT_ROOT/scripts/configure-acpx.sh
  $PREFIX/bin/dualctl doctor --workdir /path/to/git/repo
EOF
}

main() {
  parse_args "$@"
  check_dependencies
  build_jars
  install_files
  configure_acpx
  print_next_steps
}

main "$@"
