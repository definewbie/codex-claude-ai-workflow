# D04 创建硬链接与角色布局：案头核验

2026-09-30。范围：用户同意继续核对创建硬链接的既有权限检查，以及各实际角色配置维持布局条件的能力。**零 native/provider/探针/子任务、零产品实现。D04 仍 DESIGN GAP。** 本轮是作者核验；CC 上轮确认的 Gate FAIL 保持原判，旧包保持原字节。

## 1. 结论

**不能采纳“按只读/implement 两种角色即已判定可行/不可能”的结论。** 已证预存硬链接绕过不变；运行期新建硬链接仍未知，但已找到 OS 的副作用前检查点与本机明确的 `file-link` 规则语法，所以“同用户同设备必然无机制阻止”不成立。也不能反向把“有检查点、默认拒绝”当成当前组合已经禁止新链接。

应按**实际工具面、实际文件权限与完整可写根**区分条件。纯读取的固定 Claude 组合与带受限执行的 Codex 只读组合不是同一种执行能力。即使新链接被拒绝，预存别名的可信准备、程序新建/替换控制文件以及每次重连的条件仍需具体接线；这不是新监督平台的授权，也不是完整 D04 已可冻结。

## 2. 证据：创建与写入是两个不同操作

本机为 macOS 15.6.1、Darwin 24.6.0，uname 内核标识 `xnu-11417.140.69~1/RELEASE_ARM64_T6031`，见 [host.json](host.json)。核对对应 Apple 公布 tag；不宣称重建/验证过正在运行的内核二进制，也未读取私有 Seatbelt 实现。

| 来源 | 支持的事实 | 不能推导 |
|---|---|---|
| [Apple 同版 vfs_syscalls.c](https://github.com/apple-oss-distributions/xnu/blob/xnu-11417.140.69/bsd/vfs/vfs_syscalls.c)，本地 sources/xnu-vfs_syscalls.c:5791–5822 | `mac_vnode_check_link` 接收源 vnode 与目标目录/名称；失败先跳出；之后还有源 LINKTARGET、目标 ADD_FILE 检查，最后才 VNOP_LINK。link 与 linkat 汇入同一入口（:5953–5967） | 同 UID/同设备/可读源＋可写目标不等于全部许可已通过；也不能仅凭该调用断定 Seatbelt 会拒绝 |
| [Apple 同版 mac_vfs.c](https://github.com/apple-oss-distributions/xnu/blob/xnu-11417.140.69/security/mac_vfs.c)，本地 sources/xnu-mac_vfs.c:1529–1549 | MAC_CHECK 把源 vnode/label、目标目录/label、component name 交给注册策略，返回错误 | 此处不是私有 Seatbelt 对 file-link 的具体映射；宏/运行条件与有效策略仍有证据上限 |
| 本机 `/System/Library/Sandbox/Profiles/appsandbox-common.sb`:10–11、82–83；application.sb:10，摘录及 SHA 见 [local-source-index.json](local-source-index.json) | 系统策略显式出现 `deny file-link`，并把 file-write* 与 file-link 分列于同一辅助函数 | 这些系统应用 profile 并非 Claude/Codex 的实际 profile；不得复制一条规则就声称现有 adapter 接口已支持，也不据文本判定默认/层级语义 |
| 固定 Claude 2.1.277 二进制嵌入的策略构造文本，见 claude-M4.txt / claude-V4.txt / claude-la.txt | M4 有 deny default、拼入 V4 文件规则；V4 按路径产生 file-write* 允许/拒绝，以及祖先 create/unlink 拒绝。全二进制字节未命中 file-link 字符串 | 字符串缺席不是“无链接检查”；deny default 也不是“已证拒绝链接”。未执行构造函数、未采集该次 Bash 的最终 SBPL，不证明其所有调用分支 |
| [固定 Codex commit 的 seatbelt.rs](https://github.com/openai/codex/blob/3d2ee51ca2d5db578f328aa75e20aa22c0197c9a/codex-rs/sandboxing/src/seatbelt.rs)，本地 :483–589、989–1005，与 base policy | 文件写规则展开为路径 allow 与子路径排除；base 有 deny default。构造函数按条件另加 platform defaults，Process 分支含系统临时目录写许可 | 不能把 profile 的显式业务根视作最终所有可写根；未证明该次具体配置触发哪个 defaults 分支。Codex 差分场景仍未运行，不能继承 Claude FAIL 或臆测 PASS |

公开源码保存在 sources/，URL、SHA、Git blob 核验状态见 [upstream-source-index.json](upstream-source-index.json)。下载有部分中断，均记录：mac_vfs、base/network policy 的 Git blob 已核实；vfs_syscalls、seatbelt.rs、preferences 取得 raw 完整形态文件但 API blob 核验未完成；read_only_platform_defaults 未取得完整副本，不将缺失部分用于完整策略证明。Web 返回的格式化行号与本地原始文件不同，以上行号以本地副本为准。到此停止扩大下载/源码排查，不把欠缺材料藏成完整来源链。

## 3. 按实际配置拆分，而不是只按角色名

现有材料的路径与 SHA 见 [role-evidence-index.json](role-evidence-index.json)；不运行旧包、不恢复旧样例。

| 实际组合 | 已有证据 | 对无危险别名条件的判断 |
|---|---|---|
| Claude 已测固定纯读组合 | restricted/events.jsonl 的 init 仅 Glob/Grep/Read；Write/Edit/Bash/Agent 请求实际返回无此工具，MCP/plugins 为空；旧合法读取证据保留 | 对这些已测模型入口，没有创建链接或写入别名的能力。不能仅因任意文件 nlink>1 就声称模型已有写风险；扫描不是这个窄组合安全性的唯一根据。仍不能外推后续恢复/额外工具或可信程序行为 |
| Codex 已测只读组合 | 旧 read_only/launch.json 明确 shell_tool、code_mode_host、unified_exec=true，-s read-only；父/子受限执行有既有证据 | “只读＝无执行工具”错误。若存在模型可写 scratch/平台默认目录，同设备的别名目的地就须考虑；是否创建被拒仍未知。旧普通写拒绝不替代这一点 |
| Claude 本次 implement 组合 | Read/Write/Edit/Bash；控制路径拒绝、合法产品写成立；预建硬链接写已 FAIL | 现有组合不能接纳该预存布局。新建链接能否被 OS 阻止尚未证明，不能认定运行期必绕过，也不能只加开跑扫描宣布可维持 |
| Codex 候选 implement/命名 profile 及未来只读迁移组合 | §8.24 已有候选；上一 Gate 首错止损未启动 Codex | 仍是待证组合；没有足够依据认定“同类一定失败”。原有根外子任务证据不能替代内部目录及所有可写根条件 |

本表保留原正向收益，不改 Claude Code 作者／Codex 评审分工，不把纯读 Claude 强加给两个客户端。候选不应以“所有只读都增加 nlink 扫描”代替必要性判断。

## 4. 可维持条件、owner 与拒绝时点

下面是判断现有方向是否成立的证明义务，不是本轮已冻结的新实现规格：

| 条件/owner | 拒绝时点及真实边界 | 现有证据/缺口 |
|---|---|---|
| adapter/transport 的限制绑定准备者：正确工具面、路径类型/真实路径、完整可写根、当前控制普通文件无可写别名 | dispatch/重连前拒绝不支持或来源不明的布局，不能到首个模型写入后才判准入失败 | 现有 §8.24 要求；一次 nlink 观察仅描述当时普通文件，目录/symlink/失败遍历不能借 nlink 洗白。尚无产品实现 |
| native OS/工具权限消费者：拒绝源为控制文件的新硬链接 | 创建链接副作用前；包括经可写 product 或 scratch 的目标、link/linkat 的共同检查链 | OS 入口存在；当前两客户端具体策略的拒绝结果未证，保持 UNKNOWN |
| 既有程序 writer 与 D06 写会话：新建/替换/导入仍维持保护对象与布局前提 | 控制事实发布、后继消费前维持条件；程序自身有控制写权限不等于任意导入的 inode 自动可信 | 现有 writer owner 不变；要明确创建新普通文件/原子替换与旧别名的关系，不能复用启动时那一份 inode 观察覆盖未来文件。没有提出全目录实时监控服务 |
| 既有 D07/恢复准备者：按同一受限组合重建调用 | 重连/新 turn/恢复的外部副作用前；来源不明停止，不自动放宽 | 已有设计义务，未来产品接线证明；不因本报告产生新的恢复 action |
| S/D/投影 | 只能检验 scope 或证据基准；不能证明写前 control 保护 | 保持分工。实验监控命中 BAD 不能升级为产品完整性网；不能作为选项 A 已经可实施的依据 |

必要的控制链是：可信准备→绑定核验→原生分派→创建链接检查→创建/拒绝→后续写入→原生结果→既有 run/失败消费者。创建被拒时不得发后续污染写；创建成功但后续写被拒不能混称“创建前拒绝”。若未来允许链接成功，必须另有证据说明别名仍不可用于修改当前权威控制事实，不能凭源路径 deny 推断。

程序并发合法写入与模型写入必须分辨；外部可信管理员破坏约定不等于模型可破坏约定。本报告未证明抵御任意同用户不受限进程，亦不增加此要求。没有采用监控、存储迁移、inode 平台或事后校验替代方案。

## 5. 复审处置、六维与下一步价值

- 采纳 CC：材料/FAIL 有效、首错停整组、原始观察正确，D04 不可冻结。
- WARN：只读角色一概无执行工具的前提错误；“现有机制原理上看不见创建链接”与同版 OS 检查入口不符。只能撤回必然性，不能转写为必然拒绝。
- WARN：事后完整性方案不具备已审的消费前覆盖/可信预期值规格；选项 A 仍为重大取舍，不能当现成退路。
- 修正 INFO：“符号链接写拒绝”只界定已测操作，不证明硬链接是唯一绕过；未运行的 rename/delete/祖先替换等仍未知。“要求被证伪”改为“候选在已测布局下未满足要求”。

| 六维 | 当前状态 |
|---|---|
| Declared by | 用户目标与 §8.24 写前 control 防护，不降低 |
| Proven by | 旧预存别名写 FAIL；本轮仅静态核验创建检查点/实际工具配置，无新能力 PROVEN |
| Consumed by | OS MAC 策略消费源/目标；工具与 adapter 绑定为不同 owner。产品整链接线尚未实施 |
| Enforced before | 内核存在链接副作用前检查；具体组合是否拒绝的格子仍空，不能称闭环 |
| Failure route | 不满足布局/策略时准备拒绝是设计要求；实验发现绕过已停。后继产品消费者仍待实施验证 |
| Cannot-bypass | 预存别名负例已击穿；无新建链接运行反证，纯读入口拒绝不能复用于受限执行 |

**案头核验至此完成，继续无定向翻源码收益低。** 若要决定是否保留现有 adapter 方向，一项有界的“同版原生环境中创建控制硬链接→核对创建结果和后续写权限”的验证有判别价值：拒绝将支持进一步收敛可信布局前提；成功并可写才为该具体组合的运行期绕过补实证。必须有合法产品/读取对照及所有有效可写根的范围说明，不新增全排列、真模型或监督平台；不同客户端不能互相替代。这里没有准备可执行探针、申请预算或运行授权；也不承诺这个单点通过便足以冻结。

独立复审宜只核对本次新证据是否纠正上轮过度推论、是否还有便宜且决定性的既有机制依据，以及最小必要能力差分。未取得证据前，不要求用户在 A/B/C/D 中先选，更不自动改为事后保护。未核验范围包括私有 Seatbelt 的精确操作映射/有效 SBPL、本地完整 Codex defaults 文件、运行期建链、其余未运行差分以及产品链路；没有转入 Rust/内核开发。

## CC 简洁提示词

请只读复审 `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/d04-link-creation-desk-audit-20260930/assessment.md` 及其引用，遵守 AGENTS.md。自行重建创建链接→权限检查→副作用链，核对各实际角色配置、源码身份和结论上限；判断上轮“只读无执行／implement 必然能建链”的推论是否成立，以及剩余验证是否有必要。按六维说明缺项与未核验范围，不预设可行或须迁移。不要运行 native/provider/探针、启动额外 agent、修改文件或进入实现。
