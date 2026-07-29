# T6 中等复杂度 Case Readiness Assessment

日期：2026-07-29
状态：`NOT_READY / BACKEND BASELINE PREFLIGHT BLOCKED`

## 1. 唯一目标

判断最新 Dual 是否具备运行一次中等复杂度 full real-case 的条件。本 assessment
不运行 provider、不修改 production，也不建设新的 case 平台。

```text
Target phase: P2
Current status: readiness assessed, real-case not active
Upstream evidence: T5 v3 PASS at d0bae639
```

## 2. 唯一候选

复用已有 cancer-clinic P0 business case，不发明新业务需求：

- task evidence：
  `docs/testing/real-case-reports/20260619T051121Z/task.md`；
- historical report：
  `docs/testing/real-case-reports/20260619T051121Z/report.md`；
- pinned target baseline：
  `ed82f5789a2e54ea3ccdb983d40b3fe64e9a4927`；
- readiness worktree：
  `/private/tmp/dual-t6-cancer-clinic-readiness`。

历史运行证明该任务确实跨 backend API、backend tests、frontend API 和 frontend
pages，最终产生 6 个文件的业务变更；它不是 README、单文件或纯文档 case。

历史运行使用旧 workflow v5，共 24 Agent runs、67 个节点、2 次 acceptance
REJECT，耗时约 68 分钟。它只能证明 case 具有中等复杂度，不能作为最新 Dual 的
效率或正确性结论。

## 3. Bounded Scope

旧 task 中的“包括但不限于”不能进入新 real-case。T6 运行前必须把 workspace
change boundary 固定为以下 7 个相关文件：

```text
backend/app/api/chemo.py
backend/tests/test_chemo.py
miniprogram/src/api/chemo.ts
miniprogram/src/api/request.ts
miniprogram/src/pages/record/index.vue
miniprogram/src/pages/retro/index.vue
miniprogram/src/pages/treatment-record/index.vue
```

这 7 个文件覆盖业务 API、可执行 backend tests、frontend API 与三个用户路径。
Agent 不需要为了满足数字而修改全部文件，但任何实际变更必须是该集合的子集。

禁止：

- 修改 backend enum、migration、model 或 schema；
- 修改 `TreatmentEvent.status`；
- 引入规则引擎、聚合平台或第二条报告关联模型；
- 使用 title/date/notes 猜测业务关系；
- 修改上述集合以外的文件；
- 提交 target repo。

## 4. Deterministic Readiness Evidence

### 已通过

- detached worktree HEAD 精确等于 `ed82f57`；
- worktree 在 assessment 前后均 clean；
- 用户当前 cancer-clinic 工作树的
  `miniprogram/src/pages/follow-up-summary/index.vue` dirty change 未被读取为
  execution fact，也未被复制到 readiness worktree；
- 使用当前本地缓存的 pinned `node_modules`，执行
  `npm run build:mp-weixin` 为 PASS；
- 本机已有 `postgres:16-alpine` image，可在未来 preflight 中离线启动数据库；
- 历史 change evidence 显示该 task 的实际变更恰为 6 个 bounded files。

### 未通过

当前机器没有可用的 cancer-clinic Python test environment：

```text
import pytest, fastapi, sqlalchemy
-> ModuleNotFoundError: pytest
```

`backend/tests/conftest.py` 同时要求：

```text
postgresql+asyncpg://clinic:clinic_dev@localhost:5432/cancer_clinic_test
```

因此今天无法在 provider run 之前执行 authoritative backend baseline tests。
历史的 `167 passed` 不能代替当前 preflight，前端 build PASS 也不能证明 backend
acceptance 可执行。

## 5. Verification Gate Draft

该 gate 只有在 backend baseline blocker 解除后才能固化为可执行 gate 文件。

```text
Verification Gate:
- Target:
  Latest clean Dual completes the pinned cancer-clinic medium business task
  without weakening strict contracts or the bounded change scope.
- Source of truth:
  workflow/current.json; runs/*/run.json; task-contract.json;
  change-scope-evaluation.json; acceptance contract; target test logs;
  metrics/workstream-metrics.json.
- Trigger precondition:
  Dual clean SHA; target ed82f57; detached target clean; pinned Python
  dependencies installed in an ephemeral environment; local test PostgreSQL
  healthy; full backend pytest PASS; miniprogram build PASS; fresh Dual reactor
  PASS; Claude/Codex probes PASS.
- How precondition is checked before run:
  Record exact commands, dependency lock hashes, database health, test counts,
  target HEAD/status, Dual jar freshness, runner kind and probe evidence before
  creating the workstream.
- PASS:
  Workflow COMPLETED; acceptance ACCEPT or non-regressive ACCEPT_WITH_NOTES;
  backend pytest and miniprogram build PASS after change; change scope PASS;
  no prohibited medical conclusion wording; strict results valid; all usage
  reconciled; protocol correction <= 1; semantic revision <= 1.
- FAIL:
  Target quality, strict contract, business boundary, change scope or final
  acceptance fails; second correction or second semantic revision occurs;
  Agent runs exceed 15 or elapsed workflow time exceeds 35 minutes.
- INVALID:
  Pinned target, dependency environment, database, baseline tests, provider,
  model, probe, runner, freshness or usage reconciliation is not comparable.
- Stop-loss:
  Stop on second correction/revision, Agent run/time cap, out-of-scope change,
  prohibited business behavior, typed fatal/reconnect/rate-limit, missing usage
  or target worktree contamination.
- Run artifact freshness:
  Fresh Maven package and recorded jar/source mtimes; no --skip-build.
```

Token amount is observed and reconciled but is not a PASS threshold. T6 proves medium-case
correctness and bounded execution, not a second token optimization experiment.

## 6. Boundary Review

```text
Boundary Review: WARN / REAL-CASE NOT READY

Declared by:
  pinned task + bounded seven-file task contract

Proven by:
  pre-run backend pytest + miniprogram build + target cleanliness

Consumed by:
  ChangeScopeEvaluation, strict artifact validators, acceptance decision

Enforced before:
  provider workstream creation and downstream workflow success

Failure route:
  preflight mismatch -> INVALID / no workstream
  business or scope failure -> FAIL / stop-loss

Cannot-bypass:
  no provider run when backend baseline cannot execute
  no current dirty user file included in the detached baseline
  no "includes but not limited to" scope

Residual raw path:
  none accepted; historical report is evidence, not current execution truth
```

## 7. Decision

T6 当前为 `NOT_READY`。唯一 blocker 是可重复的 backend test preflight，而不是
Dual production 缺陷。

最小解除阻塞动作：

1. 在 `/private/tmp` 创建 ephemeral Python environment；
2. 按 `backend/requirements.txt` 的 pinned versions 安装依赖；
3. 使用本地 `postgres:16-alpine` 启动隔离的 `cancer_clinic_test`；
4. 在 `ed82f57` detached worktree 运行 full backend pytest；
5. 再次运行 miniprogram build 并确认 worktree clean；
6. 全部 PASS 后固化 task + executable Verification Gate；
7. 重新获得一次明确 provider 授权，才允许创建一个 T6 workstream。

依赖安装可能需要网络，因此不在本次只读 assessment 中执行。不得用跳过 backend
tests、复用历史 test count 或只跑 frontend build 的方式把 T6 提升为 READY。
