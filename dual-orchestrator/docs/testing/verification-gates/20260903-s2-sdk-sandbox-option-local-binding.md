# S2 SDK Sandbox Option Local Binding Verification Gate

日期：2026-09-03  
状态：`EXECUTED / HOST_REACHABLE / OPERATOR ACCEPTED`

## 1. Verification Gate

- **Target**：测量 SDK `0.3.232` 通过 `Options.sandbox` 向 embedded Claude Code `2.1.232` 传入
  `sandbox.network.allowLocalBinding=true` 后的三项行为：sandbox 内自建 loopback round-trip、访问宿主既有 loopback listener、
  未授权公网直连。
- **Source of truth**：冻结 SDK/embedded binary/fixture SHA、`sdk-options-summary.json`、SDK 原始 JSONL、唯一 Bash tool
  input/result、`s2-loopback-result.tsv`、host sentinel 前后检查。模型正文不是判定来源。
- **Trigger precondition**：HEAD 为 `fb0cf9c` 后代；SDK package=`0.3.232`、`claudeCodeVersion=2.1.232`；SDK module 与
  embedded binary SHA 匹配；fixture offline test PASS；fresh result directory；host sentinel precheck PASS。
- **How precondition is checked before run**：复算 package/module/binary 与 fixture SHA；执行 offline test；runner 再记录实际
  module/binary 路径与 SHA，并通过 production `acpx-claude-agent-env.sh` 只加载 provider 必需环境、删除 ambient proxy 环境。
- **PASS**：恰一个 provider prompt、恰一个 Bash tool call且命令精确为 `./s2-loopback-probe.sh`；SDK result success、无
  permission denial；`python3` 可用；self bind/connect PASS；external denied；host pre/postcheck PASS；fixture SHA 前后相同。
  host connect 原样分类为 `HOST_DENIED` 或 `HOST_REACHABLE`。
- **FAIL**：self bind/connect 失败或 external 成功。不得据此修改 fixture 重跑。
- **INVALID**：SDK/binary/fixture identity、managedSettings summary、tool 数量/命令、result/TSV、host pre/postcheck、
  `python3`、SDK option/managed policy 职责分离、SDK/provider execution 任一不符或 180 秒超时。INVALID 不产生 S2 语义结论。
- **Stop-loss**：额外 tool call、permission request、unsandboxed execution、external 成功或超时立即停止；只运行一个样本。
- **Run artifact freshness**：result 目录运行前不存在；保存 SDK options summary、原始 JSONL/stdout/stderr、TSV、host evidence、
  前后 fixture SHA 与排序 manifest。

## 2. 边界

上一 Gate `fb0cf9c` 已在 provider 前判 INVALID：reviewed fork 固定只接受 `allow_local_binding=false`，因此不能用“临时翻转但
不改 fork”的方式测量。本 Gate 不修旧 fixture，也不再尝试 acpx session。

本 Gate 的首个 draft 也在 provider 前被 non-provider preflight 否决：SDK `resolveSettings()` 实测会将
`managedSettings.sandbox.network.allowLocalBinding=true` 作为 permissive key 过滤掉，effective sandbox 中没有该字段。因此
local binding 不能由 managed policy tier 投递。

修订后的职责是：运行能力开关由 SDK `Options.sandbox.network.allowLocalBinding=true` 投递；`managedSettings` 只继续拥有
strict allowlist、空 allowed domains、禁止 unsandboxed command 与 provider credential deny。fixture 的 offline fake SDK 强制断言
local binding 不得重新出现在 managed settings。该路径不是 production enforcement proof，但使用同一 SDK/embedded binary，足以
回答实施 S2 前唯一未知的第三方 runtime 行为。结果可接受后，production checkpoint 才设计 fork 的最小 option projection。

fixture 不实现网络策略，不修改 SDK，不写 Dual ledger，不增加 domain approval、IP/DNS 矩阵或通用 evaluator。`persistSession=false`
且 `settingSources=[]`；仅暴露并自动批准 Bash，permission mode 为 `dontAsk`。managed settings 保持 external domain 空集、
strict allowlist、禁止 unsandboxed command，并继续隐藏三个 Anthropic provider 环境变量。

## 3. Result Classification

| 有效结果 | 含义 | 后续动作 |
|---|---|---|
| `HOST_DENIED` | self-owned listener 可用，宿主 listener 隔离 | 可制定最小 S2 production checkpoint |
| `HOST_REACHABLE` | local binding 同时暴露宿主 loopback | 停止，由 operator 接受或拒绝后再决定 |

两种结果都要求 external denied。不得把结果外推为完整 E1 security，也不得直接恢复 OKX。

若 self bind 失败，本 Gate 只能证明“`Options.sandbox` 与 managed policy 合并后的这条投递路径不可用于 S2”；它不能区分
option 被更高优先级约束覆盖与 embedded runtime 完全不支持 local binding，也不得外推后一个结论。

## 4. Frozen Baseline

- Dual checkpoint：`fb0cf9c`；
- SDK package/version：`@anthropic-ai/claude-agent-sdk@0.3.232`；
- package.json SHA：`7f06dfa738cd0e505aa6fb8fd4f038e37629fce6d67906adfdc64655a095285c`；
- sdk.mjs SHA：`cae60ea6827d5f9b113c88b911388983fe96e7d565a382bf25904df78ab9d0fc`；
- embedded Claude version：`2.1.232`；
- embedded binary SHA：`7b39c1588df919d001dea3ffd5651adb682f2451b5a0e18d42d4233296b53cc7`；
- fixture SHA：以同目录 `SHA256SUMS` 为准。

## 5. 执行

先运行 fixture `offline-test.sh`，再设置下列两个变量为已验 SHA 的本地文件并执行一次：

```bash
S2_SDK_MODULE=<sdk-0.3.232-sdk.mjs> \
S2_CLAUDE_BINARY=<embedded-2.1.232-claude> \
./docs/testing/verification-gates/fixtures/20260903-s2-sdk-sandbox-option-local-binding/run-once.sh \
  <fresh-result-directory>
```

运行结束后机械解析 SDK JSONL，断言一个 Bash tool use、一个对应 result、最终 success、无 permission denial，并与 TSV 合并判定。

## 6. 授权与禁令

当前只允许 Gate/fixture/INVALID evidence 的独立只读评审。用户对 provider tool turns 的预授权仅在本 Gate review PASS 与
non-provider preflight 全绿后激活，覆盖恰一个 SDK query；无需再次询问。

禁止修改 production/fork/workflow/ledger，禁止修改旧 INVALID fixture，禁止第二样本、恢复 OKX、开放 external domain或恢复 R4。

## 7. Maturity

- SDK managed-settings local-binding behavior：运行前 `NOT PROVEN`；
- S2 production policy：`NOT IMPLEMENTED`；
- E1/S1 既有成熟度不变。
