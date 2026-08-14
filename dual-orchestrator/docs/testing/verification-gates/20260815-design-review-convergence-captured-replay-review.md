# Peer Review: Design Review Convergence Captured Exact Replay Gate

日期：2026-08-15
评审对象：`20260815-design-review-convergence-captured-replay.md` + `docs/testing/replay-fixtures/20260815-design-review-convergence/` + active plan delta
基线：checkpoint `44e72306494c6df3edc31ff9de2907c7a1efbacc`（production sources 未变，已核实 `git diff 44e72306 -- dual-runtime/src dual-adapter-acpx/src` 为空）
性质：独立、只读、代码级。未修改 production/fixture，未提交，未调用 provider，未运行 real-case，未触碰 captured workstream（仅 `cp -R` 只读复制到临时目录后即删除）。

## 独立验证事实

1. **SHA 全链复现**：manifest 的 8 个 captured input SHA（requirement、design contract `bfe3205…`、design candidate、`iter-003..005`、historical review contract `4d9d08f4…`）逐一用 `shasum -a 256` 重算，全部匹配；4 个 fixture 文件 SHA 匹配；`replay-prompt.md` = 30,632 bytes 匹配。
2. **Production class SHA 复现**：`ContextBuildPackageHandler.class` = `8e90fe74…`、`AgentPromptRenderer.class` = `1247e14c…`，与 manifest 一致（由 checkpoint 同源代码本地编译复现）。
3. **Prompt 生成链证明（最强证据）**：将 `CapturedDesignReplayPromptBuilder.java.txt` 原样编译，用 checkpoint 44e72306 的 production `ContextBuildPackageHandler + AgentPromptRenderer` 对 captured workspace 只读副本在冻结 prompt 内嵌的原路径重新生成——输出与冻结 `replay-prompt.md` **逐字节一致（30,632 bytes，唯一差异为 `- Generated At:` 时间戳行）**。该 prompt 是 production 输出，不是手工拼接。
4. **Subject 完整性**：prompt 恰含一个 `## Required Architecture Review Subject`；final design 的 4 条 `checks` 与 5 条 `evidence`（含 `uq_otp_current` DDL 全文，最长 1,521 字符）逐字在内（程序化子串比对全部 True）。
5. **Iteration sequence**：`## Iteration History` 含真实 `iter-003..005` 及其 captured reason 原文（另有 iter-001/002，受 5 条上限约束）。
6. **无历史答案泄漏**：Gate 列出的 5 个短语（`stale PENDING`、`expired ACTIVE`、`UnexpectedRollbackException`、`delivery confirmation arriving after OTP expiry`、`stale-row recovery`）在 prompt 中均为 0 次；本轮独立追加 13 个 B1-B4 解答性短语（`expires_at>now`、`PENDING-to-ACTIVE`、`issuance-time recovery`、`strand`、`permanently block`、`rollback-only`、三个历史 review run id、`prior verdict` 等）也均为 0 次。`must_fix` 出现 5 次属 Output Contract 字段名与 final design 自身对 round-3 "Must Fix 1-5" 的修复引用——是 review subject 本体，不是泄漏。Recent Agent Outputs / evidenceRefs 均不含任何历史 review contract。
7. **`withinBudget=false` 解释准确**：governed sections 渲染合计 2,409 tokens < 5,000 budget；budget-exempt subject 1,928 tokens 计入 `estimatedTokens=5,566` 导致 false。没有任何 section 因 subject 被删除或 summary 化（input_artifacts FULL 563、review_evidence FULL 802、iteration_history FULL 347）。
8. **run_id 强制**：prompt Output Contract 固化 `"run_id": "replay-design-review-44e7230"`；`AgentResultEnvelopeValidator.java:44-45` 对 run_id 不匹配走 `reject`（非 repairable），validator 把固定 RUN_ID 传入 production validator，并通过 production `AgentResultEnvelopeParser`、`ContractRegistry.forNode("design.codex_arch_review")`、`AgentResultDocumentProjector` 取得 decision——无自定义宽松解析。`presentationCandidate(String)` 为 `AcpxAgentResultAcquirer.java:290` 真实私有静态方法，validator 经反射调用；validator 源码已独立编译通过。
9. **CLI 边界核实**：`acpx --cwd/--timeout/--ttl/--prompt-retries/--deny-all/--non-interactive-permissions/--allowed-tools`、`codex sessions new --name`、`codex -s/--session -f/--file`、`codex cancel -s` 参数顺序与本地 `--help` 输出一致。

## Findings

### P0 / P1

无。

### P2

1. **Run-workspace 绝对路径未显式记录**（Gate :85-86 称 "`<replay-workspace>` 只能替换为本 Gate 记录的绝对路径"，但 Gate 与 manifest 均未记录该路径；实际路径 `/private/tmp/design-review-replay-44e7230.JjjuD9/workspace` 仅隐式存在于冻结 prompt 内嵌指针中）。字节级复现（含 `ledger_pointers` 是否触发 125-token POINTER_ONLY 降级）依赖该路径长度。运行时若用不同路径，冻结 prompt 仍是唯一 provider 输入且 deny-all 禁止文件访问，故仅为外观性指针失配。最小修复：在 Frozen Inputs 记录该绝对路径，或声明运行 workspace 路径无需与生成路径一致及其原因。
2. **Session 不存在性检查未指定命令**（precondition :29）。建议补 `acpx codex sessions` 查询语句；非阻塞。
3. **Validator 反射访问 private 方法**对 production 重构脆弱——由 manifest 固化的 source/class SHA 与 INVALID 条款（"validator 未使用本 Gate 固化的 source SHA"）兜底，可接受。
4. 既有 production 怪癖随 fixture 冻结：prompt 中 `## Context Governance` 双标题（:132/:147）、`ledger_pointers` rendered 153 > original 140 的记账——非本 Gate 引入。

## End-to-end invariant

| 维度 | 结论 |
|---|---|
| Declared by | frozen final typed design（SHA `bfe3205…`）+ TaskContract/acceptance criteria + bounded `iter-003..005`（SHA 已验）——无其他事实源 |
| Proven by | SHA 链（captured×8 + fixture×4 + class×2 + 30,632-byte prompt）+ production 链字节级离线重放（仅时间戳行不同）+ validator 对 production class 编译通过 |
| Consumed by | 一个 fresh named session 中的单次冻结 prompt Codex turn |
| Enforced before | precondition（git clean、HEAD ancestry、SHA 匹配、session 不存在）全过才允许 provider 调用；stop-loss：任一不符即不调用 |
| Failure route | 进程/strict contract 失败 → FAIL 停止；timeout/非零 → cancel exact session 后 close，不重试；precondition 失败 → INVALID，不解释为部分成功 |
| Cannot-bypass | run_id 不匹配 reject（Validator :44-45）；`--deny-all` + `--allowed-tools ""` + `--prompt-retries 0` + 单次 `--file`；INVALID 覆盖 prompt 漂移、旧 session 复用、输出无法证明唯一 occurrence |
| Residual raw/legacy path | 历史 reviewer contract 仅运行后对照（不在 prompt、不参与路由，prompt 中零出现）；raw transcript/stderr 仅以 "(omitted)" 标记出现 |

Second-SoT check：authoritative facts 只有 frozen typed design + TaskContract + bounded iteration history；historical reviewer contract 在 prompt 中零出现，仅作为运行后 B1-B4 对照。成立。

## 结论

- **Boundary Review：PASS**（P0=0 / P1=0 / P2=4 观察项，均不阻塞）
- **无历史答案泄漏**（Gate 5 短语 + 独立追加 13 短语均零命中）
- **fixture 可复现**（字节级重放成立）**且不可被旧 session/retry 绕过**（fresh named session、prompt-retries=0、单次 prompt、INVALID 条款）
- **具备选择性提交条件**（本 review PASS；建议随提交顺手在 Gate 文本记录 P2-1 的 run-workspace 路径或其豁免声明）
- **提交后只可授权一次 captured Codex replay**；PASS 仅证明 v13 captured design-review boundary，captcha-auth 仍 `NOT ACHIEVED`，fresh workstream 需新 completion Gate
- **成熟度维持 `PROJECTED / VALIDATED / NOT PROVEN`**（replay 未执行）

`Gate READY FOR SELECTIVE COMMIT / PROVIDER STILL NOT AUTHORIZED`
