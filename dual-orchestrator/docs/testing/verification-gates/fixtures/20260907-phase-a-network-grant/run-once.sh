#!/usr/bin/env bash
set -euo pipefail
umask 077
DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MODE=${1:?usage: run-once.sh prepare|run <new /private/tmp/result> <reviewed jar>}
RESULT=${2:?missing result directory}
JAR=${3:?missing reviewed jar}
[[ "$MODE" == prepare || "$MODE" == run ]]
[[ "$RESULT" == /private/tmp/* && ! -e "$RESULT" && -f "$JAR" ]]
EXPECTED_JAR=cbbe03d801f7541fcc29aaa46a3a77fa6d830fb0f0fa67184fea8ce4c8294f70
[[ "$(shasum -a 256 "$JAR" | awk '{print $1}')" == "$EXPECTED_JAR" ]]
(cd "$DIR" && shasum -a 256 -c SHA256SUMS)
mkdir -p "$RESULT/work" "$RESULT/classes" "$RESULT/source"
RESULT=$(CDPATH= cd -- "$RESULT" && pwd -P)
JAR=$(CDPATH= cd -- "$(dirname -- "$JAR")" && printf '%s/%s' "$PWD" "$(basename -- "$JAR")")
cp "$DIR/PhaseANetworkGrantLauncher.java.txt" "$RESULT/source/PhaseANetworkGrantLauncher.java"
unzip -q "$JAR" 'BOOT-INF/lib/*' -d "$RESULT/runtime"
CLASSPATH="$RESULT/classes:$RESULT/runtime/BOOT-INF/lib/*"
javac -cp "$CLASSPATH" -d "$RESULT/classes" "$RESULT/source/PhaseANetworkGrantLauncher.java"
shasum -a 256 "$JAR" "$RESULT/runtime/BOOT-INF/lib/"*.jar \
  "$RESULT/classes/dev/dual/adapter/acpx/PhaseANetworkGrantLauncher.class" >"$RESULT/runtime-before.sha256"
cp "$DIR/SHA256SUMS" "$RESULT/fixture.sha256"

host_check() {
  local phase=$1 host code
  for host in example.com example.org; do
    code=$(curl --noproxy '*' --proto '=https' --connect-timeout 10 --max-time 20 \
      --fail --silent --show-error --output "$RESULT/host-$phase-$host.body" \
      --write-out '%{http_code}' "https://$host/" 2>"$RESULT/host-$phase-$host.stderr") || return 1
    printf '%s\n' "$code" >"$RESULT/host-$phase-$host.status"
    [[ "$code" == 200 ]] || return 1
  done
}

if [[ "$MODE" == run ]]; then
  command -v perl >/dev/null
  : "${PHASE_A_REVIEW_APPROVED:?set only after independent Gate/fixture review}"
  [[ "$PHASE_A_REVIEW_APPROVED" == yes ]]
  : "${DUAL_ACPX_EXECUTABLE:?reviewed E1 binary required}"
  [[ "$DUAL_ACPX_EXECUTABLE" == /* && -x "$DUAL_ACPX_EXECUTABLE" ]]
  export PHASE_A_REAL_ACPX="$DUAL_ACPX_EXECUTABLE" PHASE_A_RESULT="$RESULT"
  export CLAUDE_CODE_EXECUTABLE=/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules/@anthropic-ai/claude-agent-sdk-darwin-arm64/claude
  MODULES=/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules
  printf '%s  %s\n' \
    7b39c1588df919d001dea3ffd5651adb682f2451b5a0e18d42d4233296b53cc7 "$CLAUDE_CODE_EXECUTABLE" \
    cae60ea6827d5f9b113c88b911388983fe96e7d565a382bf25904df78ab9d0fc "$MODULES/@anthropic-ai/claude-agent-sdk/sdk.mjs" \
    f0cbbe408bb758cc4bacdae9a244bcac6efbdb6413f680195d017648abc6d816 "$MODULES/@agentclientprotocol/claude-agent-acp/dist/acp-agent.js" \
    >"$RESULT/dependencies.sha256"
  shasum -a 256 -c "$RESULT/dependencies.sha256" >"$RESULT/dependencies-precheck.txt"
  "$PHASE_A_REAL_ACPX" build-info --json >"$RESULT/acpx-build-info.json"
  shasum -a 256 "$PHASE_A_REAL_ACPX" >"$RESULT/acpx-before.sha256"
  # Identity is also enforced by production transport before session bootstrap.
  host_check before
  cp "$DIR/acpx-once.sh" "$RESULT/acpx-once.sh"
  chmod 700 "$RESULT/acpx-once.sh"
  export DUAL_ACPX_EXECUTABLE="$RESULT/acpx-once.sh"
  export DUAL_ACPX_COMMAND_TIMEOUT_SECONDS=600
  export DUAL_ACPX_AGENT_PROGRESS_TIMEOUT_SECONDS=600
  export DUAL_ACPX_SESSION_COMMAND_TIMEOUT_SECONDS=30
fi

cleanup() {
  if [[ "$MODE" == run && -s "$RESULT/session-name.txt" ]]; then
    if [[ ! -f "$RESULT/session-closed.txt" ]] || ! grep -qx true "$RESULT/session-closed.txt"; then
      (cd "$RESULT/work" && perl -e 'alarm 30; exec @ARGV or die "close exec failed"' \
        "$PHASE_A_REAL_ACPX" claude sessions close "$(cat "$RESULT/session-name.txt")") \
        >"$RESULT/fallback-close.stdout" 2>"$RESULT/fallback-close.stderr" || return 1
    fi
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

set +e
java -cp "$CLASSPATH" dev.dual.adapter.acpx.PhaseANetworkGrantLauncher \
  "$MODE" "$RESULT/work" "$DIR" >"$RESULT/launcher.stdout" 2>"$RESULT/launcher.stderr"
status=$?
set -e
printf '%s\n' "$status" >"$RESULT/launcher-exit-code.txt"
if [[ "$MODE" == run ]]; then
  cleanup || status=1
  host_check after || status=1
  shasum -a 256 -c "$RESULT/acpx-before.sha256" >"$RESULT/acpx-recheck.txt" || status=1
  shasum -a 256 -c "$RESULT/dependencies.sha256" >"$RESULT/dependencies-recheck.txt" || status=1
fi
shasum -a 256 -c "$RESULT/runtime-before.sha256" >"$RESULT/runtime-recheck.txt" || status=1
(cd "$DIR" && shasum -a 256 -c SHA256SUMS) >"$RESULT/fixture-recheck.txt" || status=1
printf '%s\n' "$RESULT"
exit "$status"
