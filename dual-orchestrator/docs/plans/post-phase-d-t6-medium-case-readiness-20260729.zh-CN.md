# T6 中等复杂度 Case Readiness Assessment

日期：2026-07-29
状态：`READY_WITH_EXPLICIT_SUPPLEMENTAL_PIN / REAL-CASE NOT ACTIVE`

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
- `/private/tmp/dual-t6-cancer-clinic-venv` 使用 Python 3.13.1 创建；
- `backend/requirements.txt` 中全部 exact pins 已安装，文件 SHA-256 为
  `412637f581208f7a64fac57a705c1c4a2ef868d954c16c7924bddb1a4cc1a391`；
- 复用项目专属、健康的 `postgres:16-alpine` 容器
  `cancer_clinic_db`，测试只连接独立数据库 `cancer_clinic_test`，不连接
  `cancer_clinic`；
- detached baseline 上 full backend pytest 为
  `191 passed, 580 warnings in 73.39s`；
- backend pytest 后再次执行 `npm run build:mp-weixin` 为 PASS；
- backend/frontend baseline 后 detached worktree 仍 clean，HEAD 仍为
  `ed82f5789a2e54ea3ccdb983d40b3fe64e9a4927`；
- 用户当前 cancer-clinic 工作树仍只有 assessment 前已存在的
  `miniprogram/src/pages/follow-up-summary/index.vue` 修改；
- 历史 change evidence 显示该 task 的实际变更恰为 6 个 bounded files。

### 显式 dependency manifest gap

仅安装 `backend/requirements.txt` 后，pytest collection 会因为以下运行时依赖
缺失而失败：

```text
RuntimeError:
Form data requires "python-multipart" to be installed.
```

原因是 `backend/app/api/examinations.py` 使用 FastAPI `File` / `Form` /
`UploadFile`，但 target baseline 的 `backend/requirements.txt` 没有声明
`python-multipart`。当前仓库与历史中也没有该依赖的既有 pin。

本次只在 ephemeral environment 中显式补装
`python-multipart==0.0.32`，随后 full backend baseline PASS。没有修改 target
repo，也没有把缺失依赖静默当作 transitive dependency。未来 T6 preflight 必须使用
同一个 exact supplemental pin 并记录实际 `pip freeze`；缺失、版本不同或未记录时
本轮必须判为 `INVALID`。

## 5. Verification Gate Draft

backend baseline blocker 已解除。以下 gate 可以在 provider 授权前固化为独立可执行
gate；当前尚未授权或创建 T6 workstream。

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
  dependencies plus explicit python-multipart==0.0.32 installed in an ephemeral
  environment; local cancer_clinic_test PostgreSQL healthy; full backend pytest
  PASS; miniprogram build PASS; fresh Dual reactor PASS; Claude/Codex probes PASS.
- How precondition is checked before run:
  Record requirements SHA-256, pip freeze including python-multipart==0.0.32,
  database identity/health, 191-test PASS, frontend build PASS, target HEAD/status,
  Dual jar freshness, runner kind and probe evidence before creating the workstream.
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
  model, probe, runner, freshness or usage reconciliation is not comparable;
  supplemental python-multipart pin is absent, different or unrecorded.
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
Boundary Review: PASS / REAL-CASE READY, NOT ACTIVE

Declared by:
  pinned task + bounded seven-file task contract

Proven by:
  191-test backend pytest + miniprogram build + target cleanliness

Consumed by:
  ChangeScopeEvaluation, strict artifact validators, acceptance decision

Enforced before:
  provider workstream creation and downstream workflow success

Failure route:
  preflight mismatch -> INVALID / no workstream
  business or scope failure -> FAIL / stop-loss

Cannot-bypass:
  no provider run when backend baseline cannot execute
  no silent dependency fallback
  no current dirty user file included in the detached baseline
  no "includes but not limited to" scope

Residual raw path:
  target requirements manifest omits python-multipart; accepted only as an exact,
  recorded ephemeral preflight pin, never as an implicit dependency
```

## 7. Decision

T6 backend/frontend baseline readiness 已通过。当前状态是
`READY_WITH_EXPLICIT_SUPPLEMENTAL_PIN / REAL-CASE NOT ACTIVE`，不是 provider
real-case 已获授权。

进入一次 T6 provider run 前仍必须：

1. 固化 executable Verification Gate；
2. 记录 target HEAD、requirements hash、完整 `pip freeze`、数据库 identity、
   191-test PASS 与 frontend build PASS；
3. fresh build Dual，并通过 Claude/Codex probes；
4. 重新获得一次明确 provider 授权；
5. 只创建一个 T6 workstream，严格执行 stop-loss。

不得用跳过 backend tests、复用历史 test count、隐式安装未固定依赖或只跑 frontend
build 的方式重新证明 readiness。
