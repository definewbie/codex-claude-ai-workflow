# 人工验证续作与 E2E 就绪性漏评复盘

状态：ASSESSMENT / AWAITING CC REVIEW。本文不是实施授权或新的 Verification Gate。

## 1. 目标与核验范围

解释为何已设计人工补证据，真实运行仍无法走到验收；检查同一链条上的开发顺序、职责和证据保存问题。
不重做全仓审计，不设计权限平台，不调用 provider，不恢复样本，不启动额外 agent。

本轮代码引用基线为 `0cb2a36b`（前置实现 `88107d42`）。工作区有未提交文档及历史输出，不能将其整体视为本评估产物。
核验来源：现有设计、实现/自测提示词、CLI 下一步提示、245D 的 typed 自测与原始 transcript、产品工作区检查脚本。
未重新运行产品测试，未全面审查 server/UI、所有异常路径或所有 workflow variant。本文自查不是独立评审。

主要证据入口（路径相对 dual-orchestrator）：

- `docs/plans/20260910-human-disposition-and-evidence-continuation-design.zh-CN.md`，§5/6/8/分相：已明确 collect_evidence 与 submit_evidence 两条不同路径，后者仍未实现。
- `docs/plans/20260910-evidence-collection-source-baseline.md`：批准时源码基准与前后复验，明确不提供外部材料接收。
- `docs/testing/verification-gates/20260909-okx-human-reviewed-scope-fresh-e2e.md`，2026-09-10 增量：submit_evidence 必需时停止。
- `docs/testing/real-case-reports/20260910-okx-evidence-continuation-e2e.md`：运行序列和止损结论；属于索引与实施方记录，评审应再查原始工件。
- 产品工作区：`/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260910T145051Z-245D`。
- 以下称 L 的账本目录：上述工作区下 `.agent/workstreams/ws-20260910T145051Z-245D`。
- L 下 `runs/run-1789061656152/outputs/implement-claude-self-test.json`、同名 `.contract.json` 和 `.transcript.log`；`nodes/node-gate-human_implementation_repair-050.json`、`workflow/current.json`。

## 2. 实际链条及状态

```text
需求 -> 合同 -> 设计及修订 -> 计划 -> 实现
                                      |
                                      v
                                    自测
          有具体测试缺陷 -> 实现修复 -> 再自测
                                      |
                         must_fix=[]，仍缺外部验证
                                      v
                         human repair gate-050 WAITING
                                      |
                      request_action 登记诉求，仍等待
                                      |
                     缺：宿主证据正式接收及核验续作
                                      v
                            自测核验 -> 验收 -> 交付
```

已确认：最后一轮完整默认套件报告 45 项、0 failures、1 error，44 项非浏览器测试通过。
浏览器错误在不经产品代码的 headless_shell 启动中亦复现 Mach bootstrap_check_in Permission denied；原始 transcript 第296/315/320行可核。
真实 OKX 请求仍受网络策略拒绝，transcript 第253/259行包含 blocked-by-allowlist。
这些证明当前环境无法完成相关验证，不证明浏览器功能本身一定正确，也不证明宿主执行必然通过。

当前不是 workflow FAILED：账本保持 WAITING_FOR_HUMAN，未进入 acceptance，无 accepted candidate。
报告的 FAIL 是本次完整 E2E 目标未完成。旧的“仅缺证据却回实现”在此切面没有复发。
collect_evidence 源码基准分支 NOT EXERCISED；request_action 自然登记成功不等于材料提交或执行授权。

## 3. Findings

### F0：已知必需出口被延期，却仍判完整 E2E 就绪

类型：开发顺序与就绪判断错误；已确认。

设计已写明外部材料需 submit_evidence，但开发只完成停门、登记与同环境补采。
Gate 又把缺该能力列为止损项。止损可防绕过，却不能替代实现前的可达性检查。
责任在主 agent 的整体决策，不能归咎用户未提醒或独立 reviewer 未覆盖。

最小纠正：完整目标开跑前，必须回答每项必需验证在哪里执行、由谁接收结果、如何回验收。
允许局部能力实验，但不得把它标为完整 E2E 已就绪。不是要求实现所有假想异常。

### F1：前置检查未覆盖真正的验收执行能力

类型：验证前置缺口；已确认。

本轮 health 证明模型/ACP 往返与 Read 可用，workspace check 证明宿主检查通过；两者均不证明 agent 沙箱能启动 Chromium。
浏览器已列入批准计划，却在实现后才验证到这一限制；下载缓存后仍在 Mach 注册层失败。

最小纠正：对本任务必需且尚未证明的执行能力做短前置，或预先确认可用的外部验证续作路径。
不能把这扩成每轮重复全部能力矩阵，也不能将宿主 curl 成功外推为 agent 内 Java/浏览器可用。

### F2：实现与自测职责划分可能放大低成本修复往返

类型：已确认策略事实，成本因果为合理判断，需评审。

`dual-adapter-acpx/src/main/resources/dev/dual/adapter/acpx/prompts/implement-claude.md:22-26`
规定实现不负责 exhaustive verification，并禁止 broad test/build；独立自测负责运行。
本次作者只做编译，初始化时序、测试参数和 fixture 等缺陷由后续自测发现，多轮返回修复。

不能断言该提示禁止所有测试，也不能证明改变提示就能消除全部缺陷。
值得收紧的边界是：作者应完成直接相关、低成本的验证，自测负责完整性与最终证据；避免两边都重复全套。
不新增节点或测试调度平台，具体修改须评审后决定。

### F3：清理规则与原始证据保留冲突

类型：提示词/证据生命周期缺口；已确认。

`implement-claude.md:30-33` 与同目录 `implement-claude-self-test.md` 的 repository hygiene 段要求清理构建产物、临时日志及额外报告。
最后自测明确删除 target；原始 surefire XML 因而未保留。durable transcript 与 typed sidecar 仍存在，不是所有证据丢失。
现状使逐测试核验主要依赖日志和模型转述，不能宣称主 agent 独立聚合了 XML。

最小纠正：定义需要保留的验证产物及现有 workstream 内落点，先保留后清理；不要把测试报告当普通垃圾。
不自动全量归档目录，不导入敏感日志，不另建证据平台。与外部材料接收复用现有 ref/hash 习惯，但不预定必须同一类实现。

### F4：启动基线检查、验收检查与交付检查混用

类型：任务/Gate 与脚本阶段语义冲突；已确认，不是通用 runtime 写入缺陷。

产品 `scripts/verify-workspace.sh:19` 要求 tracked-clean；本轮计划又要求验证完整脚本。
实现阶段三个受控文件按合同修改但尚未提交，脚本必然失败；脚本末尾还含真实 OKX 访问。
不能让 agent 为满足检查提前提交，也不能把正常待交付改动当代码缺陷。

最小纠正：在任务及检查用途上区分启动基线、实现验证和提交后检查；各自只在合法阶段验收。
尚需核对它是否会成为验收/交付间的机械循环，当前样本未到该阶段，不能称已实证死锁。
不能简单删除所有 clean 检查，也无需开发通用检查框架。

### F5：CLI 下一步提示未消费当前合法动作

类型：局部 presentation 缺陷；已确认。

`dual-cli/src/main/java/dev/dual/cli/CliNextActionHints.java:27-34` 对 WAITING_FOR_HUMAN 固定输出 approve/revise。
gate-050 的 packet 实际为 revise/request_action/collect_evidence，末尾命令提示仍为 approve/revise。
这是误导与漏展示，不证明核心允许非法 approve。

最小纠正：消费当前 gate 的动作资格，不独立维护第二份动作清单。此项可独立小修，不必阻塞材料模型设计。
评审需检查相同命令输出消费者；不因本文件有错就声称 server/UI 也有错。

## 4. 下一阶段必须评估、尚不能称已解决的风险

1. 材料接收后，下游是否真的使用材料，还是强制在同一受限环境重跑？必须走完提交到验收的序列；只有上传或 prompt 引用不够。
2. 材料 hash 只证明保存字节一致，不证明执行真实或充分。需区分源码身份、执行记录、提交者声明及验收判断，不虚构密码学真实性保证。
3. 接收材料期间或之后源码变化、材料部分写入、重复提交、冷启动、拒绝后再提交如何解释？已有设计列出部分切面，具体复用点和实现仍需核对。
4. 当前审批默认使收集发生在原 agent 环境。外部材料接收是已选方向，不是经穷尽比较证明的唯一方案；评审应挑战是否存在更小的合法闭环。但不得以放开整个 sandbox 或执行任意用户脚本作为未论证替代。

## 5. 所有权与待完成的最小闭环

| 事实 | owner | 唯一消费与边界 |
|---|---|---|
| 当前等待、合法动作、决定 | workflow ledger / 既有 gate 语义 | CLI/API/UI投影；不能以note制造新权限 |
| 批准的源码身份 | 既有源码基准能力；外部材料适用性待核 | 材料准入与续作校验；不可仅用HEAD替代工作树字节 |
| 原始执行结果 | 实际执行记录，人工提交不自动变为可信结论 | self-test核验材料；再交acceptance判断充分性 |
| 材料保存字节/ref/hash | 待实施的最小材料接收职责 | 后续不能读取漂移的外部路径冒充同一材料 |
| 最终产品通过 | 既有acceptance/completion/delivery链 | 不由上传成功、请求登记或测试计数单独决定 |

待设计的 transition：当前OPEN gate -> 校验请求/源码 -> 固化材料 -> 锁内重验并绑定决定 -> 声明边进入self-test -> 校验材料和源码 -> 原验收。
每个 durable write 之间失败的状态、retry/restart/abort、旧状态支持范围须在实施计划锁定。
这不是允许为245D新增永久救援逻辑；当前样本保持只读，后续是否可合法恢复另行判断。

必须证明的不变量：

- Declared by：既有workflow声明动作及请求契约，不接受任意目的节点。
- Proven by：源码身份、固化材料字节、执行记录/提交声明分别可核对。
- Consumed by：self-test实际消费同一材料，acceptance再审充分性。
- Enforced before：不满足身份或材料条件时不得把该结果发布成有效验证并进入验收。
- Failure route：材料不充分仍等待；有具体修改项走合法修复；执行失败走既有恢复，不混为一类。
- Cannot-bypass test：同树正常续作、材料缺失/篡改/旧源码拒绝、部分写入/冷启动、补采仍不足回人工、同边界sibling。

以上是实施前要求，不是已完成的ENFORCED声明。

## 6. 建议决策与范围止损

建议 DESIGN GAP：先闭合一个完整人工验证任务，不再以同样目标启动fresh样本。
优先将必要环境判断、最小材料保存与提交消费作为一个端到端设计单元，不等于一个大提交或新平台。
F2的作者验证职责可独立收紧，F5可独立小修；不为追求同时修完而扩大核心增量。

禁止本轮扩展：新model层、网络代理、全局权限平台、任意脚本执行、通用评估器、工作流重写、所有历史样本兼容、修改产品需求以躲避测试。
复用当前Gate；身份和必要增量判据更新即可，不再复制完整Gate。

## 7. CC 独立评审任务

请独立核对F0-F5的代码/工件证据及推论强度，不采信本文结论；允许指出误判、过度归因或更小方案。
重建人工处理前后到验收的完整transition及失败切面，判断优先顺序是否真能消除下一次同类停顿，是否遗漏同边界消费者。
检查六维不变量和上述待验证风险，不只检查字段存在或局部测试绿色。
只读、不调用provider、不恢复样本、不改production、不启动额外agent。
输出按严重度排序的findings、事实/推测区分、最小必要范围、可延期项，以及是否足以进入最小实施计划；不要预设PASS。
建议报告：`docs/plans/20260911-human-verification-continuation-readiness-assessment-review.zh-CN.md`。

## 8. 收到 CC 评审后的裁定

F0-F5核证及R1/R3-R6方向接受；不改写独立评审原文。R2的源码身份替代建议不接受，其事实前提错误：

```text
同一个已提交HEAD H：
文件从提交内容改为A -> porcelain为 " M file.txt"
同一文件再改为B   -> porcelain仍为 " M file.txt"
HEAD、路径和状态都相同，但A与B字节不同。
未跟踪文件同理：内容改变仍是 "?? file.txt"（目录折叠更不能表示内容）。
```

因此HEAD+porcelain不是未提交工作树内容身份，不能拒绝上述旧材料复用；245D恰有未提交及未跟踪源码，非假想支持范围。
仅在额外强制clean等前提下讨论HEAD绑定属于另一方案，不能静默加入提前提交要求来改变当前交付流程。

现有 `dual-runtime/src/main/java/dev/dual/runtime/handler/EvidenceCollectionSource.java` 已实现源码字节摘要，
覆盖tracked、非ignored untracked、指定路径、删除及执行位；`EvidenceCollection.verifySource` 已消费对应baseline。
最小方向应优先复用并核对其在外部材料场景的适用性，而不是重造或削弱。不能据此直接声称复用已完成；
测试开始前、结束后及提交时如何关联同一基准仍须在实施计划锁定。文件hash也不证明真实执行。

R2“成本小一个量级”无测量或改动量证据，不作为排期依据。其不可变材料副本+材料SHA部分可采纳，
仅拒绝HEAD+porcelain替代源码字节身份及将内容身份延期。

后续可进入最小实施计划，但必须使用上述修正前提。就绪规则限定于本目标必需出口，不要求覆盖所有假想异常；
短能力探针是范围约束，不把30秒写成模型执行失败期限。CLI错误提示可独立小修，不冒称材料接收的核心依赖。
245D继续保持只读，不开启provider，不在本评估批次实施production。
