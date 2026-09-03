# acpx Executable 与 Turn Deadline 边界实施计划

## 1. 目标

解除 OKX real-case 的两个通用 adapter 阻塞，不修改 workflow、ledger schema、产品代码或权限模型：

1. E1-bound Claude execution 在 session/provider 副作用前验证 reviewed acpx identity，禁止静默使用 PATH 中的普通 acpx；
2. acpx 到达声明的 turn deadline、未产生 `prompt_completed` 时，即使进程返回 0，也必须记录为既有 `ProcessUsability.TIMED_OUT`，不得进入 `AGENT_RESULT_MISSING` 路径；
3. 将 acpx 总 turn deadline 变为一个有界、显式的 adapter 配置，使复杂 real-case 可以在保留 600 秒无进展 watchdog 的同时获得更长总执行窗口。

## 2. Captured Evidence

- `run-1788428618322`：未设置 `DUAL_ACPX_EXECUTABLE`，fallback 到 PATH 中 acpx `0.11.0`；直到发送 E1 flags 才以 unknown option 失败，provider prompt 未开始。
- `run-1788428811276`：reviewed acpx，`608153ms`，exit 0，prompt 未完成，最终误报 `AGENT_RESULT_MISSING`。
- `run-1788429509046`：reviewed acpx，`603998ms`，exit 0，prompt 未完成，再次误报 `AGENT_RESULT_MISSING`。
- 两次长 occurrence 均持续产生工具活动并完成产品/测试修订；宿主 `mvn test` 为 `44/0/0/0`。

## 3. Owner 与不变量

| Fact | Owner | Consumer | Failure boundary |
|---|---|---|---|
| effective acpx executable 与 reviewed build identity | `AcpxAgentTransport` | session bootstrap / prompt launch | E1-bound request 在任何 acpx session/provider 调用前 fail closed |
| declared total turn deadline | `AcpxAgentTransport` command config | acpx CLI + `ProcessResult` classification | deadline 到达且无 completion => `TIMED_OUT` |
| prompt 是否完成 | `AcpxPromptCompletion` | process usability / result acquisition | 未完成不得被 exit 0 覆盖 |
| operator recovery | 既有 `RecoveryAssessment` | CLI/API/report | 只消费 typed timeout，不从空 output 猜测 |

不新增领域对象。`TIMED_OUT`、`SESSION_BOOTSTRAP_FAILED`、`RecoveryCategory.TIMEOUT_OR_STALLED` 均已存在。

## 4. 最小实现

### 4.1 Reviewed acpx preflight

- 把 Doctor 已有 E1 build identity 常量与 JSON 校验收敛为 adapter 内的单一可复用 owner；Doctor 改为消费该 owner。
- 仅当 Claude request 携带 execution-policy binding 时执行 `build-info --json` 校验。
- effective executable 不可执行、build-info 缺失或 identity 不匹配时，返回 typed bootstrap failure；不执行 `sessions ensure/new`，不启动 prompt。即使未设置 `DUAL_ACPX_EXECUTABLE`，PATH 解析结果也必须通过同一 identity 校验，不能因命令名可用而放行普通 acpx。
- 非 E1 request 与 Codex sibling 保持现有行为。

### 4.2 Deadline classification

- process usability 判定顺序改为：completion、deadline、exit code。
- `completion=false && reachedDeclaredTimeout=true` 必须为 `TIMED_OUT`，包括 exit 0。
- complete output 即使 wrapper 非零仍保留既有 `EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT` 语义。
- result acquirer 不为 timeout 启动 correction；core resolver 继续把 `TIMED_OUT` 映射为既有 typed timeout outcome。

### 4.3 Bounded total runtime

- 新增 `DUAL_ACPX_COMMAND_TIMEOUT_SECONDS`，正整数，默认仍为 600，避免无证据地改变所有 profile 成本。
- 本次 OKX 恢复 Gate 显式使用 1200 秒；600 秒无进展 watchdog 保持不变。
- 总 deadline 不是 inactivity timeout；二者不得共用 owner 或相互覆盖。

## 5. Cannot-Bypass Tests

1. E1 binding + effective executable 不可用或 identity 错误：session/prompt invocation 为零，typed bootstrap failure。
2. E1 binding + reviewed identity：继续进入 session 与 prompt。
3. 非 E1 request：不要求 reviewed build identity。
4. exit 0 + no completion + duration at deadline：`TIMED_OUT`。
5. exit 0 + completed：`EXITED_ZERO`。
6. non-zero + completed：保持 complete-output 语义。
7. command timeout 配置进入精确 `--timeout` 参数；非法值 fail closed。
8. handler/outcome test 证明 timeout 不再生成 `AGENT_RESULT_MISSING` 主因。

## 6. Verification Gate

- Target: captured 600 秒零输出形态被 typed timeout 正确分类，并允许当前 implement occurrence 在显式 1200 秒总上限内完成。
- Source of truth: `run.json.processResult.usability`、`executionOutcome`、prompt completion、reviewed acpx build-info、workflow ledger。
- Trigger precondition: fresh JAR；reviewed acpx identity 匹配；当前节点仍为同一 recovery node；产品宿主测试 44/0/0/0。
- PASS: provider 前 identity 校验通过；run 不在 600 秒被绝对截断；产生合法 AgentResultEnvelope 并继续到下一 workflow boundary。
- FAIL: typed timeout、provider/adapter fatal、结构化结果仍缺失或产品测试退化。
- INVALID: 旧 JAR、错误 binary、配置未进入 command、节点/工作树身份漂移。
- Stop-loss: 修复后只恢复当前节点一次；同类失败不再 retry。
- Run artifact freshness: package 后核对 JAR 时间、checkpoint SHA、command 中 reviewed binary 与 `--timeout 1200`。

## 7. 明确不做

- 不增加通用配置中心、动态 profile timeout 策略或自动重试；
- 不修改 Agent prompt、workflow JSON、human gate、ledger schema；
- 不恢复 R4 网络证明或 PUBLIC_NETWORK_DOMAIN；
- 不创建第二个 OKX 样本。
