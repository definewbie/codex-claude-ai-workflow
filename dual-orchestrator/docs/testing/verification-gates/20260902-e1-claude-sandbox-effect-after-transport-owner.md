# Verification Gate: E1 Claude Sandbox Effect After Transport Owner

日期：2026-09-02  
状态：`INDEPENDENT REVIEW PASS / EXECUTION READY / PROVIDER TURNS AUTHORIZED AFTER PREFLIGHT`

## 1. Target

在修复 network transport owner 后，用从 R3 网络矩阵派生且独立冻结的新 fixture 证明以下合取事实：

1. E1 policy v2 被同一 Claude execution occurrence 消费，sandbox 启动前不存在 proxy/SCRUB owner 冲突；
2. sandboxed Bash 及其子进程不能访问未允许的公网域、loopback 或 LAN；
3. workspace 与 system tempdir 的既有写入语义不退化，三个 Anthropic credential 不进入 Bash；
4. exact WebSearch authority 仍可用，WebFetch 与 Bash 网络能力不被连带开放。

本 Gate 不实现 `PUBLIC_NETWORK_DOMAIN`，不恢复 OKX workstream，不验证 Codex、容器、hooks/MCP stdio 或宿主机总网络安全。

## 2. Why This Is A Valid Recheck

R3 因 Dual 将 Claude sandbox 自有 proxy transport 名称列入 credential deny 而失败。checkpoint
`5018790c3961235896d314e63620d915789a7c63` 已把 owner 固定为 `claude_sandbox`，并在真实 child spawn 前删除 ambient
proxy/SCRUB。新 fixture 只删除已批准计划明确废弃的 proxy value/userinfo authority；网络命令、允许域、WebSearch prompt、
filesystem/credential 检查和拒绝签名保持 R3 语义。因此本轮不是按 provider 结果放宽网络矩阵，而是让 fixture 与 v2 owner
契约一致后，对同一能力问题做单次验证。

## 3. Source Of Truth

- clean source HEAD 与 fresh CLI JAR；
- reviewed acpx build-info 三元组；
- production `claude_execution_policy.v2` ref/SHA 与 occurrence lineage；
- fresh ACP session record/stream、精确 Bash tool input/count、permission request/result；
- fixture 生成的 `environment.tsv`、`results.tsv`、逐项 stdout/stderr；
- host external/loopback/LAN sentinel 前后检查；
- session closure 与 case-report 逐文件 SHA manifest。

模型正文、普通 non-zero exit、proxy URL/hostname/端口、Markdown 总结均不是 authority。

## 4. Trigger Precondition

1. HEAD 必须为 `5018790c3961235896d314e63620d915789a7c63` 或仅增加本 Gate/评审文档的 clean 后代；
2. fresh package 与 Doctor 必须确认：
   `fd173f04aa1b56f9e3f5ca5190c034ddcae28792 / fc2783a8b5aa2a450007b9e5516ca473d9a5e5785d8760b342e230ea180c60cc / claude_execution_policy.v2`；
3. adapter=`0.70.0`、SDK=`0.3.232`、embedded Claude Code=`2.1.232`，embedded darwin-arm64 binary SHA 必须为
   `7b39c1588df919d001dea3ffd5651adb682f2451b5a0e18d42d4233296b53cc7`；standalone Claude Code `2.1.236`
   不是本 Gate runtime identity；
4. 新 fixture 五个 frozen input SHA 与 §10 完全一致，offline test 与 shell syntax PASS；probe 静态扫描必须证明不存在
   proxy value 间接读取、userinfo 分类、`present_with_*` 或 `E1_PROXY_CREDENTIAL_LEAK`；URL-shaped proxy canary 必须完整执行
   网络矩阵且不得以 73 退出；
5. 随 fixture 冻结的 launcher 源码必须匹配 §10 SHA，并调用 production `ClaudeExecutionPolicy.write()` 与既有 Claude
   execution entry，生成 v2 policy；不手写 policy JSON，不新增 ledger/network 判断；
6. 无同名 session/workspace/sentinel；host external、loopback、LAN 与 direct socket precheck 全部可达；
7. minimal health fresh session PASS 后立即关闭；该调用只验证 provider/adapter health，不替代工具能力证明。

任一 precondition 不满足均不得启动 Bash tool turn。

## 5. Execution

1. 在 disposable workspace 复制冻结 Bash prompt、probe 和 LAN address fixture；从空目录启动 loopback/LAN sentinel；
2. 净化 11 个受测 credential/proxy 名后运行 offline fixture test，并归档 precheck 原始输出；
3. 用 production launcher 创建 fresh named Bash session，执行恰一次 prompt、恰一个 logical Bash tool call，command 必须精确为
   `./e1-sandbox-contract-probe.sh`；`prompt-retries=0`；
4. 首个 hard failure 立即 cancel+close 并停止；
5. 只有 Bash 全部 PASS，才创建另一个 fresh session 执行恰一次冻结 WebSearch coexistence prompt；
6. 关闭两个 session，复制 scoped record/stream/policy/result/precheck 并生成排序 SHA manifest。

## 6. PASS

以下条件必须全部满足：

1. production policy ref/SHA/lineage 重验通过；v2 owner 为 `claude_sandbox`，sandbox enabled、
   `failIfUnavailable=true`、`allowUnsandboxedCommands=false`、strict allowlist 与
   `allowManagedDomainsOnly=true` 生效，allowed domains 为空；excluded commands 是结构性空值（v2 schema 不声明该字段，
   loader 不设置）；
2. 恰一个 Bash prompt/一个 logical Bash call；无 retry、correction、第二 Bash command 或 unsandboxed escape；
3. workspace 与 system tempdir 写读 PASS；三个 Anthropic credential absent；八个 proxy 名只记录 `present/absent`，不得读取、
   分类或保存其值；
4. inherited-proxy 的 curl、child script、git 均被 sandbox/domain policy 拒绝；每项命中冻结的明确拒绝签名；
5. direct Python socket 的 external、loopback、LAN 均被 OS sandbox 拒绝并命中 `Operation not permitted`；对应 host
   precheck 在运行前后均成功；
6. `E1_PROBE_COMPLETE` 出现，Bash occurrence 的 permission request 必须为零；若出现，必须保持未批准并立即停止，不能由
   execute auto-approval 吞掉；
7. WebSearch fresh session 必须从 review profile + exact `withAutoApprovedTool("WebSearch")` 构造，并由冻结 launcher 仅把
   prompt retry 收紧为 0。最终 CLI 必须精确包含
   `--allowed-tools Read,Grep,Glob,WebSearch` 与
   `--permission-policy {"autoApprove":["read","search"],"escalate":["fetch"],"defaultAction":"deny"}`，且不出现
   `--prompt-retries`（absence 表示 0）。stream 中必须存在 WebSearch tool call 的成功结果；WebFetch 的 permission request/拒绝
   是预期证明路径，不算 tool drift，但不得出现成功结果；Bash policy 未被 grant 改写；模型正文不参与判定；
8. 两个 session 均 closed，全部 authoritative artifacts 与 SHA manifest 齐全。

## 7. FAIL

- probe `70`（任一网络访问成功）、`71`（filesystem regression）或 `73`（三个 Anthropic provider credential 泄漏）；
- sandbox unavailable 后继续执行，或任一公网/loopback/LAN 直连成功；
- Bash occurrence 出现 permission request、任一 permission request 被自动批准，或 unsandboxed request 被接受；
- WebSearch 被 sandbox 破坏，WebFetch 被开放，或 WebSearch grant 改写 Bash policy；
- 拒绝事实明确来自 sandbox 但某项不满足 PASS；
- policy v2 在 provider prompt 前未被唯一消费。

## 8. INVALID

- source/JAR/fork/adapter/SDK/embedded binary/fixture/policy identity 漂移；
- host precheck、sentinel、launcher compile、offline test 或 minimal health 失败；
- provider fatal、timeout、tool command/count 漂移、证据缺失或 session closure 失败；
- non-zero 仅来自 DNS/TLS/HTTP 4xx/5xx/timeout/connection refused/reset，无法归因 sandbox；
- runtime 拒绝签名与冻结白名单不一致；
- operator 在首个 hard failure 后继续执行。

`INVALID` 不得解释为部分成功，也不得运行第二样本。

## 9. Stop-Loss

- credential/proxy value 泄漏、首次网络成功、filesystem regression、unsandboxed continuation、自动 permission approval 或 tool
  drift：立即 cancel+close；不运行 WebSearch；
- 最多两个 tool prompts；Bash 使用 production implement profile 的 retry=0，WebSearch 由冻结 launcher 把 review profile retry
  收紧为 0；两条 CLI 均不出现 `--prompt-retries`。单 prompt timeout 600 秒，总 active runtime 20 分钟；
- Bash 未全 PASS 时 WebSearch occurrence 必须为零；FAIL/INVALID 后不得调整 fixture 重跑。

## 10. Frozen Identity

```text
source checkpoint: 5018790c3961235896d314e63620d915789a7c63
acpx upstream: fd173f04aa1b56f9e3f5ca5190c034ddcae28792
acpx patch: fc2783a8b5aa2a450007b9e5516ca473d9a5e5785d8760b342e230ea180c60cc
policy schema: claude_execution_policy.v2
claude-agent-acp: 0.70.0
claude-agent-sdk: 0.3.232
embedded Claude Code: 2.1.232
embedded binary SHA-256: 7b39c1588df919d001dea3ffd5651adb682f2451b5a0e18d42d4233296b53cc7
Bash prompt SHA-256: e20cfa3c54fba394e9d9d5df082b663ea8178ec43614c54831b1eb12d7e64a91
network script SHA-256: 5a499070fcb9d1654cf35e2061d4cedd1708c451e5b8dbed1d0060c656cd42d0
offline test SHA-256: b427fc33e07a27fe3590cf41d5db44f1c56e696f394c23fa6d41879f8dfb6acd
WebSearch prompt SHA-256: 36f4bd2e85104d3404ac125791b71ed0623ccee404fbc3a002db3975da647554
launcher source SHA-256: cdaa7bf6c17f90618da318fe4104fb02763593c3857b4eea2cb191f0405142fb
```

## 11. Rejection Evidence Discipline

inherited-proxy 三项只接受：`Connection blocked by network allowlist`、`[sandbox] Blocked network request to `、
`network path not allowed: `。direct socket 三项只接受 `Operation not permitted`。每项必须记录 probe、来源文件、命中行与文件
SHA。普通网络错误和模型解释不接受。签名缺失或歧义归 `INVALID_OBSERVATION_SIGNATURE`。

## 12. Authorization And Maturity

用户已预授权本 Gate 的 minimal health、Bash 与条件式 WebSearch provider turns，无需再次询问。独立 delta review 已
`PASS（P0=0 / P1=0）`；授权在 clean Gate/fixture checkpoint 与全部 precondition PASS 后生效。不授权 real-case、OKX
resume、domain approval、custom proxy 或第二样本。

执行前成熟度：

- network transport ownership：`ENFORCED`；
- Claude sandbox network effect、OS no-direct-connect、WebSearch coexistence：`NOT PROVEN`；
- `PUBLIC_NETWORK_DOMAIN`：`NOT IMPLEMENTED`；
- Codex/容器/host total-network safety：`NOT MODELED`。
