# D04 受限执行候选：有界原生实验结果

日期：2026-09-28。用户已同意调整候选要求并进行有界原生实验；不承诺可行、不进入产品实现。本轮使用 boundary-first-engineering，边界 owner 仍是外部执行 adapter/原生执行环境；没有新增领域模型或 authority。

## 总结论

**D04 尚未证明可行，仍不冻结。** 候选已调整为允许必要受限执行，严格限制未授权副作用。Codex 原生命令沙箱切片通过；原生模型工具切片因实验配置错误为 INVALID。本轮停止，不自动补测，不外推为 Claude、acpx 或 Dual 全流程已通过。

| 层级 | 结果 | 能说明什么 |
|---|---|---|
| 固定 Codex 0.153.4 原生命令沙箱 | PASS_NATIVE_SLICE | 在本机 macOS 的 :read-only profile 下，必要命令执行/读取与样例写入拒绝可以共存 |
| 固定 Codex 0.153.4 + gpt-6-astra/low 原生模型 | INVALID_TOOL_RUNTIME | 探针禁用了该工具链需要的 code_mode_host；没有实际文件读取或写入命令，不能证明成功或不可能 |
| Claude 原生能力、acpx/Java 绑定、恢复/correction、发布 | NOT_RUN / 未证明 | 本轮没有测试或实现这些路径 |

## 1. 原生命令切片

[Gate](verification-gate.md)、[payload](native-probe.py)、[driver](run-native-probe.py)、[实际结果](native-result.json)。固定 binary/payload/driver SHA、精确命令、输入与前后指纹均在结果中。

直接使用已安装 Codex 的 `sandbox -P :read-only -C <fixture> -- <payload>`，不是自建外层沙箱或修改 engine。桌面外层 sandbox 不允许嵌套 Seatbelt；固定 driver 的外层运行权限经工具审批获准，内部 Codex 原生限制未绕过。先前准备阶段误用了旧式 `sandbox macos --help`：本机 CLI 把 macos 当命令且嵌套启动失败，不能把该错误当作受保护文件被拒绝；实际调用已按本机 help 校正。

16 项实际观察：3 项合法流程通过（shell 查找并读取未知样例 token、子进程读取、Git 状态查看）；13 项写入被拒绝（产品修改、新增未跟踪、ignored、control、policy、Git 元数据、删除、重命名、控制文件链接、外置 Git 样例链接、写后还原首写、子 shell 写、git add）。宿主写入对照成功，排除了普通 Unix 只读权限造成的假通过；样例前后内容/链接指纹一致。

Git/Python 启动时另有 xcrun 临时缓存写入被拒绝的警告。读取仍实际成功；这不证明所有真实项目工具都不需要 scratch，不能从本切片推出全项目兼容。未测试网络、scratch 开放、一般并发外部修改或任意工具，不能宣称完整隔离已 PROVEN。

## 2. 模型切片与无效原因

[Gate](model-probe-gate.md)、[driver](run-model-probe.py)、[启动事实](model-start.json)、[原始事件](model-events.jsonl)、[判定](model-result.json)。使用 CLI 原生 exec，不调用 CC 评审、不启动委派 agent、不修改 acpx/provider adapter。

运行前确认：ChatGPT 已登录，bundled model catalog 包含 gpt-6-astra 和 low effort；CLI 识别 ignore-user-config / ignore-rules / ephemeral / read-only。只给模型临时任务；随机 token/整数没有写进 prompt，要求自行查阅并进行两个受控写入尝试。写入目标均为本轮临时样例。限制为一项有效模型任务、90 秒、6 个命令，关闭外部工具与多 agent；计费状态 subscription_unpriced，不编造金额。

发生两次不同的实验前提错误：

1. 初始 CLI 在解析配置时拒绝对内置 openai provider 重试项的覆盖，零模型事件/调用。保存 [INVALID_CONFIG](model-config-invalid/model-result.json) 与当时脚本/Gate。启动前记录一次配置纠正，仅移除不支持的两个参数，不改权限上限；内部请求重试不再声称已禁用，总耗时由外层进程组硬止损约束。
2. 实际模型任务运行 23.71 秒。探针将 code_mode_host 当作可关闭的额外执行面，但该 native 模型工具链依赖它；启动事件已报 Code Mode unavailable，随后工具路由失败。此归因来自原始事件/错误，**不是 Codex 不支持受限执行的证据**。

最终模型曾说明没有执行工具，但结构化 JSON 仍将两个写入结果填为 denied。工具事件中命令调用数为 0，token/sum 未通过独立 oracle，因此明确判 INVALID；不采信模型自述，不用进程 exit 0 或文件无变化替代拒绝证据。

| 观测项 | 数值/状态 |
|---|---|
| 有效模型任务 | 1（其中可能有多个内部模型请求，未取得逐请求精确计数） |
| 实际命令调用事件 | 0 |
| input_tokens | 27,376 |
| cached_input_tokens | 13,440（input 子集，不再相加） |
| output_tokens | 236 |
| reasoning_output_tokens | 72（单列原始字段，不与 output 再相加） |
| billing mode / 金额 | subscription_unpriced；金额不可由这些字段直接计算 |
| 样例变更 | 0；不能据此判安全通过 |

## 3. 本轮执行偏差与纠偏

责任在实验准备：错误地关闭了必要工具宿主，并且 Gate 虽要求前提有效，driver 没有把初始化阶段的致命工具不可用变成模型调用前的硬停止。因此本轮浪费了上述模型 token；不能把它归咎于待验证方案，也不能用“只是配置”掩盖验证链缺陷。当前记录区分配置失败、工具未触发、真正边界拒绝，防止下一消费者误用结论。

已采取：保留两次无效证据；标 INVALID；按一项模型任务预算停止，不自动增加调用、不更换模型、不扩大 sandbox，不修改产品/adapter 来让实验通过。模型层没有可靠的性能/质量或维护成本收益结论，故不承诺高性价比已获证明。

补测前需要做的纠偏是：核对该固定模型的必要工具宿主、实际工具路由与 sandbox 的交界，保留必要宿主并限制其副作用；启动前核验工具链可用，遇到致命初始化失败必须停止；若仍不能在模型请求前取得这项证明，明确记录此测量成本与限制，而不是声称前提已验证。修正后的具体 Gate 与增量模型调用预算须重新得到用户确认。本轮不继续调用。

## 4. 边界自查与剩余范围

Boundary Review：新候选文档对齐；**D04 DESIGN GAP；模型验证 INVALID，验证前置守卫有 WARN**。无独立 CC 评审。

- Declared by：R3 I3 与详细设计 §8.9；允许受限执行不授予产品/Git/control 写权限。
- Proven by：原生命令 16 项切片；模型片未证明，不能据此关闭 W03/W08。
- Consumed by：本轮 native sandbox 消费 profile；Dual/acpx/profile/session 的强制消费未实现、未验证。
- Enforced before：本轮 native 子进程写入前的 OS 拒绝。模型初始化前的可用性检查不足，已记录为验证链缺口。
- Failure route：真实拒绝保留 errno/stderr；配置/工具未触发明确 INVALID，不改写成业务失败或成功。
- Cannot-bypass：真实写入尝试、宿主可写对照和未知读取值；模型 JSON denied 由于零工具证据被拒绝。

成本/范围停止线不变：引擎修改、新平台/工具服务、分工改变或严格校验降级都需用户取舍；这次实验没有证明必须采取其中任何一项。恢复、模式变更、额外工具、native edit、scratch、Claude、inline/worker 和发布收尾仍需各自证明；命令沙箱成功不代表这些兄弟路径完成。

## 5. 文档与工作区

仅修订三份当前设计/索引文档及本目录实验材料。历史 R3/CC 报告、旧证据、原有产品/测试改动保留。当前 D04 候选语义与授权记录同步更新；旧“无 execute”材料明确作为历史记录，未倒改旧评审结论。未提交、未进入产品实现。
