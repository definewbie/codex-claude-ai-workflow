# W03 实际启动身份：生产入口与成本检查点

2026-10-07；B03 IN_PROGRESS。作者 Boundary Review：WARN / PRODUCER_DECISION_REQUIRED；不是独立评审、实施完成或真实能力 PASS。

## 1. 本轮实际完成与停止理由

已核对实际 acpx config/launch owner、默认装配、R01 与 dispatch/恢复入口；原样导入既有 agent-registry.ts，以临时文件 fixture 做 7 项离线检查。没有启动 acpx CLI、adapter、原生客户端或 provider；没有修改 Java、受审 patch、安装包、用户配置、原生源码，也没有额外 agent。

**必须先确定实际身份的生产方式，才能继续 W03。** 既有冻结文本要求实际 acpx/adapter/native/影响行为配置的身份与消费者，但尚未指定支持动态 npx/custom command 时如何取得、固定并在真正启动处核验这些内容。此前材料已登记此缺口。补版本常量不能兑现；把未知分支全部拒绝会截断仓库默认主流程；扩写任意包装脚本/npm 解析又超出“复用现有 resolver”的可预期成本。此次停止是对未确定的部署/支持范围交用户取舍，不要求再做安全沙箱实验。

## 2. 已确认事实与证明边界

| 入口 | 确认事实 | 不能推出 |
|---|---|---|
| scripts/configure-acpx.sh | 默认作者是仓库环境脚本 + npx @agentclientprotocol/claude-agent-acp@0.70.0；默认评审是 npx @agentclientprotocol/codex-acp@1.10.0 | 不代表用户此刻的全局/project 有效配置；本轮未读取用户凭据或运行 config show |
| agent-registry.ts findBuiltInAgentPackage/resolveBuiltInAgentLaunch | 匹配精确命令；当前 builtin fallback 是 Claude ^0.60.0、Codex ^1.1.5；上述默认固定版本命令及环境脚本均不匹配 builtin installed 路径 | 不能声称把现有 resolver 的 installed digest 扩展一下，就自动覆盖两个默认命令 |
| resolveInstalledBuiltInAgentLaunch | 已有 packageRoot/bin 解析；返回 installed、packageVersion/binPath；fixture 内容变更而版本不变时，返回值完全相同 | 没有现成的完整内容 identity |
| resolvePackageExecBuiltInAgentLaunch | 只选择 Node/npm 与未来 package-exec 计划，没有最终 binPath/packageVersion | npm 计划不是稍后实际安装内容；不扫描任意 npm cache 选一个包作证 |
| acp/client.ts connect/resolveAgentLaunchPlan/ensureLaunchSupport/spawnAgentProcess | 真正启动 owner；已有连接直接复用；仅在无连接/重连时解析 spawn 计划 | preflight 重新解析结果不证明 queue owner 内存里的旧客户端正在用相同内容；须同时核验活连接记录 |
| cli/config-command.ts/config.ts | 真实合并后的配置可复用；agent command、auth/session env 可含敏感值 | 不能把整份 config show、argv、环境或凭据写入 ledger；本轮不把命令文本当安全的完整配置快照 |
| 本机已索引 Claude adapter | claudeCliPath 与最终 query options 使用 CLAUDE_CODE_EXECUTABLE 或 SDK 原生文件解析 | 变量存在不证明已接入 Dual，也不证明任意版本均支持；本轮仅同版源码证据 |
| 本机已索引 Codex adapter | startAcpServer 读取 CODEX_PATH 并传 startCodexConnection | 同样仅入口事实，不是运行证明；不改 Rust/native 引擎 |
| TaskHandler/R01/Transport/AcpxExecutionRecovery | 现有固定 prepared ref、claim、逐 command intent/result 与恢复 stop owner 可承载/消费身份 | 已有 Java runtime_identity 不等于实际外部工具 identity |

证据：source-index.json 固定 21 个当前源码/文档输入，native-path-interface-source.json 固定两个本机 adapter 的内容身份和片段；resolver-check-results.json 固定 7 项离线断言。首轮 import 路径错误为作者 INVALID，见 verification-attempts.json；修正后 7/7 通过，不能称为生产先红后绿。未穷尽 native 配置、SDK 依赖或全部操作系统装配。

## 3. 推荐方向与替代

**推荐：在现有 installer/configure/受审 acpx patch 方向，固定本地安装并直接启动。** 安装时完成固定版本依赖解析、留下实际内容与启动路径；运行时不通过 npx 临时解析目标。仍是 Claude Code 客户端作者 / Codex 客户端评审；GLM 等后端连接沿既有环境注入，Dual 不要求换模型、登录或固定模型品牌。

这项推荐尚未获用户确认，也未实施。它改变的是 Dual 支持的可复现启动装配；未知动态启动在新 dispatch 前停止并指出如何准备受支持装配，不偷偷降级，也不让主流程永久只剩拒绝。自定义启动若要保留，必须明确实际内容如何由其真实 owner 固定；首版不承诺覆盖任意脚本、npm cache 或下载过程。

| 路径 | 收益 | 代价与边界 |
|---|---|---|
| 固定本地装配（推荐） | 准备时可测量实际文件；后续启动可消费同一目标；漂移可复核；移除每次运行的动态包解析 | 需要准备/升级安装一次，调整 Dual 启动配置及环境脚本的目标；installer、acpx launch/session、Java R01/dispatch/recovery 要联合接线与离线验证。没有网络/安装执行授权，当前只提出方案；不声称两行或近零成本 |
| 保留动态启动，仅记录已知 launcher/config 与明确 UNKNOWN | 当下装配改动较少；保留现有调用方式 | 不满足已冻结“实际 adapter/native 同版”的完整保证；必须由用户接受收窄承诺并做受影响审查，不能把 UNKNOWN 标为同版 |
| 维持完整保证且自动解析任意动态/custom 路径 | 保留所有旧启动形态 | 将需要更多包装脚本/npm/native 解析与握手；必要性、维护成本未证明，本轮不默认推进，不新建解析器或平台 |

未给工时/金额估计，因为安装分支和配置支持集合未确定。已能确认成本由“Java 消费接线”扩大为“实际工具装配 + acpx 启动/活连接接线”；需要先决定支持范围，不能把它包进一项模糊 helper。若推荐仍需新服务、adapter 引擎改造或无法保留正常读取/编辑/续作，继续按既有止损上交，不硬做。

## 4. 确认方向后的完整接线工作包（候选，未冻结细节）

不新增领域模型层：workstream.runtime_identity 仍持冻结装配身份，既有 run/prepared binding 持本次角色/已批准输入/实际参数身份；metadata/投影只消费，不产资格。

1. installer/configure：确定固定本地 adapter/native 目标、完整待测文件边界及升级方式；保持 Claude 环境加载用途，禁止固化或输出凭据。不能把 cache 拷贝/版本字符串当受支持安装身份。
2. 现有 acpx config/实际 launch owner：提供无外部执行的身份生产出口；实际启动和旧活连接检查消费同一内容身份。准备侧与实际侧共享 owner，不在 Java 实现 npm/config 合并。对缺失/漂移/未知分别给结构化原因。
3. Workstream 初始化与 R01：有工作目录后固定两角色的装配依据；不能由没有 cwd 的 CLI/server 全局 factory 猜本工作区配置。R01 固定实际身份/本次策略引用；profile/角色合法变化来自当前批准，不与全局冻结字段混成“永远不能变”。
4. dispatch：首个 session ensure 前、prompt/重连/correction/queue 复用前核验当前批准、实际目标及活连接身份；已失效零新调用。每次新的外部副作用仍有唯一 claim 和固定 command intent。
5. recovery：只读历史事实不授新资格；可信 R03 可按现有本地协议续完，不能因查身份重新调用模型。实际 stop/reconnect/new attempt 需核验其具体目标、当前身份与 D07 前置；漂移不能借旧身份自动重发，不能把停止失败伪装全停。
6. 投影与合并收口：统一暴露固定身份/实际核验/缺项原因，不显示未知为已验证。兄弟入口包括两客户端、inline/worker、ensure/reconnect/correction、活 queue owner 与恢复 stop；最后单轮稳定源码全模块回归，B03 合并独立评审。

这不是已经可开工的字段规格：固定安装选择、实际依赖边界、配置白名单、活连接的来源/比较规则需随所选路径具体化。不能把这个工作包误写成“所有 producer 已冻结”。C/X 回复桥接、目标 Git 交付、浏览器能力及真实 provider 验收不因此纳入本批。

## 5. Transition 与失败切面（候选义务）

| pre-state / owner | 顺序 / 消费者 | 失败后与恢复 |
|---|---|---|
| 受支持装配已准备；installer/真实 resolver 持实际目标 | 无 native 副作用测量→固定 workstream 装配事实 | 无可靠目标/测量失败：不得创建伪同版身份；原配置不改，提示准备方法 |
| 唯一 L02 claim，角色/合同有资格 | 身份准备输出→R01 固定 bytes→L 绑定 ref/SHA | R01 孤立无调用资格；L pending 只本地续完，不重算身份覆盖旧 R01 |
| R01/L 已绑定，外部 intent 尚未形成 | 当前内容/配置/活连接核验→资格→R02 intent→唯一 native command | 检查失败零新 native；记录 input preparation/identity 原因，不能改成模型缺 JSON |
| R02/command intent 已形成 | 实际 launch owner 核验并消费固定目标→执行/观测 | 检出漂移拒新调用；若其他命令已发生，不把本次拒绝推导成整个执行零副作用 |
| 外部结果可证明，R03 尚未完整 | 固定真实观察/唯一 outcome→R03→L03 | 写失败保留原事实；仅可靠固定记录允许本地补齐，禁止 re-run provider |
| 有 intent 缺可信完成；恢复 owner | 原执行者/活 session 观察→具体 stop 与身份检查→D07 分类 | 结果不明保持 UNKNOWN；身份错不能盲停同名 session，也不能借新工具恢复旧执行 |
| 可信 R03 / terminal / 历史观察 | 原内容/输入/claim 校验→固定本地消费或只读投影 | 不凭安装更新或当前文本伪造旧结果；不以身份查询作为重新 dispatch 的理由 |

以上保留 D06 L/R 不嵌套、D07 不明人工裁定、terminal 零新执行。不是一套新提交协议，也不是已经证明所有写点的结果。

## 6. 接线后验证设计及六维上限

先离线、依合法用户流程定义 oracle：固定装配正常作者/评审→prepare/执行→可信结果→冷 JVM 本地恢复；内容变更但版本未变→调用前拒；准备后/ensure 后/correction 前漂移→拒绝相应新调用且保留已有副作用事实；旧 queue owner 与磁盘新装配→不得假同版；不同目录配置、两角色合法 profile 变化→正确准入；缺准备/错 ref/写失败→零新资格；有 intent 无结果→UNKNOWN；可信 R03 恢复→零 native；terminal/历史→零调用。installed/package-exec/custom 三种来源按获批支持范围给正向/明确停止，不把只剩负例视为主流程通过。

| 六维 | 当前状态 |
|---|---|
| Declared by | 已冻结 D06/D07 runtime identity 及 W03 义务；支持装配增量本轮仍候选 |
| Proven by | 7 项原 resolver 离线检查只证明具名缺口；没有接线/原生行为能力证明 |
| Consumed by | R01、transport 实际 launch/活连接、恢复 owner 待同源接线 |
| Enforced before | 对 identity 新增要求尚未存在执行点，不声称 ENFORCED |
| Failure route | 复用准备 typed failure 与 D07；新增 identity 原因/投影具体映射待实施 |
| Cannot-bypass test | 上述正反和失败前缀是设计义务，NOT RUN；本轮测试不是其替代 |

## 7. 总体贡献与状态

这是让实际工具更新后不误称同版、正确区分可继续与结果不明的一项必要装配工作，不是给 Dual 增加安全产品职责。当前阻塞是具体 producer/支持范围选择，不是回到 D04 权限哲学。方向落定后完成整个工作包，再做 B03 合并批末评审；本报告不能作为 B03 收口或 W03 完成凭证。

本轮不重跑 Maven：源码/资源/共享 fixture 未修改，之前单轮 1,631 项只证明其固定版本。本轮 verification.json 只记录材料一致性和 7 项案头检查，不挪用旧绿灯证明 W03。无提交、真实运行或安装授权，NO-GO 保持。
