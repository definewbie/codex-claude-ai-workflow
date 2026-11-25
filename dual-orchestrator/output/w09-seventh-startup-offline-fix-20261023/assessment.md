# W09 第七例：进程核验离线修正与启动超时核对

**进程误判已定位并完成有界前向修正，离线成对验证通过。会话命令为何在内部阻塞，现有材料不足以精确复原，保持未证。** 本轮零模型/真实原生调用、零新例；第七例 INVALID 和停止标记不变。

入口：[开工边界及 transition](analysis-before-edit.md)、[测试结果](test-results.json)、[旧/新实际对照](old-vs-new-process-proof.json)、[超时源码身份](source-index.json)、[CC 提示词](cc-review-prompt.txt)。本轮推进 P0 启动前提可信性，保留 P0.5 用量未知状态；完整主流程仍未证。

## 1. 进程误判：根因明确，改动限在 Gate operator

原 `prepare-offline.py` / `pre-dispatch-recheck.py` 均用“ps exit=1 且 stdout 空”判 PID 不存在，未检查 stderr。相同条件也会出现在进程表读取被拒绝时。本轮在实际受限环境再次取得 `Operation not permitted`，旧条件确实返回 true；新 guard 返回 UNKNOWN。这是直接的旧缺陷对照，不是人工造一个产品故障。

修正为**一次成功读取完整进程表后再比对已知 PID**：exit 必须为0、stderr 空、表非空且行可解析；失败/异常/超时/畸形/重复行全部 UNKNOWN；发现已知 PID 则 PRESENT（包括可能重用，保守交人工）；仅成功观察且所有目标 PID 缺席返回 ABSENT。5秒读取上限、标准库实现，不引入服务/进程树监督/持久权限状态。

改动载体：[process_check.py](process_check.py)、[派发前候选差异](pre-dispatch-recheck.py.diff)、[准备入口候选差异](prepare-offline.py.diff)。两个完整候选文件后缀为 `.candidate`，不是已装配的新 Gate；没有执行其初始化逻辑。历史脚本、账本、发行、配置和 stop-required 均保持原字节。

### 验证及其上限

- **7项测试方法通过**：合法缺席/真实存在、权限错误/空输出/空表/非零/错误警告/畸形行/异常超时、坏输入、冷子进程 CLI 返回码，以及两个真实候选消费者的 guard 语句遇拒绝时不进入后续动作。消费者测试提取实际 guard 语句执行，不是完整新 Gate 运行。
- **真实只读成对**：受限环境 UNKNOWN；获准读取后58个旧具名 PID 为 ABSENT，当前核验进程为 PRESENT。只证明该时点、这些 PID，不声称任意子树或远程活动已停止。见 restricted-process-proof.json / readable-process-proof.json。
- 候选语句位于准备目录创建/派发前预算消费之前；没有通过“跳过检查”或旧报告复用来通过。模拟/实际对照均未执行 Claude/Codex/acpx 会话或模型。
- 本轮未更改产品 Java 或资源，659输入与冻结 JAR保持；不重跑与 operator 缺陷无关的 Maven，不叠加旧1813/210为新证明。未来装配选择与实际 dispatch 前消费仍待下一 Gate 准入；不将候选验证描述成真实主流程 PROVEN。

## 2. 会话超时：已确认的直接机制

原固定命令结果 `session_ensure` 显示45,025ms、timed_out=true、exit=-1、输出为空；领域唯一主因为 session_bootstrap_failed。记录无 prompt 请求 ID、无 prompt 命令事实，usage=UNAVAILABLE，不得填测量0。不是模型不返回 JSON，也不是170万预算耗尽。

源码显示 `DUAL_ACPX_SESSION_COMMAND_TIMEOUT_SECONDS` 默认45秒，`ensureSession` 使用它等待外部命令；到期后终止可观察进程树并固定超时。600秒 `DUAL_ACPX_COMMAND_TIMEOUT_SECONDS` 是另一配置，不覆盖该等待。**直接终止机制已经查清**，但不据此认为45秒应扩大。

原 native 链为：受审 acpx → 当前 Claude 环境 wrapper → `npx -y @agentclientprotocol/claude-agent-acp@0.70.0` → adapter → ACP initialize/auth → session/new → 本地会话记录 → 未来 prompt。会话记录在 initialize 和 session/new 完成后才准备，故“没有记录”不能反推 adapter 没有启动。acpx 非verbose时捕获但不透传子进程启动 stderr；45秒外层终止可能早于其自身 session/new 的60秒上限。因此历史空输出无法区分具体内部等待阶段。

## 3. 明确线索与不能下的结论

**已证线索**：当前 PATH 中 npx 解析到 `/usr/local/lib/node_modules/npm/bin/npx-cli.js`（npm10.9.0），不是另一路 nvm npm。安装源码先查 workspace local tree；无匹配包时，先 `pacote.manifest(... preferOnline:true)`，后查 npx cache。缓存存在不等于启动无需元数据访问。

[npm 离线控制流对照](npm-resolution-proof.stdout.json)直接抽取当前安装的两个函数，在 VM 中将 manifest I/O 替换为记录器：空 local tree会咨询元数据，精确本地版本则不咨询。没有运行 npm CLI、安装包、网络或 agent。它证明上述控制流，不证明历史发生过哪次网络访问，更不证明本次超时由网络导致。历史 npx/npm 全量字节没有快照，也不能把当前源码结论无条件外推为历史动态事实。

检查了原失败55秒窗口的已有 npm/Claude 日志可用性和保留的拒绝记录，未找到可归属于本例的记录（后者只读一次、10秒上限，实际成功完成）。**无日志不是无故障证明**。原始记录缺初始化阶段、子进程 PID/完整错误输出；在不新增 native 启动验证的约束下，无法把元数据解析、受限运行环境、ACP 初始化/会话等待等候选唯一化。

结论：不能宣称“已查清为网络/沙箱/模型/版本问题”；现有证据只支持直接超时机制与启动链风险。继续无定向案头排查无法恢复未记录的数据，ROI 低，本轮止于明确证据边界。未改超时、入口、包缓存、权限或客户端版本。

## 4. 六维自查与未完成项

Boundary Review：**PASS_OFFLINE_PROCESS_FIX / OPEN_SESSION_STARTUP_CAUSE / REAL_RUN_NO_GO**。这是作者自查，非独立CC。

- Declared by：用户本轮仅离线修正、查因和成对验证授权；禁止新增模型调用/新例。
- Proven by：旧/新实际EPERM对照、7项常驻测试（本离线包）、冷子进程/两候选消费者、实际读表正反、原命令和源码身份；模拟 npm 对照另列。
- Consumed by：两个候选入口只消费同一 require_known_pids_absent；原领域状态/用量/恢复仍由原唯一 owner 产生。
- Enforced before：两个候选消费者在失败时抛出并阻止其后动作；未来 Gate 完整装配/执行未做，不声称已对真实派发生效。
- Failure route：UNKNOWN/PRESENT均NO-GO；不误认已停止、不重试第七例、不回填UNAVAILABLE、不移除停止标记。
- Cannot-bypass：旧实际误放行对照、新拒绝、合法正向、冷CLI及实际消费者的拒绝后零后续动作；完整未来 Gate 接线仍须重核。

第七例现场与本轮输入均保持、无产品改动。进程准入缺陷属于会影响启动安全决定的第一类，不能登记后跳过；候选已经修正并验证，未装配运行前不得标为已部署。原生启动原因仍是未决前置，不以新安全平台或永久兼容来填洞。

## 5. 下一步

**先交 CC 复审本离线修正及证据边界。** 不申请新例，不自动申请预算。要把超时进一步唯一定位，最小必要增量是使用既有详细启动日志做一次有界、禁止 prompt 的原生初始化观察；那会超出本轮纯离线范围，本轮未做，须另行明确授权与前置。应优先复用现成 verbose/原命令记录，不先加产品遥测框架。复审也可判断是否已有足够静态证据或更简单的既有日志入口。

本报告不承诺未来客户端兼容或完整主链成功，不恢复第七例，也不自动开启第八例。
