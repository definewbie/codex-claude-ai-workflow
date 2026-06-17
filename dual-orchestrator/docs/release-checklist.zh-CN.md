# 发布检查清单

English version: [release-checklist.md](release-checklist.md)

这份清单用于 `dual-orchestrator` 开源发布前的最后确认。它关注用户信任和可运行证据，而不是表面上的文档洁癖。

## 正式公开前必须确认

- [x] 添加 MIT `LICENSE`。
- [ ] 决定第一版是否添加 `CONTRIBUTING.md`、`SECURITY.md` 和 code of conduct。
- [ ] 以新用户视角重读 `README.md`、`README.en.md`、`docs/quickstart.md`、`docs/quickstart.zh-CN.md`。
- [ ] 确认公开文档没有私有仓库名、本地绝对路径、原始 Agent prompt 或私有产品细节。
- [ ] 确认 `docs/testing/real-case-benchmarks/benchmark-index.md` 只包含公开 benchmark case。

## 验证命令

除非特别说明，在 `dual-orchestrator` 目录运行：

```bash
mvn -pl dual-cli,dual-server -am test
node --check dual-server/src/main/resources/static/app.js
bash -n scripts/*.sh scripts/lib/*.sh
./scripts/smoke-test.sh --case lifecycle --cleanup
./scripts/run-public-benchmark.sh
./scripts/real-case-benchmark-summary.sh
```

在仓库根目录运行：

```bash
git diff --check
git grep -n "cancer-clinic\\|/Users/.*work/projects\\|private-repo-name" -- dual-orchestrator/README.md dual-orchestrator/README.en.md dual-orchestrator/docs dual-orchestrator/scripts
```

公开 benchmark 会调用真实 `acpx` Claude/Codex，并消耗 quota。如果额度或 provider 可用性导致不能运行，需要在 release notes 中明确说明，不要把旧结果当作新证据。

## 期望证据

最新 public benchmark 应显示：

- final status: `COMPLETED`
- benchmark grade: `PASS`，或有清晰解释的 `PASS-WARN`
- original repo clean: `true`
- final report 存在
- handoff JSON 存在
- next-stage prompt 存在
- delivery PR body 存在
- delivery commit message 存在

## 不阻塞第一版 MVP 的事项

这些能力有价值，但不应阻塞第一版开源 MVP：

- Homebrew formula
- Docker image
- native image / desktop packaging
- 多用户托管 dashboard
- 自动 merge
- 跨 workstream 依赖编排
- 超出 acpx stream usage 最佳努力采集范围的精确 provider 计费对账

## Release Notes 模板

```markdown
# dual-orchestrator <version>

## Highlights

- 通过 acpx 编排本地 Claude/Codex 生命周期。
- 默认 Git worktree 隔离。
- 文件账本：.agent/workstreams/<workstream_id>/。
- Human review packet、recovery、repair loop、final report 和 handoff。
- Operator Panel 和 dualctl trace 提供可观测性。

## Validation

- Smoke: <PASS/FAIL, command, date>
- Public benchmark: <PASS/PASS-WARN/FAIL, workstream id, date>
- Maven tests: <PASS/FAIL>

## Known Limits

- pre-1.0，workflow schema 和 prompt 仍会演进。
- 真实运行依赖本地 acpx 和 provider quota。
- Operator Panel 是本地单用户工具。
```
