# Peer Review: Captured Exact Replay 运行结果

日期：2026-08-15
评审对象：`20260815-design-review-convergence-captured-replay-result.md`、`target/verification-preflight/design-review-replay-5499f4e/run/`、active plan diff
执行 SHA：`5499f4e203fb94f381f6168b9bc370af672120d3`（已核实为 HEAD；工作树仅 result 文档 + active plan diff 未提交）
性质：独立、只读。未修改任何文件，未提交，未调用 provider，未运行第二次 replay。

## 独立验证事实

1. **Evidence SHA 全部复现**：`session.stream.ndjson` = `517518ba…`、`raw-output.txt` = `715259a9…`、`final-assistant-message.txt` = `c1edee67…`、`session-record.json` = `6da767ad…`，逐一 `shasum -a 256` 重算，与 `result.json` 全部一致。
2. **恰好一次 prompt、零权限、零工具**：stream 1,035 行中 `session/prompt` 恰 1 次（line 6）；全 stream 唯一的 "permission" 字符串命中是 prompt 内需求文本（line 8），非权限事件；tool call 相关事件 0；`session-record.json` `allowed_tools` 长度 0、closed=true（`closed_at` 在 prompt 后）。`session/new`+`session/resume` 各 1 是 acpx 建立 named session 的正常两步，不是第二次 prompt。
3. **Prompt 保真**：stream 中 `session/prompt` echo 与冻结 `replay-prompt.md` **逐字符一致，唯一差异是 transport 去掉了文件末尾 1 个换行**（echo 30,487 chars / 文件 30,488 chars + UTF-8 多字节 = 30,632 bytes）。provider 收到的是完整冻结 prompt。
4. **Process facts**：stream 的 JSON-RPC completion result 含 `stopReason: "end_turn"`；`timing.txt` real 75.73s 与 result 一致；exit=0。
5. **Final assistant message 独立重建**：仅拼接 stream 中 `_meta.codex.phase="final_answer"` 的 956 个 `agent_message_chunk`，得到 **5,030 bytes、SHA `c1edee67…`，与 extractor 输出逐字节一致**（另 47 个 commentary chunks 不属于 final message）。`CapturedPromptCompletionExtractor` 忠实调用 production `AgentLifecycleEventReader`（`AgentLifecycleEventReader.java:103-105`）并断言 terminal completion——不构成 INVALID。
6. **Strict failure 独立复现**：将冻结 `CapturedDesignReplayResultValidator` 编译后对 `final-assistant-message.txt` 复跑，得到与 `result.json` 相同的 `AGENT_RESULT_PARSE_FAILED`：`verification_remediation` 自由文本不是 `AgentResultEnvelope.VerificationRemediation` 合法值（`AgentResultEnvelope.java:248-252`：仅 `NONE`/`EVIDENCE_COLLECTION`/`TEST_CODE_CHANGE`），位置 line 45 col 35，无 projected sidecar 产生。
7. **Prompt 已给合法示例**：冻结 prompt `:429` 明确 `"verification_remediation" : "NONE"`。JSON 语法本身合法（python `json.loads` 通过，单一 object 5,030 bytes），run_id echo 正确。
8. **Console stdout 不是 parser 输入**：`raw-output.txt` 以 `[client] initialize` 开头并含 `[client]`/`[thinking]` presentation 帧——result 文档对第一次 operator 误用 console stdout 的记录诚实，最终判定以 stream 投影为准，正确。
9. **B1-B4 覆盖核查**：final message 关键词扫描——`stale`/`expired`/`expir`/`rollback`/`UnexpectedRollback`/`expires_at`/`delayed` 全部 0 次；`restart`/`interrupt` 仅出现在 checks[1] 的泛化 lifecycle 清单声明里，must_fix 只有 2 条且全部关于新 interleaving。B1-B4 判 MISS 属实。
10. **新 blocker（B5）可推导性**：design 自身 `evidence[0]` 明文描述 "issue 先执行 invalidation statement、再插入新 PENDING、同事务捕获 unique-violation"，`evidence[2]` 声称 "at most one delivery attempted"——B5（B 先 invalidate 已 commit 的 A 行再插入，绕过 partial index，产生两次投递且首个 code 不可验证）直接由 supplied evidence 推出，contradicts design 自称不变量；其要求的 barrier/latch PostgreSQL 并发测试在既有 Testcontainers 栈内。contract-grounded、成比例，非企业级扩张。B5 ≠ B1-B4（B1 是 crash 遗留行的 recovery，B5 是重叠 resend 的 ownership race）。

## Findings

### P0 / P1

无。

### P2

1. **Prompt echo 的末尾换行被 transport 剥离**（`run/session.stream.ndjson` prompt echo vs fixture `replay-prompt.md`，30,631/30,632 bytes）。良性；建议在未来 Gate/result 惯例中注明 SHA 比对以文件为准，避免 echo 级误判。
2. **Extractor 未预先冻结**：`run/CapturedPromptCompletionExtractor.java` 是运行后一次性工具，SHA 仅记录于 `result.json`，不在 Gate fixture 内。本次经第 5 条独立重建证明忠实，样本维持 FAIL 而非 INVALID；流程卫生：未来 Gate 应把提取步骤随 fixture 冻结。
3. **Gate review P2-1 兑现**：运行 workspace（`design-review-replay-5499f4e-preflight`，见 `run/stderr.log`）与冻结 prompt 内嵌路径（`44e7230.JjjuD9`）不同；deny-all + 0 tool call 使其保持外观性。确认当时的"记录路径或豁免声明"建议仍然成立。
4. `result.json` 的 `strictValidation`/`semanticCoverage`/`maturity` 字段与本评审全部独立复核一致，无失实。

## 逐项回答

1. **一次 prompt / 0 权限 / 0 工具**：是（见事实 2）。
2. **exit=0、end_turn、session closed**：是（事实 4 + session-record）。
3. **production reader 生成 final message**：是；且经 final_answer chunk 独立重建逐字节验证（事实 5）。
4. **console stdout 不应作 parser 输入**：正确——它是 `[client]`/`[thinking]` presentation（事实 8）。
5. **one-off extractor 忠实，不构成 INVALID**：是（事实 5；P2-2 记流程卫生）。
6. **final message 完整合法 JSON**：是（5,030 bytes 单 object）。
7. **违反 production enum 且 prompt 有示例**：是（`AgentResultEnvelope.java:248-252`；`replay-prompt.md:429`）。
8. **strict 失败判 FAIL 非 INVALID**：正确——Gate 的 FAIL 定义含 "strict contract 任一失败"；INVALID 保留给 provenance/hash/session 损坏，本次均无。operator 的 console-stdout 首次尝试已被 production 路径取代，不改变样本判定。
9. **B1-B4 全部未覆盖**：是（事实 9）；新 concurrent-resend blocker 是独立的 B5，不得计入 B1-B4。
10. **B5 由 supplied design/TaskContract 可推导**：是（事实 10）。
11. **成熟度保持 `PROJECTED / VALIDATED / NOT PROVEN`**：是——replay FAIL，captured boundary 未被证明；result 文档与 active plan 均如实记录。
12. **禁止第二次 replay 与 fresh captcha-auth workstream**：是——result `:15`、`:84-86`；active plan diff "不得运行第二次 replay 或 fresh captcha-auth workstream"。

## End-to-end invariant

| 维度 | 结论 |
|---|---|
| Declared by | 冻结 final typed design（`bfe3205…`）+ TaskContract + `iter-003..005`（preflight 已验，本次 run 未变更） |
| Proven by | 唯一 ACP stream（`517518ba…`，1 prompt / 0 权限 / 0 工具）+ terminal completion + production reader 投影（独立重建一致）+ 冻结 validator 独立复跑同一失败 |
| Consumed by | strict `AgentResultEnvelope` boundary；未产生 authoritative sidecar，未进入 workflow routing |
| Enforced before | schema 失败在 projector 之前拒绝——无 projected sidecar 落地 |
| Failure route | Gate 立即停止、session 关闭、0 correction/retry/第二 turn；样本 FAIL / CONSUMED |
| Cannot-bypass | 恰一次 prompt occurrence；run_id 正确也不能绕过 enum reject；deny-all 下无文件访问 |
| Residual raw path | console stdout 仅 presentation evidence；历史 reviewer contract 仍仅作运行后对照，未进入任何输入 |

## 结论

- **Boundary Review：PASS**——evidence 链完整且全部独立复现；FAIL 是被测假设的测量结果，不是边界被破坏。
- **样本判定：FAIL**（strict contract 失败 + B1-B4 semantic 全 MISS；双重独立成立）。
- **strict failure 根因**：模型在 `result.failure.verification_remediation` 输出自由文本，违反 `AgentResultEnvelope.java:248-252` 的闭合 enum，尽管 prompt `:429` 已给 `"NONE"` 示例——是模型输出违反显式 schema，非 parser/transport/prompt 缺陷。
- **semantic convergence 判定：未达成**——完整 typed subject + bounded iteration history + discipline prompt 不足以保证单 turn 完整 blocker 集：模型在同一 turn 发现了 1 个新的、可推导的 in-flight resend ownership blocker（B5），但遗漏了全部 B1-B4。这是该 Gate 设计要测量的真实结果。
- **具备提交 result + active plan 的条件**：两份文档与 evidence 完全一致，本评审 PASS，无阻塞项。
- **下一步唯一允许的动作**：对 FAIL 做只读归因（architecture-review prompt 扫描策略 vs 输出 contract 可执行性 vs 固定单 turn 目标本身是否现实），据此设计下一个 checkpoint；禁止放宽 parser、自动改 enum、correction/retry、第二次 replay 或 fresh captcha-auth workstream。
