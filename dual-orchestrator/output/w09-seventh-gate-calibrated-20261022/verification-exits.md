# 第七例逐条验收出口核对

本页是候选方法到现有入口的对账，不是编译合同/环境可用性authority。需求与AC-1/2/3与第六例一致；最终方法名/executor/evidence_kind/detail_owner由本次实际reviewed plan确定，不能用本页覆盖模型产物或用户批准。

| 条款 | 必需事实/执行出口 | owner与拒绝时点 | 不足后的合法路径 |
|---|---|---|---|
| AC-1 | 实际读取测试与执行Python unittest；至少八类输入/期望与幂等；PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS | 原R02/R03固定工具观察，VerificationEvidence在采纳/发布前核同合同/源码/selector，独立reviewer核覆盖语义 | 代码错走返修→自测→复评；无工具证据停，不靠模型摘要 |
| AC-2 | 实际测试输出含运行数量、零failure/error，并与源码相符；actual_exit_required=false/expected_exit_code=null保持原需求 | 同一程序观察与唯一outcome；native completed/文本结论不是测试证据 | 若用户后续明确要求真实exit，改合同复评重批；不能把reported改actual凑通过 |
| AC-3 | 实际read README/代码及例子，或实际运行例子核输入输出/导入 | 原观察固定、源码身份重验、独立验收；历史有效证据可按真实依赖复用 | 缺read/例子依据停；源码漂移使不再适用的旧观察失效 |

原准备阶段host-python及本轮依赖字节重核的host-environment-identity.json证明宿主Python3/stdlib unittest/importlib可加载，依赖用当前realpath/SHA记录；**不证明原生客户端可执行这些工具**。不抄原W08样例中的旧Python路径、不在GOAL前生成readiness ref。

真实GOAL批准与plan/contract等待subject产生后，用户作为operator经已有 `review record-readiness <workstream> --workdir <worktree> --subject <current occurrence> --request-id <unique> --file <actual declaration>` 记录。消费实际批准条款id/method/executor/evidence_kind/环境身份，来源由原L04事件reference与同字节SHA核定。声明只证“operator申报可用”，不证执行；登记后按原revise→作者→独立复评→EXECUTION compile→用户精确批准。dispatch重核环境与有效期，不跳过原门。

同方法的AC-1/2可共一份实际声明，AC-3若方法不同分开；不能把不同方法合并成万能认证。现有CLI入口、PhasedContractContinuationTest及程序观察/自测/独立验收序列由冻结发行1813/210离线凭证承接；原准备已核入口/help，本轮按冻结JAR与659源码字节承接该依据，不重跑help或冒称新真实证据，不新建环境认证系统。

AGENT是本Gate首选。若原生工具不可用则停；operator事后prepare/submit链仅在合同executor/evidence_kind变更经作者/复评/当前EXECUTION批准后可用，本页不预授权切换，不拿host观察或人工声明充原生测试结果。

当前状态：已实现出口已映射、当前宿主前提已测、真实native执行/动态readiness声明/最终机器验收均NOT_RUN。任何必要出口被实际装配证伪时停止上交；本地PASS不能代替真实条款PASS。
