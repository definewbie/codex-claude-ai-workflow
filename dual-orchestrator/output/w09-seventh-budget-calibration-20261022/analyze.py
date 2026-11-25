"""Offline historical extraction; never imports or invokes a Gate runner/provider.

Run with Python 3 from any directory. Writes only this analysis directory.
Unit: one completed prompt occurrence, not run, session, tool call or context snapshot.
"""
import csv
import hashlib
import json
import statistics
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sources = {}


def read(path):
    path = path.resolve()
    data = path.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    assert path not in sources or sources[path] == digest
    sources[path] = digest
    return data.decode()


def load(path):
    return json.loads(read(path))


cases = ["w09-real-gate-20261010", "w09-fresh-second-gate-20261010",
         "w09-fresh-third-gate-20261010", "w09-fresh-fourth-gate-20261011",
         "w09-fresh-fifth-gate-20261011", "w09-fresh-sixth-gate-20261015"]
second_path = ROOT / cases[1] / "usage-attribution-discrepancy.json"
second = load(second_path)
fourth_path = ROOT / "w09-current-transcript-forward-fix-20261011/actual-current-transcripts-offline-proof.stdout"
fourth = {}
for line in read(fourth_path).splitlines():
    if line.startswith("run-"):
        run, usage = line.split(" ", 1)
        fourth[run] = json.loads(usage)
sixth_path = ROOT / "w09-sixth-forward-fix-20261015/historical-sixth.stdout"
sixth = {}
for line in read(sixth_path).splitlines():
    if line.startswith('{"run":'):
        row = json.loads(line)
        sixth[row["run"], row["occurrence"]] = row["usage"]

rows, no_prompt = [], []
for case_number, case in enumerate(cases, 1):
    seen_nodes = set()
    for run_path in sorted((ROOT / case / "stopped-ledger-snapshot/runs").glob("*/run.json")):
        run = load(run_path)
        node = run["nodeId"]
        is_reentry = node in seen_nodes
        seen_nodes.add(node)
        for occurrence in run["agentResultAcquisition"]["occurrences"]:
            if not occurrence["requestId"]:
                no_prompt.append({"case": case_number, "run": run["runId"],
                                  "tokens": None, "reason": "NO_PROMPT_DISPATCH; usage UNAVAILABLE, not measured zero"})
                continue
            ordinal = occurrence["occurrence"]
            usage = occurrence["usage"]
            basis, evidence_path = "FIXED_OCCURRENCE", run_path
            if case_number == 2:
                matches = [r for r in second["native_rpc_usage_by_request"]
                           if str(r["id"]) == occurrence["requestId"]]
                assert len(matches) == 1
                usage = {**usage, **matches[0]["result"]["usage"]}
                basis, evidence_path = "READ_ONLY_NATIVE_CORRECTION", second_path
            elif case_number == 4:
                replay = fourth[run["runId"]]
                if usage["measurementType"] == "UNAVAILABLE":
                    usage = replay
                    basis, evidence_path = "READ_ONLY_PRIOR_REPLAY", fourth_path
                else:
                    assert usage["totalTokens"] == replay["totalTokens"]
            elif case_number == 6:
                replay = sixth[run["runId"], ordinal]
                if usage["measurementType"] == "UNAVAILABLE":
                    usage = replay
                    basis, evidence_path = "READ_ONLY_PRIOR_REPLAY", sixth_path
                else:
                    assert usage["totalTokens"] == replay["totalTokens"]
            fields = ["inputTokens", "outputTokens", "cachedReadTokens", "cachedWriteTokens"]
            assert all(type(usage[k]) is int and usage[k] >= 0 for k in fields + ["totalTokens"])
            assert sum(usage[k] for k in fields) == usage["totalTokens"]
            transcript = run_path.parent / "outputs" / Path(occurrence["transcriptPath"]).name
            frames = []
            for line in read(transcript).splitlines():
                try:
                    frame = json.loads(line)
                except (ValueError, TypeError):
                    continue
                if (isinstance(frame, dict) and frame.get("jsonrpc") == "2.0"
                        and str(frame.get("id")) == occurrence["requestId"]
                        and isinstance(frame.get("result"), dict)
                        and isinstance(frame["result"].get("usage"), dict)):
                    frames.append(frame["result"]["usage"])
            if frames:
                assert len(frames) == 1
                assert frames[0]["totalTokens"] == usage["totalTokens"]
            rows.append({"case": case_number, "node": node, "run": run["runId"],
                         "occurrence": ordinal, "request_id": occurrence["requestId"],
                         "role": "FORMAT_CORRECTION" if ordinal > 1 else ("SEMANTIC_REENTRY" if is_reentry else "INITIAL"),
                         "tokens": usage["totalTokens"],
                         **{k: usage[k] for k in fields},
                         "provider": run["usage"]["providerId"], "model": run["usage"]["model"],
                         "billing_mode": run["usage"]["billingMode"],
                         "basis": basis, "original_fixed_tokens": occurrence["usage"]["totalTokens"],
                         "original_measurement": occurrence["usage"]["measurementType"],
                         "primary_reason": run["executionOutcome"]["primaryReason"],
                         "evidence": str(evidence_path.resolve()), "run_source": str(run_path.resolve()),
                         "raw_current_usage_crosscheck": bool(frames)})

assert len(rows) == 22
assert len({(r["case"], r["run"], r["occurrence"]) for r in rows}) == len(rows)
expected = {2: 63493, 3: 235366, 4: 403420, 5: 62136, 6: 70801}
assert {c: sum(r["tokens"] for r in rows if r["case"] == c) for c in expected} == expected
assert sum(r["tokens"] for r in rows) == 835216

old = load(ROOT / "w09-fresh-seventh-gate-20261016/budget.json")
stages = []
for stage in old["stages"]:
    observations = [r for r in rows if r["node"] == stage["node"] and r["role"] != "FORMAT_CORRECTION"]
    values = [r["tokens"] for r in observations]
    stages.append({"node": stage["node"], "n_requests": len(values),
                   "n_cases": len({r["case"] for r in observations}),
                   "min": min(values) if values else None,
                   "median": statistics.median(values) if values else None,
                   "max": max(values) if values else None,
                   "old_allowance": stage["tokens"],
                   "proposed_allowance": 90000 if stage["node"] == "design.codex_arch_review" else stage["tokens"],
                   "evidence_class": "SMALL_SAMPLE_PLUS_JUDGMENT_MARGIN" if values else "UNMEASURED_PLANNING_ASSUMPTION"})

allowances = {s["node"]: s["proposed_allowance"] for s in stages}
base = sum(allowances.values())
plan_repeat = allowances["plan.claude_plan"] + allowances["plan.codex_plan_review"]
intake_loop = allowances["intake.claude_brainstorm"] + allowances["intake.codex_product_review"]
design_loop = allowances["design.claude_design"] + allowances["design.codex_arch_review"]
correction_reserve = old["possible_correction_tokens"]
scenarios = {
    "mainline_only": {"tokens": base, "requests": 9},
    "mainline_readiness_one_correction": {"tokens": base + plan_repeat + correction_reserve, "requests": 12},
    "mainline_readiness_intake_loop_correction": {"tokens": base + plan_repeat + intake_loop + correction_reserve, "requests": 14},
    "mainline_readiness_design_loop_correction": {"tokens": base + plan_repeat + design_loop + correction_reserve, "requests": 14},
    "mainline_readiness_both_loops_correction": {"tokens": base + plan_repeat + intake_loop + design_loop + correction_reserve, "requests": 16},
}
assert base == 955000 and scenarios["mainline_readiness_design_loop_correction"]["tokens"] == 1520000
proposal = {"status": "ANALYSIS_ONLY_NOT_APPLIED_OR_RUN_AUTHORIZED", "unit": old["unit"],
            "recommended_stop": 1700000, "lower_cap_option": 1500000,
            "top_level_calls": 18, "active_seconds": 3600,
            "stages": stages, "correction_reserve": correction_reserve,
            "correction_observed_values": [r["tokens"] for r in rows if r["role"] == "FORMAT_CORRECTION"],
            "scenarios": scenarios,
            "evidence_limits": ["No full-chain observation", "No p95 or probability estimate", "No lower bound of 1.7M proven", "No per-request hard token cap", "Historical reconstructed facts do not replace old budget authority"],
            "real_run_authorized": False}
groups = defaultdict(list)
for r in rows:
    groups[(r["node"], r["role"])].append(r["tokens"])
summary = {"request_count": len(rows), "analysis_tokens_cases_2_to_6": sum(r["tokens"] for r in rows),
           "case_totals": expected, "case_1": no_prompt,
           "raw_current_usage_crosschecked_occurrences": sum(r["raw_current_usage_crosscheck"] for r in rows),
           "per_role": [{"node": n, "role": role, "n": len(v), "min": min(v), "median": statistics.median(v), "max": max(v)} for (n, role), v in groups.items()]}

with (HERE / "occurrences.csv").open("w", newline="") as out:
    writer = csv.DictWriter(out, fieldnames=list(rows[0]))
    writer.writeheader()
    writer.writerows(rows)
for name, data in [("summary.json", summary), ("budget-proposal.json", proposal)]:
    (HERE / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
for path, digest in sources.items():
    assert hashlib.sha256(path.read_bytes()).hexdigest() == digest, path
(HERE / "source-receipt.json").write_text(json.dumps({
    "scope": "Six archived real cases only; no experiment, provider, native command, Gate runner or old-ledger write",
    "sources": [{"path": str(p), "sha256": h} for p, h in sorted(sources.items())],
    "selected_sources_unchanged_after_analysis": True,
}, ensure_ascii=False, indent=2) + "\n")
print(json.dumps(summary, ensure_ascii=False, indent=2))
print(json.dumps(scenarios, indent=2))
