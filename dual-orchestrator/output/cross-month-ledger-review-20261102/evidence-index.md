# 原始证据索引

本文件由 assemble_review.py 生成。行号为一基；发现项与限制必须同时阅读。

## E01 KNJM：reconnect 与正文 end_turn 并存

失败原因与正文完成标记不一致，属于待核验的执行完成/信号优先级边界。

限制：正文标记不能证明属于本次原生请求的完成事实；不能判为已证实的假失败，更不能直接续传。

- [run 失败原因](/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260602T110016Z-KNJM/.agent/workstreams/ws-20260602T110016Z-KNJM/runs/run-1780398017039/run.json:22)；匹配行 [22]；SHA256 `e0e243d5b85c0054d71edd3c1daa77fa765f7861ad208509ce1fe585721c8f83`。
- [正文末尾](/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260602T110016Z-KNJM/.agent/workstreams/ws-20260602T110016Z-KNJM/runs/run-1780398017039/outputs/intake-claude-brainstorm.md:582)；匹配行 [582]；SHA256 `fc6e1dcabddbd1b8fc103a88bc73ef014235a8f20f5b8635b68b952e9623d016`。
- [stderr](/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260602T110016Z-KNJM/.agent/workstreams/ws-20260602T110016Z-KNJM/runs/run-1780398017039/outputs/intake-claude-brainstorm.stderr.log:1)；匹配行 [1]；SHA256 `b6a6ba53375f4da4d79f498eb0620485c348f71372276884a41306c8f43fb852`。

## E02 PRF7：进程被中断，顶层却是结果协议失败

stderr 记录 harness 主动发出 interrupt_process=true；顶层 primaryReason 为 terminal_control_missing_or_invalid。

限制：证明历史分类压缩，不能证明已发生的文件/远程副作用可安全重做。

- [顶层原因](/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260622T235255Z-PRF7/.agent/workstreams/ws-20260622T235255Z-PRF7/runs/run-1782180664429/run.json:24)；匹配行 [24, 77]；SHA256 `4ac8ceaf248500ee54fb82cfd42f49189bfa2a357328979cade7ea569458a7f8`。
- [主动停止证据](/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260622T235255Z-PRF7/.agent/workstreams/ws-20260622T235255Z-PRF7/runs/run-1782180664429/outputs/plan-claude.stderr.log:3)；匹配行 [3]；SHA256 `42e5729fed25feb1c752d06d377645d568d25aec7448f89d4c812f574f28dc31`。

## E03 AYU5 / FUMK：证据可见性准入失败

required review_evidence_packet 缺少 prompt visibility claim，进程 NOT_STARTED；同类七次分布在五个案例。

限制：这是正确的启动前拒绝；源工件存在不能证明内容已经进入最终提示。当前版本同类生产缺口是否仍可触发未证。

- [准入理由](/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260708T030301Z-AYU5/.agent/workstreams/ws-20260708T030301Z-AYU5/runs/run-1783479932615/run.json:74)；匹配行 [74]；SHA256 `e3125391f347c9212b54d096893e1927619429e7bffd0f8d9bb90dc3630dc469`。
- [未调用进程](/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260708T030301Z-AYU5/.agent/workstreams/ws-20260708T030301Z-AYU5/runs/run-1783479932615/run.json:82)；匹配行 [82, 99]；SHA256 `e3125391f347c9212b54d096893e1927619429e7bffd0f8d9bb90dc3630dc469`。
- [FUMK 再次同类拒绝](/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260708T043128Z-FUMK/.agent/workstreams/ws-20260708T043128Z-FUMK/runs/run-1783517570025/run.json:75)；匹配行 [75]；SHA256 `2150c9ca5464c489375320dce7bb3293560286ad97fa8cf1ba6864709afaa495`。

## E04 9S3P：机器路径字段混入自然语言

scope/evidence 字段未满足文件和证据契约；应审查生产者和发现入口，保留验证器。

限制：任务是否必须允许该文件，需要结合获批 scope，不能从拒绝本身推导扩大权限。

- [契约失败原文](/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260629T152657Z-9S3P/.agent/workstreams/ws-20260629T152657Z-9S3P/runs/run-1782746818017/run.json:114)；匹配行 [114]；SHA256 `a763f1c426fe28f66683d58479c59f96bd85bcfb07e08a09c66d0f6ca4505f65`。

## E05 HHZL：必须按 occurrence 顺序解释纠正

先发生 INVALID_JSON，唯一纠正后仍有非法枚举；不能把最终枚举异常归因于先前提示丢失诊断。

限制：精确枚举诊断是局部改进；对原两次输出序列并未证明可以成功。

- [单次纠正预算](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260903T225544Z-HHZL/.agent/workstreams/ws-20260903T225544Z-HHZL/runs/run-1788476621271/run.json:61)；匹配行 [61]；SHA256 `41acbf81b8fca25fc16c0a0353684e248ffe3802d41776602ecf3133b8a8f13c`。
- [最终枚举异常](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260903T225544Z-HHZL/.agent/workstreams/ws-20260903T225544Z-HHZL/runs/run-1788476621271/run.json:218)；匹配行 [218, 227]；SHA256 `41acbf81b8fca25fc16c0a0353684e248ffe3802d41776602ecf3133b8a8f13c`。
- [初次错误对应的纠正输入](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260903T225544Z-HHZL/.agent/workstreams/ws-20260903T225544Z-HHZL/runs/run-1788476621271/prompts/intake-codex-product-review.contract-correction.md:3)；匹配行 [3]；SHA256 `187e7c978f1516b44d1cff6414a4814940fbacc4977bb20f5d4de23e30667968`。

## E06 BXWV：缺外部证据仍回实现

验收 must_fix=[] 且 EVIDENCE_COLLECTION；自测说明缺外网/浏览器，随后仍路由回 implement。

限制：修复路由最多避免无依据改代码；外部证据依然必须真实取得，不能据此保证交付。

- [验收代码缺陷列表为空](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260909T151234Z-BXWV/.agent/workstreams/ws-20260909T151234Z-BXWV/runs/run-1789003988271/outputs/acceptance-codex-review.json:27)；匹配行 [27]；SHA256 `bcd5f368351b260e1458f0761f54f2f00353e27c4251f897baaf8e3df032c75e`。
- [修复动作类型](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260909T151234Z-BXWV/.agent/workstreams/ws-20260909T151234Z-BXWV/runs/run-1789003988271/outputs/acceptance-codex-review.json:38)；匹配行 [38]；SHA256 `bcd5f368351b260e1458f0761f54f2f00353e27c4251f897baaf8e3df032c75e`。
- [验收分类（具体序列见 ledger 316–331 行）](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260909T151234Z-BXWV/.agent/workstreams/ws-20260909T151234Z-BXWV/events.jsonl:316)；匹配行 [316]；SHA256 `5b889c7776a9622b9cc5ace82946fa4bfb950d0ba0e2aa57901625ce8ad35e92`。
- [自测仍回实现](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260909T151234Z-BXWV/.agent/workstreams/ws-20260909T151234Z-BXWV/events.jsonl:326)；匹配行 [326]；SHA256 `5b889c7776a9622b9cc5ace82946fa4bfb950d0ba0e2aa57901625ce8ad35e92`。
- [实际缺少外部证据的原因](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260909T151234Z-BXWV/.agent/workstreams/ws-20260909T151234Z-BXWV/events.jsonl:327)；匹配行 [327]；SHA256 `5b889c7776a9622b9cc5ace82946fa4bfb950d0ba0e2aa57901625ce8ad35e92`。

## E07 MBC2：程序已知 SHA 被模型重新转写

revision 基线哈希转写错误，验证器正确拒绝；当前 ContractRevisionBinding 已改由程序绑定执行身份。

限制：保留拒绝错误基线的测试；不要用兼容旧错误哈希挽救单例。

- [节点拒绝](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fresh-fifth-gate-20261011/stopped-ledger-snapshot/nodes/node-intake-claude_brainstorm-006.json:59)；匹配行 [59]；SHA256 `8ec8f5a5518ef0ba01b35f90e75b0d736f38bde8c2c9ffbe0fe02031459c1e24`。
- [输入基线 SHA](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fresh-fifth-gate-20261011/stopped-ledger-snapshot/runs/run-1791728580831/prompts/intake-claude-brainstorm.md:131)；匹配行 [131, 147, 283, 295]；SHA256 `b2da7c760c33bdc0a34f47125721192da9f4ee7a675e8a7e273b336848b91cb4`。

## E08 V9W8：探索范围拒绝是必要保护

读取 backend/tests/test_safety.py 越过既有探索 scope，且无范围扩展申请。

限制：exit 0 不代表节点应该成功；允许范围是否过窄需要另证。

- [越界](/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260704T175019Z-V9W8/.agent/workstreams/ws-20260704T175019Z-V9W8/runs/run-1783188244002/run.json:91)；匹配行 [91]；SHA256 `8ffa3300cf90cb3e5b94f301c31f29762e97c3e9a3df057baf16cf4f07f69177`。
- [缺少合法扩权动作](/Users/kriswang/work/projects/ai/cancer-clinic-agent-runs/ws-20260704T175019Z-V9W8/.agent/workstreams/ws-20260704T175019Z-V9W8/runs/run-1783188244002/run.json:98)；匹配行 [98]；SHA256 `8ffa3300cf90cb3e5b94f301c31f29762e97c3e9a3df057baf16cf4f07f69177`。

## E09 7SNA：固定 method 被模型加写细节

计划把已登记 method 与逐项细节混合；当前验证器要求 method 完全相等。

限制：该 run 已 completed，ledger 最新节点仍 READY。这里只能认定后续资格风险，不能宣称 ledger 已因此失败；新 validator 处理旧输入也是反事实验证。

- [计划 method](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-okx-next-e2e-gate-20261026/fixture/case/worktree/.agent/workstreams/ws-20261026T142203Z-7SNA/runs/run-1793048679201/outputs/plan-claude.plan.json:27)；匹配行 [27, 50, 73, 96, 119]；SHA256 `e7bffc3bedbea71f685cc606341dbb07b0422a46094280d5c3c9917157822ced`。
- [登记的固定 method](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-okx-next-e2e-gate-20261026/fixture/case/worktree/.agent/workstreams/ws-20261026T142203Z-7SNA/artifacts/verification-readiness/okx-ac-availability-20261027-01.json:1)；匹配行 [1]；SHA256 `1212daf7ab19146a19336a41ac32668c60a31d76f48a6bf0292d361006129d08`。
- [实际后续节点尚未执行](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-okx-next-e2e-gate-20261026/fixture/case/worktree/.agent/workstreams/ws-20261026T142203Z-7SNA/nodes/node-plan-codex_plan_review-025.json:7)；匹配行 [7]；SHA256 `cfbab41634f7b812a7a658f512435081d3a6944ef2260197c4ad7c2cfb5760fc`。

## E10 9YYF：真实交付反例

ledger 存在 delivery_committed，目标仓库 HEAD 与交付 SHA 一致，本次只读复查已确认。

限制：证明至少一个新版本案例能走到提交，不等于异构任务总体成功率或外部 grader 质量通过。

- [交付事件](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fixtures/eighth-20261023/worktree/.agent/workstreams/ws-20261023T005431Z-9YYF/events.jsonl:149)；匹配行 [149]；SHA256 `477d8c0305988e19664e94c1250bbd9a40639a3d4e058eba308ba135806497fc`。

## E11 3B2P：revision 耗尽不必然是仪式过度

人工 revise 指向 SQLite 事务等实质缺陷；有真实产品原因，不能默认放宽上限。

限制：上限是否过紧，需要按阻塞类型、每轮增量与预算分别评价。

- [修订上限事件](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260904T040052Z-3B2P/.agent/workstreams/ws-20260904T040052Z-3B2P/events.jsonl:121)；匹配行 [121]；SHA256 `31964751a4e540dac5730cffd3f96fc8e904095f0ed6ff3f311d231cb0f6b697`。
- [实质事务问题的修订要求](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260904T040052Z-3B2P/.agent/workstreams/ws-20260904T040052Z-3B2P/gates/gate-node-gate-human_design_approval-038.json:14)；匹配行 [14]；SHA256 `59e919803ce860979b466585e592c4dca4c9ee7ba82b6ee8cbcd679d41952102`。

## E12 9BZG：用量归属待核销

201ms run 与另一 run 均记录 totalTokens=404593 且 LATEST_PROMPT_IN_SESSION。

限制：相同用量本身不能证明重复收费；须 provider request/prompt ID 证明唯一归属。总账也存在缺失，不能宣称统一上界或下界。

- [短 run 归属](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260829T122452Z-9BZG/.agent/workstreams/ws-20260829T122452Z-9BZG/runs/run-1788347435520/run.json:47)；匹配行 [47, 96]；SHA256 `77f57b4e901bc4069382461d25bc73f9d82d6a5d2c7aa31bbffef94f523bb287`。
- [短 run 用量](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260829T122452Z-9BZG/.agent/workstreams/ws-20260829T122452Z-9BZG/runs/run-1788347435520/run.json:42)；匹配行 [42, 91]；SHA256 `77f57b4e901bc4069382461d25bc73f9d82d6a5d2c7aa31bbffef94f523bb287`。
- [另一 run 用量](/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260829T122452Z-9BZG/.agent/workstreams/ws-20260829T122452Z-9BZG/runs/run-1788347257826/run.json:42)；匹配行 [42, 91]；SHA256 `4711b143c455a03b9325d63737e4f1d8869d8da8e5474afec4a83313164749f4`。

## C01 当前 typed retry 策略与消费入口

所查失败分支产出 MANUAL_ONLY，WorkflowRuntime 仅对 AUTO_ALLOWED 执行 typed 自动重试。

限制：不能据此把所有失败改成 AUTO_ALLOWED；必须先证明授权、停止、副作用与预算。

- [分类策略](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-core/src/main/java/dev/dual/core/execution/AgentExecutionOutcomeResolver.java:33)；匹配行 [33, 43, 54, 72, 83, 97]；SHA256 `500026b45a9e16706d0ad0fa0c5c831d9e38af8a4b2da1f34004941c737c4fc2`。
- [唯一策略消费者](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/WorkflowRuntime.java:460)；匹配行 [460]；SHA256 `bc89b78be8466c6b58b16635e6099a081bb6e94338b0794085ee7cf6ebf6b6ff`。

## C02 当前自测 continuation 分类

当前工作区源码，独立于历史执行身份。

限制：通过定向测试不能推广为历史 binary 行为或整体 E2E 通过。

- [当前自测 continuation 分类](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/ReviewDecisionHandler.java:72)；匹配行 [72]；SHA256 `bdd7cdb2525494246276f231f634d8434f46d4c3de7a3d100a375e1bf45050b9`。

## C03 当前证据收集副作用检查

当前工作区源码，独立于历史执行身份。

限制：通过定向测试不能推广为历史 binary 行为或整体 E2E 通过。

- [当前证据收集副作用检查](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/EvidenceCollection.java:98)；匹配行 [98, 102, 184]；SHA256 `7d21d43972267957faf0f3a2bc04b966c6e7239405716368748102a70c418aa0`。

## C04 当前外部调用提交协议

当前工作区源码，独立于历史执行身份。

限制：通过定向测试不能推广为历史 binary 行为或整体 E2E 通过。

- [当前外部调用提交协议](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/AgentRunCommitProtocol.java:112)；匹配行 [112]；SHA256 `ffc5370455cd32b768833bf9606c44083f9fc0fce7a38bbf72ea6d4e5a6a8d85`。

## C05 当前 readiness 精确绑定

当前工作区源码，独立于历史执行身份。

限制：通过定向测试不能推广为历史 binary 行为或整体 E2E 通过。

- [当前 readiness 精确绑定](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/VerificationReadiness.java:162)；匹配行 [162]；SHA256 `248cb1cb41bea137a1eb0c557df1904ddaa533e1702e5e2487cbdbf079b7702d`。

## C06 当前 revision 程序身份绑定

当前工作区源码，独立于历史执行身份。

限制：通过定向测试不能推广为历史 binary 行为或整体 E2E 通过。

- [当前 revision 程序身份绑定](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/ContractRevisionBinding.java:37)；匹配行 [37]；SHA256 `893ee67c22919794f8a57e3e0e5f7cb36ed9e07a29fa48f33c466489b39d4f9b`。

## C07 当前取证：禁止污染发布，不等于阻止源码写入

三个内置 sibling 的 self_test_report 均使用 implement profile；handler 没有根据 collection 切换权限。E1 allow_write 含工作树；guard 在执行后拒绝，现有测试明确保留污染文件。

限制：证明当前源码和真实 runtime 消费序列中的权限/发布边界差异；未启动真实 provider，也不主张历史所有取证节点均发生过源码污染。

- [权限由定义解析](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxAgentTaskHandler.java:209)；匹配行 [209]；SHA256 `9804c76d0364b388cc763e9579ae1448001b946457bc07980816dabf3a949dda`。
- [取证标记只添加提示](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxAgentTaskHandler.java:259)；匹配行 [259]；SHA256 `9804c76d0364b388cc763e9579ae1448001b946457bc07980816dabf3a949dda`。
- [implement 自动允许编辑/执行](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxPermissionProfile.java:99)；匹配行 [99]；SHA256 `0e0ccc8e7c904d2cb698c84f7c9c8a28bcaa9da6d6106e8bcb2dee3de29586cc`。
- [E1 工作树可写](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/ClaudeExecutionPolicy.java:106)；匹配行 [106]；SHA256 `d1db8c5eaa853886248bc9cba8f8181774dbfed95048351ff56aa597888da439`。
- [先执行再 verify](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/EvidenceCollection.java:96)；匹配行 [96]；SHA256 `7d21d43972267957faf0f3a2bc04b966c6e7239405716368748102a70c418aa0`。
- [测试证明污染仍留磁盘](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/test/java/dev/dual/runtime/handler/EvidenceCollectionSequenceTest.java:93)；匹配行 [93]；SHA256 `10ba8103f364c567755f1fd67880e8db235aedcbd433fe0dcdddcc58b9325f4e`。
- [full 节点及随后 config](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.full.json:284)；匹配行 [284]；SHA256 `4375c8074896871bb00970b0ba8946bb4344d098800dbe060038c112a6897c0e`。
- [contract 节点及随后 config](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.contract.json:284)；匹配行 [284]；SHA256 `38979a69837754cc97af6c83df2aae6a750902b704e8b730eea3fbf64d4ff79c`。
- [terminal-delivery 节点及随后 config](/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.terminal-delivery.json:284)；匹配行 [284]；SHA256 `d9cdcf71b8974d1ed023150dc1c2146d79769d6ac6aea994b0305d2d6b895fa2`。

