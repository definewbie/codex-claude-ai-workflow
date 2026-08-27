# Review: Execution Workspace Eligibility Preflight Implementation Plan

日期：2026-08-27
评审性质：独立、只读、代码级；未修改计划/production/tests/target；未调用 provider
决策基线核对：`fddb356a` 为声明 clean checkpoint；候选比较与其评审的 P2 delta 已全部吸收
（持久化 SoT §4.1、benchmark 委托 §3.2、TOCTOU verify §5.2、exit-only §3.1.4）

## Findings

**P0：无。P1：无。**

### P2-1 T-suite 缺少含空格路径的 quoting 测试
现行 `parse_started_workstream` 用 `awk '{print $2}'` 解析 `Workspace:`（`run-real-case.sh:612`），
路径含空格即截断——这是被本计划顺带消除的旧缺陷。新 helper 全面改用持久化链后，shell quoting
纪律应被钉住：在 `real-case-workspace-preflight.test.sh` 增加一个 source root/workspace 路径
含空格的 focused case（一行测试的量级）。计划 §5 已覆盖 signal、原子写与失败 typed evidence；
唯 quoting 无测试锚。

### P2-2（轻微）§4.1 默认布局定位宜注明唯一构造点与漂移后果
`StartCommand.java:77-79` 是默认 worktree 布局 `<gitRoot.parent>/<gitRoot.name>-agent-runs` 的
唯一构造点（实测吻合计划公式）。建议 §4.1 引用该行并注明：若 Java 侧默认布局未来变更，locator
会漂移，但持久化链校验（candidate 下无合法 workstream.json → INVALID）使其 fail-visible 而非
静默错位。非阻塞。

## 代码事实核实（全部吻合）

| 计划声明 | 实测 |
|---|---|
| 持久化身份链字段 | `WorkstreamDocument.java:9/12/13`（workstreamId/repository/primaryWorkspaceId；repository 含 originalWorkdir/gitRoot `:27-28`）+ `WorkspaceDocument.java:9/10/14/15`（workspaceId/workstreamId/originalWorkdir/effectiveWorkdir）✓ |
| 默认 worktree 布局 | `StartCommand.java:77-79`：`gitRoot.getParent().resolve(gitRoot.getFileName()+"-agent-runs")` ✓ |
| `start` 无 `--run` 语义 | `StartCommand.java:53-54/:131/:139`（前轮已核）✓ |
| `dualctl run` 签名 | `RunCommand.java:19/22/28`：`--workdir`/`--max-steps`/positional workstream ✓ |
| 现行 stdout 解析面 | `run-real-case.sh:608-618`：Workstream/Workspace 均从 start.log awk 解析（含空格截断缺陷）——计划替换面精确 ✓ |
| 现行 start 即 `--run` | `run-real-case.sh:570` ✓（时序缺口真实） |
| 文件清单存在性 | `docs/testing/real-case-benchmarks/README.md` 在；`verification-gate.sh` parser 保留不动（§8 声明与文件一致）✓ |
| benchmark 委托 | `run-public-benchmark.sh:7` 委托 run-real-case.sh；§3.2 透传 + 三类 Gate 文案区分覆盖前轮 P2-3 ✓ |

## 八组问题结论

1. **Owner/SoT 正确**——eligibility evidence 归 harness；`workstream.json.primaryWorkspaceId →
   workspaces/<id>.json.effectiveWorkdir` 逐字段经 Java 文档核实，是正确 authoritative 链；
   stdout/registry/目录布局降为 locator/cross-check；候选目录构造复用 StartCommand 唯一布局
   规则且以持久化链校验收口，不复制 Java owner（P2-2 补注引用即完全闭合）。
2. **Runner 时序保持语义**——`start(no-run)→check→run→autorun` 全部原语现存；`run` 与 `autorun`
   的分工（initial segment vs 跨 gate）与现有 stop-loss/quiesce 复用声明一致；三类失败
   （start/preflight/initial-run）均有 report/cleanup/session-closure 路径，且"无 Agent run 时
   不误报 session closure"（§6.1）是正确的边界细节；INVALID 时业务调用为 0 由 T4 钉住。
3. **Helper 边界最小**——capture/verify 两动作各司其职；target 语义完全在 SHA-bound script +
   exit code；不解析 pytest/Maven/npm、无白名单化；evidence 字段足以复现判定（含 pre/post
   status、digest、时间区间）；signal/原子写/失败 typed evidence 覆盖；唯一缺口为 quoting 测试
   （P2-1）。
4. **TOCTOU 诚实**——verify 真实缩小换窗（重读 ledger/HEAD/tree/tracked digest/terminal
   digest）；`verify` 后到 Java lock 前的窄窗口与单 operator 纪律如实声明（§5.2 末段）；
   未宣称事务隔离。
5. **Legacy 兼容诚实可执行**——`not_configured/false` 强制记录且 report 不得显示 PASS；
   未做 Markdown 语义推断（正确拒绝第二控制协议）；benchmark 三类 Gate 文案区分封住冒充面。
   更强的机械约束（如拒绝 baseline-sensitive Gate 无 check）需要解析 Gate 文本，成本大于收益，
   维持声明责任是正确取舍。
6. **Evidence projection 无第二 owner**——source 配置事实（preflight.json）与 execution 结果
   （workspace-preflight.json）分离；summary/report 只投影 typed evidence；§7 明确不从
   console/test summary 重推断。
7. **Sibling sweep 完整**——benchmark 透传/文案/probe 不退化（T7）+ 三份测试文件覆盖；
   deterministic fake-dualctl smoke（smoke-test.sh/semantic-boundary-smoke.sh）正确排除；
   文件清单 8 项无遗漏（verification-gate.sh 显式不动并给出理由）。
8. **通用性成立**——check 在真实 execution workspace 内以 cwd 执行，任何 checkout
   metadata/ignored/env/bootstrap 语义差异都会显形为 check 失败；无平台机制引入；符合先有后优
   （harness-only、零 Java 变更、无需 reactor）。无更小且同样可靠的替代——前轮比较已穷尽。

## End-to-end invariant（核实后）

| 维度 | 结论 |
|---|---|
| Declared by | runner `--workspace-preflight-check` 参数 + Gate 声明（check path/SHA 与 PASS 条件） |
| Proven by | persisted 身份链 + workspace-bound evidence（HEAD/tree/status/exit/输出 SHA/digest/时间区间） |
| Consumed by | 首次 `dualctl run` 前 verify；summary/report 只投影 typed evidence |
| Enforced before | 首次业务 Agent execution；probe 提前但不替代 occurrence |
| Failure route | non-zero/signal/identity 不符/tracked dirty/evidence 缺失 → INVALID，run/autorun=0；legacy → not_configured/false 显式降级 |
| Cannot-bypass test | T1-T7（identity/check-execution/git-boundary/sequencing/TOCTOU/legacy/benchmark）+ P2-1 quoting case |
| Residual path | verify→lock 窄窗口（单 operator 纪律承担）；legacy 无 workspace proof 的显式标注；绕开 harness 直跑判 INVALID |

## 判定

- **Boundary Review: PASS**
- **Implementation READY: YES**（随附 P2-1 一条测试、P2-2 一行引用，均可在实施时顺带完成，
  不需重返计划评审）
- **文件清单与 cannot-bypass tests：完整**（8 文件 + T1-T7；补 P2-1 后闭合）
- **成熟度限定正确**：configured capability = `ENFORCED WHEN CONFIGURED / NOT PROVEN`；
  legacy no-check = `COMPATIBLE / WORKSPACE PROOF NOT CONFIGURED`；Darwin hermeticity 归
  target；`9→8` 仍 `NOT PROVEN`——§11 与要求一致。

## 下一步授权

按 §10 顺序实施（focused helper test → 两份 runner 测试 → shell suite → diff-check → 独立
diff review → 选择性提交）；全程零 Java/workflow/schema/provider 变更；不制造第二个 Darwin
样本；下一次自然且配置了 workspace check 的 real-case 完整完成后才评估 `PROVEN`。
