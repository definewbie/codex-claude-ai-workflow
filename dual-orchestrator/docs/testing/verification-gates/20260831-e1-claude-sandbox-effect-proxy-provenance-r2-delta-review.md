# E1 R2 Gate Delta 复审

日期：2026-09-01  
方式：独立只读复审；复跑离线测试，未修改代码、未调用 provider。  
结论：`PASS / EXECUTION READY`

## Findings

- P0：无。
- P1：无。
- P2：一条 nano 级知会。Node 分类器的 `fs.readFileSync(0)` 位于 `try` 外；理论上的 stdin 读取异常可能以
  `exit 1` 被解释为 external。当前 stdin 恒来自 `printf` 管道，该风险不阻塞，也不值得再次修改已冻结 fixture。

## 上轮发现关闭

1. **P1-1 CLOSED**：Node 前置自检与分类器退出码三分支共同保证 `0 -> controlled`、仅主动 `1 -> external/74`、
   其他退出（含 `126/127` 与信号）均为 `classifier_unavailable/76`。fake Node 用例证明执行异常不会产生
   `E1_UNSAFE_PROXY_PROVENANCE`。
2. **P2-1 CLOSED**：`NO_PROXY` 空项显式忽略，尾随逗号 fixture 判为 `controlled_local_only`。
3. **P2-2 CLOSED**：proxy URL 接受完整 IPv4 `127.0.0.0/8`；`127.evil` 反例被拒且不泄漏原值。
4. **P2-3 CLOSED**：Gate 记录 r1 fixture 从已评审 SHA 到实际执行 SHA 的修订链，并把历史样本限定为 observation
   evidence；未来 fixture 变化必须更新 SHA 并完成 delta review。
5. **P2-4 CLOSED**：归档 `runtime/acpx-config.json` 证明历史样本显式传入 `claude-agent-acp@0.70.0`；wrapper
   默认 pin 随本 checkpoint 落盘，未来仍由 session record 与 package identity 复核实际 runtime。

## 独立复现

- `e1-network-probe.test.sh`：PASS。
- `bash -n`：PASS。
- network script SHA：`341ac0ca6dbd9f53829e1a603794ed19340a1c302a69f2b28cb6bd15e24aab4d`。
- classification test SHA：`b972bad078636434c63e88cacea386c53145bc7a4b0c522f165331469d08fe7a`。
- launcher、Bash prompt、WebSearch prompt SHA 与 Gate 冻结值一致。

## Boundary Review

`PASS`。Node 执行异常只能进入 `INVALID`；IPv4 loopback 与伪装域名边界有正反测试；fixture 修订链与 runtime
pin 可复现；未引入新的绕过、敏感值泄漏或成熟度夸大。proxy 分类仍不是网络 enforcement 的安全结论。

## 下一步边界

1. 提交 docs/fixture checkpoint，取得 clean SHA。
2. standing 授权内执行 fresh package、Doctor、SHA 复算、launcher compile、离线测试和一次 fresh minimal health 后关闭。
3. Bash 与 WebSearch 两个带工具 capability prompts 仍需一次性明确授权；Bash 全过后才允许 WebSearch/session-conflict。
4. `FAIL` 返回 bounded comparison；`INVALID` 不创建第二样本。禁止恢复 OKX、新 real-case 或修改 production network policy。
