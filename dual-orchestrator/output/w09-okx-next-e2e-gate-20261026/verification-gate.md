# OKX 既有 Java 项目 E2E Gate 候选

2026-10-26。产品切片已获用户确认；本包是离线准备与复审材料，不是模型运行授权。当前状态：**完整项目基线和 fresh 离线装配已完成；当次预算、运行及完整装配复审待决，NO-GO**。

## 目标和证据

Target phase 为 **P0**：用固定 Dual 发行完成一次对既有 Java 项目的有用 MODIFY，从需求、复评、当前人批、实现、自测、另一客户端独立验收，直到候选交付及 COMMITTED。相对上次空仓库 CREATE，增加既有代码修改和回归保留的真实证据。它不是吞吐量、成功率、模型对比或 token 优化实验。

顶尖工程检查应先补三件事：确认不是重复实现；确认当前脏工作区不会被实验覆盖；用不依赖模型答案的算例判断新指标。原始素材为产品规范、计算服务/record/CLI、直接与兄弟测试、Git 对象、旧 E2E 的固定 run/occurrence 和发行字节。使用 Git archive/SHA、Java 25、离线 Maven/JUnit/XML、独立十进制算术；不新增模型或交易所探针。

验证题目固定为：**在指定提交的隔离 Java 项目中，经原 Dual 主链增加 90 天资金费率追平阈值，保持原建议与未知语义，由独立算例、程序测试、另一客户端验收和 Git 交付身份共同证明。**

## Verification Gate

| 字段 | 本次规则 |
|---|---|
| Target | `task-requirement.md` AC-1 至 AC-5，同时完成真实 terminal-delivery 主链 |
| Source of truth | 产品数值：PhaseOneAssessmentService → PhaseOneAssessment；测试：当前源码对应的程序观察/测试报告；业务与执行：原 typed contract、R01/R02/R03/L03、统一 outcome；交付：批准 candidate、真实 Git parent/tree/commit、原 finalize/current read model |
| Trigger precondition | 固定隔离基线可构建且基线回归通过；实际验证出口可用；发行/客户端配置和唯一启动环境 E 已核；新主体/初始账本无旧 authority；累计账和储备核对器已接入当前单步入口；用户单独批准当次预算及运行 |
| How precondition is checked before run | 核 baseline-manifest、Git 状态、当前测试报告及完整基线回执；核 evidence 中发行/33 项客户端字节并在授权后重查；用原无 --run 初始化入口产生当前 workstream，再只读核初始 current/gates/pending；从当前合同定位实际条款与 readiness，不造旧 ref；完整 operator 装配完成后做 false 授权拒绝和替身链验证 |
| PASS | AC 全满足，实际独立验收为 ACCEPTED 或合法 ACCEPT_WITH_NOTES；精确批准候选在隔离 target 应用；commit 单 parent/base、tree 与候选一致且 clean；原 finalize 后 workflow COMPLETED、delivery COMMITTED、active/open gates 为空 |
| FAIL | 业务产物或质量不满足、误拒导致合法路径走不通、越权采纳/主因漂移、预算或时间止损、真实副作用不明。领域 SUCCESS 或 RUNNING 不为实验结论改写 |
| INVALID | 错基线/发行/配置、缺必要验证前提、观测无法判断目标、未获准的新组合、运行中替换冻结输入。不得称部分成功 |
| Stop-loss | 提案为新 3,000,000 token / 18 请求 / 3,600 活动秒，尚待批准。每次派发前保留全部未完主链、当次重入、一次格式修正；未知用量/外部 pending/BOUNDARY 即停。TRANSIENT 仅按现行 AGENTS 的既有合法入口和有界次数处置并计账；不能扩契约/清终态探测 |
| Run artifact freshness | 固定现有 Dual 两 JAR/659 输入/受审客户端组合；产品从固定 archive 开始，新 build 与报告对应本次产品源码；不复用旧 target 产物或旧通过报告 |

预算是完成结果后的观察与下一派发前准入，不承诺单请求内部硬 token 上限。没有调用或缺用量不能推导花费为零。预算与业务批准分立，ALLOW_BUDGET_ONLY 不授任何写、执行、验收或交付权。

## 基线与当前证据

- 原项目 `/Users/kriswang/work/projects/ai/ai-crypto-arbitrage`，提交 `931db5db8fc468c61eb2c441d21a1da7facf69a1`。使用 Git archive 固定的 305 个版本内文件；8 个已检查核心文件与原 HEAD 相同。原工作区 92 项未提交变化不带入实验；`.env`、本地数据库和生成目录不复制。
- `source-baseline.tar` 和 `baseline-manifest.json` 是离线基线；`fixture/baseline` 是测试副本，当前仅生成 Maven 构建产物。已由该归档另建隔离 target 和关联工作树，并用原 start（无 --run）产生 `ws-20261026T142203Z-7SNA`；Git 树与源提交一致，但 target 的初始化 commit 是新本地基线，不冒称原提交历史。本案无业务批准、模型执行或发布候选。见 instance-location.json / fresh-preparation-result.json。
- `evidence/baseline-targeted-receipt.json`：单轮 13/0/0/0；`evidence/baseline-non-container-receipt.json`：另一单轮 114/0/0/0，31 suites。两者不相加。Maven `-o`，后者含本地 HTTP stub，不请求 OKX。
- 用户启动 Docker 后，`evidence/docker-ready-recheck-20261026.json` 确认服务可用且 mysql:8.4、testcontainers/ryuk:0.14.0 已缓存。随后默认完整 `mvn -o clean test` 单轮通过：166/0/0/0，42 suites，包含此前未运行的全部 11 个 MySQL/Testcontainers suite，耗时 129.591 秒；见 `evidence/baseline-full-receipt.json`、`baseline-full.log` 和 `baseline-full-reports/`。不与此前 13/114 项相加。原 Docker 不可用回执保留为历史事实。本次未拉镜像或下载依赖，临时数据库由测试启动；仅证明宿主固定基线回归，不证明原生客户端工具能力或新功能。
- `evidence/release-comparison-final.json`：两 JAR 与原 659 输入均相同；`launch-read-only-comparison.json` 的 33 项客户端/配置比较相同。前两版检查回执路径解释错误，保留为 operator error；正确结果以 final 文件为准，不当成产品漂移。
- `budget.json` / `budget-dry-run.json`：独立新预算，不继承第八例余额；阶段额度是规划假设，见原实测列，不能宣称 p95 或必然够用。

## 最小剩余动作

默认完整基线回归已完成，原始回执与 XML 已固定。

1. 对本次完整装配做只读受影响复审（cc-review-prompt.txt）。离线证据见 assembly-verification.md；原 166 项产品基线、29 项装配/observer、24 项预算反例、3 项隔离反证分层，不相加。
2. 用户决定当次 3M/18/3600 预算与 fresh 运行；业务门和 commit 委派仍单独办理。批准顺序为先固定 launch 字节→授权记录填其 SHA、当前 workstream/初始清单和预算 SHA→登记当次批准引用→显式启用预算/运行。当前两者均 false、批准 SHA 为 null。
3. 获准后重跑原只读核验、最新外部状态及逐条验证出口核对，通过后才由原单步入口推进。不得借旧 true、旧 PID 或旧余额。

本次离线装配和未授权实际拒绝已完成，但当次预算/运行未批，不能提交“已具备启动资格”的结论。若环境补齐成本明显超过小功能价值，停下提交用户取舍，不能换大项目或扩平台。
