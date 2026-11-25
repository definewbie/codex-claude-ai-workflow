# D04 原生补证 CC 评审处置（2026-09-29）

结论：接受用户带回的**材料层 PASS**；D04 仍为 DESIGN GAP，未冻结、未获产品实施授权。本次仅核对已有证据与修订材料，新增 native session、真实模型调用、子 agent 均为零。旧评审包不改写；三份受审设计文档的原字节保存在本目录 `before/`，原评审及本次定点核对分别见 [原文](cc-review.txt)、[source-checks](source-checks.json)。本处置为作者自查，未经新一轮独立评审。

## 1. 接受的结论及适用范围

- Claude Code：既有 GLM 路由的真实必要读取，与本地协议桩驱动的原生负例/开放 Write 对照互为补证。CC 接受这些证据达到所列切片的冻结前证明门槛。
- Codex：已固定版本、工具宿主和配置下，文件/受限执行、patch、提权及配置诱饵切片得到 CC 认可。
- 上述结论均受实测版本、入口、目标和工具集合限定；不是任意文件、工具、后代会话或版本均安全的证明。adapter 全入口绑定、回执、收尾、D06/D07 恢复集成仍是实施后验收义务。
- D04 当前未答问题是 Codex **额外能力上限**是否可被既有接口强制，其中 collaboration 已观测到暴露，网络工具按 LOW-1 纳入同一核验。不能把“存在”直接判作可执行，也不能凭配置开关或未暴露就声称不可绕过。

## 2. 两项 LOW 的处置

### LOW-1：网络工具上限，接受并补明确证据边界

已离线核实旧 `config-probe.json.command` 含 `web_search="disabled"`、`features.multi_agent=false`、`features.multi_agent_v2=false`。两次实际请求的 `additional_tools` 均只有 functions、clock、collaboration；exec 描述及已返回的 `exposedNestedTools` 均列 apply_patch、clock__curr_time、exec_command、view_image、write_stdin，没有网络工具。

这证明该切片的配置与工具暴露事实，**没有证明隐藏网络工具调用会在外部副作用前被 native 拒绝**。留存协议 schema 的 `definitions.ToolsV2.properties.web_search` 只是可表达字段，不能推导本轮有效能力。后续与 collaboration 同查：逐个区分声明、实际注册/分发、原生拒绝与外部副作用；优先查现有接口及强制消费链，不新增网络实验项目，不重开 R4/Phase B，不请求真实外网服务。

若缺少可保证零外部副作用的验证前提，就停止在静态核验，不能通过“发一次看看”验证禁止能力。若必须新增外层机制、改变 authority 或扩预算，按详设 §8.6 交用户决定。

### LOW-2：后续补证使用修正 guard，接受并限定其覆盖

后续 native 补证必须从旧包 `probe-guard-repair/` 修正副本派生新目录与新 Gate；旧 driver 只作为历史证据，不得再作运行入口。已有 `runDeniedWrites` 只检查命令结果的 `exit_code`，其 5 项离线注入不能代替 collaboration/网络工具的结果判定。

任何新动作进入 native 前，先定义对应 typed 成功/拒绝/未知结果与副作用前置守卫，补该动作的离线故障注入；意外成功、未知结果、transport 异常或预算耗尽必须阻止后继，不能仅收尾汇总。已有 guard 修正仍未经过新增 native 运行，不追认旧 Gate 完全机械化。实际子 agent 启动仍受用户禁止约束；执行后再中断不是“零启动”的证明。

## 3. 对评审中三处推断的收窄

1. Claude 原生拒绝文案的 “in subagents as well as here” 是引擎自述，不能当作实际运行子代理的 sibling 证明。本轮没有启动子 agent；已证明当前会话的隐藏 Agent 及其他所列调用被拒，未实测后代会话继承。
2. `is_error?: boolean` 只表明字段可省略，不能单凭省略判成功。既有离线重判还要求正确消息类型、实际 call ID/结果、oracle 内容与文件身份相符，并保留错误/缺失结果的负对照；没有以类型声明替代正向行为证据。
3. collaboration 是实际请求 `additional_tools` 的独立顶层 namespace；不是 Code Mode 的嵌套工具。“All nested tools are available” 不能证明它可经 `tools.*` 调用。真实暴露本身成立，但调用通道及限制必须分别核验。

## 4. 下一步与停止边界

先沿已固定 Codex/native/adapter 的既有接口定位 collaboration 与网络工具注册和分发的强制限制。若只读调查能够确定候选，再形成同一有界补证的具体 Gate、修正 guard 和增量预算；旧预算已经结束，本次不新开 native 会话。

证明成立后才提交 D04 冻结复审；证明不成立或成本收益偏离，则带具体证据和选项交用户决定。材料 PASS、文档补 LOW、任何静态接口存在均不自动转化为冻结、实施或运行授权。D01/D02/D03/D05–D10 九项设计冻结不变，W01–W09 不因此完成。

本次没有改变 fact owner、transition 或产品模型：执行事实仍来自 native 原始结果；诊断脚本/CC/本文仅解释证据；实施后由既有 adapter/runtime 消费绑定与结果，在副作用前守住能力上限。其 cannot-bypass 缺口仍登记为未证明，而非用材料层评审充抵。CC 第 7 节未核验范围继续保留；本次定点核对没有替代全量脚本审计或产品回归。
