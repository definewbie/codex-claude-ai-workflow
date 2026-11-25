"""Read-only Gate prerequisite. Failure to inspect is never process absence."""
import json
import subprocess
from pathlib import Path


class ProcessInspectionError(RuntimeError):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status


def require_unresolved_pids_absent(records, *, environment, executables):
    """Consume caller-verified records; a PID number is not a durable identity.

    absent=True must come from an earlier successful check in the caller's
    frozen inputs, never from a model assertion. That old process cannot return
    after confirmed absence. Unconfirmed records still use the strict reader.
    """
    if not isinstance(records, list):
        raise ProcessInspectionError("UNKNOWN", "Invalid process records")
    seen, confirmed, unresolved = set(), [], []
    for row in records:
        if not isinstance(row, dict):
            raise ProcessInspectionError("UNKNOWN", "Invalid process record")
        pid = row.get("pid")
        if type(pid) is not int or pid <= 0 or pid in seen:
            raise ProcessInspectionError("UNKNOWN", "Invalid or duplicate recorded PID")
        if "absent" in row and type(row["absent"]) is not bool:
            raise ProcessInspectionError("UNKNOWN", "Invalid prior absence fact")
        seen.add(pid)
        (confirmed if row.get("absent") is True else unresolved).append(pid)
    observation = (require_known_pids_absent(unresolved, environment=environment, executables=executables) if unresolved else {
        "status": "NO_UNRESOLVED_PIDS", "checked_pids": [],
        "scope": "Prior confirmed absence only; no current process-table or child-tree claim",
    })
    return {**observation, "previously_confirmed_absent": sorted(confirmed)}


def require_known_pids_absent(pids, *, environment, executables):
    targets = set(pids)
    if any(type(pid) is not int or pid <= 0 for pid in targets):
        raise ProcessInspectionError("UNKNOWN", "Invalid PID input")
    try:
        result = subprocess.run(
            [executables["rtk"], "proxy", executables["ps"], "-axo", "pid=,lstart=,command="],
            capture_output=True, text=True, timeout=5, check=False, env=environment,
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
        import gate_support as gate
        from admission import selected_environment
        gate.frozen_check()
        launch = selected_environment()
        print(json.dumps(require_known_pids_absent([r["pid"] for r in prior["known_pids"]],
                environment=launch['environment'], executables=launch['executables'])))
    except ProcessInspectionError as error:
        print(json.dumps({"status": error.status, "message": str(error)}))
        sys.exit(2)
