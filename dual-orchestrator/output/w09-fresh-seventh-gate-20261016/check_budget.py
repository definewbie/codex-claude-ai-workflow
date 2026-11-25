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


def self_check():
    nodes = list(RESERVES)
    def state(t=0, c=0, seconds=0, remaining=None, extra=None, complete=True, pending=False):
        return dict(actual_tokens=t, completed_calls=c, active_seconds=seconds,
            remaining_mainline=nodes if remaining is None else remaining,
            new_extra_calls=[] if extra is None else extra,
            measurement_complete=complete, external_pending=pending)
    cases = [
        ("fresh nine-node mainline", state(), "ALLOW_BUDGET_ONLY"),
        ("actual third pre-design state would have reserve", state(104635, 4, 356.79, nodes[2:]), "ALLOW_BUDGET_ONLY"),
        ("actual third post-design budget alone permits remaining stages", state(235366, 5, 557.255, nodes[3:]), "ALLOW_BUDGET_ONLY"),
        ("nine runs need not imply eighteen consumed prompts", state(450000, 9, 800, nodes[6:]), "ALLOW_BUDGET_ONLY"),
        ("mainline plus readiness and one semantic loop", state(0, 0, 0, nodes, nodes[4:6] + nodes[:2]), "ALLOW_BUDGET_ONLY"),
        ("whole unfinished tail protected, not just next small call", state(1250000, 8, 900, nodes[6:]), "STOP"),
        ("format correction cannot be hidden in one run", state(1550000, 10, 900, nodes[-1:]), "STOP"),
        ("actual top-level call pool exhausted", state(700000, 17, 700, nodes[-1:]), "STOP"),
        ("unknown usage is never zero", state(None, 5, 500, nodes[3:], complete=False), "STOP"),
        ("native pending result blocks admission", state(200000, 5, 500, nodes[3:], pending=True), "STOP"),
        ("active time cannot reserve next two-call block", state(200000, 5, 2500, nodes[3:]), "STOP"),
        ("unknown path is not silently cheap", state(200000, 5, 500, ["new.unknown.stage"]), "STOP"),
        ("observed total at stop is still a stop", state(1700000, 5, 500, nodes[3:]), "STOP"),
    ]
    results = []
    for label, s, expected in cases:
        r = check(s)
        assert r["result"] == expected, (label, r, expected)
        results.append({"case": label, "expected": expected, "input": s, "output": r})
    assert sum(RESERVES.values()) == P["illustrative_path"]["nominal_mainline_tokens"]
    assert 940000 + 175000 + 90000 + 150000 == 1355000
    assert P["total_stop"] - 1355000 == 345000
    assert 9 + 2 + 2 + 1 == 14
    (OUT / "budget-dry-run.json").write_text(json.dumps({
        "status": "PASS_OFFLINE_ARITHMETIC_ONLY", "cases": results,
        "not_proven": "native costs, runtime dispatch enforcement, seventh authorization or historical recovery",
        "native_provider_calls": 0}, ensure_ascii=False, indent=2) + "\n")
    print("13 offline admission cases and budget arithmetic passed; no dispatch or native calls")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-check"]:
        self_check()
    elif len(sys.argv) == 2:
        print(json.dumps(check(json.loads(Path(sys.argv[1]).read_text())), ensure_ascii=False, indent=2))
    else:
        raise SystemExit("Use --self-check or a planning-input JSON; this script cannot run a Gate")
