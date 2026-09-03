# acpx Executable 与 Turn Deadline Checkpoint Result

## 1. 结论

本 checkpoint 关闭两个由 OKX real-case 自然暴露的通用 adapter execution 缺口：

1. 携带 E1 execution-policy binding 的 Claude 请求在初始 prompt 和 correction prompt 前，均强制执行 reviewed acpx `build-info --json` 身份校验；身份缺失或不匹配时，在 session/provider 副作用前以 typed bootstrap failure 失败。
2. acpx 到达声明的总 turn deadline 且没有 `prompt_completed` 时，exit code 0 不再覆盖 deadline fact，统一分类为既有 `ProcessUsability.TIMED_OUT`。

总 turn deadline 由 `DUAL_ACPX_COMMAND_TIMEOUT_SECONDS` 显式配置，默认保持 600 秒；非法或非正整数 fail closed。既有 progress watchdog、workflow、ledger schema、权限模型和 recovery action 均未改变。

## 2. 边界闭环

| Invariant | 证据 |
|---|---|
| Declared by | E1 policy binding；acpx `--timeout` 参数 |
| Proven by | `ReviewedAcpxBuildIdentity` 三元组校验；`AcpxPromptCompletion` 与 elapsed deadline |
| Consumed by | `AcpxAgentTransport` 的 initial/correction provider 入口；core execution finalizer |
| Enforced before | reviewed identity 在 session ensure 与 prompt 前；deadline classification 在 result acquisition/finalization 前 |
| Failure route | identity 错误 -> `SESSION_BOOTSTRAP_FAILED` / `NETWORK_AUTHORITY_NOT_CONFIGURED`；deadline -> `TIMED_OUT` |
| Cannot bypass | 错误 identity 的 invocation log 只有 `build-info --json`；correction 入口同样拒绝；timeout + missing result 的最终主因仍为 `process_timed_out` |

Doctor 与 runtime transport 共享同一个 reviewed identity owner，消除了两份常量和两套 JSON 判断的漂移面。Codex 与不携带 E1 binding 的 sibling 保持原行为。

## 3. 验证

- `AcpxAgentTransportTest`: 42/0/0/0
- `DoctorCommandTest`: 11/0/0/0
- `AcpxAgentTaskHandlerTest`: 46/0/0/0
- `AgentExecutionFinalizerTest`: 9/0/0/0
- full Maven reactor: 1311/0/0/0，`BUILD SUCCESS`
- `git diff --check`: clean

## 4. 范围与成熟度

Production 变更仅为 adapter transport 的身份/deadline 边界、共享 reviewed identity，以及 Doctor 对该 identity 的复用。没有 workflow JSON、ledger schema、prompt、产品代码、自动重试或网络策略变化。

- reviewed acpx identity before E1 provider side effects: `ENFORCED`
- deadline-over-exit classification: `ENFORCED`
- configurable total turn deadline: `ENFORCED`
- OKX recovery completion under 1200 seconds: `NOT PROVEN`

现有未跟踪文件 `docs/testing/verification-gates/20260903-okx-s2-recovery-and-terminal-completion.md` 不属于本 checkpoint，不随本提交选择。

## 5. 下一步

先完成独立代码级 diff review。通过后选择性提交本 checkpoint、构建 fresh JAR，并按既有 Verification Gate 只恢复当前 OKX 节点一次：reviewed acpx identity 必须匹配，`DUAL_ACPX_COMMAND_TIMEOUT_SECONDS=1200`，同类失败再次出现立即停止。
