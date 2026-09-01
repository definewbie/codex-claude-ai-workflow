# E1 Network Transport Owner 实施计划独立评审记录

日期：2026-09-01

## 首轮结论

`NOT READY（P0=1 / P1=0）`。

唯一 P0：计划曾固定 `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB=1`，但 Anthropic settings reference 明确该变量会关闭
`sandbox.autoAllowBashIfSandboxed`。这会让 child env 在运行时否决 policy 的 `auto_allow_sandboxed_bash=true`，
并使 capability Gate 的 Bash probe 重新进入 permission flow。

其余 owner、fork hook、sibling isolation、测试矩阵与成熟度分层经评审成立。

## Delta

计划已完成以下文字修订，尚待独立 delta review：

1. E1 finalizer 改为在最终 child env 删除 ambient `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB`；
2. provider credential 的 sandboxed Bash 隔离继续由 policy v2 `sandbox.credentials.envVars` 唯一拥有；
3. finalizer 挂在 `ensureLaunchSupport()` 之后、`spawnAgentProcess()` 之前；
4. 增加 production wrapper + adapter 命令形态识别测试；
5. evidence 引用补齐具体文件与 SHA；
6. hooks/MCP stdio 与一般自定义 secret 明示为本 checkpoint 范围外，不冒充全子进程 credential isolation。

Delta review 已 `PASS / IMPLEMENTATION READY（P0=0 / P1=0 / P2=3）`。三项 P2 已吸收：Boundary Map 旧措辞
改为删除 ambient SCRUB、官方链接改为 settings-reference、SCRUB 大小写变体加入测试 5。

## 当前授权边界

只允许对修订后的计划做独立 delta review。delta PASS 前不修改 production/fork patch、不调用 provider、不恢复 real-case。
