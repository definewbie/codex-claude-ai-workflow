# Release Checklist

中文版本: [release-checklist.zh-CN.md](release-checklist.zh-CN.md)

Use this checklist before publishing `dual-orchestrator` as an open-source
release. It keeps release work focused on user trust and runnable evidence, not
cosmetic cleanup.

## Required Before Public Release

- [x] Add the MIT `LICENSE`.
- [ ] Decide whether to add `CONTRIBUTING.md`, `SECURITY.md`, and a code of
  conduct for the first public release.
- [ ] Re-read `README.md`, `README.en.md`, `docs/quickstart.md`, and
  `docs/quickstart.zh-CN.md` from a new-user perspective.
- [ ] Verify public docs do not expose private repo names, local absolute paths,
  raw Agent prompts, or private product details.
- [ ] Verify `docs/testing/real-case-benchmarks/benchmark-index.md` contains
  only public benchmark cases.

## Verification Commands

Run from `dual-orchestrator` unless noted otherwise:

```bash
mvn -pl dual-cli,dual-server -am test
node --check dual-server/src/main/resources/static/app.js
bash -n scripts/*.sh scripts/lib/*.sh
./scripts/smoke-test.sh --case lifecycle --cleanup
./scripts/run-public-benchmark.sh
./scripts/real-case-benchmark-summary.sh
```

From the repository root:

```bash
git diff --check
git grep -n "cancer-clinic\\|/Users/.*work/projects\\|private-repo-name" -- dual-orchestrator/README.md dual-orchestrator/README.en.md dual-orchestrator/docs dual-orchestrator/scripts
```

The public benchmark uses real `acpx` Claude/Codex and consumes quota. If quota
or provider availability prevents a run, record that explicitly in the release
notes instead of treating an old run as fresh evidence.

## Expected Evidence

The latest public benchmark should show:

- final status: `COMPLETED`
- benchmark grade: `PASS` or a clearly explained `PASS-WARN`
- original repo clean: `true`
- final report present
- handoff JSON present
- next-stage prompt present
- delivery PR body present
- delivery commit message present

## Do Not Block Release On

These are useful, but not required for the first open-source MVP:

- Homebrew formula
- Docker image
- native image / desktop packaging
- multi-user hosted dashboard
- automatic merge
- cross-workstream dependency coordination
- exact provider billing reconciliation beyond best-effort acpx stream usage

## Release Notes Template

```markdown
# dual-orchestrator <version>

## Highlights

- Local Claude/Codex lifecycle orchestration through acpx.
- Default Git worktree isolation.
- File ledger under .agent/workstreams/<workstream_id>/.
- Human review packets, recovery, repair loops, final report, and handoff.
- Operator Panel and dualctl trace for observability.

## Validation

- Smoke: <PASS/FAIL, command, date>
- Public benchmark: <PASS/PASS-WARN/FAIL, workstream id, date>
- Maven tests: <PASS/FAIL>

## Known Limits

- Pre-1.0 workflow schemas and prompts may evolve.
- Real runs depend on local acpx and provider quota.
- Operator Panel is local single-user tooling.
```
