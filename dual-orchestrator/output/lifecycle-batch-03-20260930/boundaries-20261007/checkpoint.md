# B03 权限证据与退出码重批续作检查点（2026-10-07）

状态：续作检查点已验证；作者自查 WARN（声明覆盖语义待独立判断）；B03 IN_PROGRESS，真实运行 NO-GO。本文件不作为整批收口或准入证明。

本轮 P0 目标是阻止“有完整 JSON 就把权限拒绝当可选”，同时让已批准、可证明的合法续作可达。批前 dirty 内容保存在 before-source.json；incremental.patch/changed-files.json 仅列本轮增量，未回退既有改动。

## 控制链与当前实现

ACP 当前 prompt/session 的 request + 真实 option kind → AdapterSignal.OperationRef；完整 JSON 参数 hash 保留未知参数，不解析 shell 正文。typed tool observation 另绑定 promptId，重复 RPC、跨 turn/错 session/缺参数不产生可容纳关联；当前无关联的权限决定仍形成阻断，不能因缺绑定消失；以 PERMISSION_UNRESOLVED/UNKNOWN 经 health/timeline 输出，不虚构成原生取消，真实 cancelled 另行保留。记录请求/决定 JSON 帧（去除行首尾空白）的内容与 hash，并在同一 run 的不可变 verification-observations 中固定 signal 与实际工具观察。

ArtifactContractValidator 完成实际 artifact/D05 校验后，PermissionEvidence 生成 permission_assessments（程序事实），唯一 Finalizer/Resolver 对同一 signal 消费。两通道：D05 所有已批准 verification bindings 有独立完成证据，且被拒工具不是其生产者；或 EXECUTION 已批准精确非必需声明（当前 node、参数 hash、criterion、规则、冻结 node 输入 ref/SHA）。第一通道不把模型语义断言变成机器观察，仍沿既定 PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS 的证据边界与独立验收职责。

声明来自现有冻结 workflow definition 的 node config：原定义是 node contract 的不可变容器，compiler 指派 source ref/SHA，不能由提案自填来源或从未批准 config 直接放行。编译及资格消费者比较同一来源；空声明意味着没有声明通道。审批材料、共同人闸摘要、两套 UI、CLI packet 与报告由 canonical TaskContract 同源投影；审批绑定完整合同 SHA，不只批准一个“optional”标记。

首版仅支持可唯一关联的 typed read/search、规范真实产品文件目标（相对路径或工作区内绝对路径，path/file_path）；参数 hash 仍固定原始实际输入，不把不同参数表示当同一已批准声明。控制路径/不支持操作/身份缺项保持 UNKNOWN/permission_blocked，不新写命令分类器或权限 DSL。完成 process（含 exit 0）的已证明非必需拒绝保持 SUCCESS_WITH_WARNINGS；其他阻断、E02 不完整进程和坏 envelope/产物的优先次序不放宽。R03 固定唯一 outcome 后，恢复读取固定结果，不重发 provider 或重判旧权限。

## 验证与失败切面

权限合法/拒绝序列覆盖三个 bundled variant 与 small 实验定义，另有真正产品 worker 入口（离线 provider fixture）的正反。正向包括独立完成证据、精确批准声明、审批前显示、后继及最终报告；反向包括被拒工具仍是必需生产者、错 session、参数不符、控制路径、工作区外绝对路径、缺关联。工作区内绝对 file_path 的正向另覆盖 inline/worker，并保留原请求参数 hash。固定 warning completion 经冷 JVM 重读；这是固定事实重读，不能冒称带权限信号的完整冷启动发布补齐。

CC LOW-1 的专门序列：批准 actual_exit_required=true/expected_exit_code=0 → 准备失败 → 作者新计划提出弱化要求 → 独立复评 → 新 EXECUTION 门 → 用户批准 → 实现消费新合同。未批准前零实现调用。原要求未因为已有 native completed 状态自动降低。

写失败沿既有 R01–R03/L03：观察不能固定时零可容纳事实；run outcome 写失败不能发布，固定 publication/R03 的本地续完不重发外部。全模块中既有前缀故障/冷恢复测试须一并通过；本次新增冷 JVM 用例只证明新字段的固定重读，不宣称逐一新注入了所有 permission 专属写点。

## 六维与不能声称完成之处

- Declared by：冻结 D02 §3、D05 逐项证据及用户退出码决定。
- Proven by：本目录实际原始报告；ACP 是合成协议 fixture，业务文件读取/本地批准/ledger/worker/冷 JVM 是真实离线程序行为；不属于真实原生权限能力证明。
- Consumed by：ArtifactContractValidator → PermissionEvidence → 唯一 AgentRunMetadataFinalizer/Core Resolver → R03/L03 → recovery 和投影。
- Enforced before：成功采纳、产物发布与后继推进前；不是原生文件副作用前，更不是 OS 沙箱。
- Failure route：UNKNOWN/REQUIRED 保留 permission_blocked；I/O/pending/外部不明仍沿 D06/D07，不制造第二模型失败。
- Cannot-bypass test：原完整产物反例先红；无关联不能变 warning；被拒必需工具的拒绝与精确声明的合法通道分别有序列对照；代码另禁止声明覆盖被显式选作必要生产者的工具；合法 warning 与无拒绝成功同时保留，防全部拒绝假通过。

已知支持限制须纳入本次判断：当前声明匹配要求 criterion_ids 覆盖该 EXECUTION 全部 verification bindings；局部条款声明保持 UNKNOWN，不升级。冻结补记允许声明列明受影响义务，但没有明说必须列全；该实现限制是否符合合法用户流程尚未充分证明，不能把保守拒绝当作 W02 完成。应在独立评审中核对生产/消费契约，不凭新增标签降低原验收。

B03/W02/W03 不因此关闭。原生实际身份核对见 native-identity-findings.md/source-index：既有 config show 提供可复用有效配置展示，但未绑定 R01/dispatch/recovery；实际安装/launch 字节生产尚无完整接线，不用版本常量或 npx 字符串冒称实测。下一阶段应核对/复用实际 acpx resolver 的可用输出，不能在 Java 另造包解析器；本轮没有临场扩展 patch 或权限平台。

旧 smoke 迁移/退役与有效引用清理、实际 native/adapter/有效配置身份、受影响批末独立判断仍待完成。D04 C/X 原生批准回复桥接仍是总体未竟项，未被重新纳入本批。无提交、无 live provider/native 探针、无额外 agent。

## 验证口径

core-red.log/red-reports 是开工前无关联拒绝的先红证据。其余中间 focused/compile 失败保留以供审计，不冒称预期反证。reactor-clean.log 在检查期间仍有代码变动、跨模块编译接口不同步，不能作为最终源码凭证，分类 INVALID。第一次源码稳定的全模块运行 reactor-final.log 返回 FAIL：旧 transport 测试缺当前 prompt/session 与 option kind，却期待明确 DENIED；源码前后身份一致，原始报告保存在 reactor-first-reports。修正测试以完整原生协议材料证明明确拒绝，生产规则未放宽；transport-fixture-fix.log 定点验证通过。reactor-final-2.log 是修正 fixture 后、进一步兄弟核验前的完整 clean PASS（1,625 tests / 198 suites；源码无漂移，原始报告保存在 reactor-second-reports）；不能代表之后的当前源码。兄弟自查新增两条用户流程反证：工作区内绝对路径被误拒、无法关联的回复被误记取消；sibling-red.log/unresolved-red.log 及 sibling-red-reports 分别保留预期失败。现有 PermissionEvidence 路径解析、ACP typed 状态/health/timeline 已同步修正，sibling-green.log 的对应正反通过。最终凭证限定 reactor-final-3.log、单轮全模块报告及 final3-pre-reactor-sha256 的前后核对，不合并多个轮次制造全绿。过程读取的模块数字不作为最终凭证。

新增用例未单独注入 permission 专属每个持久化点、未直接实测真实 native 产物、未逐一跑重复 RPC/多请求并发的全业务流程；现有共享协议故障/ACP 关联测试和源码守卫不能被概括成这些新增专项均 PROVEN。独立评审应判断这些剩余覆盖是否需在 B03 收口前加强。

## 最终验证落账

最终 `reactor-final-3.log`：一次 `mvn -q clean test`，退出码 0；1,627 tests / 198 suites / 0 failures / 0 errors / 0 skipped。8 模块原始 XML 固定在 reactor-reports；逐模块/逐报告 SHA 与边界见 verification.json。636 个源码/资源/POM 前后内容一致，本次 32 文件源码增量相对开工前真实 dirty 内容，不相对 HEAD 混入先前改动。观测到的末尾 HEAD 是 3362eb421514e75fdc4332f570df5bf274b3725e，不将末尾读取冒称批前测量。

JS 语法检查已通过；无真实客户端/provider、额外 agent、提交或历史恢复。本轮源码增量与材料分别提供，主清单 §13 和 progress-and-decision 的 10 月 7 日补记均保持 B03/W 项未关闭。作者自查不替代独立判断；CC 提示词见 cc-review-prompt.md。下一判断应先核对上述声明支持范围及实际身份缺口，不以全绿自动开启后续实现或真实 Gate。
