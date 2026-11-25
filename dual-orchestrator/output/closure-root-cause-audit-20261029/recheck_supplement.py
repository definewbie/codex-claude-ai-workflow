"""Read-only raw-ledger/IO check; no provider calls or production mutations.

Run from any directory: python3 recheck_supplement.py
The existing census is used ONLY to discover the 12 audited roots.
"""
import collections
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
sources = {}


def read(path):
    data = path.read_bytes()
    sources[str(path)] = hashlib.sha256(data).hexdigest()
    return data.decode("utf-8")


discovery = json.loads((HERE / "census.json").read_text())
roots = [Path(c["root"]) for c in discovery["cases"]
         if c["cohort"] == "okx-btc-k-line-demo"]
cases, runs, failures, reviews, zero_usage = [], [], [], [], []
for root in roots:
    events = [(n, json.loads(s)) for n, s in enumerate(
        read(root / "events.jsonl").splitlines(), 1) if s.strip()]
    cases.append({"root": str(root), "eventCount": len(events),
                  "last": events[-1],
                  "eventTypes": dict(collections.Counter(e["event_type"] for _, e in events)),
                  "retryEvents": [(n, e) for n, e in events if e["event_type"] == "node_retry_requested"],
                  "boundaryEvents": [(n, e) for n, e in events if e["event_type"] in
                      ("revision_limit_reached", "failure_classified", "iteration_started")]})
    for path in sorted(root.glob("runs/*/run.json")):
        run = json.loads(read(path))
        runs.append(run)
        outcome = run.get("executionOutcome") or {}
        if outcome.get("nodeMaySucceed") is False:
            usage = run.get("usage") or {}
            row = {"path": str(path), "runId": run["runId"],
                   "durationMs": run.get("durationMs"), "outcome": outcome,
                   "contract": run.get("agentResultContract"), "usage": usage,
                   "acquisition": run.get("agentResultAcquisition"), "transcriptTails": []}
            if outcome.get("primaryReason") == "agent_result_missing_or_invalid":
                for transcript in sorted(path.parent.glob("outputs/*transcript.log")):
                    data = read(transcript)
                    row["transcriptTails"].append({"path": str(transcript),
                        "bytes": transcript.stat().st_size, "tail": data[-1000:]})
            failures.append(row)
            if usage.get("totalTokens", 0) == 0:
                zero_usage.append({"path": str(path), "durationMs": run.get("durationMs"),
                                   "usageObjectPresent": bool(usage)})
        if root.name.endswith("BXWV"):
            path = path.parent / "outputs/acceptance-codex-review.json"
            if path.exists():
                result = json.loads(read(path))["result"]
                reviews.append({"path": str(path), "runId": run["runId"],
                    "startedAt": run.get("startedAt"), "result": result})
sessions = {}
for run in runs:
    usage = run.get("usage") or {}
    entry = sessions.setdefault(run.get("sessionScope", "missing"),
                               {"runs": 0, "recordedTokens": 0, "cachedReadTokens": 0})
    entry["runs"] += 1
    if usage.get("measurementType") == "PROVIDER_RUN_USAGE" and not usage.get("cumulative"):
        entry["recordedTokens"] += usage.get("totalTokens", 0)
        entry["cachedReadTokens"] += usage.get("cachedReadTokens", 0)
result = {"cases": cases, "runCount": len(runs), "eventCount": sum(c["eventCount"] for c in cases),
          "lastEventTypes": dict(collections.Counter(c["last"][1]["event_type"] for c in cases)),
          "failures": failures, "zeroOrMissingUsage": zero_usage, "sessions": sessions,
          "acceptanceReviews": sorted(reviews, key=lambda r: r["startedAt"]), "sourceSha256": sources}
(HERE / "supplement-recheck.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
print(json.dumps({k: result[k] for k in ("runCount", "eventCount", "lastEventTypes", "sessions")},
                 ensure_ascii=False, indent=2))
print("Failed runs:", len(failures), "zero/missing usage:", len(zero_usage))
print("BXWV must_fix:", [len(r["result"]["must_fix"]) for r in result["acceptanceReviews"]])
