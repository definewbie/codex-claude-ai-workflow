# W03 实际启动身份核对（离线增量，2026-10-07）

结论：存在可复用的解析 owner，但不存在当前可直接消费的完整身份输出。材料核对不是 W03 实现，也没有运行 acpx/native/provider。

| 已核入口 | 当前真正提供的事实 | 与启动/恢复的距离 |
|---|---|---|
| cli/config-command.ts handleConfigShow + config.ts toConfigDisplay | acpx 自身合并后的配置、来源/loaded 标记、agent 命令、权限与超时；auth 仅方法名，不返回凭据 | 没有 launch、安装字节或当前运行绑定；配置展示也不等于每次 dispatch 的实际覆盖参数 |
| agent-registry.ts resolveBuiltInAgentLaunch | 实际 installed / package-exec 选择；installed 有 binPath/packageVersion，package-exec 只是 npm 解析计划 | 无完整内容 digest 输出；package-exec 在计划时不能证明稍后实际选到哪个安装内容，不可用版本字符串顶替 |
| acp/client.ts resolveAgentLaunchPlan | 真正准备 spawn 的 command/args 与解析分支；既有客户端唯一 launch owner | 目前只用于启动，logAgentLaunch 是文字日志而非不可变身份事实；未见进入 R01/资格比较的 structured 出口 |
| cli/build-info-command.ts e1BuildInfoDocument | upstream/patch/schema 的声明常量 | 不是实际安装内容测量，不能扩大为 native identity |
| CliLedgerFactory / ServerLedgerFactory | Dual 内容 identity + ReviewedAcpxBuildIdentity.runtimeBehavior 常量/环境行为 | create() 无工作目录参数，不能在此凭全局配置猜每 workstream 的有效配置；本轮未改此装配 |
| AgentRunCommitProtocol.prepare | R01 固定现有 ledger runtime_identity | 没有实际 native/config producer 引用；已存在字段不等于该边界成立 |

必要后续是限定的 producer→consumer 接线：在既有 acpx 实际 config/launch owner 内明确非敏感输出、installed/package-exec/custom 三分支的可证明身份边界，并与调用前检查及 R01/R02/恢复消费绑定。不能先在 Java 重新实现 acpx/npm 解析，也不能新增一份预检解析后假定真正 spawn 必用同一结果。当前输出不足，不实施“多调用一次 config show 即完成身份校验”的局部补丁。

这属于此前 W03 登记的实际缺口，不重新开启 D04 沙箱/内核方向。尚未冻结的具体内容：真实启动输出如何在首次外部副作用前生产并核验、package-exec/custom 分支的实际安装身份来源、工作区配置与冻结实例身份的绑定位置。需在既有受审 patch 方向写出具体增量与成本后再实施；本轮未修改 patch、安装包、用户配置或 Java producer。若只能靠包解析器/引擎改造/服务来实现，按既有止损提交用户取舍。

独立性与未核验：仅核对这些源码入口，没有遍历全部 sdk/native 依赖，也没有声称已穷尽引擎接口。Claude 绑定 GLM 等模型名称是客户端配置/观测，不能因模型名不同就另建 Dual 权限模型。
