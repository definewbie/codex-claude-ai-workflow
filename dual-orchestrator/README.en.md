# dual-orchestrator

中文版本: [README.md](README.md)

`dual-orchestrator` is a local Agent lifecycle orchestrator. It uses `acpx` to
drive Claude and Codex through a traceable, pausable, recoverable software
delivery workflow: requirement clarification, design review, planning,
implementation, self-test, verification, repair, delivery, and handoff.

It is not another chat UI and it is not a CI replacement. It targets a common
operator problem: copying artifacts between Claude Code and Codex, tracking
state by memory, and manually assembling evidence after a long Agent run.

## Core Idea

- Claude acts as the developer / architect for design, implementation,
  self-test, and handoff.
- Codex acts as the external reviewer / verifier for product, architecture, and
  final acceptance.
- `acpx` is the Agent transport boundary, so concrete Agent tools can evolve.
- Each workstream runs in an isolated Git worktree by default.
- State transitions, events, Agent outputs, review packets, reports, and
  delivery materials are written to a local ledger.
- Humans only approve, revise, or complete explicit human gates.
- CLI and Operator Panel answer the same question: where is it, why did it stop,
  and what should happen next?

## Current Capabilities

- `dualctl` CLI and local Operator Panel.
- Task / Decision / Gate workflow runtime.
- LiteFlow as the default routing kernel, with built-in kernel fallback.
- Claude / Codex `acpx` Agent tasks.
- TaskContract, artifact contracts, context packages, handoff / next-stage prompt.
- Failure classification, recovery, retry, and bounded repair loops.
- Final report, delivery PR body, and commit message preparation.
- Public smoke test and real-case benchmark.

## Install

Prerequisites:

```bash
java -version       # JDK 21+
mvn -version        # Maven 3.9+
git --version
acpx --version
```

Install:

```bash
cd dual-orchestrator
./scripts/install.sh
export PATH="$HOME/.local/bin:$PATH"
dualctl --help
```

## 5-Minute Validation

Run the controlled smoke first:

```bash
./scripts/smoke-test.sh --case lifecycle --cleanup
```

Then run the public real-case benchmark:

```bash
./scripts/run-public-benchmark.sh
```

This copies `examples/issue-flow-app` to `/tmp`, runs the full lifecycle with
real `acpx` Claude/Codex, and writes a local ignored report under:

```text
docs/testing/real-case-reports/<timestamp>/
```

The committed public benchmark index keeps only sanitized aggregate results:

```text
docs/testing/real-case-benchmarks/benchmark-index.md
```

## Run On Your Own Repo

Check the target repo first:

```bash
dualctl doctor --workdir /path/to/repo
```

Start with a small bounded task:

```bash
cat > /tmp/dual-task.md <<'EOF'
Update the README with a short "Local Development" section.
Keep the change documentation-only. Do not modify source code.
EOF

./scripts/run-real-case.sh \
  --repo /path/to/repo \
  --task-file /tmp/dual-task.md \
  --variant full
```

The script creates a safe worktree and auto-approves intermediate benchmark
gates. For real delivery, still read the review packet and final report.

## Inspect State

Start with:

```bash
dualctl trace --workdir <effective_workdir> <workstream_id>
```

`trace` shows current status, active node, stop reason, attention items, recent
timeline, final acceptance, repair loops, key artifacts, and the next command.

If the workflow is waiting at a human gate:

```bash
dualctl review show --workdir <effective_workdir> <workstream_id>
dualctl approve --run --workdir <effective_workdir> <workstream_id>
dualctl revise --feedback-file feedback.md --run --workdir <effective_workdir> <workstream_id>
dualctl complete --workdir <effective_workdir> <workstream_id>
```

## Operator Panel

Start the local dashboard:

```bash
java -jar dual-server/target/dual-server-0.1.0-SNAPSHOT.jar
```

Open:

```text
http://localhost:8080/
```

The Operator Panel uses the same ledger read model as `dualctl trace` and
subscribes to overview updates through SSE. It is a local single-user operator
tool, not a hosted multi-user web product.

## Documentation

- [Quickstart](docs/quickstart.md) / [中文](docs/quickstart.zh-CN.md)
- [Docs index](docs/README.md) / [中文](docs/README.zh-CN.md)
- [Architecture and execution flow](docs/architecture-and-execution-flow.en.md) / [中文](docs/architecture-and-execution-flow.md)
- [Workflow state machine](docs/design/workflow-state-machine.md)
- [Artifact contracts and context](docs/design/artifact-contracts-and-context.md)
- [Failure recovery and calibration](docs/design/failure-recovery-and-calibration.md)
- [Operator observability](docs/design/operator-observability.md)
- [Current feature status](docs/features.md)
- [Real-case benchmark](docs/testing/real-case-benchmarks/README.md)
- [Release checklist](docs/release-checklist.md)
- [Full reference](docs/reference.md)

## Current Limits

- The project is pre-1.0; workflow schemas and prompts will continue to evolve.
- Real runs depend on local `acpx`, Claude, Codex availability, and quota.
- Public benchmark runs consume real Agent quota.
- Delivery automation is conservative: it prepares evidence and PR materials,
  but never auto-merges.
- The project uses the MIT License; contribution and security-reporting docs
  should be added before broader public collaboration.
