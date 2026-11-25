# W03 边界自查与纠偏记录

Analyze → Modify → Review → Reflect → Analyze，由主 agent 完成，不称独立评审。

## 范围与原因

先前把“可复核原生启动”解释成“第一次调用前证明全部 npm/native 内容”，会逼出固定安装或第二解析器，成本超过主流程当前收益。用户决定按现有入口和必需接口接入，版本只作观察；详设 §7.4 区分冻结 Dual 运行内容与每次调用的原生入口。旧动态解析否定证据原样保留，不改成成功。

没有推导出“任意版本必兼容”：实际原生版本当前可能 UNKNOWN，sessions show 只有上次持久化握手，live queue 当前初始化不能由它证明。将其作为具名的记录身份检查，而非安全沙箱或完整引擎证明；实际接口错误及验收证据不足继续保留原原因。这是本轮刻意的承诺边界，真实版本范围不作声称。

## 本轮发现及回到分析后的处置

1. 新 native 命令不只有 prompt：ensure、build-info、metadata、rebuild、correction、cancel/close 都有 intent/result。R01 invocation 由同一 transport 生产，每命令由同一 R owner 核对；不是在各 handler 加失败词匹配。
2. 原 run.json 完成写入覆盖之前的 native 命令事件：在同一 handler 的唯一 writer 收集已固定 command 事件，合并到完成投影；R 的不可变 per-command 记录仍是事实，不让投影创造资格。
3. 真实 handler 拒绝序列暴露 bootstrap/compatibility failure 引用不存在 stderr/transcript 的老问题。失败诊断正常记入实际 stderr，只引用确实存在的文件；不造空 transcript 或模型候选来“满足”哈希。分别用 protocol/metadata-command/ensure 失败做完整结果与恢复序列。
4. 普通命令重核原 executor，released/finished/outcome recorded 不借 R02 磁盘 intent 获新发送权；明确 cleanup 仍可达，只允许原目标 cancel/close，不重新核验已经失效的批准 policy。
5. 第一轮全模块开始后自查第4项发现 cleanup 会被新 policy 验证误挡，主动终止本次唯一 Maven PID。该轮 INVALID，不汇总未执行模块的残留 XML 当覆盖。同时发现 PhasedContractContinuation 的旧 stop fixture 没有 R01 invocation，迁移到真实固定身份，没有放宽生产守卫。

## 验证分类

- `red.log`/red-reports：生产修改前，新合法/拒绝/写失败序列对旧行为形成反证。含新检查不存在导致的失败，不声称所有后来新增测试都先红。
- `focused-1.log`：作者新增方法未声明 IOException，编译 FAIL，不是生产反证。
- `focused-2.log`：测试错误地序列化 Instant 的 author error，不是生产反证。
- `focused-3.log`：新守卫拒绝旧无身份 stop fixture；迁移后保留合法出口，不加 missing-field fallback。
- `sequence-1/2.log`：真实 handler 暴露两个实际缺失诊断引用，属于 production failure，修复后 sequence-3 通过。
- `aborted-*`：主动终止的不完整 reactor，INVALID；其已有两个具名 fixture 错误记录保留，不能说成绿色或有效总体计数。
- `focused-final-2.log`：最终选定协议/handler/R01/恢复消费者全绿，包含真正独立 Java JVM 冷读与配置观察；协议消息本身合成，不是实际 acpx/client/provider 能力。
- 最终单轮结果只取 `verification.json` + reactor-clean.log + reactor-reports，源码身份前后相同才成立。不拼接旧绿色。

## Sibling sweep

primary/correction/rebuild 共用 run 前 metadata 核对；inline/worker 共用 handler/R01 owner；两个客户端共用 schema/协议检查，环境只捕获该客户端路径变量，避免无关客户端配置污染。cleanup 不借 authority，exact target 校验不撤销；纯 R03 恢复不调用 transport，不因客户端升级重新执行。CLI/server 的冻结运行身份不再把 acpx 入口 token 当 Dual binary，而将其移交本次执行。query/attention 只消费 RecoveryAssessment 的 typed 原因，不新增 UI 分支。

## 未证明范围

零 acpx/native/provider 运行。未证明当前真实客户端已返回特定版本号/真实 exit 字段、任意升级兼容、live queue 握手/每个工具能力、完整 C/X/真实 E2E/目标 Git 交付、所有 kernel I/O 故障或多 ClassLoader/多 JVM 隔离。runtime fixture 的局部声明补全入口依旧未证明；不是本轮新增实现目标。原生安全实现沿 §8.25 依赖边界，不把业务采纳前检查夸大成 OS 写前拦截。
