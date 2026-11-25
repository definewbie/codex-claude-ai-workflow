# W09 首次真实 Gate：停止与接口边界评估

2026-10-10；用户已明确授权按已批准 Gate 开始真实运行。**本次 Gate FAIL：主流程在第一次合法新会话的兼容性核验被阻断，未完成研发/测试/交付。**原执行状态为 `RECOVERABLE_ADAPTER_FAILURE / adapter_incompatible`，workflow `WAITING_FOR_RECOVERY`；不改写成模型质量失败，也不自动重试。

本次使用正确的冻结源码/JAR/选定 acpx/config；尚未发现错误工件或配置漂移，不能只因发生在前置阶段就把产品误拒降为 INVALID。可选字段是否确能安全缺省仍交受影响独立判断；若该判断提出新的实证，应有据修订分类，而非凑成功。

## 1. 实际过程与计数

- 本次仅一个 fresh `ws-20261010T055644Z-6QCS` / terminal-delivery / INLINE；先完成 context.build_package 内部节点，再执行一次 intake.claude_brainstorm。两个 CLI段依次运行、无并发路由；实际 code source/loader证据见 actual-router-topology.json。
- 一次 agent attempt；原 R01/R02/R03 与 L03 已固定。命令事实仅为 build_identity→session_ensure→session_compatibility，**没有 prompt 的 intent/dispatch**、没有 correction、没有 Codex 运行。
- sessions new 成功，ACP protocolVersion=1、acpxRecordId/acpSessionId存在、name/cwd与本次调用匹配、closed=false；原兼容性检查因缺 agentSessionId 拒绝。不是“GLM 没返回 JSON”：需求提示词还没有发出。只证明本次 ACP 会话创建/握手记录，GLM 模型连接与业务响应未证。
- CLI agent 段耗时4.965s；run.json durationMs=113 仅反映最终兼容性拦截命令，不能当作总准备/启动/模型耗时。预算保守计入1次顶层 attempt（剩17，但停止后不继续调度），不把零 prompt 当成可无界重复创建。
- usage.measurementType=UNAVAILABLE，unavailableReason=ADAPTER_INCOMPATIBLE。原数字槽为0也不当实测0 tokens/成本；provider=zhipu/model=glm-5.3/billingMode=subscription_token_only 来自 claude_settings_env，只是配置观察，不是实际模型或计费响应。金额未计算。
- 原 outcome禁止成功、未发布输出sidecar、无 GOAL/EXECUTION/交付批准；target仍空基准、产品文件0，未apply/commit/finalize。原CLI与账本保持失败/恢复状态。

## 2. 已核事实与判定边界

| 发现 | 证据 | 判断 |
|---|---|---|
| F01 首次合法会话的接口形态被强制拒绝 | AcpxSessionCompatibility.java:50–51 要求三种ID均非空；同版 acpx client.ts 的 SessionCreateResult.agentSessionId?:string，createSession 保留 result.sessionId 并从可选 _meta 提取 agentSessionId；extractAgentSessionId 在无meta时返回undefined。实际 session记录/show 无该字段但其他身份/协议成立 | producer可选与consumer必填的契约冲突已确认；高度支持误拒。不能临场填假ID，也不能只删除检查而不核其下游身份使用 |
| F02 恢复报告的原因与核心事实不一致 | run/node封闭 facts 的唯一 primaryReason=adapter_incompatible；RecoveryAssessment 显示 agent_interface_incompatible / FIX_AGENT_INTERFACE_THEN_RETRY。current-failure.md 却在 Summary 写 AGENT_OUTPUT_CONTRACT_VIOLATION，并突出 AGENT_RESULT_MISSING；FailureSummaryWriter 优先读取旧result.reason而未消费唯一outcome原因 | 核心控制原因未被改写；错误仍在兄弟投影/旧reason字段。修复应消费原唯一事实，不再产生第二原因或把合法缺产物诊断升为主因 |
| F03 离线合法夹具未覆盖这次真实形态 | AcpxCompatibilitySequenceTest.metadata、SessionMetadataFixture及NativePermissionTransportSequenceTest 正向均补有agentSessionId；负向覆盖错误protocol/name/cwd/closed/record_id，但未见缺该可选字段的合法正向 | 现有离线PASS不是虚假，但合法集合建模不足；新回归须来自实际合法会话形态而非照抄当前必填实现 |

source-checks.json 按文件SHA固定上述锚点。未核实际客户端为何不返回_meta、所有 native identity消费者、两客户端/重连/fork/permission sibling的完整关联；本轮未改源代码、未重跑测试，没有用旧1755当本轮新增能力证明。

## 3. 停止与现场

当前node `node-intake-claude_brainstorm-002` FAILED；execution `nxe-1791614610553-b1df99de`；run `run-1791614610585`。原账本在 `/private/tmp/dual-w09-newlines-20261010/worktree/.agent/workstreams/ws-20261010T055644Z-6QCS`。

按 Gate 止损关闭仅本次具名会话 `claude-main-ws-20261010T055644Z-6QCS-run-1791614610585`，随后原 sessions show 确认 closed=true。原记录 messages=0/request usage记录=0、last_seq=0，记录 connection_close；退出码/信号不可得。CLI runner已正常返回，**会话关闭或连接断开不能证明任意原生子树物理全停**，没有以此获得新attempt资格。没有其他新模型调度或恢复动作。

stopped-ledger-snapshot 是77文件的只读归档，所有原账本依然 authoritative；native会话只存安全白名单/原文件SHA，不拷贝可能含配置凭证的完整原文件。原失败诊断保留，不手补合同、事件、identity或输出，不恢复历史样本、不为本例加兼容救援。

## 4. 六维与对总目标的贡献

| 维度 | 本次实际结果 |
|---|---|
| Declared by | 原已评审 Gate + 本次明确运行授权；关键门/产品commit不预批准 |
| Proven by | 冻结前置再核、真实sessions new/show、原命令/R03/L03、停止观察；业务主链FAIL，其他能力未证 |
| Consumed by | transport兼容性消费session metadata→唯一execution outcome→node/current→RecoveryAssessment；旧failure报告有F02漂移 |
| Enforced before | 未通过compatibility前零prompt、零sidecar/推进/交付；同时暴露其合法性判据可能过严，不能因拒绝安全就宣称正确 |
| Failure route | typed adapter failure→MANUAL_ONLY→WAITING_FOR_RECOVERY；具名session关闭，无自动retry，不伪造模型证据 |
| Cannot-bypass | 本次实际拒绝与未调用前缀有原事实；现有离线拒绝测试仍在，但缺可选字段合法正向及同族完整sweep，不声称完整闭环 |

这次真实运行的价值是以一次有界前缀定位了模型调用前的合法入口阻塞，以及仍在兄弟报告里的失败原因漂移。它没有证明80% case可跑完，也没有证明任一产品验收。修复此入口直接推进既定主流程；无需安全平台、版本锁、Rust/内核研究或环境认证矩阵。

## 5. 建议下一步（未实施）

先由CC独立核对F01身份语义、F02投影来源与同族影响，确定什么身份必需、什么只能如实未知；不能用acpSessionId复制成假的engine agentSessionId。之后在原owner内做根因修复与实际合法/非法成对回归（主调用/correction、Claude/Codex、错误identity/cwd/protocol/closed、写入失败及恢复同源），核准后重新冻结发行与受影响Gate。

不救这次失败样本、不临场改账本或resume旧binary。当前Gate已停止；修复实施与后续fresh运行须明确其范围/冻结身份及授权，不自动启动第二case。只读评审提示见 cc-review-prompt.txt，无需立即追加原生实验。
