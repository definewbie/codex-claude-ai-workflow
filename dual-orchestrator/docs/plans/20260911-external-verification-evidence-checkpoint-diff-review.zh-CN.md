# 外部验证材料续作 Checkpoint 独立 Diff Review

日期：2026-09-11。评审者：CC（只读；基线 `0cb2a36b` 工作树；未改 production、未调
provider、未恢复 real-case、未启动额外 agent）。方法：逐行读全部未提交生产面（4 个新类 +
14 个修改文件）与两个测试类，沿 prepare→submit→自测→context→acceptance 重建链路，
独立复跑聚焦测试。不采信 checkpoint 自述。

## 0. 总判定

**链路真实闭合，判定：允许提交。** 无 P0/P1；三个 P3 级备注。我独立复跑
ExternalVerificationEvidenceTest（8/0/0/0）+ EvidenceCollectionSequenceTest（7/0/0/0），
与 checkpoint 声明的 15/0/0/0 一致。成熟度边界与自述相符：deterministic sequence 已证，
natural E2E 未证，245D 只读。

## 1. 链路重建（全部代码级核证）

**prepare**（`ExternalVerificationEvidence.prepare`）：锁内 `captureBaseline(..., "submit_evidence")`
（资格=implementation_repair gate + submit 在 allowed_actions；qualified contract；
`EvidenceCollectionSource.capture` 字节身份）→ 内容寻址原子写 `Preparation(baseline, recordedAt)`
（temp+ATOMIC_MOVE+读回复验）→ gate/current 不变。**唯一 owner：baseline 捕获路径与
collect 共用**，无平行模型。

**submit**：requestId/note/declaration 形状校验 → 读准备（ref/SHA/canonical/NOFOLLOW）→
声明一致性（startedAt≥recordedAt、finishedAt≥startedAt）→ `verifySource`（源码未漂移）→
逐文件复制（canonical regular file、realpath==词法路径拒 symlink、8MiB/文件、32MiB 总量、
32 文件、扩展名白名单）→ manifest 内容寻址固化 → **取锁** → `verify()`（准备+manifest+
全部材料 SHA+源码全复验）→ gate 已 COMPLETED 分支：仅当 prior==submission、human_action/
note/gate doc 三方一致**且存在对应 node_routed 事件**才幂等返回，否则"partial evidence
decision; no automatic recovery"；否则锁内重捕获 baseline 必须等于准备 baseline（源码或
gate 漂移即拒）→ `ledger.submitEvidence` → typed `resolveOpenGate(..., collection, submission)`
（**submit_evidence XOR submission 强制**——普通 decide 无法伪造材料；collection 分支校验
baseline.gateNodeInstanceId==当前 gate）→ gate 决定+node `external_evidence` 绑定+按定义路由
至 self-test。**唯一决定 owner：RoutingEngine typed 入口。**

**自测/验收消费**：执行边界（inline `WorkflowRuntime:321` 与 worker `AgentWorker:115` 均调
`EvidenceCollection.execute`）→ `open()` 从 inputTransition marker 回溯 gate（冷读时与
events 的持久化 route 逐条比对，marker 被改即拒）→ Guard 复验 approval/gate SHA、
human_action∈{collect,submit}、submission 与 action 的 XOR 一致、baseline==submission.baseline、
`ExternalVerificationEvidence.verify`（材料+源码全量）+ note 三方一致。prompt（`AcpxAgentTaskHandler:217`
调 `promptInstruction`）列出**精确文件路径**并要求逐 ref 写入 result.evidence。发布侧：
`ArtifactContractValidator` 两处（validateAgentOutput 分支前 + sidecar 写前）调
`verifyResult` —— `evidence.containsAll(refs)` **元素级精确成员判定**，仅作用于
SelfTestResult/AcceptanceReviewResult 且仅当节点有绑定（普通 run 零影响，有反例测试）。

**context→acceptance**：marker 经 `RoutingEngine:412-423` 沿路由传播继承；序列测试加入
真实 full-definition 的 `context.acceptance_codex_verifier_review`，断言 acceptance verifier
的 requiredReferences 与自测**完全一致**、两个 sidecar 的 evidence 均等于同一 ref 集。
提交只路由 self-test（4 个 definition 均仅加一条 normal 边；HumanGateActionSemantics 强制
"恰好一条无条件路由到 self-test 任务"；CHANGE_SCOPE_VIOLATION 源排除 submit/collect）。

## 2. 五个重点的裁定

1. **owner 唯一且被强制消费**：✓（见上）。baseline/材料/决定各有唯一写入者；后继在
   provider 前（Guard）、发布前（verifyResult×2）、acceptance（marker+同 refs）三层消费。
2. **fail-closed**：✓ 六个切面全部有代码路径+测试：源码变化（同 porcelain 反例——字节级
   capture 的必要性在此兑现）、材料篡改/删除（SHA/canonical/跨 workstream 前缀拒绝——
   `EvidenceSubmission` 构造器级 ref 格式校验）、伪造提交（typed XOR+note 三方一致）、
   部分写入（幂等仅认"已完整路由"，事件级验证，"no automatic recovery"）、重复提交
   （同内容幂等且 **events 字节级不变**——测试断言 readAllBytes 相等）、冷启动
   （subprocess ColdRead 风格复验；删除原外部文件后仍从 workstream 副本工作）。
3. **精确引用且提交不能直通验收**：✓ `containsAll` 元素级；suffix（`ref+".old"`）与
   summary（纯文字）两种攻击模式实测停在 WAITING_FOR_RECOVERY 且**零 sidecar 发布**；
   提交动作本身不产生 COMPLETE/ACCEPT——决定仍归自测/验收语义。
4. **一致性**：✓ inline/worker 双模式实测；四 definition parity（submit 边+动作集）；
   CLI 提示修复（我此前 R1）：`CliNextActionHints` 硬编码 approve/revise 删除，改为
   `ReviewPacketCliHints.printActions` 消费 `HumanGateActionSemantics` 单一来源，逐动作
   给出真实命令（含 prepare/submit 指引）；packet MD/JSON 附 CLI-only 说明；console.js
   只读展示；server 无 submit 端点（计划既定的 v1 切口，非第二 authority——校验全部在
   runtime 共享服务）。未发现遗漏消费者：ReviewShow 经 packet 展示说明；acceptance context
   节点在序列中真实经过。
5. **测试真实性与最小性**：✓ 真实 git 仓库、真实 runtime/worker、真实发布链；反例是
   行为级（summary/suffix 攻击等价于"删校验必红"的突变证明）。生产面与计划 §8 类别
   一致：无新节点（仅边）、无服务端上传、无第二扫描器/审批/平台；材料限额 32 文件/
   8MiB/32MiB；保留规则仅改两行提示且如实声明"共享服务强制材料持久化，一般输出清理
   仍是提示纪律"。未发现可再减而不削弱身份的等价实现。

## 3. Findings（按严重度；事实/推测分列）

**无 P0/P1。**

- **P3-1（事实，设计确认）**：submit 的材料复制发生在取锁之前。该窗口由锁内
  `verify()`（全部材料 SHA+源码复验）与锁内 baseline 重捕获等值检查闭合；材料权威是
  已复制字节（内容寻址），原文件后续变化无关。并发双 submit 由锁串行化+幂等分支处理。
  顺序正确，无需改动——记录以免后续误判为 TOCTOU。
- **P3-2（事实，语义备注）**：同 requestId 异内容在**无持久决定前**（首次提交在 gate
  写前失败）可重新提交不同内容——此时无 durable 效果需要去重，语义可接受；计划 §6
  "同 id 异内容拒绝"对已 resolved gate 成立（"gate already resolved with different
  evidence"）。如需严格全周期去重可加 requestId 索引，非必要。
- **P3-3（核证边界）**：全模块 1476/0/0/0 未由我复跑（成本）；我复跑的 15/0/0/0 与声明
  一致，其余测试 diff 逐行看过均为 definition 边数/动作集断言同步，无断言削弱。
- **P3-4（诚实边界确认）**：执行声明（命令/环境/起止/退出码）与操作者身份均为声明值，
  机器只校验形状与一致性（startedAt≥recordedAt 等），不证明执行真实——checkpoint
  §诚实边界 已如实声明，与实现一致。

## 4. 六维

| 维度 | 判定 |
|---|---|
| Declared by | ✓ workflow submit_evidence 声明边 + typed EvidenceSubmission + 人工 note（三方一致被强制） |
| Proven by | ✓ 准备 baseline（工作树字节）、manifest/材料 SHA、gate/route 持久化事件 |
| Consumed by | ✓ Guard（provider 前）→ prompt 精确路径 → verifyResult（发布前×2）→ acceptance 同 refs |
| Enforced before | ✓ provider 执行、sidecar 发布、验收读取之前；submit 决定前锁内全复验 |
| Failure route | ✓ typed 拒绝（EVIDENCE_COLLECTION_SOURCE_INVALID 等）→ WAITING_FOR_RECOVERY；部分状态显式停止不自动恢复 |
| Cannot-bypass | ✓ summary/suffix 攻击、同 porcelain 源码变化、篡改/删除/symlink/限额、冷 JVM、部分写入、幂等字节级、四 definition parity——行为级实测（我复跑确认） |

## 5. 提交判定与成熟度边界

**允许提交**（建议提交面：4 新生产类 + 14 修改文件 + 2 测试类 + 4 definition + 2 prompt +
console.js/CLI + checkpoint 文档；排除 output/ 与历史无关工件）。成熟度边界：
- **DETERMINISTIC SEQUENCE PROVEN**：成立（我独立复跑确认），但仅覆盖离线序列与替身
  provider，不含真实外部执行。
- **NATURAL E2E NOT PROVEN**：成立；245D 保持只读，其原始 definition 不因主仓库更新而变；
  未来若在其上合法续作，须按新 definition/新批准链另行判断，本 checkpoint 不构成恢复授权。
- collect 基线自然样本 NOT EXERCISED 的既有状态不变。
