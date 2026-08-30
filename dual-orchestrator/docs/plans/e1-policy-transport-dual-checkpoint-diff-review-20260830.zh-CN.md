# E1 Policy Transport — Dual Checkpoint 独立代码级 Diff Review

日期：2026-08-30
评审对象：工作树相对 `f1c4e925` 的全部未提交变更（Dual adapter 5 文件 + Doctor + 3 scripts + 文档 + .gitignore +
`tools/acpx-e1` 修订版 manifest/patch），入口为 implementation plan 与
`e1-policy-transport-dual-checkpoint-result-20260830.zh-CN.md`，结论均自行核实。
评审方式：只读。未修改 production、未调用 provider、未恢复/创建 real-case、未提交。已执行 Maven 测试、shell 测试与
只读 grep/diff；外部仓库依赖安装仍按此前权限约束不执行（以 installer 本机审计工件间接核实 upstream suite）。

## 0. 独立核实结果

| 检查项 | 方法 | 结论 |
|---|---|---|
| fork 修订完整性 | 新旧 patch 各自 apply 于 `fd173f04` 干净 worktree 后 diff 全树 | 仅 `owner-env.ts` + 其测试两文件差异——恰为上轮 P2-2 修复（畸形 binding 由静默跳过改 throw），无其他变更 |
| manifest 修复 | 直接读 manifest + `git ls-remote`/npm pack SHA | P1-1（openclaw URL）与 P2-1（tag object + npm tarball SHA 入 manifest）均已修复；patch SHA `304577dc…` 三处一致（manifest/磁盘/结果文档/Doctor 常量/configure 常量） |
| patch 可应用性 | 新 worktree `git apply --check` | ✅ CLEAN |
| E1 激活面 | 解析 3 个 bundled workflow JSON | 全部 claude 节点均带 named `permission_profile`（contract_discovery/observe/implement）——bundled 工作流无静默 legacy 缺口 |
| policy writer | 通读 `ClaudeExecutionPolicy.java` | 固定值生成（strict allowlist、空 domains、无 local binding/unix socket、固定 11 deny 名、sandbox 四布尔、setting_sources=[]）；原子发布；写后立即自验（realpath/SHA/lineage）；无 secret |
| transport 强制消费 | `AcpxAgentTransport` diff + fake-acpx 行为测试 | `addExecutionPolicyArguments` 覆盖 prompt 路径与 `ensureSession`（rebuild=close+ensure 继承）；**每次 acpx 命令前重验 binding**；Codex 携带 binding 即 throw；approveAll 即 throw |
| handler 门控 | `AcpxAgentTaskHandler` diff | E1 激活 = claude agent + named profile；approveAll 抛 `NETWORK_AUTHORITY_NOT_CONFIGURED`（与 transport 双层）；policy 在 provider 调用前写入并传入主/correction/governed 三条请求路径 |
| run.json 投影 | `AgentRunDocument/AgentRunMetadataWriter` diff | additive `executionAuthority`：有绑定为 `{schema, policyRef, policySha256, POLICY_BOUND}`；legacy 为 `{NETWORK_AUTHORITY_NOT_CONFIGURED}`；无 secret value |
| env wrapper | `acpx-claude-agent-env.sh` diff + shell test | 8 键固定 provider allowlist（ANTHROPIC_BASE_URL/AUTH_TOKEN/API_KEY/MODEL/DEFAULT_×3/CLAUDE_CODE_MODEL——上轮"枚举清单"要求已落实）；NUL 分隔无 eval；unset 全部 8 个 proxy 变量 |
| installer | 通读 `install-acpx-e1.sh` + 本机 `.tools/acpx-e1/audit/` | patch SHA 先行校验 → clone+checkout+tag object/commit 双核验 → npm tarball SHA 交叉核验 → `apply --check` → `pnpm test` 全量（日志落盘）→ 测试后注入 identity → 重建 → staging 原子换入；审计工件在盘且 `build-info.json` 三元组与 pin 逐一吻合，log 含 `tests 973` |
| configure/Doctor | 两文件 diff + 测试 | 三元组逐字校验；缺 build-info/mismatch/官方 acpx → `NETWORK_AUTHORITY_NOT_CONFIGURED` FAIL；Doctor 与 transport 使用同一 `DUAL_ACPX_EXECUTABLE` 解析 |
| 测试复跑 | 本评审执行 | focused **95/0/0**（handler 45、policy 3、transport 36、doctor 11）；full reactor **1294/0/0/0** BUILD SUCCESS（与结果文档一致）；`git diff --check` clean；`bash -n` OK；env allowlist shell test PASS |
| 结果文档声明 | 逐项对盘 | upstream `973/0/0/0` 经审计 log 间接证实；"sandboxed reactor 两条进程树测试因 sysctl 环境差异报错"未计入 PASS 的处置诚实（本评审 clean reactor 未复现该错误） |

## 1. Findings

### P0

无。

### P1

无。

### P2

**P2-1 policy 的 filesystem scope 把 system `java.io.tmpdir` 作为 scratch，先于 proof #7 固化。** E1 enforcement 计划
§4.1/proof #7 要求"优先 run-owned scratch"并在 proof 阶段强制二选一；transport checkpoint 已把
`[workspaceRoot, java.io.tmpdir]` 写进固定 policy。这不是 transport 缺陷（sandbox 生效前 filesystem 声明无强制力），
且与 OKX incident 的 `/tmp` 用法兼容；但应把"system tmpdir vs run-owned scratch"登记为 proof #7 的**未决输入**，
防止后续把当前选择当作已验证结论。另注：`java.io.tmpdir` 是 JVM 属性，测试与运行环境可能不同值（writer 按 realpath
取，行为一致但 scope 随环境变）。

**P2-2 "官方 acpx + E1-enabled run" 的 fail-closed 依赖 commander 对未知 flag 报错，无 Dual 测试钉住。** 未装
fork、未设 `DUAL_ACPX_EXECUTABLE` 时，E1-enabled run 会向官方 acpx 传 `--claude-execution-policy-*` → commander
unknown-option 报错 → run 失败（fail-closed 成立），检测由 Doctor 承担。建议在结果文档 residual 中显式登记该行为
（"官方 acpx 下 E1-enabled run 硬失败"），或以最小 fake（模拟 unknown-flag 拒绝）补一条测试。

**P2-3 installer 审计工件位于 git-ignored `.tools/`。** upstream 全量测试证据只存在于本机；fork 评审 P2-3 的"落盘
可审计"在磁盘层面满足、在可提交层面不满足。由于 patch+manifest 已入库、Doctor 可随时确定性复核 identity、CI/他人
重跑 installer 可再生产证据，可接受；建议在结果文档注明该限制。

**P2-4（知会）`DUAL_ACPX_EXECUTABLE` 为无校验 env override。** 指向任意可执行文件时 transport 会直接使用。操作者
与 Dual 属同等信任级（可改 PATH），不构成威胁模型内旁路；Doctor 是运行前检测面。无需修改，登记即可。

## 2. Boundary Review

| 维度 | 判定 |
|---|---|
| **policy ref/SHA 在 provider side effect 前唯一、强制消费** | ✅ 三重强制：handler 在 provider 调用前 write+self-verify；transport 在**每次** acpx 命令（ensure/prompt/rebuild）前 `binding.verify()`（realpath containment + 文件存在 + SHA 重读 + workstream/node/run lineage 全等）；fork 在 `session/new` 前再验 schema/SHA/固定值。行为测试断言无效 binding 时**acpx 调用日志文件根本不存在**（零副作用）。唯一 policy owner 是 occurrence 文件；session record/run.json 只存 binding 投影 |
| **ensure/new、prompt、correction、queue/reload 同一 occurrence binding** | ✅ 同一 `Binding` 对象传入主请求与 correction 请求；fake-acpx 行为测试断言每条 policy-bearing 命令行都携带相同 ref/SHA、ensure 存在、prompt 出现 ≥2 次（主+correction）；fork 侧 queue owner/cold reload/clone 上轮已验证保真（本轮 patch 修订又将 owner-env 畸形 binding 改为 throw） |
| **legacy/Codex/approveAll/删除篡改/错误 fork identity fail-closed** | ✅ legacy（无 named profile）→ null policy + `NETWORK_AUTHORITY_NOT_CONFIGURED` disposition，不冒充 ENFORCED；Codex 双层拒绝（handler 不生成 + transport throw + fork throw）；approveAll 双层 throw（handler/transport）；删除/篡改/lineage 漂移由 `Binding.verify` 与 `ClaudeExecutionPolicyTest.rejectsDeletionTamperingAndLineageDrift` 覆盖；错误 fork identity 由 Doctor 三元组逐字校验 + `rejectsOfficialOrMismatchedAcpxBuildIdentity` 覆盖；官方 acpx 情形见 P2-2 |
| **installer/env/Doctor 无绕过、无 secret 泄漏、无第二配置 owner** | ✅ installer 五重校验（patch SHA/git tag object+commit/npm tarball/apply --check/全量测试）后才注入 identity；env wrapper 8 键 allowlist + proxy 全清 + 无 eval，secret 不进 policy/manifest/命令行/日志（policy 仅固定字段；`metadata()` 只含 ref/SHA/schema/disposition）；Doctor/configure 与 transport 同源解析 executable。`.tools/` 为安装产物（ignored），配置 owner 仍只有 `~/.acpx/config.json`（adapter 命令，既有模式）与 occurrence policy 文件——无新增第二配置面 |
| **最小范围** | ✅ 变更面 = adapter 5 文件 + Doctor + 3 scripts + .gitignore + 文档；workflow JSON/ledger/server/UI/AgentResultEnvelope/TaskContract 零改动（git status 全量核对）；`AgentRunDocument` 仅 additive 一字段；fork patch 修订最小（2 文件）；无 platform/DSL 扩张 |
| **成熟度诚实** | ✅ `E1 POLICY TRANSPORT ENFORCED / SANDBOX EFFECT NOT PROVEN` 与代码事实一致：transport（写入→验证→flag→fork→`_meta`）全链 deterministic 证明；sandbox/网络真实效果、Q4-Q15、OKX 恢复均显式排除；无冒充 |

## 3. End-to-End Invariant（经代码与测试核实）

| 维度 | 事实 |
|---|---|
| Declared by | named `permission_profile`（E1 激活 owner）+ immutable occurrence policy 文件（`runs/<run-id>/execution-authority/claude-execution-policy.json`，固定 schema） |
| Proven by | `ClaudeExecutionPolicy.write`（固定值+原子发布+自验）→ `Binding.verify`（每命令前 realpath/SHA/lineage 重验）→ pinned fork 三元组 build identity（Doctor/configure/installer 三方校验） |
| Consumed by | Dual transport flags（`--claude-execution-policy-ref/sha256`）→ acpx session binding（含 queue/cold reload）→ `session/new._meta.claudeCode.options`（settingSources=[] + sandbox + managedSettings） |
| Enforced before | acpx session creation 与 prompt process；无效 binding 时 acpx 调用为 0（行为测试断言日志文件不存在） |
| Failure route | handler/transport IOException（Codex、approveAll、删除、篡改、lineage 漂移、越界）→ run typed 失败；fork 侧 `CLAUDE_EXECUTION_POLICY_CONFLICT`（session 复用异 binding）；legacy → `NETWORK_AUTHORITY_NOT_CONFIGURED`（run.json + Doctor 双投影） |
| Cannot-bypass test | delete/tamper/lineage/outside-workspace（policy 3 测）、invalid-before-side-effect、Codex+approveAll、ensure+prompt+correction 同 binding（fake acpx 行为测）、session conflict 与 queue reload（fork 测，上轮已验）、build identity exact/official/mismatch（doctor 3 测）、env allowlist shell test |
| Residual | sandbox/网络真实效果未验证（Q4-Q15 另立 Verification Gate）；filesystem scratch 选择待 proof #7（P2-1）；官方 acpx 下 E1-enabled run 硬失败未钉测试（P2-2）；Codex 网络不在本轮；OKX 恢复未授权 |

## 4. 选择性提交判定

**满足，可选择性提交。** 前序 fork 评审的全部发现（P1-1/P2-1/P2-2）已在本轮 delta 中真实关闭且经独立核实；
implementation plan §9.3-9.5 的 Dual 侧步骤完成：failing-first fake tests → writer/request/transport/metadata →
scripts → focused（95/0/0 本评审复现）→ full reactor（1294/0/0/0 本评审复现）→ shell/diff-check → 本 diff review
（第 9.6 步）。建议提交信息涵盖：E1 policy transport 双层强制消费、approveAll/legacy NOT_CONFIGURED、env
allowlist、installer 五重校验与审计、fork patch 修订（owner-env throw）。P2-1/P2-2/P2-3 为提交后跟进项（登记到
active plan），P2-4 知会。

## 5. 准确成熟度

- **本 checkpoint 声明上限**：`E1 POLICY TRANSPORT ENFORCED / SANDBOX EFFECT NOT PROVEN` —— 准确。transport 链
  （occurrence policy → 双重验证 → acpx flag → fork binding → `_meta`）已由 deterministic 测试闭环证明；
  sandbox/network/WebSearch 共存/tool env isolation 均未验证、未声明。
- **下一 Gate**：按原计划离线重放 capability proof Q1-Q3（不调 provider）；Q4-Q15 需单独 Verification Gate（唯一
  provider 接触点）。OKX workstream 恢复仍被禁止；不得把 transport PASS 解释为网络控制 PROVEN。
- acpx fork：`VALIDATED`（patch 双 worktree 应用验证 + upstream 973 测试审计）→ 由 installer+Doctor 维持
  `ENFORCED` 的 build identity 门。

本次评审未修改 production、未调用 provider、未恢复或创建 real-case、未提交。
