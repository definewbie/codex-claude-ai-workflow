# 原生实际身份接线核对（离线，2026-10-07）

本轮未运行原生客户端/provider，也未修改其装配以猜测安装来源。W03 仍未完成。

已核事实：ReviewedAcpxBuildIdentity.runtimeBehavior() 当前记录 patch/upstream 常量与明确行为环境变量，方法注释也声明它不是安装字节测量。AcpxAgentTransport.reviewedBuildIdentityFailure() 仅在 Claude + claudeExecutionPolicy 非空时查询 build-info 并比较声明字段；Codex 与其他入口不走此检查。它不提供启动 adapter 的已解析安装路径/内容 SHA，也没有把该结果绑定成 R01 的实际运行 identity。

acpx 同版源码的 cli/config.ts 存在两级实际配置：用户 ~/.acpx/config.json 与工作目录 .acpxrc.json（此外有显式 MCP 配置）。只捕获 DUAL_ACPX_EXECUTABLE 或配置命令文本不能证明有效配置/客户端字节同版。agent-registry.ts 的 resolveInstalledBuiltInAgentLaunch 与 resolvePackageExecBuiltInAgentLaunch 在 Node 侧选择安装包/包执行路径；Java 不能从一个 npx 版本字符串推出它最终启动的包内容。不能在 Java 新写一套配置合并/包解析算法后冒称同一来源。

已找到现有 `cli/config-command.ts` 的 `config show --format json`：它展示真实解析后的有效配置并带来源路径，属于优先复用候选；本轮只读源码，未调用该命令。该入口存在不等于它已经被 R01/dispatch/恢复消费，也不等于提供实际启动 adapter 的内容身份。

后续正确接线应优先由既有 acpx 的实际配置/launch 解析入口生产非敏感身份快照，由 adapter 绑定 R01，并在 dispatch/恢复比较。需核对已有输出是否足以提供该事实；不足时在既有受审 patch 方向冻结明确字段与消费者，不从当前本机 npm cache 任选同名包作证明。本轮不把该缺口补成“未知即相同”，也不把文件常量校验标成 W03 完成。

这项核对与 D04 原生批准回复桥接是两项工作，不因此把 C/X 接线纳入 B03。更不需要 OS 沙箱、内核、认证服务、通用环境平台。当前支持状态：Dual Java 内容身份已实现；实际 native/adapter/有效配置身份尚无完整生产—强制消费证明。
