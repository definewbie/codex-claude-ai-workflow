# 验证出口与控制链

## 对象和唯一 owner

Definition：沿用 `agent-lifecycle-terminal-delivery@5`；本次不改流程定义。
Instance：原 start（无 --run）已产生 fresh workstream `ws-20261026T142203Z-7SNA`；初始清单固定于 initial-ledger-sha256.json，尚无模型执行或业务批准。
Execution：原 run/occurrence 固定 R01/R02/R03 与工具观察；operator 回执是实验操作证据，不是第二业务账本。
产品阈值：现有评估服务唯一生产、现有 typed result 承载，CLI 只读显示。兄弟 workbench 当前显式映射旧字段，不要求本次新增 Web 能力，旧行为须保留。

## 逐条出口

| 条款 | 首选执行和证据 | 缺失后的合法路线 |
|---|---|---|
| AC-1/2 | Java 服务 JUnit 与十进制独立算例；当前源码、断言与实际程序工具输出/报告对应 | 实现错误走原 repair/self-test/独立验收，不用模型推导代替执行 |
| AC-3 | 未知成本/无结算/未确认条款/开放期末/非法名义金额成对用例；typed 结果与 CLI 无精确数值 | 不可用保持不可用，不新增默认 0 或临时豁免 |
| AC-4 | 原 CLI 测试调用真实 service；非整除展示/单位/不可用用例；实际读取使用说明 | CLI 不另算；不自动扩到 Web、Portfolio、scenario |
| AC-5 | 固定基线默认完整 Maven 单轮 166 项/42 suites 全绿（含全部 11 个 MySQL/Testcontainers suite），见 baseline-full-receipt.json；宿主证明与未来模型执行分列；模型侧执行需 R02/R03 工具观察，独立验收读当前材料 | 缺环境先停止；operator 事后证据出口只有经当前合同重定 executor/evidence kind、作者复评和 EXECUTION 人批后才能使用 |

本需求未统一强制底层真实 process exit 字段。测试必须有程序工具观察及零 failure/error 的当前报告；原生 completed 或模型正文不足。接口确实提供 actual exit 时如实固定，不能把 reported 冒充 actual。未来合同若明确要求 actual exit，必须先证明所选方法能提供。

宿主 Java/Maven 通过不证明原生编码客户端能运行其工具。在真实 GOAL/plan subject 存在后，由原 `review record-readiness` 生产绑定实际条款、方法、executor、environment identity 的材料；本轮不造 schema 有字段但无真实生产者的引用。

## Transition 与每个失败前缀

| 权威前态与动作 owner | 持久化/副作用顺序与成功消费者 | 失败或重启解释 |
|---|---|---|
| 原脏仓库只读 → operator 固定隔离基线 | 原 Git archive → SHA 清单 → 独立测试副本；后续初始化只能读所选基线 | archive/manifest 半写不是可用包；原仓库不可被实验 target 替代 |
| 新包未批 → 用户当次 Gate/运行批准 | 固定 launch/预算/主体 → 具名新授权；当前 false；预算许可不能代业务许可 | 旧 true、旧第八例 checkpoint/余额/stop 授权均不能借用；新 stop 仍拒绝 |
| fresh 只读核验 → 原无 --run 初始化 | 新原程序实例/初始 current/gates → 保存身份；其后原用户 GOAL 门 | 任一核验未知零派发；初始化部分写按原 D06，不手造 ID 或改账本 |
| 当前需求/计划批准 → 原 compiler/qualification | 当前 subject 人批 → GOAL/EXECUTION 编译 → R01/claim → 同 E/预算/身份重核 → R02 intent → provider | 无写批准零 implement；orphan R01 零资格；R02 后外部不明按 D07，不自动重发 |
| 原 provider 完成 → 唯一 validator/outcome | typed 输出/测试观察 → 唯一 outcome 先固定 → 合法发布 → R03 → L03 → 下一 review | 不合格零新 sidecar/推进；R03/L03 间按原固定字节补齐；冷读不重跑 provider |
| 验收 ACCEPTED/ACCEPT_WITH_NOTES → 原交付门 | exact candidate → 当前最终人批 → L07 intent → 真实 apply/PID/退出 → APPLIED | 没批准零 target 写；UNKNOWN 不伪造停止；不得因 notes 自行拒绝合法验收 |
| APPLIED → 用户或另行明确委派 commit → 原 finalize | 精确 base/tree/clean commit → 原只读核对 → COMMITTED/COMPLETED | apply 不等于 commit；丢回执先读原记录；终态只有观察，不能 reopen/retry |

权限请求沿原 B04 C/X，abort/cancel/resume 沿原 D06/D07，不新增 outbox。实验预算停止不更改领域合法结果；历史样本与旧 raw 均不补写。旧二进制/旧 ledger 不作为本次新执行依据，不建生产兼容解释。

## 六维检查与成熟度

- Declared by：用户确认此产品切片、原 AGENTS、当前要求；当次运行另批。
- Proven by：本包基线默认完整单轮 166 项/42 suites，先前单轮 114 项与单独 13 项分层不相加；另有独立算例、发行字节对账；新阈值与真实 E2E **NOT_IMPLEMENTED / NOT_RUN**。
- Consumed by：未来原 service → result → CLI；合同/工具观察 → 原唯一 outcome → R03/L03；交付 → 原 finalize。
- Enforced before：本案真实 false 授权已在 target/step/Java 前拒绝；替身整链验证同 E、固定字节/主体/预算、pending/终态/部分写入的拒绝。实际授权后的 Java/native 启动与产品业务副作用仍 NOT_RUN；不能以替身称真实 E2E 已闭环。
- Failure route：原 typed failure、D06 固定补齐、D07 未知人工核对；本包前提不齐 NO-GO。
- Cannot-bypass test：本案 29 项装配/observer 含未授权、错主体、输入漂移、pending/终态/部分写入、冷读、消失窗口及同 E 正向；3 项隔离反证另列。新产品未知成本不可产数、CLI 不独算、scope 外无修改、交付 parent/tree 与真实合法链仍待未来实施/执行；24 项预算反例只证算术。

Review/Reflect：完整基线和 fresh 离线装配已通过自查；当前 WARN 为完整装配尚待 CC 复审和当次预算/运行授权，真实执行与新产品能力仍未证。沿既有规则核对，不扩产品、不把替身测试写成真实 E2E 收口。无独立 reviewer 已执行的声明，本轮自查不冒充 CC。
