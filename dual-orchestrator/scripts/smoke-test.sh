#!/usr/bin/env bash
# Retired 2026-10-07: the old lifecycle fixture does not produce D05 program evidence.
# Original dirty-workspace bytes are preserved in mainline-20261007/*.retired.
# This entry deliberately does not build, create worktrees, approve gates or call agents.
set -euo pipefail
printf '%s\n' 'RETIRED: old smoke results cannot prove the current approved lifecycle.' >&2
printf '%s\n' 'Offline verification: from dual-orchestrator run rtk proxy mvn -q clean test.' >&2
printf '%s\n' 'Real native/provider runs require a separately approved Verification Gate.' >&2
exit 2
