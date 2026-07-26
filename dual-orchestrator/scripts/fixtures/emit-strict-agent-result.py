#!/usr/bin/env python3
"""Emit one controlled AgentResultEnvelope into an acpx session stream."""

import argparse
import json
import os
import re
from pathlib import Path
import time


def prompt_field(prompt: str, name: str) -> str:
    matches = re.findall(rf'"{re.escape(name)}"\s*:\s*"([^"]+)"', prompt)
    if not matches:
        raise ValueError(f"strict result contract field is missing from prompt: {name}")
    return matches[-1]


def failure(category_hint: str = "", reason: str = "") -> dict:
    return {
        "category_hint": category_hint,
        "contract_gaps": [],
        "out_of_scope_files": [],
        "missing_tests": [],
        "context_gaps": [],
        "reason": reason,
    }


def agent_output(decision: str, summary: str, category_hint: str = "") -> dict:
    return {
        "artifact_type": "agent_output_contract",
        "decision": decision,
        "summary": summary,
        "lifecycle_status": "READY",
        "blocking": False,
        "checks": ["controlled smoke fixture"],
        "evidence": ["controlled typed result"],
        "missing_evidence": [],
        "must_fix": [],
        "acceptance_notes": [],
        "open_risks": [],
        "changed_files": [],
        "verification_summary": "Controlled smoke emitted a strict AgentResultEnvelope.",
        "failure": failure(category_hint),
        "reread_reasons": [],
    }


def task_proposal() -> dict:
    return {
        "artifact_type": "task_contract_proposal",
        "decision": "READY_FOR_PRODUCT_REVIEW",
        "summary": "Requirement is clear enough for product review.",
        "needs_human_input": False,
        "objective": "Add goodbye() and document it without changing command-line output.",
        "scope": {
            "included": [
                {"path": "main.py", "operation": "MODIFY"},
                {"path": "README.md", "operation": "MODIFY"},
            ],
            "excluded": [
                "Do not change __main__ output",
                "Do not add dependencies",
                "Do not modify unrelated files",
            ],
        },
        "acceptance_criteria": [
            "main.py defines goodbye() returning the exact string goodbye",
            "README.md documents goodbye()",
            "Running python main.py still prints only the existing hello output",
        ],
        "test_requirements": [
            "Import main and assert goodbye() returns goodbye",
            "Run or inspect __main__ behavior",
        ],
        "constraints": ["Keep the change scoped to the fixture files"],
        "non_goals": ["No package manager changes", "No unrelated refactor"],
        "risk_areas": ["Accidentally changing __main__ output", "Missing documentation"],
        "evidence_refs": ["main.py", "README.md"],
        "open_questions": [],
    }


def plan_result(include_plan: bool, scenario: str) -> dict:
    plan = None
    if include_plan:
        reread_fixture = scenario == "reread-reason"
        plan = {
            "implementation_steps": [
                *([] if reread_fixture else ["Update main.py with goodbye()."]),
                "Document goodbye() in README.md.",
            ],
            "edit_files": ["README.md"] if reread_fixture else ["main.py", "README.md"],
            "reference_files": [],
            "file_level_changes": [
                "main.py: add goodbye().",
                "README.md: document goodbye().",
            ],
            "validation_plan": ["Import main and assert goodbye()."],
            "rollback_notes": "Revert main.py and README.md.",
            "known_risks": ["Missing documentation."],
            "human_review_checklist": ["Confirm function and docs."],
        }
    return {
        "artifact_type": "plan_result",
        "decision": "READY_FOR_PLAN_REVIEW",
        "summary": "Plan body is complete." if include_plan
        else "Plan body is intentionally missing.",
        "lifecycle_status": "READY",
        "blocking": False,
        "checks": ["controlled semantic smoke"],
        "evidence": ["controlled plan result"],
        "missing_evidence": [],
        "must_fix": [],
        "acceptance_notes": [],
        "open_risks": [],
        "changed_files": [],
        "verification_summary": "Controlled plan boundary result.",
        "failure": failure(),
        "plan": plan,
    }


def self_test_result() -> dict:
    return {
        "artifact_type": "self_test_report",
        "decision": "SELF_TEST_COMPLETE",
        "summary": "Self-test passed.",
        "lifecycle_status": "READY",
        "checks_run": ["Imported main and verified hello() and goodbye()."],
        "checks_skipped": [],
        "evidence": ["Python assertions completed without error."],
        "missing_evidence": [],
        "acceptance_notes": [],
        "verification_summary": "Controlled smoke self-test passed.",
    }


def acceptance_result(decision: str, summary: str, category_hint: str = "") -> dict:
    rejected = decision == "REJECT"
    return {
        "artifact_type": "acceptance_review",
        "decision": decision,
        "summary": summary,
        "lifecycle_status": "REJECTED" if rejected else "ACCEPTED",
        "blocking": rejected,
        "checks": ["Inspected controlled acceptance evidence."],
        "evidence": ["Controlled acceptance fixture."],
        "missing_evidence": ["Repair evidence is required."] if rejected else [],
        "must_fix": ["Run one repair pass."] if rejected else [],
        "acceptance_notes": [],
        "open_risks": [],
        "changed_files": ["main.py", "README.md"],
        "verification_summary": summary,
        "failure": failure(
            category_hint,
            "Controlled rejection." if rejected else "",
        ),
    }


def handoff_result() -> dict:
    return {
        "artifact_type": "stage_handoff",
        "decision": "HANDOFF_READY",
        "summary": "Controlled handoff is ready.",
        "lifecycle_status": "READY",
        "completed_work": ["Added goodbye() to main.py.", "Documented goodbye() in README.md."],
        "important_decisions": ["Kept the change scoped to the controlled fixture."],
        "changed_files": ["main.py", "README.md"],
        "verification_state": "Controlled self-test and acceptance completed.",
        "open_risks": [],
        "recommended_next_stage": "Use the generated handoff artifacts.",
        "suggested_next_prompt": "Preserve the completed controlled fixture change.",
    }


def result_for(template: str, profile: str, scenario: str) -> dict:
    decision = os.environ.get("DUAL_FIXTURE_DECISION", "")
    summary = os.environ.get("DUAL_FIXTURE_SUMMARY", "")
    category_hint = os.environ.get("DUAL_FIXTURE_CATEGORY_HINT", "")

    if template == "intake-claude-brainstorm.md":
        return task_proposal()
    if template == "plan-claude.md":
        return plan_result(
            not (profile == "semantic" and scenario == "plan-body"),
            scenario,
        )
    if template == "implement-claude-self-test.md":
        return self_test_result()
    if template == "acceptance-codex-review.md":
        if profile == "semantic" and scenario == "review-noise":
            return acceptance_result("REJECT", "minor stylistic issue only; should not block")
        return acceptance_result(
            decision or "ACCEPT",
            summary or "Controlled verifier accepted the implementation evidence.",
            category_hint,
        )
    if template == "stage-claude-handoff.md":
        return handoff_result()
    return agent_output(
        decision or "PASS",
        summary or "Default controlled response.",
        category_hint,
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--prompt", required=True)
    parser.add_argument("--template", required=True)
    parser.add_argument("--profile", choices=("controlled", "semantic"), required=True)
    parser.add_argument("--scenario", default="")
    parser.add_argument("--session-id", required=True)
    args = parser.parse_args()

    prompt = Path(args.prompt).read_text(encoding="utf-8")
    envelope = {
        "schema_version": 1,
        "workstream_id": prompt_field(prompt, "workstream_id"),
        "run_id": prompt_field(prompt, "run_id"),
        "node_id": prompt_field(prompt, "node_id"),
        "result": result_for(args.template, args.profile, args.scenario),
    }
    assistant_message = json.dumps(envelope, ensure_ascii=False, separators=(",", ":"))
    request_id = time.time_ns()
    stream = Path.home() / ".acpx" / "sessions" / f"{args.session_id}.stream.ndjson"
    stream.parent.mkdir(parents=True, exist_ok=True)
    records = [
        {
            "jsonrpc": "2.0",
            "id": request_id,
            "method": "session/prompt",
            "params": {},
        },
        {
            "jsonrpc": "2.0",
            "method": "session/update",
            "params": {
                "sessionId": args.session_id,
                "update": {
                    "sessionUpdate": "agent_message_chunk",
                    "messageId": f"assistant-{request_id}",
                    "content": {"type": "text", "text": assistant_message},
                },
            },
        },
        {
            "jsonrpc": "2.0",
            "id": request_id,
            "result": {"stopReason": "end_turn"},
        },
    ]
    with stream.open("a", encoding="utf-8") as output:
        for record in records:
            output.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n")


if __name__ == "__main__":
    main()
