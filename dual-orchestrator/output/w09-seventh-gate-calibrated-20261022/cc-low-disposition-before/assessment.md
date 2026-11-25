# W09 第七例 Gate：预算校准并入与离线启动核对

2026-10-22（Asia/Shanghai）。**离线核对完成；候选差异待合并 CC 复审；真实运行 NO-GO，未授权、未启动。**

入口：[本次 Gate](fresh-seventh-gate.md)、[机器摘要](preflight-summary.json)、[CC 提示词](cc-review-prompt.txt)。原 10 月 16 日准备包保持不变，本目录是同一个“第七次真实案例”的新准备候选，不是第八例。本次只做 P0 主流程验证准备与 P0.5 用量/储备对账，不修改产品代码、原生 patch、客户端设置，不新增模型实验或版本锁。

## 预算已按用户决定并入

上限 **1,700,000 token / 18 请求 / 3,600 秒活动时间**，预警 1,200,000、单命令 600 秒、储备式准入保留。

六例校准源：[报告](../w09-seventh-budget-calibration-20261022/assessment.md)、[22 次请求明细](../w09-seventh-budget-calibration-20261022/occurrences.csv)、[输入身份](../w09-seventh-budget-calibration-20261022/source-receipt.json)。原案例的三处只读归因修正不回填旧 authority。

- 九节点基础规划从 940,000 调至 **955,000**；架构复评从 75,000 调至 **90,000**，明确已有 53,075 / 70,687 两项实测。其他额度不变。
- 后半五节点 **625,000 仍是无真实样本的假设**；两次需求阶段修正成本不能被泛化为后半链修正成本，因此暂保留 150,000 修正储备。
- 主链 + 准备重跑 + 一轮设计返修 + 一次修正：**1,520,000 / 14 请求**，余 180,000 / 4 请求。
- 若需求、设计各返修一次：**1,610,000 / 16 请求**，余 90,000 / 2 请求。
- 第一次派发准入预留 **1,105,000 / 10 请求**；当前实际模型用量和请求均为零。预留不等于已消耗或必须花完。

`budget.json` 是本候选的唯一预算规划表；准入函数、观察器、runner 消费同目录表。准入语义未变，只更新额度、依据、场景与断言。真实用量 SoT 仍是固定 execution/occurrence，不是该表或 observer。

## 实际离线核对

| 项目 | 本轮结果 | 证据与限制 |
|---|---|---|
| 储备算术 | **21 项通过** | 原 13 项加设计/双循环、精确 token/请求/时间阈值及重复路径；直接断言 1,105,000 / 1,520,000 / 1,610,000 与请求数。见 budget-dry-run.json；不证明原生单请求上限或真实成本 |
| 观察器 | **12 项通过** | 当前日志优先、历史追加、同号重用、修正分计、坏帧/部分/缺固定完成等；与旧受审观察器代码和测试字节一致，不与旧 Maven 数量相加 |
| 未授权拒绝 | **实际 runner 在首 guard 拒绝** | unauthorized-runner.stderr.txt；发生于新 target/实例/step 创建和 Java 执行之前；本次授权文件始终 false |
| 发行 | **659 输入及两份冻结 JAR SHA 一致** | before/after-source-sha256.json、selected-release.json；沿用原 1813/210 单轮依据，本轮不重跑 Maven、不重冻 JAR，不冒称新真实能力 |
| 原准备材料 | **原 96 项完整保持** | revision-provenance.json / preservation-check.json；未改 10 月 16 日预算、判据或历史结论 |
| 新离线实例 | **原 start、不带 --run，空树/clean、零 run/门/批准** | 原临时实例文件缺失，另建空路径，见下节；新 ID 均由程序生成，未恢复/手写旧账本 |
| 当前只读重核 | **通过静态身份、新实例、依赖、Git、预算核对** | pre-dispatch-recheck.json；它不批准升级、业务门或运行，不证明活接口兼容 |
| 已知旧执行 | **17 个具名会话 closed，58 个已知 PID 缺席** | 仅本地元数据/进程观察，不调用 session 命令，不宣称任意子树已停止 |
| 宿主与验收出口 | **宿主依赖字节保持、逐条出口映射保留** | host-environment-identity.json / verification-exits.md；真实原生工具、自测与独立验收仍未运行 |

脚本执行记录：local-operations.json；真实 Java 动作只有原 `start`（无 `--run`），其余为本地 Git/元数据/进程读取及未授权拒绝检查。本轮无 prompt/R02、无真实模型或新原生 session，不执行安装、网络探针或目标产品交付；空 Git 初始化提交不等于产品 commit。

## 等待期间发现并显式处理的差异

### 原临时实例文件缺失

旧路径仍有目录，但初始账本文件已不在，不能继续声称其初始 SHA 可重核。原因未查明，**不将其归因为产品故障或清理程序**。保留旧准备材料，不补造旧 ID/批准/账本。使用新空路径：

- target：`/private/tmp/dual-w09-newlines-20261022-offline-07/target`
- workspace：`/private/tmp/dual-w09-newlines-20261022-offline-07/worktree`
- 实际 workstream 及 ledger：见 instance-location.json。

仅通过原 owner 离线初始化；新账本身份固定于 initial-ledger-sha256.json，原六例不恢复，旧预算不继承。新的真实案例仍是第七例，因为此前没有第七例模型请求。

### 当前 Codex 程序与配置指纹变化

29 项候选中 27 项字节保持；变化的两项为本机 Codex 程序和 `.codex/config.toml`。既有 acpx wrapper/dist、ACP adapter 已核安装源码和发行保持。当前 `CODEX_PATH` 未设置，既有 adapter 源码按 PATH 解析 codex；本轮 PATH 的命令解析路径与旧准备相同，但 Codex 程序内容已变。没有调用客户端探测兼容。

四个已留有旧值的配置项（model、effort、context window、auto compact 阈值）与旧案头记录一致；**旧完整配置字节未保存，无法据此证明整份配置语义等价**。差异、旧/新 SHA 与证据限制见 waiting-period-drift.json；当前候选文件清单见 current-config-and-installed-sources.json / launch-identity.json。

本包将当前字节列为**待复审的候选装配**，没有恢复旧客户端、改设置，或把新 SHA 加入清单就宣称兼容成立。CC 须判断当前案头核对是否足以让候选进入未来受控真实 Gate，还是仍缺具名静态前置。通过后，未来实际 R01 仍须证明解析/协议/身份；先于 prompt 的原资格守卫不变。程序升级既不自动判不兼容，也不自动享有旧版行为证明。

## 边界与作者自查

**Boundary Review：PASS_STATIC_CHECKS / WARN_SELECTED_CANDIDATE_DRIFT / REAL_RUN_NO_GO。** 本轮作者自查不是 CC 独立复审。

- Declared by：用户批准校准并入及离线核验；运行明确未授权。
- Proven by：预算 21 项、观察器 12 项、实际未授权拒绝、新实例/身份/依赖/Git 的只读重核；不外推真实实现、验收、交付或新版客户端运行能力。
- Consumed by：原 runtime/typed artifact/ledger 管业务与执行；operator checker 仅消费固定用量。预算 ALLOW 不授许可。
- Enforced before：未授权 guard 在实例读取/新 step/Java 之前；获准后每次原派发仍核资格、业务批准和储备；当前没有任何派发。
- Failure route：前提漂移不静默放行，未知执行不自动重发；失败按当前 AGENTS 分级，TRANSIENT 使用既有合法入口并计预算，BOUNDARY 停止；实验停止不改领域状态。
- Cannot-bypass test：实际 false 授权拒绝、尾链/修正/调用/时间储备拒绝与合法阈值成对，零初始业务批准和零 run 重核。

复盘：旧离线 PASS 有时间边界，不能用旧核验或预算同意推导今天可以派发；也不能仅因指纹变化就无限回滚/锁版本。此次只记录真实变化、重建缺失的空准备实例、交一轮受影响评审，没有扩展产品机制。首版声明限制、其他诊断族、UI 等原登记边界不由本批改变；非阻塞 notes 不由 Gate 额外改判拒绝。

## 下一步

**把本包交 CC 合并复审，覆盖校准、准入消费、离线实例及当前装配差异。** 复审通过后再交用户批准当次真实运行；预算 170 万 / 18 次 / 60 分钟已确认，无须重复请求预算选择。获准后必须再次运行只读重核，不能沿用今天的瞬时状态；当前 fresh_run_authorized=false，第七例未运行。
