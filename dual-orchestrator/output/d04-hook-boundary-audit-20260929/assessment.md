# D04 hook 失败语义与配置边界核验

2026-09-29。结论：**现有 PreToolUse command hook 不能单独承担 D04 的故障时拒绝要求；停止该候选的实验准备，D04 仍 DESIGN GAP。** 这是同版源码推导，不是 native 运行结果，也不是“所有现有接口均不可能”的结论。未经本轮 CC 独立复审。

本轮依用户“进入下一步”继续只读核验：不运行 native/provider/注入/hook，不启动 agent，不修改产品、adapter、全局配置或 Rust 引擎。只更新材料。若需新机制或扩大 authority，按详设 §8.6 停等用户取舍。

## 1. 身份及范围

沿用上一轮 Codex 0.153.4 → commit `3d2ee51ca2d5db578f328aa75e20aa22c0197c9a` 的包/二进制/发布 provenance 关联；不重复声称签名链校验或可复现构建。新增文件均来自该提交，大小、Git blob SHA1 与 SHA256 核验记录在 source-index.json。

**快照仅覆盖取用文件，不是全树；跨文件定义不得假定已经核实。** 下载遇到截断时保留失败记录；output_parser.rs 两次下载失败，未作为证据，也未继续为它增加取材。下述启动失败/超时/退出码结论不依赖该文件。

入口不是一个完整运行器：core/hook_runtime.rs 把调用交给 codex_hooks，后者还跨 registry、engine、events/pre_tool_use、dispatcher、command_runner。前轮 CC 所说“补取一个模块”“唯一 blocker”不能作工作量或充分性承诺。

## 2. 失败如何到达消费者

以下路径相对 verified-source/codex-rs；旧 core/tools/registry.rs 位于上一轮 d04-native-source-audit 包。

| 切面 | 生产者与结果 | 唯一消费后的行为 |
|---|---|---|
| hook 未命中 | hooks/src/events/pre_tool_use.rs:76–89 → should_block=false | core/hook_runtime.rs:219–220 → Continue |
| hook 命令启动失败 | engine/command_runner.rs:244–257 → error=Some | pre_tool_use.rs:193–212 保持 should_block=false，只记 Failed |
| 写 stdin / 等待失败 | command_runner.rs:267–282、306–315 → error=Some | 同上，不自动拒绝 |
| hook 超时 | command_runner.rs:285–326 → error=Some(timeout) | 同上；hook 失败记录不能替代工具执行限制 |
| 退出码 2 + 非空 stderr，且同步 hook | pre_tool_use.rs:261–269 → should_block=true + reason | core/hook_runtime.rs:223–240 → Blocked；旧 registry.rs:577–590 在 handler 前返回 |
| 退出码 2 但无原因 / 其他非零退出 / 无退出码 | pre_tool_use.rs:270–290 → Failed，should_block 仍 false | Continue |
| 多个 hook | pre_tool_use.rs:115–118 对 should_block 做 any | 一个明确拒绝可阻止；一个失败不会自动阻止。全部失败且无人明确拒绝时继续 |
| 异步 hook / executor-scoped hook | engine/mod.rs:140–155、dispatcher.rs:124–187 | 不以其控制结果作为同步前置；不能拿它做强制 gate |

调用传递无额外“失败即拒绝”修正：hooks/registry.rs:212–214 → engine/mod.rs:399–400 → events/pre_tool_use::run；返回到 core/hook_runtime.rs:209–226 后只消费 should_block/block_reason。后者即使 should_block=true 但无 reason 也继续；本轮未证明这种组合能由当前 producer 产生，不把它冒充可触发事故。

前置 hook 发生在目标 handler 之前，但 hook 自身会启动命令进程（command_runner.rs:216–242）。其失败或超时不等于零副作用；之后工具仍可能执行。旧 spawn 顺序已证：线程创建早于初始任务发送。因此“hook 失败通知→adapter 再停止进程”没有证明能抢在创建前发生，不可当补救守卫。重试、重连、重启不应盲目重发结果不明的工具；本轮未运行探针，不存在新增恢复样本。

**反证口径：** 对一个合法且应禁止的 collaboration 调用，若唯一同步拒绝 hook 启动失败或超时、且没有其他 hook 明确阻止，现有 hook 层返回 Continue。目标 handler 的其他前置仍可能拒绝，所以不宣称必然创建；但禁止委派已经不能由该 hook 保证。这是足以否定“以 hook 单独承载该限制”的源码反例，不是一次已执行实验的 FAIL。

## 3. 配置信任能解决什么，不能解决什么

- hooks/registry.rs:42–51 接收 feature_enabled、bypass_hook_trust、ConfigLayerStack、plugin sources 和 shell 配置。engine/mod.rs:228–265 据此建立 handler 集合；禁用普通 hooks 时清除其启动警告/required load errors，可能仍保留 builtin，不能把 feature=false 泛化为所有 hook 消失。
- discovery.rs:94–192 从 requirements、配置层的 hooks.json/TOML 与插件来源发现定义；:794–838 区分 managed、trusted、modified、untrusted 与来源；:713–733 只把 enabled 且满足 trust/bypass 条件的 handler 加入执行集合。存在真实信任机制，不能声称“没有可信装配”。但 Dual 尚未证明自己的配置到该集合的完整绑定。
- managed requirements 有 required_load_errors（discovery.rs:65–70、217–241）。这是加载阶段事实；本轮未完整追踪其 session 准入消费者，不能认定受管加载失败一定放行。更不能把加载严格误读为运行错误也严格：进入相同运行器后，error 分支不按 managed/source/requirement 转成 Blocked。
- ConfiguredHandler 的字段及 PreToolUse 结果归并里没有 fail-closed 策略；现有同步/异步、来源信任、超时参数均不改变上述 error→Failed→Continue。此判断限于已读链，不宣称穷尽所有管理员或引擎接口。
- shell/脚本包装器最多控制自己正常运行时的退出，无法独自消除外层命令启动失败、原生超时等分支。为它另建监督/拦截机制不属于本轮顺手修补。

未核实 core 如何为当前 session 构造 ConfigLayerStack/feature/bypass 的完整链、配置文件与可执行文件的防修改、环境/shell 的完整限制。本轮已取得与这些信任问题独立的失败放行反例，故不继续扩大取材；即使这些均可信，已证反例仍成立。

## 4. 调用覆盖与兄弟路径

既有六个协作工具是 Function；旧 registry.rs:129–137 的默认 hook payload 与 V2 namespace wrapper（spec_plan.rs:1493–1507）提供相应调用前入口。但 matcher 使用规范名称及 alias，不能只用人读名称假定命中；旧 registry.rs:799–807 对默认/V1 spawn 特判，其他工具使用 flat name。

默认 payload 不涵盖 Custom。MCP 目标工具/custom 的覆盖不能由 Function 路径推出；完整逐工具覆盖本轮未完成。特别地，dispatcher.rs:209–223 的 McpTool 是 **hook 自己由 MCP 执行**，不等于被拦截的 MCP 工具已经全覆盖。新增 MCP hook 服务也不是已批准替代路径。

本地 web.run 注册抑制与远端托管声明抑制沿用上一轮分层结论，本轮不新增网络能力证明、不重开 R4/Phase B。无需通过扩大 MCP/custom 审计来挽救已在 collaboration 路径失败的候选。

## 5. 对前轮 CC 意见的处置与成本收益

接受材料 PASS、D04 不冻结；F2 快照部分性已显式注明。F1 的运行失败语义现已定位，结果是失败放行，不能转为 hook 运行实验准入。F3 的子代理策略继承作为有利静态证据回写详设 §8.6，但不接受“最坏情形已从权限逃逸降级”的结论：配置传递不证明实际强制、后续 turn 与模式切换保持限制。也不接受“维护面与 patched acpx 同量级”的无证成本判断。

下一取舍不在本轮执行：维持当前严格禁止原生自行委派的要求并寻找其他经授权机制，或在充分风险证据后由用户改变该要求。不能把放宽要求说成原目标已实现。没有尝试 Rust patch、外围 supervisor、代理服务、自动换版本或接受自行委派。

## 6. 六维与停止结论

| 维度 | 当前判断 |
|---|---|
| Declared by | D04 / §8.6 声明只允许必要受限执行及严格 authority；未改变 |
| Proven by | 同版源码证明明确拒绝的前置位置，也证明 hook 自身错误不保证拒绝；无新运行证据 |
| Consumed by | command_runner → pre_tool_use → core hook_runtime → registry 的消费者已定位；Failed 是观测状态，Block 是控制事实 |
| Enforced before | 明确 Blocked 时阻止 handler；对故障路径不成立，不能标整条边界 ENFORCED |
| Failure route | error/超时/异常退出会继续；潜在工具副作用不可由事后通知或自动重试抵消 |
| Cannot-bypass test | 未运行。源码反例已否定当前候选对故障路径的保证；不以一次正常拒绝测试覆盖这个缺口 |

**Decision：停止当前“单靠现有 hook 强制禁止委派”候选；D04 DESIGN GAP。** 不需要先花预算证明一次正常拒绝。建议由用户发起 CC 复审此次停止依据与是否漏掉同版、同 authority 下可消除反例的现成配置；若没有，应回到 §8.6 的具体取舍，由用户决定，而不是继续补脚本。其他九项设计冻结与全案未授权实施状态不变。
