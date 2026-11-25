# 故障排查

English version: [troubleshooting.md](troubleshooting.md)

当 run 停住、页面状态看起来不一致，或者 Agent 运行时间明显异常时，先看账本，不要只看 UI。

## 第一组命令

```bash
dualctl trace --workdir <effective_workdir> <workstream_id>
dualctl recovery show --workdir <effective_workdir> <workstream_id>
dualctl runs --workdir <effective_workdir> <workstream_id>
dualctl events --workdir <effective_workdir> --limit 80 <workstream_id>
```

`trace` 是第一入口。它应该回答：现在跑到哪、为什么停、有什么证据、下一步什么操作是安全的。

## acpx / Claude 401

现象：

- 交互式 shell 里 Claude 正常，但 `acpx claude ...` 返回 401。

常见原因：

- Claude ACP 子进程没有拿到 Anthropic-compatible provider 的环境变量。

检查：

```bash
dualctl doctor --workdir /path/to/repo
```

修复：

```bash
DUAL_NODE_BIN_DIR=/path/to/node-22.13-or-newer/bin ./scripts/install-acpx-e1.sh
export DUAL_ACPX_EXECUTABLE="$PWD/.tools/acpx-e1/bin/acpx"
./scripts/configure-acpx.sh
```

如果使用 GLM、DeepSeek、one-api、claude-code-router 等后端，把 provider
变量放在 `~/.claude/settings.json` 的 `env` 字段里。配置脚本安装的 Claude
wrapper 会在运行时读取这些变量，不会把 token 写入 `~/.acpx/config.json`。

## Reconnect 或疑似挂住

现象：

- 日志出现 `agent needs reconnect`。
- Agent 看起来很慢或像挂住，但后续又有输出。
- run 长时间停在 `[client] session/resume (running)`，迟迟没有进入 prompt。

runtime 的处理方式：

- 把 acpx/Claude reconnect 当成 adapter lifecycle signal。
- 不会因为旧的 reconnect 文案就杀掉仍在工作的 Agent。
- 如果后续 stream activity 和完整 terminal contract 证明 Agent 仍在推进，会覆盖旧 reconnect 证据。
- Agent step 默认使用 workstream-scoped session：
  `acpx <agent> -s <base>-<workstream_id> -f <prompt>`。节点上下文仍来自 prompt、
  context package 和 ledger；ACP session 只作为同一 workstream 内的执行缓存和连续性辅助。
- acpx queue owner 属于这个 session lifecycle。Dual 不应该在每个节点结束后杀掉它；
  清理由 acpx TTL 负责。默认 TTL 是 1800 秒，可通过 `DUAL_ACPX_QUEUE_TTL_SECONDS` 覆盖。

检查：

```bash
dualctl runs --workdir <effective_workdir> <workstream_id>
dualctl trace --workdir <effective_workdir> <workstream_id>
```

如果旧 run 使用了已经过时的 session 策略，并卡在 `session/resume`，先停止该 run，再用当前 CLI build 重试。如果 workflow 进入 recovery，先看恢复评估再 retry：

```bash
dualctl recovery show --workdir <effective_workdir> <workstream_id>
dualctl retry --workdir <effective_workdir> <workstream_id>
```

## 等待人工 Review

现象：

- 状态是 `WAITING_FOR_HUMAN`。

先读当前 review packet：

```bash
dualctl review show --workdir <effective_workdir> <workstream_id>
```

然后选择一个动作：

```bash
dualctl approve --run --workdir <effective_workdir> <workstream_id>
dualctl revise --feedback-file feedback.md --run --workdir <effective_workdir> <workstream_id>
dualctl complete --workdir <effective_workdir> <workstream_id>
```

历史 gate 只是证据；只有当前 gate 才能执行动作。

## 已完成但 UI 还像 Running

账本是事实来源。先刷新页面，再对照：

```bash
dualctl trace --workdir <effective_workdir> <workstream_id>
```

如果 `trace` 显示 `COMPLETED`，workflow 已经进入终态。run/resume/retry/approve
不应该再创建新的 workflow node。此时只允许观察、summary、delivery merge 或 cleanup。

## Worktree 太多

每个 workstream 默认使用独立 worktree。从原始仓库查看：

```bash
git worktree list
```

确认不再需要后清理：

```bash
git worktree remove <worktree_dir>
git branch -D <branch>
```

不要在保存或合并目标改动前删除 worktree。

## 合并已验收改动

workstream 完成，且 final acceptance 是 `ACCEPT` 或 `ACCEPT_WITH_NOTES` 后：

```bash
dualctl delivery merge \
  --workdir <source_worktree> \
  --target <original_repo> \
  <workstream_id>
```

这个命令会把已审查 patch apply 到目标仓库 index，并在 workstream ledger 下写入 delivery 证据。它不会自动 commit。

## Token 或 Context 明显过大

先看 run metadata 和 context package：

```bash
dualctl runs --workdir <effective_workdir> <workstream_id>
```

如果某个阶段反复消耗过多上下文，应把它当成 context package 边界问题，而不是 Agent 性格问题。正确方向是减少原始工具 transcript 传递，改用结构化 artifact、压缩 handoff 和聚焦 evidence refs。

Token source 含义：

- `acpx_stream`：真实 provider usage，包含 input/output 拆分。
- `acpx_usage_update`：只拿到 ACP total used/size，能展示总量，不能拆 provider input/output。
- `artifact_estimate`：根据 prompt/output/transcript metadata 做的本地估算。

## Public Benchmark 失败

先跑：

```bash
rtk proxy mvn -q clean test
dualctl doctor --workdir /path/to/repo
```

如果离线回归通过，但另经批准的 public benchmark 失败，查看本地报告：

```text
docs/testing/real-case-reports/<timestamp>/
```

优先把失败归类为：

- provider/session 可用性；
- artifact contract 违规；
- recovery 边界缺陷；
- 产品/领域验收拒绝；
- benchmark 预期不完整。

不要用 workflow handler 里的临时字符串解析来修这类问题。外部输出必须先转成 typed artifact、event 或 failure signal。
