# OKX 外部验证材料续作 Fresh E2E

状态：FAIL / AUTHORED_RESPONSE_ID_INVALID。唯一样本GCTW在第三次设计修订输出后按约止损，账本为WAITING_FOR_RECOVERY / evt-000038 / node-design-claude_design-036；未进入实现。复用[原 Gate 的2026-09-12执行节](../verification-gates/20260909-okx-human-reviewed-scope-fresh-e2e.md)，不改变需求/验收。旧245D等样本只读；无额外agent、无autorun、无第二样本。

## 身份与前置

- code checkpoint：b197ce90；Gate文档：00aa315。
- JAR：c8584379a5115b7e2249da4ac57d4452084162349ed72fbb5aaee961e8aebfe1；small workflow：3ea9ea6c567608f4593bfef65f5868d7cbbd1be0925315c650abd8ec3f1896d5，28/51/8/6，CLI validation通过。production drift为空。
- target HEAD/tree、requirement/check身份与原Gate相同，clean；source check通过。
- Doctor通过，reviewed fork/adapter/模型配置沿本轮Gate。Claude单Read nonce往返、exit0/end_turn、adapterSignals空、session closed；Codex无工具exec精确JSON、exit0/end_turn、gpt-6-astra。源码fixture是历史名称DeepSeekReadProbe，不按模型分支。
- 宿主Playwright1.49.0/Chromium1148标准CLI渲染data页面、截图并正常退出。此前裸Chromium dump-dom命令停留未退出，关闭后输出页面；该次不作成功前置，标准Playwright结果才是可用性证据。仅证明环境可启动，不是产品浏览器验收。
- 唯一样本：ws-20260911T231749Z-GCTW（UTC命名，对应本地2026-09-12）。工作区：/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260911T231749Z-GCTW；分支codex/okx-external-evidence-20260912。start不带run，effective workspace capture/verify通过后才业务调用。
- 原始证据：output/real-cases/20260912-okx-external-evidence-e2e。

## 执行纪律

逐门读typed packet/来源并决定；仅缺证据时不回实现。若自然需要外部验证，先prepare，再执行真实测试并submit绑定，随后自测/context/acceptance逐步消费相同ref。未触发记NOT EXERCISED，不制造故障。不提前提交产品来满足启动前专用check。quota暂停提示用户切换，耗时本身不判失败；缺陷按Gate止损。

## 阶段记录

1. intake原提案run-1789168747482通过结构化校验。产品评审run-1789168979272提出陈旧响应表述与首次启动回填测试两项义务；第一次patch补齐启动级300根测试，复审run-1789169143917要求收窄其新增的闭合行永久不可更新限制；第二次patch后run-1789169270034为READY_FOR_PRODUCT_REVIEW、must_fix/contract_obligations空。两次均走正常提案修订链，非恢复。
2. 代理完整核对原需求、37项路径及三版提案后批准requirement gate-016，subject SHA cc45d4225a974c04c2e8a2ca33bc4827e5f9f335fe06f07194f796f4f9c20a02。不把其他历史样本的人工作用域静默移植成当前合同，当前批准以本轮明确条款为准。
3. compile-017的合同SHA 973920078c5ed2e8824cfc856ae0286a6fab9b96ccb3e182d209b722e7b607ca、resolution SHA d0de03c23a99f101408a47da0edc7080eeb8331439b3c3f1ebc2be6049d8e282均逐位重验；objective/scope/四组条款/evidence_refs与已批准提案全等，批准contract gate-018进入设计。完整产品E2E和材料自然续作仍未证明。
4. design run-1789169462819正常完成（约580秒、9次只读、零写入/Bash、SUCCESS），耗时未判失败。review run-1789170046878提出AR-1至AR-5：行情/状态事务、禁用状态、API到图表字段及时间单位、安全摘要、新鲜度承诺。主agent通读完整设计后确认，gate-023带note一次修订全部五项，特别明确单轮attemptAt先内存、完整解析后行情与成功状态同事务、失败独立记录且数据库不可写时不得声称持久化。无合同或路径扩张。
5. 第一轮设计修订run-1789170184692通过结构化发布，37路径/操作与合同机械全等；review run-1789170644963确认AR-4/5已关闭，但成功事务的planned_files文案仍与正文矛盾，新增内存镜像存在择新/恢复歧义，启动排序与图表依据未闭合。主agent核实[KLineCharts v9官方数据契约](https://v9.klinecharts.com/guide/data-source)明确毫秒，[官方示例](https://v9.klinecharts.com/en-US/guide/quick-start)使用1587660060000；gate-029追加这些新证据并要求减法（去内存状态双源合并、启动初始化失败即停止启动）。本轮已有两项关闭与具体新证据，尚不属于同blocker无新证据连续两轮；下一轮仍按同一止损条件判读，不以revision_responses自称ADDRESSED代替全文核验。
6. 第二轮设计修订run-1789170793937删除状态内容镜像并改正毫秒；review run-1789171190662关闭AR-1b/3及先前4/5，剩余beforeCommit被冒充底层提交失败、CountDownLatch无失败退出。gate-035明确选择非阻塞ready检查、删除latch，并要求单连接池下真实commit拒绝注入与标准回滚配置，不扩展到提交后丢失确认等未测故障。本轮有实质关闭而非同blocker空转；所有修订仍为正常设计链，无execution retry或合同返工。此时历史10份binding、80个bound artifacts及10份sidecar SHA冷读均一致（design-cold-hashes.json）。
7. 第三轮design run-1789171318596正常end_turn/exit0（357434ms、4 read、0 write、0 Bash、adapterSignals空），但返回的14个revision_responses中，最后一个human-note ID的SHA段为65字符而非64。JSON可解析、原始文本与候选SHA一致，非截断、超时或quota。AuthoredRevisionValidator拒绝，contract sidecar未发布，未进入复审/计划/实现；原始输出仅为失败证据，不能声称其设计已通过。

## 决定性证据与归因

`terminal-id-diagnostic.json`从真实gate字节复算SHA并与prompt/输出逐项比较：

```text
输入及gate SHA：41f08fffbe5deec3cb0e54e6e03236e163f7249ac771bfc2ed8963db5cef46ae
模型输出 SHA：41f08fffbe5deec3cb0e54e6e03236e163f7249ac77a7bfc2ed8963db5cef46ae
正确ID只存在于prompt；错误ID不在prompt中。长度64 -> 65。
```

- 已确认的直接原因：模型抄写身份串错误。模型实际身份为zhipu/glm-5.3；本样本不能外推为其他模型不兼容或GLM必然失败。
- 校验边界正确：`AuthoredRevisionValidator.shapeErrors`要求精确SHA形状，`AgentResultEnvelopeValidator`将错误作为reject；不能为了续作截断、模糊匹配或人工改写原输出。无错误批准/副作用，也不是外部材料新链的失败。
- 已确认的现有恢复覆盖：`agentResultAcquisition.correctionAttempted=false`、`correctionReason=NONE`、`correctionDisposition=NOT_NEEDED`，同一occurrence却明确`contractSchemaValid=false`、`contractCorrectionDisposition=NOT_REPAIRABLE`。代码只对parse failure或显式SCHEMA_REPAIRABLE目标启用一次correction，身份错误不在集合内；NOT_NEEDED不表示输出正确。当前行为与策略一致，不能仅凭这次停止断言validator实现错误；但它暴露了从小型引用抄写错误恢复的鲁棒性限制。
- 账本恢复提示为MANUAL_ONLY / retrySafe=false。CLI通用提示建议retry不等于安全授权；本轮没有retry，没有修改output/ledger，没有放宽身份规则来挽救样本。
- 样本FAIL而非INVALID：工件、输入、环境前置有效，失败发生于真实模型输出；没有以新JAR或新输入原地重跑。

## 收口与下一步

- 原target与有效工作树均未产生产品改动；JAR/workflow在止损后复算仍为冻结字节。8个属于GCTW的空闲ACP session已用正式close关闭并保留记录，未调用新prompt，已知存活的4个agent PID关闭后均不再存在。终点冷读13份binding的SHA及node/run lineage、104个bound artifacts SHA、12个有效sidecar SHA全部通过；第13个失败run未发布sidecar。执行日志、`session-cleanup.jsonl`、`terminal-cold-hashes.json`及8份终点关键原件副本在本轮output目录，排序`SHA256SUMS`记录保存字节。
- 外部材料链保持`DETERMINISTIC SEQUENCE PROVEN`；`prepare -> submit -> self-test -> acceptance`在本样本为`NOT EXERCISED`，`NATURAL E2E NOT PROVEN`。没有产品测试/浏览器验收/candidate，不能用前置浏览器能力或1476个harness测试替代产品验收。
- 下一必要工作仅为一次小范围恢复边界评估：能否在既有一次correction中修复明确的引用错误，并由当前active obligation集合唯一重验，且禁止改变设计正文/回应归属/其他字段。先验证可否实现严格约束；不默认引入新身份平台，不做相似度匹配，不重开网络/model层，不在GCTW上现场补丁重跑。
- 复盘：前几轮正常语义修订确有推进，但义务回应不断累积，完整身份串由模型重复抄写仍会失败。此处“身份守住”与“任务可收敛”必须分开评价；不能把拒绝正确等同于整条生产路径已可靠完成。
