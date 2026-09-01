# E1 Network Transport Owner Checkpoint Result

日期：2026-09-01  
状态：`IMPLEMENTED / DETERMINISTIC PASS / DELTA REVIEW PASS / READY TO COMMIT`

## 1. 结论

本 checkpoint 已消除 E1 policy 的 proxy owner 冲突：Dual 不再把 Claude sandbox 自身的 proxy transport 名称当作
provider credential deny；Claude E1 adapter child 在启动前也不再继承宿主或 session 注入的 proxy 与 ambient
`CLAUDE_CODE_SUBPROCESS_ENV_SCRUB`。

准确成熟度：

```text
NETWORK TRANSPORT OWNERSHIP ENFORCED
SANDBOX EFFECT NOT PROVEN
```

未调用 provider，未恢复 OKX workstream，未实施 domain approval 或 custom proxy。

## 2. Authoritative Chain

1. Dual 为每个 E1 Claude execution occurrence 写入 `claude_execution_policy.v2`；
2. policy 固定 `network_transport_owner=claude_sandbox`，只把三个 Anthropic provider 名交给
   `sandbox.credentials.envVars`；
3. ref/SHA 沿既有 CLI、queue、cold-load 与 session reuse binding 传输；
4. reviewed fork 在全部 auth/session/process env 合并与 Claude executable resolution 后、child spawn 前执行 finalizer；
5. finalizer 删除八个 proxy 名和 ambient SCRUB，保留 `CLAUDE_CODE_EXECUTABLE` 与一般非网络环境；
6. schema、binding、agent kind 或 fork identity 不一致时 fail closed，provider prompt 不开始。

## 3. Build Identity

- upstream commit：`fd173f04aa1b56f9e3f5ca5190c034ddcae28792`
- reviewed patch SHA：`fc2783a8b5aa2a450007b9e5516ca473d9a5e5785d8760b342e230ea180c60cc`
- policy schema：`claude_execution_policy.v2`
- Claude ACP adapter：`@agentclientprotocol/claude-agent-acp@0.70.0`

installer 从 frozen upstream 应用 patch，完整测试后注入 build identity；本机 `acpx build-info --json` 与 manifest
逐字段一致。

## 4. Verification

- fork P1 delta focused（build-info/client/spawn-options）：`95/0/0`
- fork installer pre-stamp full suite：`980/0/0`
- fork stamped delivery-tree full suite：`980/0/0`
- Dual focused：adapter `84/0/0` + Doctor `11/0/0`，合计 `95/0/0`
- Dual full reactor：`1294/0/0/0`
- wrapper env test、patch apply/reverse check、`git diff --check`：PASS
- fresh CLI JAR `doctor --workdir .`：PASS（fork identity 与 Claude/Codex adapter pin 全部 OK）

第一次在 Codex 文件沙箱内运行 full reactor 时，两个既有 ProcessHandle 测试因 macOS `sysctl` 返回 EPERM 而失败；
这是验证环境 `INVALID`，不是本 diff 的行为失败。相同 reactor 在正常系统权限下重跑为 `1294/0/0/0`。

## 5. Cannot-Bypass Evidence

- v1 schema、错误 owner、proxy 混入 provider deny 均被 fork loader 拒绝；
- process/session env 在 merge 后注入 proxy 或 SCRUB，finalizer 仍删除；Windows 大小写变体有独立测试；
- `AcpClient.start()` 通过真实 child process 捕获最终 spawn env，证明完整 E1 binding 下八个 proxy 名与
  `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB` 均 absent，且 `CLAUDE_CODE_EXECUTABLE` 保留；
- unbound Claude 与 non-Claude sibling 通过同一真实 child harness 证明 proxy env 保留，finalizer 不越界；
- `CLAUDE_CODE_EXECUTABLE` 与无关环境保留；
- 半绑定和 non-Claude binding 在 launch plan 阶段拒绝；
- production wrapper + `npx -y @agentclientprotocol/claude-agent-acp@0.70.0` 命令形态有识别测试；
- Doctor/configure 对旧 patch、旧 schema 或缺 build-info fail closed。

## 6. Diff Review P1 Closure

独立 diff review 的两个阻塞项已按原边界关闭：

1. `build-info-command.test.ts` 不再把 `UNBUILT` 当作永久期望，而是读取 generated identity 常量。因此同一测试既覆盖
   pre-stamp source tree，也覆盖 installer 注入 SHA 后的 delivery tree；stamped full suite 为 `980/0/0`。
2. finalizer 不再只由纯函数测试证明。新增测试从 production `AcpClient.start()` 到真实 child spawn，捕获最终 child env，
   同时以 unbound Claude/non-Claude sibling 反证 finalizer 不能越过完整 E1 binding。

计划 sweep 点名但本 checkpoint 无需修改 `AcpxAgentTaskHandler`、`AcpxAgentTransport` 与
`AgentRunMetadataWriter`：三者只传输并持久化 schema-independent 的 policy ref/SHA，新的 transport-owner 字段由同一
policy document 承载；既有 binding/correction/metadata tests 继续证明 ref/SHA 同源。为这些文件制造空洞改动会扩大范围，
因此零 diff 是核实后的结果，不是遗漏。

## 7. Residual

- Claude runtime 内部 proxy endpoint、token、端口与拓扑仍由 Claude sandbox 拥有，Dual 不读取或审计；
- provider deny 只覆盖三个列名并只作用于 sandboxed Bash，不代表 hooks/MCP/任意 secret 的通用隔离；
- OS 网络阻断、direct-connect、WebSearch 共存仍需新的 capability Verification Gate；
- `PUBLIC_NETWORK_DOMAIN`、custom proxy、Codex network safety 仍未实现或证明。

## 8. Next Gate

独立代码级 delta review 已 `PASS（P0=0 / P1=0）`，确认两个 P1 均真实关闭，交付树 `980/0/0`，六方
identity 一致。下一步为本 checkpoint 的 clean commit；随后另起 capability Verification Gate 并先做独立评审。

用户已预授权该 Gate 后续所需的 provider tool turns，无需再次询问；该预授权不跳过 Gate freshness、trigger、INVALID、
stop-loss 或单样本约束，也不授权 real-case、domain approval、custom proxy 或其他 provider 工作。
