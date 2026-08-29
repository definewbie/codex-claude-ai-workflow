# Accepted Delivery Candidate 实施计划

日期：2026-08-28  
状态：`IMPLEMENTED / DETERMINISTIC ENFORCED / DIFF REVIEW REQUIRED / REAL-CASE NOT PROVEN`

## 1. 目标与边界

本 checkpoint 只关闭一个 P0 lifecycle integrity 缺口：

> 任何 commit、push、PR 或 original repository 写入，都必须消费并严格匹配 final human acceptance 实际批准的 immutable delivery candidate；不得在批准后重新把“当前 worktree”解释成批准对象。

不处理 D2 completion projection，不处理 E1 Bash/network sandbox，不新增 workflow node、module、通用 evidence graph、自动 commit 或自动 PR。

## 2. Sweep 结论

### 2.1 当前 authoritative path

```text
acceptance.generate_final_report
  -> delivery.prepare_pr
       仅生成 commit-message.txt / pr-body.md
  -> gate.human_acceptance
       GateDocument.subject_input_refs 仅绑定上述两个 Markdown ref
  -> workflow terminal / handoff
  -> CLI DeliveryMergeCommand 或 server DeliveryViewService
       各自扫描 runs/**/acceptance-codex-review.contract.json
       各自重新 preview 当前 source worktree
       各自 apply 到 target
```

当前 human gate 没有批准 patch bytes、accepted/excluded path sets 或 expected Git tree。CLI/server 的 acceptance reader 也是按目录排序取最后一份 sidecar，不是 routed lineage。

### 2.2 producer / consumer / sibling 清点

| 类别 | 当前 owner/消费者 | 判定 |
|---|---|---|
| candidate producer | 不存在；`DeliveryPrepareHandler` 只写 Markdown | 必须新增 |
| Git capture/apply | `GitPatchMergeService` | 保留为唯一 Git plumbing owner；支持 temp index |
| target merge orchestration | CLI 与 server 两份独立实现 | 收敛到 runtime application service |
| merge lifecycle | `DeliveryMergePlan` / `DeliveryMergeStatus` | 保留；激活既有 `COMMITTED` |
| human decision | `GateDocument` / `RoutingEngine.resolveOpenGate()` | 保持 SoT；直接消费 gate subject binding，不新增 locator |
| CLI projection | merge/status/summary/trace | 消费共享 service/read result，不重算 authority |
| API/UI projection | controller/service/console/management overview | 消费共享 service/read result，不重算 authority |
| workflow siblings | full、contract、terminal-delivery、small-profile fixture 均为 prepare -> acceptance gate | 零 graph 变化；加一致性测试 |
| legacy delivery | `delivery commit`、`delivery create-pr` 可直接 add/commit/push | 未经 gate candidate，必须删除或收敛 |

### 2.3 新发现的 bypass

`DeliveryCommitCommand` / `DeliveryCreatePrCommand` 及其 runtime handlers 会直接对 source worktree 执行 `git add -A`、commit、push 和 `gh pr create`。它们：

- 不读取 final human gate；
- 不绑定 candidate；
- 不在四个 bundled workflow graph 中；
- 仍被 CLI advertised handler catalog、workflow defaults 和文档公开声明为支持能力；
- 写独立 `delivery/delivery.json`，形成与 `merge-plan.json` 并行的 delivery lifecycle。

这不是 D1 之外的功能，而是同一“外部交付副作用”边界的 sibling bypass。计划选择删除该 legacy family，而不是为 source-branch commit/PR 再实现第二套 candidate apply 协议。custom workflow 若仍声明这两个 handler，将在 definition validation 阶段 fail-visible；不保留静默兼容。

## 3. Owner 模型

### 3.1 Definition / Instance / Execution

- Definition：workflow 仍只声明 `delivery.prepare_pr -> gate.human_acceptance`。
- Instance：content-addressed `DeliveryCandidateDocument` 是一次 delivery candidate occurrence。
- Human decision：resolved `GateDocument` 是“人批准/要求修订”的唯一 SoT。
- Resolution：`AcceptedDeliveryCandidateResolver` 从 authoritative event/node/gate history 机械选择最终 acceptance occurrence；不写 projection，不按 mtime 或目录顺序猜 latest。
- Execution：`DeliveryMergePlan` 记录 candidate 应用到 target 及人工 commit finalize 的生命周期。
- Adapter：`GitPatchMergeService` 只提供 Git capture/apply/commit-inspection，不判断 workflow 或 human decision。

### 3.2 唯一消费链

```text
source Git facts
  -> GitPatchMergeService.capture(temp index)
  -> immutable candidate + immutable patch
  -> prepare outputRefs
  -> GateDocument.subject_input_refs
  -> human approve
  -> AcceptedDeliveryCandidateResolver(resolved gate history)
  -> DeliveryApplicationService
  -> target write
  -> operator commit
  -> finalize(commit SHA)
  -> DeliveryMergePlan.COMMITTED
```

## 4. Durable documents

### 4.1 `DeliveryCandidateDocument`

最小字段：

- `schemaVersion`、`workstreamId`、`producerNodeInstanceId`、`iterationId`；
- source workspace id/effective workdir；
- `baseCommit`、`baseTree`；
- sorted `acceptedFiles`、sorted `excludedFiles`；
- immutable binary patch ref + SHA-256；
- `expectedResultTree`；
- commit-message ref/SHA、PR-body ref/SHA；
- `createdAt` 仅审计，不参与语义比较。

candidate occurrence artifacts 先写入由 ledger node identity 唯一确定的
`delivery/candidates/<producer-node-instance-id>/`；该目录名不依赖 candidate 内容。candidate 文件为同目录下
`candidate-<candidate-sha256>.json`，文件名中的 SHA 必须与 candidate 文件 bytes 一致。candidate 内只引用同一
occurrence 目录下的 patch/presentation artifacts，因此不形成“candidate 内容包含自身 SHA”的循环 identity。

现有 `delivery/commit-message.txt` 与 `delivery/pr-body.md` 可继续作为 operator/report compatibility projection，但
candidate 必须绑定 occurrence 目录中的 immutable copies。稳定路径文件不得被 gate、apply 或 finalize 当作 authority；
两者内容漂移只能影响展示，不能改变已批准 candidate。

候选生成必须先把所有 artifact 写入临时 occurrence 目录，完成逐项 SHA 与 candidate SHA 计算后，再原子发布最终
occurrence 目录及 content-addressed candidate 文件。失败不得留下可路由 candidate ref。

### 4.2 accepted candidate resolution

不新增 `accepted-candidate.ref.json`。`AcceptedDeliveryCandidateResolver` 只读 `events.jsonl`、对应
`NodeInstanceDocument` 与 `GateDocument`：

1. 从 ledger `node_routed` event 顺序定位全部 `gate.human_acceptance` occurrence；
2. 取 event 顺序最后一个 acceptance occurrence，而不是按文件名、mtime 或目录迭代顺序取“最新”；
3. 该 gate 必须 `resolved`，`human_action` 必须是 definition 对应的 canonical approval action；若最后 occurrence 为
   `revise`、open、缺失或损坏，均不得回退更早的 approved gate；
4. `subject_input_refs` 必须含恰好一个 canonical candidate ref；candidate 文件名 SHA、文件 bytes、patch/artifact SHA
   必须全部重验；
5. node/gate/workstream/iteration lineage 必须互相匹配。

这避免给 human transition 增加新的多文件写入和 R1 崩溃窗口。GateDocument 仍是唯一 human authority，resolver 只是
deterministic read model。删除 candidate、伪造 gate ref 或把较早 approval 放在较晚 revise 前都必须 fail closed。

### 4.3 `DeliveryMergePlan`

在既有文档上增加 candidate ref/SHA、base commit/tree、expected result tree；`READY/APPLIED/FAILED/COMMITTED` 保持唯一 application lifecycle。增加 `withCommitted(commitSha)`，禁止只写 `COMMITTED` 而不带经验证 SHA。

旧 workstream 没有 candidate/gate binding 时，application read result 返回 reason code `LEGACY_NOT_BOUND` 且不得
merge；它不是新的 `DeliveryMergeStatus`，也不写伪造的 merge plan。不扫描 runs 或当前 worktree 构造兼容 candidate。

## 5. Git capture 与应用

### 5.1 capture

`GitPatchMergeService.capture(source)`：

1. 验证 source 为 Git worktree，读取 HEAD commit/tree；
2. 用 `git status --porcelain=v1 -z --untracked-files=all` 分类完整状态；
3. exclusions 沿用现有 `.agent/`、`.serena/`、build output、log 规则；accepted/excluded 两集合排序后都进入 identity；
4. 建立临时 index，以 `GIT_INDEX_FILE` 执行 `read-tree HEAD`；
5. 对 accepted paths 执行 pathspec-safe `git add -A`；rename 必须同时包含 old/new path；
6. 生成 `git diff --cached --binary HEAD` 与 `git write-tree`；
7. 成功和异常后 source index bytes/entries 均不变。

untracked text/binary、空格、rename、delete、mode change 都是正常 candidate 内容，不再要求 operator 预暂存。

### 5.2 excluded semantics

- excluded path set 本身参与 candidate identity；内容不进入 patch/tree；
- temp index 中 excluded path 保持 base 状态；base 不存在则仍不存在；
- apply 前重捕获 source，accepted/excluded 两组 path set、patch SHA 与 result tree 必须全部相同；
- post-approval accepted 内容变化，以及 accepted/excluded path set 的新增、删除或重分类，均视为 drift；
- excluded 内容本身不属于批准或交付 bytes；只要 path classification 集合不变，其内容变化不触发 target write，也不被复制；
- target clean 只检查 target，不因 source 中已声明 excluded paths 阻塞。

### 5.3 target apply

`DeliveryApplicationService.apply()` 固定顺序：

1. 获取 workstream lock；
2. workflow current 必须 `COMPLETED`；
3. completion audit 必须 `ready`；
4. resolver 选出的 resolved GateDocument、candidate/patch SHA 全链重验；
5. 重捕获 source 与 approved candidate 完全一致；
6. target 必须 clean，HEAD commit/tree 与 candidate base 一致；
7. 先写 `READY` plan；
8. apply immutable candidate patch 到 target index；
9. target staged tree 必须等于 `expectedResultTree`；
10. 成功写 `APPLIED`，失败写 typed `FAILED`，并保留 stdout/stderr。

任何 target write 之前必须完成 1-6。不得自动更新 candidate、使用 current preview 替代 candidate，或自动 commit。

### 5.4 finalize

`finalize(commitSha)`：

- plan 必须 `APPLIED`；
- commit 必须存在，target HEAD 必须等于传入 SHA；
- commit parent 必须等于 candidate base commit；
- commit tree 必须等于 candidate expected result tree；
- target index/worktree 不得含未记录变更；
- 验证通过后写 `COMMITTED + commitSha`。

错误 parent、额外文件、缺文件、amend 漂移或未知 SHA 均拒绝。operator 仍自行执行 commit。

## 6. Shared application service 与端口

### 6.1 模块归属

- `dual-adapter-git`：Git commands 与 typed capture/apply/inspect result；不依赖 ledger。
- `dual-ledger-file`：candidate/merge-plan documents、paths、atomic writers。
- `dual-runtime`：`DeliveryApplicationService`，依赖 ledger + Git adapter，拥有 guard/transition 顺序。
- `dual-cli` / `dual-server`：参数、HTTP/UI 与 read projection；不得读取 runs 选择 acceptance，也不得直接调用 Git adapter。

`dual-runtime` 增加对 `dual-adapter-git` 的单向依赖；adapter 仍只依赖 core，无循环，不新增 module。

### 6.2 CLI/API/UI

- `delivery merge` 委托 shared service；保留 `--target` 但必须与 ledger original repo identity 一致；
- 新增 `delivery finalize --commit <sha>`；
- server 增加 finalize endpoint；console 在 `APPLIED` 时显示 commit SHA 输入/确认动作，在 `COMMITTED` 后只读；
- CLI/server 对相同 failure 使用同一 typed reason；UI 不自行判断可 merge/finalize；
- status/summary/attention 继续读取 merge plan；更广的 projection 统一留给 D2。

### 6.3 删除 legacy sibling

删除：

- `DeliveryCommitCommand`、`DeliveryCreatePrCommand`；
- `DeliveryCommitHandler`、`DeliveryPrCreateHandler`；
- 仅被它们使用的 `DeliveryLedgerWriter`、`DeliveryReportUpdater`；
- CLI subcommands/handler catalog entries；
- `WorkflowDefinitionLoader` 的 `delivery.create_pr` defaults；
- 对应 tests 与把它们声明为现行能力的 docs。

保留 `commit-message.txt` / `pr-body.md` 作为 candidate presentation artifact。若未来重新支持 push/PR，必须消费同一 accepted candidate 和 finalized commit，不得恢复直接 `git add -A` 路径。

## 7. 文件范围

### 7.1 Production

- `dual-adapter-git`: `GitPatchMergeService`；
- `dual-ledger-file`: candidate document + writer、`DeliveryMergePlan*`、`WorkstreamPaths`；`RoutingEngine` production 行为不改，仅以 integration test 证明既有 subject ref 传递；
- `dual-runtime`: `pom.xml`、`DeliveryPrepareHandler`、新 `DeliveryApplicationService`、`HumanReviewGateHandler`；删除 legacy delivery handlers/helpers；
- `dual-adapter-acpx`: handler registry factory 删除 legacy handlers；
- `dual-cli`: merge delegation、finalize command、delivery command/catalog、status/summary hints；删除 legacy commands；
- `dual-server`: delivery service/controller、console projection/action；
- `docs`: delivery/reference/features/quickstart 中的现行命令说明。

四个 workflow JSON 不修改。若实现需要修改 graph，立即停止并回到架构评审。

### 7.2 Tests

1. Git adapter：untracked text/binary、spaces、rename/delete/mode、source index 不变；
2. prepare handler：content-addressed candidate，artifact SHA，四 workflow sibling routed ref；
3. ledger/runtime resolver：按 event 顺序选择最后 acceptance occurrence；最后为 approve 时通过，最后为 revise/open、伪造/缺 candidate 时拒绝且不回退较早 approval；
4. application service：post-approval accepted mutation、excluded path-set drift、target dirty/base drift、patch tamper、删除 candidate 全部在 target write 前失败；另证 excluded-only 内容变化既不进入 patch也不污染 target；
5. application service：exact APPLIED tree 与 finalize PASS；wrong parent/tree/SHA/head drift 失败；
6. CLI/server parity：同 fixture 的 status/reason/candidate identity 一致；
7. UI/API：APPLIED 只允许 finalize，COMMITTED 只读；
8. subtraction sweep：legacy command/handler/default/delivery.json writer 在 main/test/docs 现行能力中零残留；custom definition 声明旧 handler时 validation fail-visible；
9. full reactor + `git diff --check`。

## 8. End-to-end invariant

| 维度 | 证明口径 |
|---|---|
| Declared by | content-addressed candidate + acceptance gate subject binding |
| Proven by | patch/artifact SHA、base commit/tree、expected result tree、resolved GateDocument |
| Consumed by | 唯一 `DeliveryApplicationService` |
| Enforced before | target apply、commit finalize，以及任何保留的 delivery external side effect |
| Failure route | typed merge-plan `FAILED`，target write 前零副作用；legacy 缺 binding 为 `LEGACY_NOT_BOUND` |
| Cannot-bypass | mutation/tamper/delete/parity/finalize/subtraction tests |
| Residual | apply 后至人工 commit 之间仍由 operator 管理；finalize 检测而非事务锁定该窗口 |

## 9. 实施顺序与止损

1. 先实现 candidate capture + deterministic tests；
2. 再接 gate subject binding 与 history resolver；
3. 再实现 shared apply/finalize；
4. CLI/server/UI 委托；
5. 删除 legacy sibling；
6. focused tests、full reactor、diff-check；
7. 独立代码级 diff review；
8. PASS 后选择性提交。

止损：若 sweep 后发现 accepted candidate 不能在不改 workflow graph 的前提下进入 gate subject refs，或删除 legacy delivery 有已验证的当前生产消费者，则停止实施并回到 bounded comparison；不得保留旁路或增加兼容 fallback。

## 10. 成熟度

- 当前：delivery human-to-bytes binding `NOT MODELED`；现有 merge `PROVEN TO APPLY / NOT AUTHORITY-SAFE`。
- deterministic checkpoint PASS 后：`ENFORCED / NOT REAL-CASE PROVEN`。
- 只有新的自然 real-case 经 candidate approval、apply、manual commit、finalize 全链成功后，才可标 `PROVEN`。

## 11. 2026-08-29 Implementation Result

- `delivery.prepare_pr` 现在以临时 Git index 捕获 tracked/untracked/binary/rename/delete/mode change，发布
  content-addressed candidate occurrence；source 真实 index 不变；Git 子进程 stdout/stderr 并发排空，避免大 patch
  管道互锁。
- final human acceptance packet 强制重验并展示唯一 candidate ref/SHA、expected tree、accepted/excluded paths；既有
  RoutingEngine outputRefs -> gate inputRefs/subject refs 传递仍是唯一绑定路径，workflow graph 零修改。
- `AcceptedDeliveryCandidateResolver` 按 event 顺序选择最后 acceptance occurrence；revise/open/损坏不得回退早期
  approve；candidate、artifact 与 producer/gate/workstream/iteration lineage 全链重验。
- `DeliveryApplicationService` 成为 target apply/finalize 唯一 owner；CLI/server/UI 只消费共享 View；失败码写入
  `merge-plan.json`，进程重启后保持同一投影。source drift、excluded path-set drift、target/base/tree drift、candidate
  tamper/delete 均 fail closed。
- 删除 `delivery commit` / `delivery create-pr` command、handler、registry/default 与平行 writer；生产/测试资源对
  旧 handler id 零命中，custom definition 会因 missing handler fail-visible。
- focused boundary slice：ledger `2/0/0/0`、Git adapter `5/0/0/0`、runtime `78/0/0/0`、registry
  `2/0/0/0`、CLI `7/0/0/0`、server `4/0/0/0`。
- 非沙箱 full Maven reactor：`1268/0/0/0`；`git diff --check` 在最终文档收口后执行。

成熟度：accepted delivery candidate boundary `ENFORCED / NOT REAL-CASE PROVEN`。下一步仅允许独立代码级 diff
review；review PASS 前不提交、不启动 provider/real-case，也不进入 D2/E1 production 实施。

### 11.1 Independent Diff Review

独立代码级 diff review：`PASS（P0=0 / P1=0 / P2=2 知会）`。评审独立追踪 candidate 生成、gate subject
绑定、event-ordered final acceptance resolution、apply/finalize、CLI/API/UI 委托与 legacy subtraction；独立复跑
focused `78/0/0/0`、full reactor `1268/0/0/0`，`git diff --check` clean。

两项 P2 均不影响 D1 authority：历史 `delivery.json` 只读残迹结转 D2；apply 从 verified candidate 重建稳定
commit-message/PR-body 仅是展示 projection。D1 满足选择性提交条件；成熟度保持
`ENFORCED / NOT REAL-CASE PROVEN`。
