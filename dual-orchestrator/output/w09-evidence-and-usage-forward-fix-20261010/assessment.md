# W09 F03/F04 前向修复与新发行 · 受影响交评材料（2026-10-10）

状态：**AUTHOR_OFFLINE_PASS / REFROZEN_CANDIDATE / AWAITING_AFFECTED_CC / FRESH_THIRD_NOT_AUTHORIZED**。这是作者自查与离线证明，不冒充独立评审。前两次真实 Gate 仍 FAIL；本批零真实客户端/provider调用，无提交、安装、旧实例恢复或新目标创建。

## 目标与范围

P0：让空产品仓的合法 intake 具有正确的证据引用指令，保持非法程序空间引用拒绝。P0.5：本次完成回执对应的请求用量由唯一reader归属，每次固定后完整聚合；没有可用关联不借其他调用。不是提高模型能力或扩大平台。本批13源码/资源/测试文件（7生产/资源、6测试）相对批前dirty内容单列，不把全工作区改动混作本批；主工作清单另作增量。详见 changed-source-files.json / source-incremental.patch / baseline/。

## F03：生产与消费的语义对齐

- 作者模板删除“additional durable evidence references remain allowed”歧义；共享 AgentResultPromptContract 明确 evidence_refs 为 canonical product-tree 文件路径，空 tracked inventory 可 []，非空仍须真实 tracked fact；.agent/.git 材料由 RepositoryFactBinding 承载，不能复制为产品引用，也不能造文件填空。
- 共享规则进入初始作者、修订patch（evidence_ref_additions）、intake reviewer；原acquirer的格式修正使用同一contract，实际correction输入有常驻断言。两实际渲染入口均断言上述约束并否定旧歧义指令。
- 原 TaskContractProposalValidator/TaskContractValidator/ArtifactContractValidator **未改**。真实临时Git空基准→capture inventory→写repo-map/绑定→合法空引用通过，两个程序路径负例仍拒；非空inventory的tracked fact正向/无证据与未知路径负向保留。
- 提示词是契约投影，不是安全机制，也不能保证下一模型必然正确产出；采纳前硬校验仍是原validator。

## F04：从活completion关联到每次固定用量

- 原current-stream projector在completion上增加实际 ACP sessionId；requestId仍来自当前关联的完成结果。它与 stderr 选出的acpx recordId分立，也不使用可选engine内部ID。旧3参数构造只表示没有这项观测，不复制或补造身份。
- Handler把各自AcpxRunResult的completion分别送入reader。reader在record stream中按(sessionId,requestId)关联request/result，删除prompt文本匹配与LATEST回退；无completion/无stream/未找到usage/歧义有明确不可用状态。重复同session/id不能唯一归属时保守UNAVAILABLE，不临场增加世代平台；异ACP session的同id合法路径可正常读取。已有显式typed per-result transport usage通道保留；当前生产transport仍不在该槽供应旧latest值，测试手工供应的旧归因标签不假称新reader实测。
- 既有有界等待继续等延迟result.usage，session snapshot不短路等待、不当请求量。成本仍是接口cost观察；未知不造数，token不制造美元，不宣称订阅实付金额。
- 每occurrence usage先进入acquisition，只有两次均可加总且provider/model/billing同源时才MULTI_PROMPT_SUM。任一次缺失/快照/不可归因不能把另一半冒称总量：聚合UNAVAILABLE，各次已知原值仍在occurrence记录。
- 原writer/R03/L03与唯一outcome/metrics消费未改。usage不创建第二控制原因或执行许可；Gate每段仍核用量与预算，归因不可用停止新dispatch，不从缺用量推零执行。

## 验证分层（不相加）

| 层 | 凭证 | 能证明 / 不外推 |
|---|---|---|
| 旧实现反证 | red.log / red-reports：2项预期断言失败、0错误 | 旧reader借了correction，旧实际渲染缺规则；不是测试编译错误 |
| 聚焦 | focused-03：156项全绿，focused-results.json | 主调用/correction固定与聚合、合法/拒绝、session/id/延迟/快照/金额/metadata、提示词初始/patch/reviewer/correction |
| 单轮全模块 | reactor.log / test-results.json / reactor-reports：**1779 tests / 206 suites / 0 failure/error/skipped**，clean test exit0；654冻结输入前后相同 | 四变体/inline-worker、批准/返修/资格/写入失败/冷启动/恢复/交付原序列承接；不把继承回归说成本批重新设计全部机制或真实模型E2E |
| 原真实数据只读离线对照 | actual-stream-offline-proof.stdout / native-proof-inputs-unchanged.json | 用新reader读第二例原stream，主36945、修正26548、总63493；metadata zhipu/glm-5.3/subscription_token_only；供入的completion身份取自原已核记录，**不是新原生执行的全链证明**，旧run的53096不改 |
| 删除式隔离 | offline-proof-results.json：当前对照PASS，2个mutant预期断言失败；单独classpath，不改生产字节 | 移除请求ID关联/共享引用规则会被反证抓住；不计入1779 |
| 发行 | package.log / artifact-verification.json / release-identity.json | 当前CLI/server、内嵌六模块与源构建字节相等；14资源、6次CLI元数据/定义核验；package跳测试不是第二轮绿灯 |
| 选定装配与现场 | selected-native-bytes-recheck.json / failed-case-preservation.json | 原wrapper/dist/config/Gate env相同，原两个证据目录及快照保持；不是新native能力证明 |

差异卫生：本批已跟踪路径与新增增量行检查通过；全仓git diff --check报告既有受审native patch的补丁文本尾空格，hash仍为1517f828，不改无关补丁（diff-hygiene.json）。

过程纠偏：focused-01有手工transport归因旧fixture断言误改，且运行中增加测试，不能代表最终输入；focused-02是作者漏改全限定Path方法签名造成编译失败。两轮原日志保留，不挪成先红证据。最终唯一全模块凭证不拼旧绿灯。开工先记录645项src内容，9项pom随后在生产修改前分别快照；完整654项输入在最终clean前后统一实测。

## 控制链、失败切面与六维

完整表见 transition-plan.md，兄弟/唯一消费者见 sibling-map.md；8个原guard/owner源码未变有 unchanged-owner-guards.json。

| 维度 | 本批对应 |
|---|---|
| Declared by | 用户原目标/已有前向修复纪律 + CC第二例F03/F04裁决；无新增authority |
| Proven by | 实际渲染/真实临时Git/离线handler持久化/156聚焦/单轮1779/真实历史stream只读对照，证明边界逐项分列 |
| Consumed by | shared contract→renderer/acquirer→typed proposal→原validator；current completion→唯一reader→handler/acquisition→run/R03/L03→原metrics/Gate核对 |
| Enforced before | 产品非法引用在采纳/发布前原guard拒；用量归因/完整聚合在记录与预算判断前。prompt只是投影，不称写前保护 |
| Failure route | prompt/R01写失败零调用；缺/歧义usage不可归因，snapshot/部分量不计总量；run/R03/L03写失败沿原D06/D07，不新增重发；artifact拒绝仍原typed outcome；UNKNOWN不造停止/重试许可 |
| Cannot-bypass test | 两先红、空/非空仓成对、实际correction输入、无/错/重复请求与正向关联、延迟和完整聚合、两个隔离删除反证；原写点/冷读/兄弟整链由当轮全回归承接 |

本批作者边界自查：离线范围PASS，无待修WARN/FAIL。未覆盖而不宣称：新发行真实模型遵守引用规则、活原生completion在新发行下端到端的实际usage、Codex独立业务验收、原生ask/CX、真实目标Git/最终人门、浏览器视觉；原延期事项不自动关闭。相同ACP session/id历史重用不能唯一归属时不作精细消歧，不以UNKNOWN当成功；只有未来实际频繁阻塞才另评估，不能以本批追加追踪平台。

## 当前决定与下一步

待CC同时判断修复认可、发行有效、fresh-third-gate.md可执行性。第三次提案保持同一纯产品目标/验收、新隔离target/workstream、独立新18调用池、600s/60min/120k预警/160k止损与原关键/最终人门及用户commit责任。首例17/二例16剩余不沿用，新目录尚未创建。

**下一步：CC受影响只读复审（cc-review-prompt.txt）→处置必须项→用户明确授权fresh第三例→才按冻结Gate创建和运行。** 不关闭W项、不恢复前两例，不用材料PASS代替真实运行许可。
