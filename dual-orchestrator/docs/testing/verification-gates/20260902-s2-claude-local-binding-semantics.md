# S2 Claude Local Binding Semantics Verification Gate

日期：2026-09-02  
状态：`INDEPENDENT REVIEW PASS / PREAUTHORIZED / PROVIDER NOT RUN`

## 1. Verification Gate

- **Target**：使用 Dual production Claude 链测量 `sandbox.network.allow_local_binding=true` 的三项行为：sandbox 内自建
  listener 的 round-trip、sandbox client 对执行前已存在的宿主 loopback listener 的可达性，以及未授权公网直连是否被拒绝。
- **Source of truth**：冻结 fixture SHA、production E1 policy ref/SHA、launcher JSON、scoped acpx session stream/record、唯一
  Bash tool input/result、`s2-loopback-result.tsv`、宿主 sentinel 前后检查。模型最终正文不是判定来源。
- **Trigger precondition**：Dual HEAD 是 `08229040` 后代；Doctor 验证 reviewed E1 acpx identity；实际链为
  `claude-agent-acp@0.70.0`、SDK `0.3.232`、embedded Claude Code `2.1.232`；fixture SHA 全匹配；runner 生成并记录
  唯一的 fresh run-scoped session 名；宿主 sentinel 运行前可达。
- **How precondition is checked before run**：fresh package；fixture offline test 与 package-local launcher compile；Doctor/build-info
  验证 `fd173f04 / 81acbb74... / v2`；embedded binary SHA 验证；runner 的 host precheck。
- **PASS**：恰一个 provider prompt、恰一个 Bash tool call，input 精确为 `./s2-loopback-probe.sh`；sandbox 内 `python3`
  可用；self bind 与 self connect 成功；external HTTPS 失败；host sentinel 前后均可由宿主访问；结果完整且 fixture SHA 前后不变。
  `host_connect` 原样分类为 `HOST_DENIED` 或 `HOST_REACHABLE`，两者都是有效测量。
- **FAIL**：self bind/self connect 失败，或 external HTTPS 成功。此时不得实施 S2，回到 bounded comparison。
- **INVALID**：production identity、policy binding、运行前后 SHA、host precheck/postcheck、sandbox 内 `python3`、tool-call
  数量/命令、result/stream/session closure 任一不符；provider/adapter fatal；脚本未完成。INVALID 不得解释结果或修改 fixture 重跑。
- **Stop-loss**：任何额外 tool call、Bash permission request、unsandboxed execution、external HTTPS 成功或 180 秒超时立即停止；
  只运行一个样本。
- **Run artifact freshness**：fresh result directory；记录唯一 session 名、开始/结束时间、policy ref/SHA、launcher JSON、原始
  stream/record、probe TSV、host sentinel 日志、前后 fixture SHA 和排序 evidence manifest。

## 2. 测量边界

本 Gate 不再调用 standalone Claude `2.1.236`。它复用 production 的：

```text
ClaudeExecutionPolicy.write(v2)
-> fixture 仅把 allow_local_binding false 改为 true 并重算 binding SHA
-> reviewed AcpxAgentTransport / acpx fork
-> claude-agent-acp 0.70.0
-> SDK 0.3.232 / embedded Claude Code 2.1.232
-> implement permission profile
```

该临时翻转只存在于 disposable workspace 的 occurrence policy；production 默认值继续为 `false`。launcher 不实现网络策略、不写
workflow ledger、不判断成熟度。它沿用 allowlist differential 已验证的注入形态，仅更换被测字段。

probe 只做三个判别：self-owned loopback、host-owned loopback、未授权 external HTTPS。所有 curl 使用 `--noproxy '*'`，排除本机
Shadowsocks proxy 环境对 loopback 判定的中介。不得加入 domain approval、IP/DNS 绕过矩阵或通用网络评估器。

## 3. Result Classification

| 有效结果 | 含义 | 后续动作 |
|---|---|---|
| `HOST_DENIED` | self-owned listener 可用，宿主既有 listener 保持隔离 | 可制定只翻转 production policy 字段的最小 S2 checkpoint |
| `HOST_REACHABLE` | 开关也允许访问宿主 loopback | 必须由 operator 接受或拒绝该暴露面，再决定是否实施 |

两种结果都必须同时满足 external HTTPS denied。不得把 `HOST_REACHABLE` 隐藏为 residual，也不得从 standalone 版本外推。

## 4. Fixture 与编译

fixture 位于
`docs/testing/verification-gates/fixtures/20260902-s2-claude-local-binding-semantics/`，包含 launcher、prompt、probe、runner、
offline test 与 SHA manifest。编译必须使用当前 production classes：

```bash
cd dual-orchestrator
mvn -q -pl dual-adapter-acpx -am -DskipTests package
mvn -q -pl dual-adapter-acpx dependency:build-classpath \
  -Dmdep.outputFile=/private/tmp/s2-adapter-classpath.txt
mkdir -p /private/tmp/s2-launcher-src/dev/dual/adapter/acpx /private/tmp/s2-launcher-classes
cp docs/testing/verification-gates/fixtures/20260902-s2-claude-local-binding-semantics/E1LocalBindingDiagnosticLauncher.java.txt \
  /private/tmp/s2-launcher-src/dev/dual/adapter/acpx/E1LocalBindingDiagnosticLauncher.java
javac \
  -cp "dual-adapter-acpx/target/classes:dual-runtime/target/classes:dual-core/target/classes:$(cat /private/tmp/s2-adapter-classpath.txt)" \
  -d /private/tmp/s2-launcher-classes \
  /private/tmp/s2-launcher-src/dev/dual/adapter/acpx/E1LocalBindingDiagnosticLauncher.java
```

运行时必须设置 `DUAL_ACPX_EXECUTABLE` 为 Doctor 验证通过的 reviewed binary，并调用：

```bash
./docs/testing/verification-gates/fixtures/20260902-s2-claude-local-binding-semantics/run-once.sh \
  <fresh-result-directory> /private/tmp/s2-launcher-classes /private/tmp/s2-adapter-classpath.txt
```

运行后复制并验 SHA 的 scoped session stream/record，确认一个 prompt、一个 Bash tool call、session closed；不得扫描其他 session。

## 5. Frozen Baseline

- Dual HEAD：`08229040`（S1 occurrence-policy session lifetime checkpoint）；
- reviewed upstream：`fd173f04aa1b56f9e3f5ca5190c034ddcae28792`；
- reviewed patch SHA：`81acbb7491a17aedfb48b1a73f077b59e06f1d5eceb7cd617e59df86aa5c60ab`；
- policy schema：`claude_execution_policy.v2`；
- adapter/SDK/embedded Claude：`0.70.0 / 0.3.232 / 2.1.232`；
- embedded binary SHA：`7b39c1588df919d001dea3ffd5651adb682f2451b5a0e18d42d4233296b53cc7`；
- fixture 文件 SHA：以同目录 `SHA256SUMS` 为准，delta review 必须逐项复算。

任一 fixture 字节或 runtime identity 变化都使本 Gate 失效，必须重新评审，不得消耗第二个 provider 样本。

## 6. 授权与禁令

用户已明确预授权后续 provider tool turns，无需再次询问；该授权仅在本 Gate delta review PASS、non-provider preflight 全绿后激活，
且只覆盖这一个 production-chain Bash occurrence。现阶段仍不得执行 provider。

禁止修改 production policy、fork、workflow、ledger 或 OKX；禁止恢复 real-case、创建第二样本、开放 external domain、恢复 R4，或
把本结果解释为完整 E1 网络安全证明。

## 7. Maturity

- S1 occurrence session lifetime：`ENFORCED`；
- E1 policy transport：`ENFORCED`；
- local-binding production-chain behavior：运行前 `NOT PROVEN`；
- S2 production policy：`NOT IMPLEMENTED`。
