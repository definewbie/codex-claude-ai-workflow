# 首个完整 Real-Case 后的共性边界统一修复计划

日期：2026-08-28  
状态：`ARCHITECTURE REVIEW PASS / D1 SWEEP COMPLETE / D1 IMPLEMENTATION PLAN IN REVIEW / NO PROVIDER AUTHORIZED`

## 1. 决策摘要

`ws-20260828T054556Z-AHBV` 已经完整运行到 `workflow/current.json.status=COMPLETED`，产品变更经独立审查与
`100/0/0/0` 测试后提交为 `captcha-auth-app@c2e74f9`。这个结果证明 Dual 能完成一条真实的 human-in-the-loop
主链，但也暴露出三组共性边界缺陷：

1. **交付副作用没有绑定最终人工批准的精确变更集**：新文件被 merge preview 拒绝；operator 暂存后才能继续；merge
   又重新读取当下 worktree，而不是消费 human acceptance 所批准的 immutable change-set identity。
2. **同一 durable fact 在 CLI、UI、report 中被各自重新解释**：prepared delivery 被显示为 unavailable；self-test 明明存在，
   final report 却显示 not available；`workstream.json` 的创建时字段被投影成当前状态，与 authoritative
   `workflow/current.json` 冲突。
3. **WebSearch human authority 没有覆盖 Bash 子进程网络出口**：exact WebSearch 已能进入 human recovery，但
   `execute` 仍可通过 `docker manifest inspect`、`docker pull`、依赖下载等方式访问网络。

这三组问题不能合并成一个大改动。统一的是 owner 纪律，不是代码 checkpoint：

```text
真实副作用必须消费经批准的 immutable fact
投影必须消费共享 read model，不得重新猜状态
安全边界必须由可机械执行的 sandbox/policy 强制，prompt 只做引导
```

实施拆成三个原子 checkpoint，顺序固定：

1. `D1 Accepted Delivery Candidate`：先关闭会改变目标仓库的交付正确性边界；
2. `D2 Shared Completion Projection`：再统一 delivery/self-test/workflow status 的只读投影；
3. `E1 External Execution Authority`：先做 capability proof，只有 OS 级网络边界可被当前 frozen adapter 强制时才实施。

当前主线锁定分类：`D1/D2 = P0 lifecycle integrity`；`E1 = P0.5 adapter authority blocker`。不开展 P1/P2/P3
功能扩张，不新增 workflow node、通用 evidence platform、规则引擎或第二次 real-case。

## 2. Captured 事实与分类

### 2.1 已确认事实

- workstream：`ws-20260828T054556Z-AHBV`；
- authoritative lifecycle：`workflow/current.json.status=COMPLETED`、`activeNodeInstanceIds=[]`、
  `openGateIds=[]`、`lastEventId=evt-000048`；
- legacy identity document：`workstream.json.status=created`、`currentStageId=stage-design`、
  `currentIterationId=iter-001`，自创建后未更新；
- delivery artifacts：`delivery/commit-message.txt`、`pr-body.md`、`merge.patch`、`changed-files.txt` 均存在；
- `delivery/merge-plan.json.status=APPLIED`，但 `commit=""`；实际 target commit 为 `c2e74f9`；
- 初次 `delivery merge` 因两个合法 untracked test files 被拒；operator 暂存这两个文件后，同一交付才成功应用；
- final report 显示 `Developer Self-Test Summary (not available)`，但至少三个
  `implement-claude-self-test.contract.json` durable artifact 存在；
- workstream summary 显示 `Delivery: unavailable`，但 PR body 与 commit message 已准备完成；
- implementation turn 可通过 Bash/docker 发起网络访问，未经过 exact WebSearch human decision。

### 2.2 不是同一种缺陷

| 现象 | 正确分类 | 不能采用的解释 |
|---|---|---|
| untracked 文件导致 merge 失败 | patch capture 能力缺口 | 要求 Agent/operator 先暂存即可 |
| acceptance 后重新读取 worktree | 交付 authority 缺失，P0 | merge 最终成功所以无风险 |
| merge plan 停在 APPLIED | delivery lifecycle 未闭合 | commit 在 Git 中存在就够了 |
| self-test/report 不一致 | routed evidence projection 缺口 | 扫 runs 目录找最新文件 |
| workstream status 冲突 | immutable identity 与 current projection 混用 | 每次 transition 重写 workstream.json |
| Bash 可访问网络 | execution sandbox 缺口 | exact WebSearch approval 已关闭网络边界 |

## 3. Owner 模型

| Fact | Owner | Lifetime | 唯一消费者/强制点 |
|---|---|---|---|
| workflow 当前状态 | `workflow/current.json` / ledger transition | current projection | CLI/API/UI/status/scheduler |
| workstream 创建身份 | `workstream.json` | workstream immutable identity | locator、repository、workspace identity |
| final accepted change set | 新增 `DeliveryCandidateDocument` | delivery occurrence immutable | final human gate、merge、commit finalize |
| patch 应用/提交状态 | `DeliveryMergePlan` | delivery lifecycle mutable | delivery read model |
| self-test/acceptance 结论 | typed contract sidecar + routed refs | run occurrence immutable | acceptance、final report、completion projection |
| provider tool request | ACP session event | execution occurrence | permission policy / human recovery |
| Bash 网络可达域 | provider sandbox policy | session/execution | OS sandbox before child process network I/O |

`DeliveryCandidateDocument` 是唯一新增领域文档。它不是通用 evidence graph，而是当前模型缺少的“最终人工验收究竟批准了
哪组 bytes”这一事实。已有对象不能承载：acceptance contract 拥有质量决定，不拥有 Git patch bytes；merge plan 拥有应用
生命周期，创建时间晚于 acceptance，不能反向冒充已批准对象。

## 4. Checkpoint D1：Accepted Delivery Candidate

producer/consumer/sibling sweep 已完成，实施计划见
`docs/plans/accepted-delivery-candidate-implementation-plan-20260828.zh-CN.md`。sweep 除确认 CLI/server 的重复 merge owner 外，
还发现公开的 `delivery commit` / `delivery create-pr` 会绕过 human candidate 直接 add/commit/push，并写入平行
`delivery.json` lifecycle；四个 bundled workflow 均不消费这两个 handler。D1 因此必须同时删除该 legacy family，不能只修
original-repo merge 后留下另一条 delivery side-effect bypass。

### 4.1 目标链

```text
delivery.prepare_pr
  -> 从 source worktree + frozen base 生成完整 binary patch
  -> 写 immutable delivery candidate（refs + SHA + file set + base identity）
  -> candidate ref 进入 final human gate 的 subject/input refs
  -> human decision 与 candidate identity 一起持久化
  -> delivery merge 重新 capture 当前 patch
  -> current candidate 与 approved candidate 逐字段、逐 SHA 相等
  -> 仅相等时允许写 target repository
  -> operator commit
  -> delivery finalize 验证 commit 精确包含 approved candidate
  -> merge plan = COMMITTED + commit SHA
```

### 4.2 Candidate 最小字段

- `schema_version`、`workstream_id`；
- source workspace identity、base commit、base tree；
- sorted `changed_files`、`excluded_files`；
- binary patch ref + SHA-256；
- 临时 index `write-tree` 产生的 expected result tree SHA；
- commit-message/pr-body refs + SHA-256；
- gate subject 绑定 candidate ref；candidate 文件名中的 SHA-256 与文件 bytes 必须一致（candidate 文档本身不包含自引用 SHA）；
- `created_at` 仅作审计，不参与 identity。

不得把 Markdown、console 输出、mtime、目录顺序或 source index 状态作为 identity。

`excluded_files` 采用以下精确语义：capture 必须先对完整 source status 做一次分类，sorted accepted set 与 sorted excluded set
都参与 candidate identity；只有 accepted set 进入临时 index、binary patch 与 expected result tree，excluded paths 在临时 index 中
保持 frozen base 的状态（base 中不存在则继续不存在）。merge 前必须重新分类并要求两组 path set 均与 approved candidate 一致；
excluded 文件内容不复制到 target，也不参与 target clean 判定。target clean 只检查 target repository 自身，不能因 source 中存在
已声明且集合未漂移的 excluded paths 而失败；新增、删除或重新分类 excluded path 则视为 candidate drift，必须重新批准。

### 4.3 Git patch capture

`GitPatchMergeService` 改用临时 index 捕获完整变更，不修改 source worktree 的真实 index：

1. 临时 index 从 frozen `HEAD` 初始化；
2. 对 candidate paths 执行 `git add -A` 到临时 index；
3. 从临时 index 生成 `git diff --cached --binary HEAD`；
4. 同一临时 index 执行 `git write-tree`，把预期结果 tree 作为序列化无关的最终 identity；
5. status/path 解析使用 NUL-delimited porcelain，覆盖空格、rename、delete、binary、mode change 与 untracked；
6. source index 在成功和失败后 SHA/entries 均不变。

这会删除“合法 untracked business file 必须预先 staged”的旧限制及其测试，不增加 fallback。

### 4.4 单一应用服务

CLI `DeliveryMergeCommand` 与 server `DeliveryViewService` 当前各自实现 preview、plan、apply。新增一个低层共享
`DeliveryApplicationService`（放在可被 CLI/server 同时依赖的 delivery/ledger 边界），两端只做参数解析与 read-model 投影。

服务必须按固定顺序 fail closed：

1. workflow current 必须 COMPLETED；
2. completion audit 必须 ready；
3. final human decision 必须接受且绑定 candidate；
4. candidate 自身及 patch SHA 必须可重验；
5. source 当前重捕获结果必须与 approved candidate 完全相同；
6. target 必须满足 clean/base identity；
7. 写 `READY` plan 后才 apply；失败写 typed `FAILED`，成功写 `APPLIED`。

任何 post-acceptance 文件变化，无论看似无害，均在 target write 前被拒绝。不得自动“更新 candidate”或沿用旧批准。

### 4.5 Commit finalize

保留 operator 决定 commit message 与执行 Git commit 的边界；Dual 不自动 commit。新增显式 finalize 动作：

- 输入 target commit SHA；
- 验证 merge plan 为 APPLIED；
- 验证 commit parent/base 与 candidate 一致；
- 验证 commit tree 与 candidate 的 expected result tree 完全一致；patch SHA 继续证明应用输入，不用 patch 文本序列化
  代替 Git tree identity；
- 验证通过后写 `DeliveryMergeStatus.COMMITTED` 与 commit SHA；
- commit 混入额外文件、缺文件、amend 后不一致、target HEAD 漂移均拒绝。

现有 `COMMITTED` enum 被真正激活，不新增同义状态。

### 4.6 D1 cannot-bypass tests

1. untracked text/binary、带空格路径、rename/delete/mode change 均进入 patch；
2. capture 前后 source index 完全不变；
3. acceptance 后修改、增加、删除任一文件，merge 在 target write 前失败；
4. 伪造 candidate ref/SHA、替换 patch、遗漏 gate binding 均失败；
5. CLI 与 server 对相同 fixture 得到同一 plan/status/reason；
6. target dirty/base drift 均失败；
7. exact commit finalize PASS；混入额外变更、错误 parent、错误 SHA 均失败；
8. 删除 approved candidate 后，即使 current worktree 看起来正确也必须失败。

## 5. Checkpoint D2：Shared Completion Projection

### 5.1 一个 read model，三类状态分开

新增共享 `CompletionDeliveryReadModel`，由 CLI summary、server API、UI 与 final completion view 共同消费：

- `workflowStatus`：只来自 `workflow/current.json`；
- `workstreamIdentity`：来自 `workstream.json`，旧 status/stage/iteration 只能标为 legacy initial metadata；
- `deliveryPreparation`：`NOT_PREPARED | PREPARED`，由 verified candidate/PR artifacts 判定；
- `deliveryApplication`：`NOT_STARTED | READY | APPLIED | FAILED | COMMITTED`，来自 merge plan；
- `remoteDelivery`：只有 `delivery.json` 真实存在时才显示 commit/PR remote facts；
- `verificationEvidence`：按 routed typed refs 选择 self-test 与 acceptance，不扫描目录猜 latest。

因此 prepared delivery 不再显示为 unavailable；也不会把“PR 尚未创建”误写成“交付材料不存在”。

### 5.2 Final report evidence lineage

`ReportGenerateFinalHandler` 当前只在 final-report node 的 input refs 中按文件名寻找 self-test。修复必须从 workflow/routing
链确认 self-test ref 在 decision、acceptance、final-report 之间如何传递：

- 优先方案：沿既有 `outputRefs -> routed inputRefs` 显式保留 selected self-test contract ref；
- 只有 sibling sweep 证明现有 ref 集合无法表达“最终采用的验证集合”时，才允许增加一个 bounded typed
  `VerificationEvidenceSelection`；
- 禁止扫描 `runs/`、mtime/latest、文件名排序或从 acceptance 自然语言重推断；
- recent outputs 同样读取 typed run/output refs，不因 agent output 改成 JSON 就报告为空。

### 5.3 workstream status 兼容

`workstream.json` 继续作为冻结创建身份，不在每次 transition 重写。所有 current-state projection：

- primary `status/stage/iteration` 来自 workflow current + active node/iteration；
- 如 API 必须兼容旧字段，显式命名为 `legacyInitialStatus` 等，不能作为 UI fallback 的首选；
- scheduler、list、overview、CLI status/trace/watch 与 UI 做 sibling sweep；
- frozen old workstream 没有 workflow current 时，legacy fallback 必须标记来源，不能伪装成 authoritative current。

### 5.4 D2 cannot-bypass tests

1. fixture 同时含 PREPARED artifacts、无 remote delivery，CLI/API/UI 均显示 prepared 而非 unavailable；
2. APPLIED 与 COMMITTED 分别投影，commit SHA 只在 verified finalize 后出现；
3. routed self-test + acceptance 在 final report 均可见；删除 self-test routed ref 后明确 fail/缺失，不扫描目录补回；
4. JSON agent outputs 可被 typed recent-output projection 发现；
5. `workstream.json=created`、`workflow/current=COMPLETED` 时所有 current surfaces 显示 COMPLETED；
6. 两个不同 lifecycle workstream 的 CLI/API/UI 首屏一致，历史 metadata 不覆盖 current state。

## 6. Checkpoint E1：External Execution Authority

### 6.1 已确认的安全事实

exact WebSearch human recovery 只控制 ACP `WebSearch` tool request。它不控制 Bash 子进程。Claude Code 官方文档明确说明：

- Bash 命令参数 pattern 对 URL 限制很脆弱；
- 禁止 WebFetch 不会阻止 Bash 通过 curl/wget 等访问网络；
- 覆盖 Bash 及其全部子进程的网络边界是 OS 级 sandbox + domain allowlist；
- sandbox 不可用时默认可能降级为 unsandboxed，安全 gate 必须使用 `failIfUnavailable=true`；
- `allowUnsandboxedCommands=false` 才能关闭 `dangerouslyDisableSandbox` escape hatch。

参考：

- https://code.claude.com/docs/en/permissions
- https://code.claude.com/docs/en/sandboxing
- https://code.claude.com/docs/en/configuration

一手文档取证日期为 `2026-08-28`。E1 capability assessment 必须同时记录实际 frozen `acpx`、
`claude-agent-acp`、Claude Code 版本、OS 与上述文档 URL/取证日期；文档声明不能代替对该版本组合的经验性 enforcement proof。

因此不能通过维护 `curl|wget|docker|mvn|npm|git` 黑名单、Bash 前缀白名单或 prompt 禁令声明网络边界闭合。

### 6.2 先做 capability proof，不直接写 production policy

用 frozen `acpx + claude-agent-acp` 进行离线/本地 capability assessment，必须回答：

1. Dual 能否为每个 session 注入隔离的 Claude settings，而不读取用户全局可变配置；
2. effective settings 是否可持久化 ref/SHA 到 run occurrence；
3. `sandbox.enabled=true`、`failIfUnavailable=true`、`allowUnsandboxedCommands=false` 是否真实生效；
4. 空 `allowedDomains` 时，curl/wget/docker/git/maven/npm 及脚本子进程是否都被 OS 层阻断；
5. local filesystem edit/test 是否仍可在 worktree 内运行；
6. WebSearch 是否仍产生独立 ACP permission request，可由现有 human authority 精确批准；
7. sandbox/ACP 版本不支持时能否在 provider turn 前 typed fail closed，而不是静默 unsandboxed。

assessment 只使用 fake/local endpoints 与不会访问公开网络的 denial tests；不调用真实 provider。

### 6.3 只有 capability proof PASS 才允许的实现

- 新增 session-scoped `ExecutionAuthorityPolicy`（现有 execution/run metadata 的 typed 子结构，不新建 workflow state）；
- profile 声明 filesystem/write/tool intent；sandbox policy 拥有 network domains；两者不能混成 `autoApproveKinds`；
- 默认 implementation network domains 为空；
- exact public WebSearch 仍默认 ask，经 human decision 后只批准该 request，不把域永久开放给 Bash；
- operator opt-in public repository mode 只允许明确配置的 WebSearch tool/domain，不开放整个 fetch/Bash；
- prompt 说明优先 WebSearch 与查询纪律，但不计入 enforcement maturity；
- run metadata 保存 effective policy ref/SHA、sandbox available/enforced、allowed domains、unsandboxed escape disabled；
- 任一字段缺失或 sandbox unavailable，provider execution 前失败。

### 6.4 Capability proof FAIL 时的决定

若 frozen adapter 无法可靠注入/证明 OS sandbox：

- 不实现 Bash command parser；
- exact WebSearch 继续作为 tool-level authority，但文档明确为 `WEBSEARCH ENFORCED / TOTAL NETWORK NOT MODELED`；
- implementation profile 的 Bash 只能在隔离 worktree、最小 secrets 环境中运行；
- 后续只在两个方向中选择：升级到支持 sandbox policy 的 pinned provider adapter，或把 build/test/network discovery 移到
  harness-owned deterministic executor；二者均需独立 bounded comparison，不在本计划中顺带实施。

### 6.5 E1 cannot-bypass proof

至少验证直接命令、shell chain、变量/替换、脚本子进程、docker、git remote、Maven/npm 下载、sandbox escape 参数；任何一个可绕过
空 domain policy 都使 capability proof FAIL。不得以“模型通常不会这样做”判 PASS。

## 7. End-to-end invariant

| 维度 | D1 Delivery | D2 Projection | E1 Execution authority |
|---|---|---|---|
| Declared by | delivery candidate + gate binding | shared read model contract | session execution policy |
| Proven by | patch/file/base refs + SHA | ledger current + routed typed refs | effective sandbox policy + denial evidence |
| Consumed by | shared application/finalize service | CLI/API/UI/report | provider process launcher |
| Enforced before | target apply 与 commit finalize | operator 展示/完成报告 | provider/Bash child process network I/O |
| Failure route | typed delivery FAILED，无 target write | explicit unavailable/source，不猜测 | typed execution failure / human permission wait |
| Cannot bypass | post-approval mutation、untracked、wrong commit | conflicting fixture、missing routed ref | shell/subprocess/network bypass suite |
| Residual | apply 后、commit 前仍由 operator 管理 | old ledger 仅 source-labelled fallback | TLS/domain allowlist 本身的已知限制 |

## 8. 实施顺序与 review gates

### 8.0 硬前置

当前工作树中的 root-closure/WebSearch permission checkpoint 必须先完成独立 diff review、focused/full tests、选择性提交并恢复
clean SHA。D1、D2、E1 不得叠写到该 dirty checkpoint；否则测试与 real-case 证据无法归因。本计划文档本身可独立提交，但任何
production 实施都受此条件阻断。

### 8.1 D1

1. 完成 producer/consumer/sibling sweep，锁定 candidate 路由点；
2. 独立架构评审 PASS；
3. 实施 candidate、temp-index capture、shared application、finalize；
4. focused tests + full reactor + diff-check；
5. 独立 diff review；
6. deterministic delivery fixture 从 prepare -> human bind -> apply -> manual test commit -> finalize 完整 replay。

### 8.2 D2

D1 clean checkpoint 后再实施。先追踪 final-report 的 routed refs，若 existing refs 足够则禁止新增 typed selector。完成
CLI/API/UI/report sibling sweep、focused tests、reactor 与独立 diff review。

D1 diff review 结转的 sibling 清单：

- `StatusCommand`、`SummaryCommand`、`WorkstreamQueryService` 与 `smoke-test.sh` 仍只读历史
  `delivery/delivery.json`；其 writer 已删除，新 workstream 不再产生该文档。D2 必须把它明确归类为 legacy historical
  projection 或删除，不得让它与 `merge-plan.json` 并列解释当前 delivery 状态。
- `delivery/commit-message.txt` 与 `delivery/pr-body.md` 是从已验证 candidate 重建的稳定展示文件；D2 不得把稳定路径
  当成 acceptance、candidate 或 completion SoT。

### 8.3 E1

可与 D2 的只读分析并行，但 production 实施必须等 capability assessment PASS。assessment FAIL 即止损，不因安全目标正确就制造
不可证明的 command policy。

### 8.4 Real-case

D1/D2 deterministic ENFORCED、E1 有诚实结论且当前 dirty adapter checkpoint 已独立评审并提交前，不启动新的 real-case。
下一次 real-case 只验证：

- approved candidate 与实际 merge/commit 精确一致；
- delivery/self-test/workflow status 多端一致；
- 若 E1 已实现，Bash network denial 与 WebSearch human approval 各自然触发一次；
- 产品任务仍必须完整完成，不能为了触发安全分支人为破坏任务。

## 9. 明确不做

- 不为本次 captcha-auth 文件名、Mailpit、Docker 写特判；
- 不要求 operator 预先 `git add`；
- 不自动 commit 或自动批准 delivery；
- 不扫描目录寻找 latest self-test/acceptance；
- 不同步重写 `workstream.json` 冒充 current ledger；
- 不用 prompt、Bash 字符串黑名单或 URL regex 冒充网络 sandbox；
- 不把三个 checkpoint 合成一次不可归因的大提交；
- 不在现有未提交的 root-closure/WebSearch checkpoint 上叠写，先完成其独立 review/clean commit。

## 10. 成熟度与退出条件

当前状态：

| 能力 | 成熟度 |
|---|---|
| small workflow 完整 real-case | `PROVEN`（单一 Mailpit case） |
| product Mailpit feature | `PROVEN / COMMITTED @ c2e74f9` |
| delivery supports accepted untracked files | `ENFORCED / NOT REAL-CASE PROVEN` |
| accepted bytes bound to target apply/commit | `ENFORCED / NOT REAL-CASE PROVEN` |
| delivery/self-test/current-state projection consistency | `VALIDATED DEFECT / NOT ENFORCED` |
| exact WebSearch human authority | `ENFORCED / REAL-CASE OBSERVED` |
| total Bash/subprocess network authority | `NOT MODELED` |

停止扩张条件：

- D1 若无法在不改变 source index 的条件下生成完整 Git patch，回到 Git object/index 边界设计，不增加 staging fallback；
- D2 若 shared read model 仍需扫描 runs 目录，说明 routed lineage 未闭合，回到 workflow/ref 传递分析；
- E1 若 frozen adapter 无 OS sandbox 注入与 fail-closed 证据，记录 capability gap 并停止，不建设命令解析安全层；
- 后续两个 real-case 未再发现 projection sibling 漂移时，不继续建设通用 projection framework，只保留共享 completion read model。

本计划通过评审前不修改 production，不调用 provider，不创建或恢复 workstream。
