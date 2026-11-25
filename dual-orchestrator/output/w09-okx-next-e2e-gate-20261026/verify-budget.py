"""Offline admission checks only. No process, model, network or ledger mutation."""
import json
from pathlib import Path
import runpy

OUT = Path(__file__).resolve().parent
checker = runpy.run_path(str(OUT / "check_budget.py"))
check = checker["check"]
P = checker["P"]
nodes = [s["node"] for s in P["stages"]]


def state(tokens=0, calls=0, seconds=0, remaining=None, extra=None, **changes):
    value = dict(
        actual_tokens=tokens,
        completed_calls=calls,
        active_seconds=seconds,
        remaining_mainline=nodes if remaining is None else remaining,
        new_extra_calls=[] if extra is None else extra,
        measurement_complete=True,
        external_pending=False,
    )
    value.update(changes)
    return value


cases = [
    ("fresh mainline and correction", state(), "ALLOW_BUDGET_ONLY"),
    ("readiness repeat retained", state(extra=nodes[4:6]), "ALLOW_BUDGET_ONLY"),
    ("readiness and design repair retained", state(extra=nodes[4:6] + nodes[2:4]), "ALLOW_BUDGET_ONLY"),
    ("all planned repeats exceed ceiling", state(extra=nodes[4:6] + nodes[:4]), "STOP"),
    ("exact tail threshold", state(2750000, 10, 1000, nodes[-1:]), "ALLOW_BUDGET_ONLY"),
    ("one token too little reserve", state(2750001, 10, 1000, nodes[-1:]), "STOP"),
    ("whole tail protected", state(2800000, 10, 1000, nodes[-2:]), "STOP"),
    ("actual eighth pre-implementation measurements with new reserves", state(848242, 10, 1267.614, nodes[6:]), "ALLOW_BUDGET_ONLY"),
    ("eighth fixed implementation cost still leaves tail", state(1767028, 11, 1798.449, nodes[7:]), "ALLOW_BUDGET_ONLY"),
    ("exact call pool", state(1000000, 16, 1000, nodes[-1:]), "ALLOW_BUDGET_ONLY"),
    ("one call too many", state(1000000, 17, 1000, nodes[-1:]), "STOP"),
    ("exact time reserve", state(1000000, 10, 2100, nodes[-1:]), "ALLOW_BUDGET_ONLY"),
    ("one second too little time", state(1000000, 10, 2101, nodes[-1:]), "STOP"),
    ("tokens at ceiling", state(3000000, 10, 1000, nodes[-1:]), "STOP"),
    ("unknown measurement", state(measurement_complete=False), "STOP"),
    ("pending external action", state(external_pending=True), "STOP"),
    ("missing tokens are not zero", state(tokens=None), "STOP"),
    ("bool tokens are invalid", state(tokens=True), "STOP"),
    ("fractional calls are invalid", state(calls=1.5), "STOP"),
    ("negative recorded time is invalid", state(seconds=-1), "STOP"),
    ("missing stage plan", state(remaining=[], extra=[]), "STOP"),
    ("unknown stage", state(remaining=["unknown.stage"]), "STOP"),
    ("duplicated mainline stage", state(remaining=[nodes[-1], nodes[-1]]), "STOP"),
    ("correction reserve cannot be silently omitted", state(2850001, 10, 1000, nodes[-1:]), "STOP"),
]
results = []
for label, given, expected in cases:
    actual = check(given)
    assert actual["result"] == expected, (label, actual, expected)
    results.append(dict(case=label, input=given, output=actual, expected=expected))

assert sum(s["tokens"] for s in P["stages"]) == 2215000
assert results[0]["output"]["required_token_reserve"] == 2365000
assert results[1]["output"]["required_token_reserve"] == 2690000
assert results[2]["output"]["required_token_reserve"] == 2940000
assert results[3]["output"]["required_token_reserve"] == 3030000
assert results[2]["output"]["required_call_reserve"] == 14
assert P["previous_balance_inherited"] is False
assert P["real_run_authorized"] is False
result = dict(
    status="PASS_OFFLINE_ARITHMETIC_ONLY",
    case_count=len(results),
    cases=results,
    model_calls=0,
    not_proven="Java product correctness, actual stage cost, native runtime, fresh dispatch integration or authorization",
)
(OUT / "budget-dry-run.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
print(f"{len(results)} offline budget cases passed; no dispatch")
