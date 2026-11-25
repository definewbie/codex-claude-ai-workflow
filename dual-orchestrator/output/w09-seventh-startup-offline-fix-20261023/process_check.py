"""Read-only Gate prerequisite. Failure to inspect is never process absence."""
import json
import subprocess
from pathlib import Path


class ProcessInspectionError(RuntimeError):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status


def require_known_pids_absent(pids):
    targets = set(pids)
    if any(type(pid) is not int or pid <= 0 for pid in targets):
        raise ProcessInspectionError("UNKNOWN", "Invalid PID input")
    try:
        result = subprocess.run(
            ["rtk", "proxy", "ps", "-axo", "pid=,lstart=,command="],
            capture_output=True, text=True, timeout=5, check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise ProcessInspectionError("UNKNOWN", "Cannot read process table") from error
    if result.returncode != 0 or result.stderr.strip() or not result.stdout.strip():
        raise ProcessInspectionError("UNKNOWN", "Process table unavailable or incomplete")
    observed = set()
    for line in result.stdout.splitlines():
        fields = line.strip().split(None, 1)
        if len(fields) != 2 or not fields[0].isascii() or not fields[0].isdecimal():
            raise ProcessInspectionError("UNKNOWN", "Malformed process table")
        pid = int(fields[0])
        if pid <= 0 or pid in observed:
            raise ProcessInspectionError("UNKNOWN", "Invalid or duplicate process row")
        observed.add(pid)
    present = sorted(targets & observed)
    if present:
        raise ProcessInspectionError("PRESENT", "Known PIDs present: " + str(present))
    return {"status": "ABSENT", "checked_pids": sorted(targets),
            "observed_processes": len(observed), "ps_exit": 0,
            "scope": "This snapshot and these PIDs only; not arbitrary descendants or remote state"}


if __name__ == "__main__":
    import sys
    # A read-only local check, with no option to launch an agent or mutate a ledger.
    prior = json.loads(Path(sys.argv[1]).read_text())
    try:
        print(json.dumps(require_known_pids_absent([r["pid"] for r in prior["known_pids"]])))
    except ProcessInspectionError as error:
        print(json.dumps({"status": error.status, "message": str(error)}))
        sys.exit(2)
