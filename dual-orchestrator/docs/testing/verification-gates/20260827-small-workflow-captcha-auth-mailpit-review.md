# Review: Captcha-Auth Mailpit Verification Gate

日期：2026-08-27
评审性质：独立、只读；未修改代码/Gate/plan/target；未调用 provider；未创建 workstream
（唯一机械动作：在 source checkout 独立复跑 target check script 以复核 baseline 声明）

## Findings

**P0：无。P1：无。**

### P2-1 Procedure §2 与 §3 对 workspace preflight 的授权归属存在措辞歧义
§2 允许"提交后只执行 fresh package、workflow validation 和 **non-provider identity/workspace
preflight**"；§3 又规定"provider probes 与 **fresh workstream** 仍需分别授权"。而 harness 的
workspace preflight（capture/verify）按设计发生在 `dualctl start(no-run)` 创建 workstream
**之后**——两句话可以读出两种流程：(a) §2 已允许 start+capture（provider-free 的资格检查，
新能力的合理优势）；(b) workstream 创建仍属 §3 授权，§2 的"workspace preflight"仅指 source 侧
identity 复算。建议加一句消除歧义（推荐 (b)，与既往 Gate 的授权粒度一致）：例如"workspace
preflight（capture/verify）在 fresh workstream 获得独立授权后、作为运行序列首段执行"。

### P2-2（正面记录）前轮 Darwin Gate 评审的 P2-1 已被吸收
FAIL 清单现显式包含 "provider/adapter fatal"（`:41`），消除了当时"运行中 provider fatal 只能
间接推导为 FAIL"的归属缺口；workspace check 因 Docker/Maven/Java/Testcontainers/环境失败 →
INVALID（`:44`）则把 Darwin 的教训制度化为分类规则。无需行动，仅记录闭环。

## Frozen Baseline 独立复算（全部通过）

| 项 | 声明 | 实测 |
|---|---|---|
| Dual checkpoint | `212176b4927e…b8be2` | 存在，= "feat(harness): verify execution workspace eligibility"（eligibility 能力提交）✓ |
| workflow SHA | `d41b835e…faaec` | `shasum` 逐位一致（与 Darwin Gate 冻结值相同文件）✓ |
| workflow shape | 28/46/8/6（`6 human.review`） | 与 checkpoint graph identity 测试一致 ✓ |
| target HEAD/tree | `dc0ea592…`/`6b71d91d…` | `git rev-parse` 一致；`status --porcelain` 空 ✓ |
| task SHA | `32a6f679…32a0c` | `MAILPIT-LOCAL-EMAIL-OTP-TASK.md` 逐位一致 ✓ |
| check SHA | `a1902c10…466c8` | `scripts/verify-workspace.sh` 逐位一致 ✓ |
| baseline 99/0/0/0 | `verify-workspace.sh -> 99 tests / 0 failures / 0 errors / 0 skipped` | **独立复跑 BUILD SUCCESS；surefire XML 聚合（14 files）= tests 99 / failures 0 / errors 0 / skipped 0** ✓ |

## 核实结论

**Gate 验证的是 Dual，不是环境/模型/Markdown。** 双重证据结构保证这一点：
- Dual 侧被验证事实：8-call small-profile 完整执行（runs/occurrences 恰 8、correction/revision=0）、
  A6 缺席 + A5 经 human gate 的 persisted graph、workflow COMPLETED + audit ready/gaps=[]、
  scope/lineage/self-test/acceptance 不退化——全部来自 ledger/typed sidecar/metrics 等 SoT
  （`:13-16`），Markdown/console/模型自述显式排除；
- 环境归 INVALID、模型/执行归 FAIL：workspace check 的 Docker/Maven/Testcontainers 失败
  （`:44`）在 provider 之前 INVALID——环境兼容不被误记为 Dual 平台能力；Mailpit 产品语义
  （PASS 4-7）全部锚定 target-owned 任务的机械验收（真实 SMTP-backed test、`mvn test`、
  `docker compose config`、README），不由 Dual 解释。

**逐项**：
- Task 真实有用且小：本地开发 email-OTP 可用性 + OTP 保密边界（`:5/:13`），约束清单
  （`:26-30`）封死 scope（无 SMS vendor/reset/social/admin、dev 下也不暴露 OTP、不引入第二
  delivery 抽象）；PASS 4-7 与 task Required Behavior/Verification 一一映射。
- Check script 是 harness 边界的正确实例：target-owned、exit-code-only（`set -euo pipefail` +
  终止命令 `mvn -Dapi.version=1.44 test`）、工具链存在性检查；`-Dapi.version=1.44` 的 Docker 29
  兼容 pin 在脚本注释与冻结基线中均诚实声明为 verification-procedure-scoped，不改产品依赖。
- Preflight 接线正确：`:22-25` 使用 capture + verify，且要求 `status=passed` **且**
  `verificationStatus=passed`——与刚评审过的 harness 语义（proven 需 verify 真实发生）一致；
  这是 eligibility 能力的第一个 configured Gate 使用，Darwin 的 mtime 类差异将在此 preflight
  阶段显形为 INVALID 而非事后 FAIL。
- 8-call 判定机械可证（metrics + node instances + `runs/*/run.json` occurrence 求和，同
  Darwin Gate）；首次 correction/revision 即 FAIL 止损与 9→8 唯一收益目标一致。
- INVALID 清单完整（identity/旧 JAR/preflight 未配置或未 PASS/autorun/bundled variant/
  `--skip-build`/复用/手工 ledger/target 不 clean），并明确"不得解释为部分成功、不得第二样本"。
- Stop-loss 与 human authority：60 分钟 active runtime 排除 WAITING_FOR_HUMAN；human gate 仅由
  用户经 UI/fresh JAR CLI 决定；禁 autorun。
- 成熟度边界精确：eligibility `ENFORCED WHEN CONFIGURED / NOT PROVEN`；small graph
  `VALIDATED / PRODUCTION PROFILE NOT MODELED`；`9→8`、Mailpit 完成与 check real-case 价值
  全部 `NOT PROVEN`——单次 PASS 的外推限制与 Darwin Gate 同构。
- Active plan `:184` 已记录任务选自 captcha-auth-app 真实未完成能力；工作树仅 Gate + plan
  （+本评审）。

## End-to-end invariant

| 维度 | 结论 |
|---|---|
| Declared by | 冻结 Dual/target/workflow/task/check 六项 identity + Gate 声明的 eligibility 与 PASS 条件 |
| Proven by | persisted ledger/typed sidecars/run.json/metrics + workspace-preflight.json（capture+verify 双 passed）+ target 机械验收（mvn/compose/Mailpit-backed test） |
| Consumed by | 首次 `dualctl run` guard；report 只投影 typed evidence |
| Enforced before | 首次业务 Agent execution（preflight verify 先行；human gate 先于 implementation） |
| Failure route | preflight/env → INVALID（provider 0）；有效样本 correction/revision/质量失败 → FAIL 止损；单样本纪律 |
| Cannot-bypass test | harness 的调用序测试（本 Gate 复用）+ Gate PASS.2/3 的 ledger 机械断言 + frozen SHA 全量复算 |
| Residual path | verify→lock 窄窗口（已声明）；Mailpit 产品质量归 target 测试；`9→8` 需本 Gate 真实 PASS 才可评估 |

## 判定

- **Boundary Review: PASS**
- **提交条件：满足** —— Gate + active plan（+本评审）作为单一文档 checkpoint 选择性提交；
  提交前可顺带以一句话消除 P2-1 的授权措辞歧义（非阻塞）。
- **授权边界**：提交后仅 fresh package、workflow validation、identity 复算；provider probes 与
  fresh workstream（含 workspace capture/verify 与首段 run）分别独立授权；全程单样本、禁
  autorun、禁 `--skip-build`、禁复用。
