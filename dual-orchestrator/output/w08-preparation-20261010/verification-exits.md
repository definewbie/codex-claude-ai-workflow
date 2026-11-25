# G03：首任务验收出口与补齐接线

需求仍是 2026-10-09 的换行规范化候选，未批准、未创建目标。具体字段见 criterion-exit-candidates.json；其中显式占位的 ref/SHA/occurrence/epoch 不能作为合同或运行输入，不伪造注册证据。

| 条款 | 执行方法 / typed evidence / 消费者 | 合法出口 / 不足时 |
|---|---|---|
| AC-1 输入输出与幂等 | python-unittest；AGENT / PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS。真实读取测试/实际运行结果支持至少八种输入和幂等；模型 assertion 由独立 reviewer 语义核对 | 结果与源码身份不变可复用固定观察；代码错误走作者→自测→复评，不把缺权限当代码错误 |
| AC-2 测试实际执行 | 同 python-unittest，输出需实际覆盖数及零 failure/error；R02/R03 固定完整 tool result，VerificationEvidence 采纳前核同合同/源码/selector | actual_exit_required=false、expected_exit_code=null 是待批准要求，不是降低已有合同。若用户要求真实 exit 则改候选并批准；缺真实 exit 不能以 native complete 补齐 |
| AC-3 README一致 | source-and-example-review；AGENT / 同现有程序观察种类。真实 read 结果加源码/测试/例子输出核对；独立 reviewer 判断文字/接口一致 | 无需建立新的“报告证据”类型；单纯模型说读过不成立。无有效工具结果停在缺证据，不能改为假成功 |

## 必需环境：已观察与未观察

host-environment-observation.json 是本地 operator 程序真实测量：Python 可启动，stdlib unittest/importlib 可加载，binary/标准库 canonical 依赖 SHA 可重核。它**不证明** Claude/Codex 原生工具已在该环境执行，也不能直接填成产品 test evidence 或 feasibility ref。当前 config/权限接线来自 metadata 与源码，实际工具能力留首个必要消费点验证。

## 时序：避免在 fresh GOAL 尚未存在时要求有效引用

1. Gate 开跑前审查条款、方法、选定环境和已实现合法出口；确认现有宿主支持所需 stdlib，精确批准需求中的退出码口径。没有通用认证/新服务要求。
2. fresh 流程产生并批准真实 GOAL，设计/计划只读推进。计划缺 readiness 时，原 plan/contract 等待门必须保留可达。
3. 在当前原生门/准备失败 occurrence 上，具名 operator 用既有 `review record-readiness <workstream-id> --workdir <effective-workspace> --subject <occurrence-id> --request-id <unique-id> --file <request.json>` 登记：按实际 reviewed plan 的 method/executor/evidence_kind/detail_owner 原值登记；本页方法名称只是候选，不另造一套授权名称。AC-1/2 若实际方法相同可共享一份声明，AC-3 若为另一方法则分开。绑定实际 GOAL ref/SHA/epoch、effective workspace、已测且重新核定环境依赖。声明明确“可用性申报，不证明原生工具已执行”，不能把宿主观察当原生程序实证。
4. 登记不批准、不执行。按原 `revise`→作者→独立计划复评→EXECUTION compile→用户精确批准，使用 CLI 返回的真实 ref 和同次 L04 `verification_readiness_recorded` 事件中 `payload.reference.sha256`（与记录字节重核）作为 environment_refs 与 feasibility_refs；不手改计划产物，不伪造无实际 producer 的引用。该路径有 PhasedContractContinuationTest 常驻序列，属于本次聚焦/全回归。
5. dispatch 再核环境/readiness 漂移；自测必须产生实际工具观察。独立验收可同源码/同合同合法复用固定观察；不能为了符合材料格式声称真实退出码。代码漂移使过期观察失效。
6. 若原生所需工具不可用，先停止。本任务首选 AGENT；operator 事后材料出口有原 prepare/submit 链，但切换 executor/evidence_kind 改变授权比较，必须作者→复评→新 EXECUTION 批准。不能仅提交人工声明满足原 AGENT 条款，也不能以此清单授权切换。

本轮只证明字段/生产入口/消费者映射与离线合法出口；动态 ref 只能在授权的 fresh 运行中登记。实际客户工具效果仍 NOT_PROVEN，不能标全项机器验收已证。

生产者核对：ReviewRecordReadinessCommand 当前只打印 ref，不打印 SHA；SHA 取已登记事件的 reference，不从模型正文获得，也不把整个追加 events 文件哈希当记录身份。未来运行的材料索引只引用事件/记录，不造第二个 readiness owner。
