# Verification Gate: Design Review Convergence Captured Exact Replay

日期：2026-08-15

状态：`INDEPENDENT REVIEW PASS / NON-PROVIDER PREFLIGHT AUTHORIZED / PROVIDER NOT AUTHORIZED`

本 Gate 只验证 checkpoint `44e72306` 是否让 Codex 在**一个 fresh review turn** 中，从 v13 最终 typed design、
TaskContract 与 bounded iteration history 推导完整的当前 blocker 集。它不是新的 workflow、benchmark、retry、
review platform 或 captcha-auth 样本；不修改、resume 或路由 captured workstream。

## Verification Gate

- Target: 使用已冻结的 replay prompt 调用 Codex 一次，首轮同时识别 v13 final design 中 partial unique index 引入的
  stale/expired-row recovery、过期 activation、rollback-safe constraint handling 与对应 cannot-bypass tests；允许提出
  contract-grounded 的更小替代设计，但不能把可见 blocker 故意留到下一轮。
- Source of truth: checkpoint `44e72306494c6df3edc31ff9de2907c7a1efbacc`；fixture `manifest.json`；冻结的
  `replay-prompt.md`；单次 acpx process facts；raw assistant output；production presentation normalization、strict
  `AgentResultEnvelopeParser`、`AgentResultEnvelopeValidator` 与 `AgentResultDocumentProjector` 生成的 projected sidecar。
  历史 reviewer contract 只用于运行后对照，不进入 prompt，也不参与路由。
- Trigger precondition: Git clean 且 `44e72306` 是 HEAD ancestry；fixture SHA 全部匹配；captured requirement、final design
  contract/candidate、`iter-003..005` 与历史 review contract SHA 匹配 manifest；fresh package 后两个 production class SHA
  匹配 manifest；prompt 恰含一个完整 `Required Architecture Review Subject`、`iter-003..005`、固定
  `run_id=replay-design-review-44e7230`，并且不含历史答案词组 `stale PENDING`、`expired ACTIVE`、
  `UnexpectedRollbackException`、`delivery confirmation arriving after OTP expiry` 或 `stale-row recovery`。
- How precondition is checked before run: 校验 Git/fixture/captured SHA；fresh package；校验 class SHA；读取
  `context-package-metadata.json`，确认 required subject=`1928` tokens、iteration history=`347` tokens；确认 prompt 为
  `30632` bytes。`withinBudget=false` 在本 fixture 中是**预期的诚实记账**：governed sections 自身保持在 5,000-token
  budget 内，budget-exempt 必达 subject 的 1,928 tokens 被额外计入，而不是被 summary/drop；不得将该字段误判为
  prompt 无效。最后运行
  `acpx --cwd <replay-workspace> codex sessions --local`，其输出不得包含精确 session name
  `codex-design-review-replay-44e7230`；存在 open 或 closed 同名记录都视为 INVALID，不得复用或覆盖。
- PASS: 恰好一次 Codex prompt；无 correction、retry、resume 或第二 turn；process exit=0；production
  normalization + strict parser/schema/semantic/projector 全部通过；typed decision=`NEEDS_REVISION`、`blocking=true`、
  `must_fix` 非空、`missing_evidence=[]`；输出以当前 design/TaskContract 为证据，覆盖下述 B1-B4，且无不必要的
  enterprise/distributed scope 扩张。B1-B4 可合并表达，不要求逐字匹配或固定实现方案。
- FAIL: 进程/strict contract 任一失败；使用工具或 permission request 才能作答；只发现 stale PENDING 而遗漏其他
  当前可推导边界；把 blocker 降为 open risk；错误返回 READY；引用未提供的 prior verdict/design；引入超出
  TaskContract 的强制能力；timeout；或需要第二 turn/correction 才补齐。
- INVALID: Git/JAR/class/fixture/captured hash 不符；prompt 含历史答案；复用旧 Codex session；调用前修改 prompt、
  provider 配置或 captured ledger；输出无法证明来自唯一 prompt occurrence；临时 workspace 不是 captured ledger 的
  只读副本；或 validator 未使用本 Gate 固化的 source SHA。INVALID 不得解释为部分成功。
- Stop-loss: precondition 任一不符时不得调用 provider；单 turn 300 秒 timeout；timeout/interrupt/non-zero 时立即
  `cancel -s`，随后 close exact session，不重试；strict parse/schema/semantic 失败即停止；无论 PASS/FAIL 都关闭
  session，不运行 captcha-auth fresh workstream。
- Run artifact freshness: Gate/fixture 独立 review PASS 后提交 clean SHA；从该 SHA fresh package；记录 JAR/class
  SHA、acpx version、session name、command、prompt/output SHA、duration、exit code 与 validator projected sidecar。

## Required Blocker Coverage

以下是业务语义，不是字符串断言：

| ID | 必须覆盖的当前边界 |
|---|---|
| B1 | 发行 replacement 前，在同一事务内使 stale PENDING 与 expired ACTIVE 退出 partial-index current set；crash/restart 后 resend 不能永久被旧行阻断。 |
| B2 | `PENDING -> ACTIVE` 必须再次约束 `expires_at > now()`；延迟 delivery confirmation 不得激活已过期 OTP，失败路径保持不可验证。 |
| B3 | `uq_otp_current` 冲突处理必须在 Spring transaction 语义下 rollback-safe；不能 catch 已标记 rollback-only 的异常后承诺正常返回，最终变成 `UnexpectedRollbackException`。允许通过事务边界调整、预先清理或更小机制解决。 |
| B4 | 测试必须覆盖 persisted stale PENDING、restart 后 resend、expiry 后 delivery confirmation，以及 constraint conflict 的真实事务结果。 |

若 reviewer 提出删除 partial index 或改用更小状态模型，只有同时证明 one-current-OTP、crash/restart recovery、
late activation、rollback/conflict 与 B4 测试仍闭合，才算覆盖 B1-B4。

## Frozen Inputs

```text
checkpoint: 44e72306494c6df3edc31ff9de2907c7a1efbacc
captured workstream: ws-20260814T103857Z-AEHF (read-only / FAIL / CONSUMED)
captured final design run: run-1786707985252
historical reviewer run: run-1786708014731 (comparison only; not prompt input)
replay run id: replay-design-review-44e7230
fixture generation workspace: /private/tmp/design-review-replay-44e7230.JjjuD9/workspace (generation only; removed)
runtime replay workspace: a new absolute-path read-only copy of the captured ledger, recorded in the run report

design contract sha256: bfe320531137cd63046185b1dc3b268c3a73f34692f034feec5f2d3ddcd5455e
iteration 003 sha256: 99d286724c91ccd2047ff62c5615180b3a6fdc0143f9e97701a59465cbdaaca2
iteration 004 sha256: 5d44cfbf36b43f45c60592def61429f0fe3df27a49a02a3651765aa8c589b776
iteration 005 sha256: 5ba0996029da97d2802e6e3110c7e1e3d254fd7694b4871aa915890a30619be3
replay prompt sha256: 255318dcf144e71db2a4d6140054a5344397d535f61669a2b15266cdbebc288f
```

完整列表见同目录 fixture `manifest.json`。

## One-Time Execution Procedure

1. 只读复制 captured workspace 到 manifest 对应的临时 replay workspace；不得修改源 workstream。
   runtime workspace 不要求复用已经删除的 fixture generation path：provider 的唯一输入是 SHA 已冻结的 prompt，且
   `deny-all`/空 allowed-tools 禁止运行时读取其中的绝对路径。新 runtime workspace 的绝对路径仍必须记录，并且内容
   必须来自 captured workspace 的只读副本；不得把路径差异解释为重新生成或修改 prompt 的许可。
2. 使用 `CapturedDesignReplayPromptBuilder.java.txt` 的精确源码和 fresh target classes 重新核对生成链；provider 实际
   输入必须使用已冻结的 `replay-prompt.md`，其 SHA 不得变化。
3. 创建 fresh session `codex-design-review-replay-44e7230`。命令边界固定为：300 秒 timeout、TTL 600、
   `prompt-retries=0`、`deny-all`、non-interactive permission=`fail`、allowed tools 为空；运行目录为只读副本对应的
   effective workspace。以下参数顺序已由本机 `acpx --help`、`acpx codex --help`、`acpx codex cancel --help` 与
   `acpx codex sessions new --help` 核实；`<replay-workspace>` 与 `<repo>` 只能替换为本 Gate 记录的绝对路径：

   ```bash
   acpx --cwd <replay-workspace> --timeout 300 --ttl 600 --prompt-retries 0 \
     --deny-all --non-interactive-permissions fail --allowed-tools "" \
     codex sessions new --name codex-design-review-replay-44e7230

   acpx --cwd <replay-workspace> --timeout 300 --ttl 600 --prompt-retries 0 \
     --deny-all --non-interactive-permissions fail --allowed-tools "" \
     codex --session codex-design-review-replay-44e7230 \
     --file <repo>/dual-orchestrator/docs/testing/replay-fixtures/20260815-design-review-convergence/replay-prompt.md
   ```

   第二条命令只能执行一次，并分别保存 stdout、stderr、exit code 与 duration。
4. 成功返回后立即关闭 session。异常返回先 `cancel -s codex-design-review-replay-44e7230`，再 close；不得 retry。

   ```bash
   acpx --cwd <replay-workspace> codex cancel --session codex-design-review-replay-44e7230
   acpx --cwd <replay-workspace> codex sessions close codex-design-review-replay-44e7230
   ```
5. 用 `CapturedDesignReplayResultValidator.java.txt` 的精确源码编译临时 validator，将 raw assistant output 依次送入
   production presentation normalization、strict parser、semantic validator 与 projector；保存 projected sidecar。
6. 只从 projected sidecar 判定 decision/blocking/must_fix/missing_evidence，再按 B1-B4 做独立语义核对；保存
   prompt/output/sidecar SHA、process facts、调用数与 PASS/FAIL/INVALID 报告。

## Mature Boundary

- checkpoint `44e72306`: `PROJECTED / VALIDATED / NOT PROVEN`；
- replay PASS 后：仅可将 **v13 captured design-review boundary** 标为 `PROVEN`；
- captcha-auth 产品仍为 `NOT ACHIEVED`；
- fresh captcha-auth workstream 仍需新的 completion Gate，不由本 replay 自动授权。

本 Gate 不新增 production code、workflow、schema、parser fallback、correction、retry、memory producer、registry、
engine 或通用 replay harness。
