# dual-agent-runner

[中文文档](README.zh.md)

`dual-agent-runner` is a minimal Bash workflow for running a dual-agent automation loop in code repositories.

- Claude is the main agent: PRD analysis, architecture, implementation, testing, and change reporting.
- Codex is the external reviewer: product, architecture, user acceptance, and quality gates.
- `acpx` is the unified agent entrypoint.
- `.agent/runs/<run_id>/` is the run ledger.
- Git diff, developer self-test, optional runner verification, logs, PR metadata, and notification logs are kept as evidence.

The runner stays intentionally small: no Web UI, no database, no automatic PR merge, no dedicated Slack/Feishu/Notion API, and no multi-round automatic repair.

## Install

Required:

- Bash
- Git for default worktree mode
- `jq`
- `acpx`
- `gh` only when `--create-pr` is used

On macOS:

```bash
brew install jq gh
chmod +x dual-agent-runner/bin/dual
export PATH="$PWD/dual-agent-runner/bin:$PATH"
```

## Initialize

```bash
dual init
```

Run this once in the target repository. It creates the repo-side files that tell Claude and Codex how to behave:

```text
CLAUDE.md
AGENTS.md
.agent/config.json
```

`dual init` does not create a run ledger and does not call the agents. It records the default acpx session names in `.agent/config.json`; the main `dual "task"` command uses those names later.

Default config:

```json
{
  "claude_session": "claude-main",
  "codex_session": "codex-reviewer",
  "max_review_rounds": 1,
  "default_test_command": "",
  "artifact_retention": "keep",
  "worktree": {
    "enabled": true,
    "base_dir": "..",
    "branch_prefix": "ai"
  },
  "pr": {
    "enabled": false,
    "base": "main",
    "draft": true
  },
  "notification": {
    "enabled": false,
    "command": "",
    "on": "failure"
  }
}
```

Command-line arguments override `.agent/config.json`. Worktree mode is now the safe default; use `--in-place` to run directly in the selected directory.

## acpx Setup

Before running real tasks, make sure `acpx` itself is installed, authenticated, and able to start both agents. `dual` expects these sessions by default:

```text
claude-main
codex-reviewer
```

During a normal run, you do not create prompt files yourself. `dual` renders prompt files automatically under:

```text
.agent/runs/<run_id>/prompts/
```

Then it calls:

```bash
acpx claude -s <claude_session> -f <prompt_file>
acpx codex -s <codex_session> -f <prompt_file>
```

Optional health check:

```bash
printf 'Say OK and exit.\n' > /tmp/dual-acpx-check.md
acpx claude -s claude-main -f /tmp/dual-acpx-check.md
acpx codex -s codex-reviewer -f /tmp/dual-acpx-check.md
```

`/tmp/dual-acpx-check.md` is only a temporary test prompt. It is not used by `dual`; it just confirms that `acpx` can invoke both configured sessions.

## Default Safe Mode

By default, `dual` creates a git worktree and runs the full workflow there:

```bash
dual "Add a short project description to README"
```

Default branch:

```text
ai/<run_id-slug>
```

Default worktree directory:

```text
<repo-parent>/<repo-name>-agent-runs/<run_id-slug>
```

All agent calls, developer self-test reports, optional runner verification, git snapshots, and run ledger files are written in the effective worktree. The original repo should not receive business-code changes.

`--worktree` is kept for backward compatibility. Worktree mode is now the default:

```bash
dual --worktree "Add a short project description to README"
```

## Usage

Normal run:

```bash
dual "Implement the task described here"
```

Add an optional runner-level verification command:

```bash
dual --test "npm test" "Implement the task described here"
```

Without `--test`, Claude still acts as the developer and must choose appropriate tests or checks, then write a Developer Self-Test Report. Codex final acceptance reviews that report like a verifier.

Run against another repository:

```bash
dual --workdir /path/to/repo --test "npm test" "Implement the task described here"
```

Dry-run creates the ledger and prompts without calling `acpx`:

```bash
dual --dry-run "Implement the task described here"
```

Run in-place:

```bash
dual --in-place "Add a short project description to README"
```

In-place execution on `main` or `master` is refused unless explicitly allowed:

```bash
dual --in-place --allow-main-in-place "Add a short project description to README"
```

Specify a branch:

```bash
dual --branch ai/my-feature "Task description"
```

Specify a worktree directory:

```bash
dual --worktree-dir ../my-feature-worktree "Task description"
```

Attach an existing worktree:

```bash
dual --attach-existing --worktree-dir ../my-feature-worktree "Task description"
dual --attach-existing --branch ai/my-feature "Task description"
```

Non-git directories cannot use default worktree mode. Use `--in-place` to run there; git evidence will be marked unavailable.

## Cleanup

`dual` never deletes worktrees automatically.

```bash
dual cleanup --worktree-dir <path>
dual cleanup --worktree-dir <path> --delete-branch
```

Manual git cleanup is also fine:

```bash
git worktree list
git worktree remove <worktree_dir>
git branch -D <branch>
```

## Pull Requests

PR creation is opt-in:

```bash
dual --create-pr "Task description"
```

Because worktree mode is the default, this runs in an isolated worktree automatically.

Options:

```bash
dual --create-pr --pr-base main "Task description"
dual --create-pr --pr-title "My PR title" "Task description"
dual --create-pr --pr-draft "Task description"
```

Before creating a PR, `dual` checks that:

- the effective workdir is a git repo
- `gh` exists
- the current branch is not `main` or `master`
- there are business-code changes or existing branch commits
- an `origin` remote exists

If uncommitted business changes exist, `dual` creates a commit:

```text
feat: dual agent task <run_id>

<task summary>

Run report: .agent/runs/<run_id>/final-report.md
```

If PR creation fails, the run ledger remains intact and `final-report.md` includes the PR failure status and stderr tail.

## Notification

Notifications are opt-in:

```bash
dual \
  --notify-command 'echo "$DUAL_STATUS $DUAL_RUN_ID $DUAL_REPORT_PATH"' \
  --notify-on always \
  "Task description"
```

The notification command receives:

```bash
DUAL_RUN_ID
DUAL_STATUS
DUAL_TASK
DUAL_WORKDIR
DUAL_ORIGINAL_WORKDIR
DUAL_EXECUTION_MODE
DUAL_WORKTREE_DIR
DUAL_BRANCH
DUAL_REPORT_PATH
DUAL_FINAL_DECISION
DUAL_TEST_EXIT_CODE
DUAL_PR_URL
```

Notification failure does not override the main exit code.

## Run Ledger

Each run creates this ledger in the effective workdir:

```text
.agent/runs/<run_id>/
  run.json
  events.jsonl
  00-user-request.md
  prompts/
  outputs/
  steps/
  snapshots/
  verification/
    developer-self-test.md
    runner-test-command.txt
    runner-test-output.log
    verification.json
  pr/
  notification/
  final-report.md
```

`final-report.md` includes execution environment, review commands, cleanup commands, timeline, agent step summary, Developer Self-Test, optional runner verification, PR status, notification status, and Codex final acceptance summary.

Every event uses this stable top-level schema:

```json
{
  "ts": "2026-05-19T00:00:00Z",
  "run_id": "run-id",
  "actor": "dual",
  "event": "event_name",
  "step": null,
  "session": null,
  "prompt_path": null,
  "output_path": null,
  "exit_code": null,
  "duration_ms": null,
  "data": null
}
```

## Prompt Personas and Design Principles

The 4 templates in `dual-agent-runner/prompts/` are not chat-style personalities. They are workflow contracts. The personas exist to constrain each step's focus, boundaries, and output format.

Default split:

- `01-claude-prd-architecture.md`: Claude is `Staff Engineer + Product-minded Implementer`, responsible for turning the user request into a PRD, requirement IDs, architecture plan, test strategy, and implementation checklist.
- `02-codex-review.md`: Codex is `Product Manager + Architect + User Proxy`, responsible for pre-implementation review, product completeness, architecture fit, user acceptance risks, and Must Fix items.
- `03-claude-implement.md`: Claude is `Conservative Maintainer`, responsible for the smallest safe implementation, prioritizing Codex Must Fix items, avoiding unrelated refactors, and writing a Developer Self-Test Report.
- `04-codex-final-acceptance.md`: Codex is `Verifier + Release Gatekeeper + User Acceptance Reviewer`, responsible for final acceptance using the user request, git diff, developer self-test, optional runner verification, and agent outputs.

Design principles:

- Personas serve responsibility boundaries, not roleplay.
- Claude is the only agent allowed to modify business code.
- Claude acts as the developer and chooses how to test; it must explain test approach, results, failures, and untested areas.
- Codex performs external review and acceptance; it does not edit business code. It must judge whether Claude's self-test is adequate.
- Prompts should be short and firm: clear inputs, constraints, output contracts, and quality gates.
- You can adjust checks and tone, but do not break the stage responsibilities.
- The first `Decision:` line in Codex prompts is parsed by the Bash quality gate and must remain stable.

## Developer Self-Test and Runner Verification

By default, `dual` does not guess the project's test command. Claude acts as the developer, chooses appropriate tests or checks for the change, and writes a Developer Self-Test Report in `outputs/03-claude-implement.md`. The runner archives it to:

```text
verification/developer-self-test.md
```

Codex final acceptance reviews that report as developer-provided test evidence: whether the tests are relevant, whether they cover the requirements, and whether untested areas are acceptable.

`--test` is an optional runner-level verification command. When provided, the runner executes it after Claude implementation and saves:

```text
verification/runner-test-command.txt
verification/runner-test-output.log
```

So:

- Without `--test`: Developer Self-Test + git evidence + Codex verifier.
- With `--test`: the same flow plus runner-level verification.

## Quality Gates

Codex design review should output:

```text
Decision: PASS
Decision: NEEDS_REVISION
Decision: BLOCKED
```

`BLOCKED` stops before implementation and marks the run `blocked`.

Codex final acceptance should output:

```text
Decision: ACCEPT
Decision: ACCEPT_WITH_NOTES
Decision: REJECT
```

`REJECT` marks the run `rejected` and exits with code `2`.

If `--test` is provided and the runner verification command fails, final acceptance still runs unless `--skip-final-acceptance` is set. The run exits with code `3` unless `--allow-test-failure` is set.

## Exit Codes

- `0`: success, accepted, dry-run completed, or runner verification failure allowed
- `1`: usage, dependency, protected in-place branch, or unexpected runtime failure
- `2`: Codex final acceptance returned `REJECT`
- `3`: runner verification command failed and `--allow-test-failure` was not set
- `4`: Codex design review returned `BLOCKED`
- `5`: worktree creation or attachment failed
- `6`: PR creation failed

Notification failure does not change the exit code.

## Smoke Tests

Default worktree:

```bash
dual "Add a short project description to README"
```

Explicit worktree compatibility:

```bash
dual --worktree "Add a short project description to README"
```

In-place:

```bash
dual --in-place "Add a short project description to README"
```

Protected branch:

```bash
dual --in-place "Add a short project description to README"
```

Allow main in-place:

```bash
dual --in-place --allow-main-in-place "Add a short project description to README"
```

Non-git default:

```bash
dual "Task description"
```

Non-git in-place:

```bash
dual --in-place "Task description"
```

PR:

```bash
dual --create-pr "Task description"
```

Notification:

```bash
dual \
  --notify-command 'echo "$DUAL_EXECUTION_MODE $DUAL_WORKDIR $DUAL_WORKTREE_DIR"' \
  --notify-on always \
  "Task description"
```

## Limits

- No Slack, Feishu, Notion, or other dedicated notification API.
- No database.
- No Web UI.
- No automatic PR merge.
- No automatic worktree deletion.
- No multi-round automatic repair.
- PR creation is never default.
- `dual` does not silently downgrade from worktree mode to in-place mode.
- `dual` refuses in-place execution on `main` or `master` unless `--allow-main-in-place` is set.
