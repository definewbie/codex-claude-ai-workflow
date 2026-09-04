# Human-Noted Revise Budget Override 评审证据补录

日期：2026-09-04  
checkpoint：`425abf25c3c468c8ff679f36d35faa0638ac699b`

## 结论

该 checkpoint 在提交前已经完成独立代码级 diff review，结论为 `PASS / 允许选择性提交（P0=0 / P1=0 / P2=3）`。原评审通过 operator 对话返回，但未作为独立文件落盘；本文只补录已发生的证据，不冒充一次新的评审。

## 已核验边界

- 只有真实 `resolveOpenGate` 中 canonical `revise` 且 note 非空时，才把 `humanAuthorizedRevision=true` 传入内部路由。
- public `completeAndRoute`、伪造 result、Agent 自动 revision 与 autorun 不能产生该 authority。
- 一次 human revise 只越过当前一次自动 revision-limit 检查；后续再次耗尽仍需新的人工决定。
- note 持久化到 gate result 与新 iteration；普通 BLOCKED、declared BLOCKED→gate、synthetic recovery 与 execution retry 语义不变。

## 提交前证据

```text
WorkflowGateResolutionTest: 5/5
WorkflowFileLedgerTest: 49/49
SmallWorkflowProfileGateIntegrationTest: 4/4
Full reactor: 1326/0/0/0
git diff --check: clean
```

提交同时加入 `AutoRunCommandTest`，钉住 autorun 只产生 `approve/complete`，关闭独立评审提出的 autorun sibling 风险。

原评审其余两项 P2 为：(1) 瞬态 flag 会跳过当次 completion 的全部 revision-limit 检查；在当前单后继 gate 语义下没有额外越权面，仅作记录；(2) 独立评审未复跑 full reactor，要求提交前补跑。后者已由提交前 `1326/0/0/0` 与 clean diff-check 关闭。

## 未声称闭合的残余

该评审证明的是 human authority 与 revision-budget 的逻辑路由边界，不证明 file-ledger 多文件 mutation 的 crash consistency。R1 已通过 14 个 durable-prefix restart slices 得出 `PASS=2 / FAIL=12`，并决定 `RISK VALIDATED / IMPLEMENTATION DEFERRED`。因此：

- 不把 `425abf25` 描述为跨每个 durable write 的事务闭合；
- fresh E2E 只验证正常路径及自然失败，不注入写入故障；
- 若自然出现 ledger 持久化失败，样本判 `FAIL` 并停止，不原地修补或 resume；
- 只有 R1 revisit trigger 成立时，才另立 bounded comparison，不在 real-case Gate 中扩建恢复协议。
