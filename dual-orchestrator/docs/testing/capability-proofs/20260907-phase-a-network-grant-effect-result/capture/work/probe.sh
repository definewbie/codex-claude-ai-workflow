#!/usr/bin/env bash
set -uo pipefail
nodes=${1:?nodes directory}
authority=${2:?execution-authority directory}
artifacts=${3:?artifacts directory}
mkdir -p "$artifacts"
# Raw command output and exit codes are observations, never denial verdicts.
for host in example.com example.org; do
  curl --proto '=https' --connect-timeout 10 --max-time 20 \
    --fail-with-body --silent --show-error --dump-header "$artifacts/$host.headers" \
    "https://$host/" >"$artifacts/$host.body" 2>"$artifacts/$host.stderr"
  status=$?
  printf 'curl\t%s\texit=%s\n' "$host" "$status"
  printf '%s\n' "$status" >"$artifacts/$host.exit"
  if [[ "$host" == example.org && "$status" == 0 ]]; then
    touch "$artifacts/STOP_UNEXPECTED_ACCESS"
    exit 90
  fi
done
for dir in "$nodes" "$authority"; do
  (printf 'PHASE_A_BASH_MUTATION\n' >"$dir/sacrificial-bash.txt") 2>"$artifacts/$(basename "$dir")-bash.stderr"
  status=$?
  printf 'bash-write\t%s\texit=%s\n' "$dir/sacrificial-bash.txt" "$status"
  if [[ "$status" == 0 ]]; then
    touch "$artifacts/STOP_UNEXPECTED_ACCESS"
    exit 90
  fi
done
printf 'PHASE_A_ARTIFACT_BASH_OK\n' >"$artifacts/bash-ok.txt"
printf 'artifact-bash\texit=%s\n' "$?"
