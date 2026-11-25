# W09 当前调用用量归因 · 前向修复

2026-10-11；**IMPLEMENTED / OFFLINE_VERIFIED / RELEASE_FROZEN_CANDIDATE / AWAITING_CC / FIFTH_NOT_AUTHORIZED**。本轮授权是 A 案生产 reader+Gate observer 联合修复、成对回归和新发行材料，不是第五例运行许可。当前未提交、未改原生 patch 或配置、零真实客户端/provider调用、未启动额外agent。

## 结论与对主目标的贡献

跨连接同 session/request 编号合法重用时，有独立当前日志的调用不再被整会话历史判为歧义。Dual 沿原 owner 固定每次真实用量，再由新 Gate 核对器消费；解除的是 P0.5 观测归因造成的 P0 主链阻塞，不增加审批/安全/工作流平台，也不承诺主流程真实跑通。

生产增量仅3类：`AcpxUsageReader`优先核当前transcript与typed completion；`AcpxAgentTaskHandler`主调用与correction分别传自己的日志；`AcpxStreamWireLine`在唯一grammar owner核完整非负整型input/output/total，optional cache缺席合法为0、畸形值不伪填0。无新domain schema/连接代字段/锁/服务。开工前dirty工作区654输入与增量单列，见[transition-plan](transition-plan.md)、[changed-files](changed-files.json)、[incremental.patch](incremental.patch)。

## 范围与残余限制

1. 当前唯一完成帧的request/stopReason及可得session必须一致；startup/permission非prompt结果不当完成帧。合法缺result.sessionId由原bounded completion承担身份，不复制标识。当前重复/错误/不完整记录不可由共享历史洗白。
2. 当前日志无完成帧时，保留原严格唯一共享stream/有界等待出口；跨代重复仍UNAVAILABLE。不做latest/文本/首末匹配。第四例实际8次材料中6次走当前来源，2次早期Claude只在共享stream有完成用量；本轮不声称所有到达形式被当前日志覆盖。纯stream-only且跨代重复仍是已知未支持组合，实际发生就停止并提交证据，B案未自动开启。
3. Token与金额分开：当前请求完整token可成立；货币仅同数值且严格唯一的历史成本基准可补充，未知不造0/实付；session cost/context snapshot不冒充请求量。provider/model/billing沿原metadata保留。
4. 新observer以run+occurrence计调用，拒绝共用日志；先核R03固定usage/acquisition与输出SHA，再核当前result及固定数值。不以共享历史追加撤销当前日志事实。无当前result时同向保留唯一延迟出口。预算算术、callback批准、scope/验收/交付不变。
5. 原四例保持FAIL；第四例领域仍RUNNING/design-decision READY，332733为当时已固定前7项，第8项仍UNAVAILABLE。新reader案头403420不回填、不授续作或预算许可。此前F2计数/F3通用恢复提示继续登记禁作预算/许可来源。

## 证据分层（不相加）

| 层 | 工件与结果 | 不外推 |
|---|---|---|
| 先红 | [red-results](red-results.json) 2项/2真实断言失败，0编译或测试作者错误 | 证明旧共享归因和部分对象默认0缺口 |
| 聚焦 | focused-01 69/0；focused-02为演进期间早期编译切片，非最终源码凭证；focused-03最终新增11/0 | 不拼接为全模块凭证 |
| 常驻新增 | 当前来源9项+wire部分量1项+Handler主/correction同号固定1项；既有延迟/双客户端/聚合/恢复回归保留 | 协议形态为合成，不冒称新真实客户端能力 |
| 真实旧材料只读 | [offline-proof-results](offline-proof-results.json)：8实际调用，6当前/2严格共享；第8项70687，诊断合计403420 | 零native/provider；旧结果不改，不是救样本 |
| observer | [observer-tests](observer-tests.log) 9项正反；[observer-real-transcript-proof](observer-real-transcript-proof.json) 真实原字节+隔离模拟固定事实；原UNAVAILABLE仍拒绝，Codex共享追加不撤销当前来源 | 模拟完成工件不证明新R03提交；stream-only重复不在稳定性承诺内 |
| 隔离反证 | Java2项（移当前来源/移request守卫）+observer2项（恢复全历史唯一/移固定SHA守卫）均预期失败 | 不修改生产classes或原Gate；不计Maven |
| 原完整协议回归 | [test-results](test-results.json)：单轮clean **1790/208/0失败/0错误/0跳过**，656输入前后相等 | 原R03失败/冷JVM/兄弟由常驻序列承接，不冒称新增每写点专项 |
| 发行 | [artifact-verification](artifact-verification.json)：package成功，CLI/server六内嵌模块与own class/resource字节一致、14资源与6只读metadata/定义命令全部通过 | 打包不是第二轮测试；未来启动身份待核 |

原四例manifest核对116/153/487/738项，第四例live及snapshot437权威文件不变，见[historical-integrity-recheck](historical-integrity-recheck.json)。native wrapper/dist及acpx配置字节不变。两项只读工件核对/fixture准备错误如实见[operator-errors](operator-errors.json)。

## 控制链与六维自查

完整transition与各写入/重启切面见修改前[transition-plan](transition-plan.md)，唯一消费者与sibling见[consumer-map](consumer-map.md)。

| 维度 | 本轮证明与界限 |
|---|---|
| Declared by | 用户A案授权、原Gate可归因完整用量义务；没有授权缩验收或调预算 |
| Proven by | 常驻真实离线Handler固定序列、原协议回归、八次真实旧材料只读对照与独立新observer离线对照；新活客户端/全真实主链未证 |
| Consumed by | 唯一wire parser→reader→handler occurrence/原aggregate→原R03/L03→metrics与新operator核对器；无第二路usage权威 |
| Enforced before | 用量完整性/总量采纳前；Gate下一模型派发前核固定事实及原储备。operator不是生产guard；新runner全入口顺序目前是源码核验，未获准执行 |
| Failure route | 当前错误/部分→UNAVAILABLE；本地固定失败原D06/D07，外部UNKNOWN不自动重发；观测不可核则禁止新Gate派发，不改写领域正常结果 |
| Cannot-bypass test | 旧生产先红、错/重/部分拒绝与合法同号正向、历史追加、主/correction两条日志、固定SHA、四项隔离删除反证；runner无fresh授权拒绝 |

自查/反思：A能够解决实际具名阻塞，无需B；真实材料暴露两条stream-only路径，保留其严格出口且明确限制，不伪装已证明普适性。原amount与token分立、零历史修复及严格权限/批准保留。本报告是主agent自查，不冒称CC独立判断。

## 下一步

单轮完整回归与发行已核齐，现交[CC受影响复审](cc-review-prompt.txt)，分别判断修复、发行候选和[第五例Gate](fresh-fifth-gate.md)前提。通过后再交用户当次170万/18请求/60min的fresh准备与真实运行批准，重新核配置/实际身份/出口/observer并执行。当前无第五例目标/实例，无旧余额沿用；W09仍未完成。

发行候选CLI SHA `06900ad8f7273e3d5410095f11cc956ed4a6b68eeccdfea17d53d8be9279d88e`、server SHA `0f4ca616a4eeeccb4409aa7f5c4f3f508c9bcb394686c6d0b272a23df7f5be19`；完整身份见[release-identity](release-identity.json)。HEAD不是dirty工作区身份；656输入与包字节共同锚定本候选。CC尚未复审本轮，不将自查/发行验证称为其认可。
