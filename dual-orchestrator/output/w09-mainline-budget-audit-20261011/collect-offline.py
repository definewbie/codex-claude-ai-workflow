"""Read saved third-case evidence and installed text; never launch a client.

Writes this material directory only. Config exports are an explicit non-secret
allowlist; the original settings, session metadata and streams are not copied.
"""
import hashlib
import json
import os
import tomllib
from pathlib import Path

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[1]
THIRD = ROOT / "output/w09-fresh-third-gate-20261010"
SNAP = THIRD / "stopped-ledger-snapshot"
sha = lambda p: hashlib.sha256(Path(p).read_bytes()).hexdigest()


def write(name, value):
    (OUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


safe_env = ["MAX_THINKING_TOKENS", "CLAUDE_CODE_EFFORT_LEVEL",
            "CLAUDE_CODE_MAX_OUTPUT_TOKENS", "CLAUDE_CODE_MAX_CONTEXT_TOKENS",
            "CLAUDE_CODE_MAX_TURNS", "DISABLE_PROMPT_CACHING",
            "ANTHROPIC_MODEL", "ANTHROPIC_DEFAULT_SONNET_MODEL",
            "ANTHROPIC_DEFAULT_OPUS_MODEL", "ANTHROPIC_DEFAULT_HAIKU_MODEL"]
configs = []
for path in ["/Users/kriswang/.acpx/config.json",
             "/Users/kriswang/.claude/settings.json",
             "/Users/kriswang/.claude/settings.local.json",
             "/Users/kriswang/.codex/config.toml",
             "/Library/Application Support/ClaudeCode/managed-settings.json",
             str(Path("/private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.claude/settings.json")),
             str(Path("/private/tmp/dual-w09-newlines-20261010-fresh-03/worktree/.claude/settings.local.json"))]:
    p = Path(path)
    item = {"path": path, "exists": p.exists()}
    if p.exists():
        d = tomllib.loads(p.read_text()) if p.suffix == ".toml" else json.loads(p.read_text())
        item.update(sha256=sha(p), key_names=list(d))
        item["selected"] = {k: d[k] for k in ["model", "effortLevel", "alwaysThinkingEnabled",
            "model_reasoning_effort", "model_context_window", "model_auto_compact_token_limit",
            "defaultAgent", "defaultPermissions", "nonInteractivePermissions"] if k in d}
        item["selected_env"] = {k: d.get("env", {})[k] for k in safe_env if k in d.get("env", {})}
        if path.endswith("/.acpx/config.json"):
            item["agent_commands"] = d.get("agents", {})
    configs.append(item)
write("config-observation.json", {
    "scope": "current files, not a reconstruction of all ambient third-run env",
    "files": configs, "current_inherited_selected_env": {k: os.environ[k] for k in safe_env if k in os.environ},
    "client_provider_invocations": 0, "persistent_configuration_changes": 0})

sessions = []
tools = []
for p in sorted((THIRD / "native-evidence").glob("*.stream.ndjson")):
    for line in p.read_text().splitlines():
        d = json.loads(line)
        if d.get("method") == "session/new":
            o = d.get("params", {}).get("_meta", {}).get("claudeCode", {}).get("options", {})
            sessions.append({"stream": str(p.relative_to(ROOT)), "sha256": sha(p),
                "record_type": "session/new request", "option_keys": list(o),
                "explicit_cost_options": {k: o[k] for k in ["settingSources", "maxTurns", "effort", "thinking", "maxBudgetUsd", "taskBudget"] if k in o}})
        r = d.get("result")
        if isinstance(r, dict) and "configOptions" in r:
            sessions.append({"stream": str(p.relative_to(ROOT)), "record_type": "session/new response",
                "rpc_id": d.get("id"), "options": [{"id": o["id"], "currentValue": o.get("currentValue")}
                    for o in r["configOptions"]]})
        if p.name.startswith("26a99c40") and d.get("method") == "session/update":
            u = d.get("params", {}).get("update", {})
            if u.get("sessionUpdate") == "tool_call_update" and u.get("status") == "completed":
                texts = [c.get("content", {}).get("text", "") for c in u.get("content", [])]
                text = "\n".join(texts)
                item = {"toolCallId": u.get("toolCallId"), "text_chars": len(text),
                    "locations": u.get("locations", []), "text_sha256": hashlib.sha256(text.encode()).hexdigest()}
                if u.get("toolCallId") == "call_f381930f507c47a89ea57d7a":
                    paths = [x for x in text.splitlines() if not x.startswith("(Showing ")]
                    item["truncation_footer"] = [x for x in text.splitlines() if x.startswith("(Showing ")]
                    item["path_count"] = len(paths)
                    item["program_path_count"] = sum(x == ".git" or x.startswith((".agent/", ".git/")) for x in paths)
                    item["visible_product_path_count"] = sum(not (x == ".git" or x.startswith((".agent/", ".git/"))) for x in paths)
                tools.append(item)
write("native-config-and-tool-observation.json", {"sessions": sessions, "design_completed_tools": tools,
    "meaning": "offered/effective ACP options are not proof of GLM provider enforcement or saved token percentage",
    "internal_turn_count": "NOT_EXPOSED_IN_ARCHIVED_ACP_STREAM; unique tool ids are not model turn count"})

runs = []
for p in sorted((SNAP / "runs").glob("*/run.json")):
    d = json.loads(p.read_text())
    m = json.loads(Path(str(p.parent / "prompts" / (d["promptTemplate"] + ".md.manifest.json"))).read_text())
    runs.append({"run_id": d["runId"], "node_id": d["nodeId"], "agent": d["agent"],
        "usage": d["usage"], "prompt_bytes": m["totalBytes"],
        "prompt_estimated_tokens": m["totalEstimatedTokens"], "duration_ms": d["durationMs"],
        "session_scope": d.get("sessionScope"), "session": d["session"]})
write("observed-costs.json", {"runs": runs, "actual_total_tokens": sum(x["usage"]["totalTokens"] for x in runs),
    "sample_scope": "third case only; unknown later stages have no real samples", "quantiles": "NOT_ESTIMATED"})

design = SNAP / "runs/run-1791641578962/prompts/design-claude.md"
manifest = json.loads(Path(str(design) + ".manifest.json").read_text())
write("input-observation.json", {"prompt_ref": str(design.relative_to(ROOT)), "sha256": sha(design),
    "total_bytes": manifest["totalBytes"], "estimated_tokens": manifest["totalEstimatedTokens"],
    "sections": manifest["sections"], "authored_contract": manifest["authoredContext"]["contract"],
    "tracked_product_inventory_count": 0,
    "direct_contract_refs_are_not_inlined": True,
    "approved_contract_read": "necessary approved requirements, not redundant merely because raw request is in prompt",
    "gate_read": "authority corroboration; cost-only audit does not authorize its removal",
    "broad_glob": "all returned paths were program metadata; lower-value search, no proven saving ratio",
    "manifest_section_sizes": "descriptive metadata; not assumed to be mutually exclusive token attribution"})

source_ranges = {
    "scripts/acpx-claude-agent-env.sh": [[36, 68]],
    ".tools/acpx-e1/source/src/acp/agent-command.ts": [[301, 327], [350, 369]],
    ".tools/acpx-e1/source/src/acp/claude-execution-policy.ts": [[50, 90]],
    ".tools/acpx-e1/source/src/acp/client.ts": [[1180, 1200], [1429, 1449]],
    ".tools/acpx-e1/source/src/runtime/engine/session-options.ts": [[1, 38]],
    ".tools/acpx-e1/source/src/cli/flags.ts": [[338, 350]],
    ".tools/acpx-e1/source/src/cli/command-handlers.ts": [[185, 202]],
    ".tools/acpx-e1/source/src/cli/command-registration.ts": [[267, 291]],
    "dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.terminal-delivery.json": [],
    "/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules/@agentclientprotocol/claude-agent-acp/dist/acp-agent.js": [[233, 266], [4764, 4773], [4866, 4890], [5120, 5129], [5617, 5643], [7065, 7079]],
    "/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules/@agentclientprotocol/claude-agent-acp/dist/settings.js": [[74, 103]],
    "/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules/@anthropic-ai/claude-agent-sdk/sdk.d.ts": [[1665, 1719]],
    "/Users/kriswang/.npm/_npx/208e725ef8df0f5d/node_modules/@agentclientprotocol/codex-acp/dist/index.js": [[28406, 28427], [28655, 28672], [33165, 33183]],
}
index, excerpts = [], []
for name, ranges in source_ranges.items():
    p = Path(name) if name.startswith("/") else ROOT / name
    index.append({"path": str(p), "sha256": sha(p), "ranges": ranges,
        "evidence_boundary": "current installed/source bytes; not reproducible-build proof of third native subprocess"})
    lines = p.read_text().splitlines()
    for a, b in ranges:
        excerpts.append(f"\n{name}:{a}-{b}\n" + "\n".join(f"{i+1}: {lines[i]}" for i in range(a-1, b)))
for path in ["/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules/@agentclientprotocol/claude-agent-acp/package.json",
             "/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules/@anthropic-ai/claude-agent-sdk/package.json",
             "/Users/kriswang/.npm/_npx/208e725ef8df0f5d/node_modules/@agentclientprotocol/codex-acp/package.json"]:
    p = Path(path)
    index.append({"path": path, "sha256": sha(p), "version": json.loads(p.read_text())["version"]})
sdk = Path("/Users/kriswang/.npm/_npx/fca12915ff656968/node_modules/@anthropic-ai/claude-agent-sdk/sdk.mjs")
sdk_text = sdk.read_text()
key = "CLAUDE_CODE_MAX_OUTPUT_TOKENS"
offset = sdk_text.index(key)
index.append({"path": str(sdk), "sha256": sha(sdk), "character_offset": offset,
              "scope": "environment schema symbol only, not native/provider enforcement proof"})
excerpts.append(f"\n{sdk}: character offset {offset}, env schema only\n" + sdk_text[max(0, offset-90):offset+300])
write("source-index.json", index)
(OUT / "source-excerpts.txt").write_text("\n".join(excerpts) + "\n")

original = json.loads((THIRD / "before-identity.json").read_text())
ledger = json.loads((THIRD / "stopped-ledger-identity.json").read_text())
reviewed = json.loads((THIRD / "material-sha256.json").read_text())
def mismatches(base, entries):
    return [str(k) for k, h in entries.items() if not (base / k).is_file() or sha(base / k) != h]
material_files = reviewed.get("files", reviewed)
write("identity-before.json", {"source_count": len(original["source_inputs"]),
    "source_diff": mismatches(ROOT, original["source_inputs"]),
    "stopped_ledger_count": len(ledger["files"]), "ledger_diff": mismatches(Path(ledger["source"]), ledger["files"]),
    "reviewed_material_count": len(material_files), "reviewed_material_diff": mismatches(THIRD, material_files),
    "source_index_sha256": sha(OUT / "source-index.json"), "native_provider_calls": 0})
print(json.dumps({"actual_tokens": sum(x["usage"]["totalTokens"] for x in runs),
    "design_tools": tools, "configuration_files": len(configs), "source_files": len(index)}, ensure_ascii=False))
