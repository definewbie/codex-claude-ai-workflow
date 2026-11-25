"""Gate arithmetic only: no client, dispatcher, ledger mutation or approval.

Input is operator-prepared planning data, not a new workflow source of truth.
ALLOW_BUDGET_ONLY never grants business, native or delivery authorization.
"""
import json
import math
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parent
P = json.loads((OUT / "budget.json").read_text())
RESERVES = {s["node"]: s["tokens"] for s in P["stages"]}


def check(state):
    for k in ("actual_tokens", "completed_calls", "active_seconds"):
        v = state.get(k)
        if type(v) not in (int, float) or not math.isfinite(v) or v < 0:
            return {"result": "STOP", "reason": "MEASUREMENT_UNAVAILABLE_OR_INVALID"}
        if k != "active_seconds" and type(v) is not int:
            return {"result": "STOP", "reason": "TOKEN_OR_CALL_COUNT_NOT_INTEGER"}
    if state.get("measurement_complete") is not True or state.get("external_pending") is not False:
        return {"result": "STOP", "reason": "MEASUREMENT_OR_EXTERNAL_RESULT_UNCERTAIN"}
    remaining, extra = state.get("remaining_mainline"), state.get("new_extra_calls")
    if not isinstance(remaining, list) or not isinstance(extra, list) or not (remaining or extra):
        return {"result": "STOP", "reason": "NO_NEXT_MODEL_WORK_OR_MISSING_PATH"}
    if any(type(n) is not str or n not in RESERVES for n in remaining + extra):
        return {"result": "STOP", "reason": "UNKNOWN_STAGE_REQUIRES_EXPLICIT_PLANNING"}
    if len(set(remaining)) != len(remaining):
        return {"result": "STOP", "reason": "MAINLINE_AND_REPEATED_WORK_MUST_BE_SEPARATE"}
    # Fixed allowance for all unfinished mandatory model nodes plus this newly
    # proposed repeat work; keep one possible in-command format correction.
    needed = sum(RESERVES[n] for n in remaining + extra) + P["possible_correction_tokens"]
    calls_needed = len(remaining) + len(extra) + P["possible_correction_calls"]
    facts = {"required_token_reserve": needed, "required_call_reserve": calls_needed,
        "remaining_tokens": P["total_stop"] - state["actual_tokens"],
        "remaining_calls": P["top_level_calls"] - state["completed_calls"],
        "warning": state["actual_tokens"] >= P["warning"]}
    if state["actual_tokens"] >= P["total_stop"]:
        return {"result": "STOP", "reason": "OBSERVED_TOKEN_STOP", **facts}
    if state["actual_tokens"] + needed > P["total_stop"]:
        return {"result": "STOP", "reason": "INSUFFICIENT_MAINLINE_AND_CORRECTION_RESERVE", **facts}
    if state["completed_calls"] + calls_needed > P["top_level_calls"]:
        return {"result": "STOP", "reason": "INSUFFICIENT_ACTUAL_CALL_RESERVE", **facts}
    if state["active_seconds"] + P["next_agent_time_reserve_seconds"] > P["active_seconds"]:
        return {"result": "STOP", "reason": "INSUFFICIENT_ACTIVE_TIME_RESERVE", **facts}
    return {"result": "ALLOW_BUDGET_ONLY", "reason": "WITHIN_PROPOSED_OPERATOR_BUDGET", **facts}



if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Use one planning-input JSON; no runtime entry")
    print(json.dumps(check(json.loads(Path(sys.argv[1]).read_text())), ensure_ascii=False, indent=2))
