# Delivery Merge 设计

English version: [delivery-merge.md](delivery-merge.md)

## 问题

默认执行模式使用 Git worktree，这是安全的：Agent 改代码时不会污染原始仓库。

但这里缺了交付闭环：workstream 验收通过后，用户仍然需要手工把 worktree 的 diff 合回 original repo。手写 `git diff | git apply` 容易出错，也会导致一堆 worktree 堆在那里，不知道哪个已交付、哪个可清理、哪个应该保留。

## 目标

把 delivery merge 做成验收后的显式交付动作：

```text
accepted worktree -> merge plan -> patch -> apply to original repo -> delivery record
```

workflow runtime 在 completed 后仍然保持终态。Delivery merge 是受控交付动作，不是 resume、retry，也不是 semantic repair。

## 边界模型

| 概念 | 归属 | 含义 |
|---|---|---|
| Source worktree | Git workspace | Agent 执行和改代码的沙箱。 |
| Target repo | Git workspace | 接收已验收改动的原始仓库。 |
| Delivery merge plan | Ledger artifact | merge 准备状态和执行结果的 source of truth。 |
| Patch | Delivery artifact | 从 source worktree 生成的精确 diff。 |
| CLI / UI | Projection + command surface | 展示状态，并触发显式 delivery action。 |

target repo 不能被后台 workflow 自动修改。只有显式 delivery merge 命令可以写 target repo。

## Ledger 结构

```text
.agent/workstreams/<workstream_id>/delivery/
  merge-plan.json
  merge.patch
  changed-files.txt
  excluded-files.txt
  apply.stdout.log
  apply.stderr.log
```

`merge-plan.json`：

```json
{
  "schemaVersion": 1,
  "workstreamId": "ws-...",
  "sourceWorktree": "...",
  "targetRepo": "...",
  "acceptanceDecision": "ACCEPT_WITH_NOTES",
  "changedFiles": [],
  "excludedFiles": [],
  "status": "READY",
  "commit": "",
  "failureReason": ""
}
```

允许状态：

- `READY`：patch 已生成，target repo 可以安全 apply。
- `APPLIED`：patch 已 apply 到 target repo index/worktree。
- `COMMITTED`：target repo commit 已记录。
- `FAILED`：merge 准备或 apply 失败，日志已保留。

## 前置条件

只有满足以下条件才允许 delivery merge：

- workflow status 是 `COMPLETED`；
- completion audit ready；
- final acceptance 是 `ACCEPT` 或 `ACCEPT_WITH_NOTES`；
- source worktree 存在并且是 Git worktree；
- target repo 存在并且是 Git repo；
- target repo 工作区干净；
- source worktree 有非空业务 diff。

默认排除：

```text
.agent/
node_modules/
dist/
build/
target/
*.log
```

## CLI

第一版命令：

```bash
dualctl delivery merge \
  --workdir <source_worktree> \
  --target <original_repo> \
  <workstream_id>
```

行为：

1. 从 source worktree 读取 workstream ledger；
2. 校验 completed 和 acceptance；
3. 校验 target repo 干净；
4. 从 source worktree 生成 changed file list 和 patch；
5. 排除 ledger / build artifacts；
6. apply patch 到 target repo index；
7. 写 delivery merge plan 和日志；
8. 输出 target diff stat 和下一步 commit 命令。

它不自动 commit，也不自动清理 worktree。

第一版支持 tracked 业务文件改动。未跟踪的业务文件会以清晰错误失败，不会静默修改
source worktree 的 index。后续如果需要支持新增文件，应使用专门的临时 index 实现。

## Operator Panel

completed workstream 应该展示 Delivery 区块：

```text
Delivery
- Acceptance:
- Changed files:
- Target repo:
- Merge status:
- Last failure:
```

第一版 UI 可以先展示命令，不急着做按钮。等 CLI 和 ledger 模型稳定后，再补按钮。

## 测试

必须覆盖：

- accepted completed workstream 可以把 source worktree diff apply 到 clean target repo；
- target dirty 时阻止 merge，且 target 不被修改；
- rejected acceptance 阻止 merge；
- empty patch 给出清晰失败；
- excluded files 不进入 target patch；
- patch apply conflict 写 stderr，并把 plan 标记为 failed。

## Review Checklist

- Delivery merge 没有塞进 workflow resume/retry。
- Completed workstream 对 runtime execution 仍然是终态。
- target repo 写入只发生在显式 delivery command。
- CLI 和 UI 读取同一份 merge plan。
- apply 失败时保留 patch 和 stderr。
- cleanup 仍然是显式动作，不自动执行。
