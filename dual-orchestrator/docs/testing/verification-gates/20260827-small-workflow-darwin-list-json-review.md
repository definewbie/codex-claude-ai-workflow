# Review: Small Workflow / Darwin List JSON Verification Gate

日期：2026-08-27
评审性质：独立、只读、代码级；未修改任何 production/Gate/task/plan/目标项目
评审基线：Dual HEAD `e369a6e7`（= 冻结 checkpoint，实测一致）

## Findings

### P1-1 冻结 target 的 baseline 测试集在干净机器上不可复现——Gate 前置条件自相矛盾

`darwin/data/registry/bindings.yaml:6,13,20` 与 `assets.yaml:4,10` 内嵌**他人机器的绝对路径**
`/Users/krisdwang/.hermes/skills/...`。在冻结 HEAD `1ad41527` 上执行 Gate 指定的
`python3 -m pytest -q`，三个测试文件在 **collection 阶段**即报错（模块级解析缺失外部资产）：

- `tests/test_e2e_phase1.py:19` → FileNotFoundError
- `tests/test_e2e_phase2.py:15` → FileNotFoundError
- `tests/test_evaluator.py:17` → FileNotFoundError

实际输出：`3 errors in 0.29s`（本机实测，非推测）。disposable worktree 无法修复——路径虽在
repo 内，但指向 repo 外且属于不存在的用户主目录。因此 Gate 的 Trigger precondition
"target baseline tests 必须全部 PASS"在任何缺少该外部资产的机器上必然失败，preflight 将
自判 INVALID，Gate 无法执行。

任务本身不受影响（`darwin list --json` 只依赖 `args.output_dir` 下的 manifests，不需要该
资产），受影响的只是 baseline 验证语义。**最小修订（仅 Gate 文本）**：把 baseline 定义改为
"collected tests 零失败 + 预登记的 3 个环境依赖 collection errors（按文件+错误签名白名单）
在 task 前后完全一致"；INVALID 判据中"baseline tests 未通过"相应限定为"白名单外新增失败或
collected 测试失败"。另一选项是 target 先修复 registry 再重冻结（新 HEAD/SHA，需重发 Gate），
成本更高。修订前 Gate 不可执行。

### P2-1 运行中 provider/adapter fatal 的判定归属未显式声明

Stop-loss 列出 "provider fatal"，FAIL 清单未含该项，只能经"workflow 未完成"间接推导为 FAIL。
建议补一句："有效样本中 provider/adapter fatal → FAIL（execution-failure 类），保存 wire
evidence 后止损"，避免执行期把它误判为 INVALID。

### P2-2（知会）Frozen Baseline 的"clean"描述不完整

`:89` "target baseline: clean; JSON mode absent..." 属实（git clean ✓、`darwin/cli.py:59-75`
仅人类可读输出 ✓、无 `--json` ✓），但未提及上述 3 个预存 collection errors——并入 P1-1 修订。

## Frozen baseline 独立复算（全部通过）

| 项 | 声明值 | 实测 |
|---|---|---|
| Dual checkpoint | `e369a6e79a4017edfb1dc33d5aeefab450f882da` | = 当前 HEAD（`test(workflow): validate small plan review profile`）✓ |
| workflow SHA-256 | `d41b835e…faaec` | `shasum` 逐位一致 ✓ |
| workflow shape | 28/46/8/6 | 直读 JSON + checkpoint tests 双确认 ✓ |
| target HEAD | `1ad41527…a074` | `git rev-parse` 一致 ✓ |
| target tree | `36d498d1…5c33` | 一致 ✓；`status --porcelain` 空 ✓ |
| task SHA-256 | `951517b8…6eca` | 逐位一致 ✓ |
| `run-real-case.sh` 自动审批 | 属实 | `:100` "Max human gates to auto-approve. Default: 12"、`:108` 自述 auto-approves ✓ |
| target JSON mode 缺失 | 属实 | `cmd_list` 仅文本表格；无 `--json` ✓ |

## 逐项核实结论

1. 冻结基线六项全部真实（见上表）。
2. 任务真实、有用、足够小、可机械验收：为 `darwin list` 增加 opt-in `--json` 是真实 CLI
   价值；malformed-entry 语义忠实镜像现有文本行为（`cli.py` 的 `except Exception` load-error
   行）；验收全部机械化（exact keys/order/empty/no-write/default 不变）。
3. task contract 明确：`run_id/asset_id/status/round_count` exact keys、排序锚定
   `ManifestStore.list_runs()`、空列表 `[]`、坏条目 `{run_id, error}` 且继续、no-write、默认
   输出不变、禁新依赖/持久化/命令/框架、禁改 `RunManifest`/`list_runs`/eval/run/report。
4. A5→gate 直连与 A6 三节点缺席由 checkpoint `e369a6e7` 的 graph identity 测试证明，Gate
   PASS.4 再于 persisted definition 运行时复核。
5. runs=8/occurrences=8/revision=0/correction=0 可机械证明：PASS.2 枚举精确节点集合 +
   PASS.3 对全部 `runs/*/run.json` occurrence 求和。
6. "首次 revision/correction 即 FAIL"与 9→8 目标一致：revision 使本轮唯一收益不成立，
   记录 typed evidence 后立即止损；INVALID 不可用作逃逸（身份/freshness 类才归 INVALID）。
7. `run-real-case.sh` 自动审批判断属实（上表）；禁止它是证明 human authority 的必要条件——
   脚本审批会伪造 L3 决策。
8. fresh JAR 分段执行保持统一 ledger boundary：`start/run/review decide/approve` 全部经
   `WorkflowFileLedger.resolveOpenGate → RoutingEngine`（WorkstreamLock 下，历轮已证）；UI/API
   同边界（H4）。
9. PASS 同时守住质量与边界：audit ready/gaps=[]、节点集合、occurrence 总和、A6 缺席+A5 经
   gate、JSON 精确 shape、target tests+README+无依赖、scope/lineage/acceptance、diff 最小性
   ——不是只看调用数。
10. FAIL/INVALID 可区分：身份/新鲜度/autorun/手工 ledger/旧工件 → INVALID；有效样本的
    revision/correction/质量/contract/session closure → FAIL。缺口仅 P2-1（运行中 provider
    fatal 归属）。
11. SoT 完整且排除项明确（ledger/sidecars/run.json/metrics/scope/audit/target diff/pytest/
    JSON stdout+manifest；排除 Markdown/console/模型自述）。
12. 无第二 owner、无旧 workflow 旁路（`--variant` 显式 INVALID）、worktree/worktree/session
    复用显式 INVALID；case report 只能从 SoT 派生，不得重推事实。
13. 成熟度边界正确：一次 PASS 仅证明该冻结任务可省 A6；不提升 production variant、不删除
    bundled plan reviewer；FAIL 先归因（A5 plan quality / 模型执行 / harness boundary）。
14. Gate+task+active plan 构成单一文档 checkpoint（active plan `:135-143` 已记录）。
15. 无过度设计；无更小而同等可靠的 Gate——弱化 probe/freshness 要求会直接损害 INVALID 判别力。

## End-to-end invariant

| 维度 | 结论 |
|---|---|
| Declared by | 冻结 task 文件（SHA）+ external workflow id/version/SHA/shape + target HEAD/tree |
| Proven by | preflight 全量复算 + persisted definition 运行时复核 + ledger/sidecar/run.json/audit 事实 |
| Consumed by | human plan gate（用户本人）→ 不变的 implementation/self-test/acceptance 链 |
| Enforced before | preflight 全过才建 workstream；human action 先于 implementation dispatch；禁止 autorun |
| Failure route | 有效样本 revision/correction/质量失败 → FAIL 即止损；身份/新鲜度违例 → INVALID（不得部分成功） |
| Cannot-bypass evidence | checkpoint graph identity + Gate PASS.2-5 的 ledger/metrics/run.json 机械断言 + bundled SHA 冻结 |
| Residual path | P1-1 baseline 白名单缺口（修订前 Gate 不可执行）；单任务结论不外推 |

## 结论

- **Boundary Review：WARN**（唯一 P1 为 Gate 文本/baseline 定义缺陷，非设计缺陷；修订成本一段文字）
- **Gate 可执行：否——待 P1-1（附 P2-1）文本修订后可执行**
- **选择性提交：修订并入后满足**（Gate+task+active plan 单一原子文档 checkpoint）
- **提交后授权边界**：仅允许非 provider preflight（身份/新鲜度复算、workflow validation、
  fresh package、修订后 baseline pytest）；**provider probes 与 fresh workstream 仍需分别独立
  授权**（Execution Procedure §3-4）
- **成熟度**：test-only `VALIDATED`；reused owners 维持 `ENFORCED`；small-profile 质量/收益
  `NOT PROVEN`；production variant `NOT MODELED`——声明与证据精确对齐
- **下一步唯一动作**：按 P1-1/P2-1 修订 Gate 文本（baseline 白名单 + provider fatal 归属），
  复核冻结值不变后选择性提交三份文档
