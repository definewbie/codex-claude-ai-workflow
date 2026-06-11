# dual-orchestrator

`dual-orchestrator` is a local Agent lifecycle orchestrator. It uses `acpx` to
drive Claude and Codex through a traceable software delivery workflow:
requirement clarification, design review, planning, implementation, self-test,
verification, repair, delivery, and handoff.

Chinese version: [README.md](README.md)

## What It Solves

- Claude acts as the developer/architect: requirement clarification, design,
  implementation, self-test, and change summary.
- Codex acts as the external reviewer/verifier: product, architecture, and final
  acceptance review.
- `acpx` is the Agent transport boundary, so the concrete Agent tool can evolve.
- Each workstream runs in an isolated Git worktree by default.
- Every state transition, prompt, Agent output, review packet, and report is
  written to a local ledger.
- Humans approve, revise, or complete work at explicit gates.
- CLI and the Operator Panel both answer: where is the workflow, why did it
  stop, and what should happen next?

## Current Capabilities

- Java / Maven multi-module project.
- `dualctl` CLI.
- Default Git worktree isolation.
- File ledger under `.agent/workstreams/<workstream_id>/`.
- Task / Decision / Gate workflow runtime.
- Claude / Codex `acpx` Agent tasks.
- Human review gates and review packets.
- Failure classification, recovery, retry, and bounded repair loops.
- TaskContract and structured Agent artifact contracts.
- Context packages and next-stage handoff.
- Public real-case benchmark and smoke tests.
- Operator Panel with overview, attention items, timeline, review packet, and
  recovery summary.

## Install

Prerequisites:

```bash
java -version       # JDK 21+
mvn -version        # Maven 3.9+
git --version
acpx --version
```

Install locally:

```bash
cd dual-orchestrator
./scripts/install.sh
export PATH="$HOME/.local/bin:$PATH"
dualctl --help
```

## Quickstart

Check a target repository:

```bash
dualctl doctor --workdir /path/to/repo
```

Run a bounded real task:

```bash
./scripts/run-real-case.sh \
  --repo /path/to/repo \
  --task-file /path/to/task.md \
  --variant full
```

Inspect a workstream:

```bash
dualctl trace --workdir <effective_workdir> <workstream_id>
```

For a reusable public benchmark:

```bash
./scripts/run-public-benchmark.sh
```

## Workflow Kernel

LiteFlow is the default workflow routing kernel. It only computes transition
candidates from the `WorkflowDefinition` and the current ledger snapshot; the
ledger, human gates, artifact contracts, and completion contract remain owned by
dual.

```bash
dualctl trace --workdir <effective_workdir> <workstream_id>
./scripts/run-real-case.sh \
  --repo /path/to/repo \
  --task-file /path/to/task.md \
  --variant full
```

For diagnostics or emergency fallback, explicitly select the built-in kernel:

```bash
DUAL_FLOW_KERNEL=builtin dualctl trace --workdir <effective_workdir> <workstream_id>
java -Ddual.flow.kernel=builtin -jar dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar ...
```

Supported kernel values are `liteflow` and `builtin`. Unknown values fail fast
instead of silently falling back to the wrong kernel.

## Documentation

- [Quickstart](docs/quickstart.md) / [中文](docs/quickstart.zh-CN.md)
- [Docs index](docs/README.md) / [中文](docs/README.zh-CN.md)
- [Architecture and execution flow](docs/architecture-and-execution-flow.md)
- [Workflow state machine](docs/design/workflow-state-machine.md)
- [Workflow execution model](docs/design/workflow-execution-model.zh-CN.md)
- [Artifact contracts and context](docs/design/artifact-contracts-and-context.md) / [中文](docs/design/artifact-contracts-and-context.zh-CN.md)
- [Failure recovery and calibration](docs/design/failure-recovery-and-calibration.md) / [中文](docs/design/failure-recovery-and-calibration.zh-CN.md)
- [Operator observability](docs/design/operator-observability.md) / [中文](docs/design/operator-observability.zh-CN.md)
- [Design evolution notes](docs/design/design-evolution-notes.md) / [中文](docs/design/design-evolution-notes.zh-CN.md)

## Current Limits

- The project is pre-1.0; workflow schema and prompts will continue to evolve.
- Real runs depend on local `acpx`, Claude, and Codex availability.
- Operator Panel is a local single-user tool, not a hosted multi-user product.
- Delivery automation is conservative: it prepares evidence and PR materials,
  but never auto-merges.
