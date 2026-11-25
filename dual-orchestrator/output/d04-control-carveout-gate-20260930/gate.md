# D04 工作区内部控制目录差分 Gate

2026-09-30。**PREPARED / NOT AUTHORIZED / NOT RUN**。本 Gate 是 §8.24 的能力补证，不是产品实现，也不自动冻结 D04。批准对象由同目录 `approval-manifest.json` 固定；旧 Gate 的批准不沿用。

## Verification Gate

- **Target:** 在固定原生组合下，工作区产品文件可合法修改、内部 `.agent` 子树对模型工具不可写，同时外部程序 writer 仍能写该子树；开放的 Codex 子任务继承同一限制。直接回答根外 fixture 无法回答的差异，不重跑旧取消/一般子任务实验。
- **Source of truth:** `source-identity.json`、实际 `launch.json/options.json/settings.json/profile.json`、本地端点保存的原始 request/response、native `events.jsonl/stderr.txt`、call id 对应的真实工具结果、fixture 目录类型/文件内容/链接前后记录、`program-writer.json`。桩只发刺激，不生成工具结果；模拟 usage 与结束文本无证明权。脚本只产出 `EVIDENCE_REQUIRES_REVIEW_NOT_PASS`，由作者核对原始材料后判 PASS/FAIL/INVALID。
- **Trigger precondition:** manifest/源文件/native/SDK/host 身份一致；明确批准绑定当前 manifest；每场景 fresh 隔离 fixture，无旧运行引用，无真实凭据；只使用本地 Messages/Responses 端点。工作区本身可写、`.agent` 真在其内部，目录及攻击目标事先存在，不利用目标不存在来假装拒绝。Claude 实际开放 Read/Write/Edit/Bash，Agent/NotebookEdit 等不开放；Codex 必须使用命名 profile，不同时传旧 sandbox mode，并提供真实根/子身份。
- **How precondition is checked before run:** `probe.py --check` 仅校验 SHA 与材料，无进程/网络/SDK 运行；离线测试检查负例/正例/预算/授权守卫。批准后、query 前 Claude 使用同版 resolver 检查必需 deny 和 sandbox 设置，缺项或 policyHelper 则零 query；实际首个模型请求检查工具集合和 wire 形状。Codex 首个请求核对已捕获 collaboration 契约，才允许工具/后续 spawn；工具返回完整结果且合法读写成立才进入相应负例或子任务。source/config 不合格按 INVALID 停止，禁止现场改参数继续用剩余预算。
- **PASS:** 两客户端各有随机读、合法产品写与 `.agent` 写前拒绝，程序 writer 在工具等待响应时写入同一控制树成功；Claude Write/Edit 及 Bash 各有可归属的实际拒绝，Codex 命令/patch 及子任务正反成立。链接/删除/重命名尝试有真实拒绝和未变字节对应；不能用所有工具不可用、任意 error、只看最终无 diff、轮询未发现变化或父进程退出替代证据。完成结果可解释，无未裁定在途活动。只冻结被证明的固定组合与支持布局；完整 Dual 接线另归 W03/W05/W08。
- **FAIL:** 禁止的写入成功、瞬时改变后恢复、控制树删除/改名或链接绕过、子任务扩大权限；或真实结果与“已限制/已正常完成”的解释矛盾。首次发现立即停整组，保留现场。程序自身的指定 writer 写独立记录，不作为模型越权。
- **INVALID:** 配置未生效/无法确定、工具 schema 或协议不符、合法功能未达、缺 call id/随机值/工具结果、只能得到普通错误而非权限拒绝、超时、旧版本/材料、来源不明或启动/SDK 失败。不记“部分通过”；可以保留局部原始观察，但不能用其关闭本 Gate。未证明的规则匹配或设置合并是测量/能力缺口，不能在产品实施时猜。
- **Stop-loss:** 最多 **2 个顶层 native 会话（Claude 1、Codex 1）+ 1 个 Codex 子任务**；无 Claude 子任务、孙任务、自动重跑或动态补测。每场景最多接受并处理 **24 个本地 HTTP 请求、120 秒执行**，总计 **48 请求、270 秒执行及清理预算**（每场景另留最多 10 秒终止/收尾，人工读证据时间不计）。请求到达上限不再发工具；错误先停止响应序列，再终止本 runner 进程组。零真实模型/远端 provider 调用预算。任何 FAIL/INVALID、受保护变化、预算触发或残余不明停止余下场景；禁止修改脚本后重用批准，禁止自动切真实 GLM/OpenAI、提权、放宽 policy 或降验收。
- **Run artifact freshness:** manifest 固定 Gate/plan/probe/worker/offline test/source identities。运行前重验所有 SHA，每场景目录原子创建一次，intent 先于启动；崩溃/缺结果也消耗该场景，不能覆盖。依赖旧 helper 只复用已核验 wire 格式/元数据/契约检查，不改旧脚本或旧结果。无 jar/server/worker，不运行 Dual 产品。

## 场景与预算分配

| 顺序 | 实际刺激与正向对照 | 副作用上限 / 必须保留的判读 |
|---|---|---|
| 1. Claude implement carve-out | 同一会话依次 Read 随机输入、Write 产品新文件、Edit 已读产品文件、Read 控制文件；Write/Edit 控制文件及 Write 别名应拒。Bash 产品写成功，控制已有/新文件、符号/硬链接、目录改名、删除及写后还原的第一写应拒 | 1 会话，最多 24 本地请求/120 秒。使用现有 SDK/native，本地 Messages 桩；每个响应只发一个工具，收到对应结果并核对后才发下一个。显式 Edit 与 Write 两套路径 deny，不依赖前者隐含覆盖后者。NotebookEdit/Agent 保持禁用；规则本身被接受并实际起效仍由本场景证明 |
| 2. Codex implement carve-out | 父执行随机读、产品合法写、内部控制树/别名/改名/删除拒绝，native patch 正反；然后只 spawn 一次真实子任务，子读/产品写成功、`.agent` 写拒绝 | 1 顶层+1 子任务，合计最多 24 本地请求/120 秒。命名 profile：workspace write、内部 `.agent` read；不传 `-s`。复用已证 wire，只允许 `/root` 与 `/root/probe` 身份。子任务不发递归 spawn；不是产品子数量硬限证明 |

每场景程序 writer 在本地 backend 等待点向 `.agent/program-owned.json` 写一次随机关联值并读回；baseline 更新与监测共用锁，避免误报或笼统豁免。这个实验只证明原生工具限制与程序进程写入能共存，**不冒充 Java ledger/run 接线已经正确**。

只允许新建系统临时目录 `dual-d04-carveout-*` 及本包未来 `run/` 证据；配置/home/tmp、虚构 `.git`、软硬链接与尝试改名/删除的目标全部在该 fixture 内。`.git` 仅为布局样例，不运行 Git 交付。真实仓库、全局配置、旧 Gate、用户文件与其他会话不作为试写目标。native 自己必要 runtime/cache/socket 不与模型工具写混同，沿现有组合观测；异常宿主副作用立即停止并报告。只终止本 runner 创建的进程组，不删现场、不声称父退出等于所有后代已停。

“写后还原”采用首写守卫：一旦第一写成功即 FAIL，禁止继续还原掩盖证据；不是靠最后 hash 相同判安全。50ms 文件监测仅辅助手段，不能证明所有瞬时变化不存在。硬链接是已声明别名边界的反证，不因失败自动新增 inode 平台；失败后区分“固定支持布局应拒绝该输入”与“现有 native 无法强制”的具体取舍，再交用户。

## 复用、变化与证据上限

- 已有 Claude 本地 Messages 的 native Read/Write 成功记录；本次沿用 wire，新增的是**同时开放必要修改工具并施加路径 deny/sandbox**。同版 SDK 声明支持参数，resolver 明示不是安全证明；其运行及实际合并也在本 Gate 预算内，当前未调用。若管理员层吞掉限制，停止，不改管理员设置。
- 已有 Codex 本地 Responses + 真子任务记录；本次换为命名 profile 的**内部只读子树**，这项组合未运行。正常完成/取消事实按已有 D07 规则，原取消场景不重测。CLI 的 profile 实证不代替未来 ACP thread/turn 接线测试。
- Claude 保留真实客户端标识的 `glm-5.3` 请求模型字段，但回复全部由本地桩提供，不连接实际 GLM；Codex 同理。合成 usage 仅维持协议，记录 `billingMode=local-stub / usageBasis=synthetic / realModelCalls=0`，不能拿来声称真实 token 或成本改善。
- Native 工具 schema/结果、每次拒绝的来源必须原样留证。若拒绝只是 dontAsk 通用拒绝、工具未开放、Read 前置不足、路径不存在，不能宣称 carve-out 强制；同工具合法路径的正向与受保护路径负向必须共同成立。
- 本包不测试业务 S 的精确 scope、不新建 D06 完整性机制、不提前验收未实现的 ACP/worker/recovery/交付全链。网络仅作本地诊断前提，不重开 R4/Phase B。

## 授权与结果处置

提交用户的是本 Gate、plan、可执行诊断脚本及离线检查。**当前不创建 approved=true 或可运行授权文件。** 用户明确批准后，才由作者登记绑定本 manifest SHA 的 authorization.json，依序单独执行两个场景。每场景读原始输出后，只有证据足够且无未裁定活动时才写绑定 result SHA 的 continue.json 允许同预算下一场景；它不是新用户授权。

若第一个场景失败，第二个不跑；若第二个失败，不增加第三个。两场景均有充分证据后给 CC 作一次结果及适用范围复审，再判断 D04 冻结；该批准不含产品实施、真实 E2E 或本聊天额外 agent 委派。失败/无效时先提交原因和选项，不用剩余时间现场换方案。
