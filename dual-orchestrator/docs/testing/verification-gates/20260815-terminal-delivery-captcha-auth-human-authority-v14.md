# Verification Gate: Terminal-Delivery Captcha-Auth Human Authority v14

日期：2026-08-15  
状态：`INDEPENDENT REVIEW PASS / AUTHORIZED FOR FRESH PACKAGE AND NON-PROVIDER PREFLIGHT / PROVIDER NOT AUTHORIZED`

本 Gate 使用未完成的 captcha-auth 产品作为自然开发任务。唯一产品目标仍是完成登录注册应用；本轮同时在自然
architecture review 边界验证 checkpoint `d5a36483`。不指定或诱导 reviewer verdict，不重放已消费样本，不为了
成熟度标签创建第二个 workstream。

## Verification Gate

- Target: 使用 bundled `agent-lifecycle-terminal-delivery@5` 完整交付
  `/Users/kriswang/work/projects/ai/captcha-auth-app`。产品必须满足 `REQUIREMENT.md` 的 Java 21、Spring Boot 3、
  Thymeleaf、PostgreSQL/Flyway、手机号或邮箱加密码登录、登录前 image Captcha、注册短信或邮件 OTP、测试、README、
  scope、lineage、completion audit 与 terminal acceptance。条件子目标是：首次自然 architecture review 的任意合法
  verdict 都必须先进入 human design gate，后续只能由 operator 的 canonical `approve` 或带 note 的 `revise` 决定。
- Source of truth: target `REQUIREMENT.md`；persisted workflow definition/instance；validated design/review sidecars 与
  references；design decision node、human gate document、node/iteration documents、`events.jsonl`；每个 `run.json` 的
  typed execution facts；compiled contract、self-test、change-scope、completion audit、terminal acceptance、PostgreSQL
  verification、`mvn test` 与最终 Git diff。LLM verdict 是 advisory artifact；human action ledger 是进入 plan 或创建
  design revision 的唯一 authority。target `VERIFICATION-GATE.md` 是初始化仓库时的历史 bootstrap 说明，其中的
  `terminal-delivery@1` 不是本轮 workflow identity 或控制事实；本轮只以本 Gate、bundled source 与 persisted
  definition 为准。Markdown、UI 文案、模型自述和 autorun 日志不是控制事实。
- Trigger precondition: Dual 必须是包含 `d5a36483b5fc5a21bcf666371872f093a462f428` 的 clean Gate SHA；target 必须
  保持 clean HEAD `7c7e35e89a66b14c29a335958ad90c8a92779fa0`、tree
  `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574`、requirement SHA-256
  `42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0`，且只有
  `REQUIREMENT.md`、`VERIFICATION-GATE.md` 两个 tracked files。workflow 必须精确为下节冻结的 @5 定义。fresh
  package、隔离 PostgreSQL 与 non-provider preflight 必须先 PASS；provider probes 与 workstream 需要 Gate review 和
  后续明确授权。
- How precondition is checked before run: 记录 Dual/target Git identity、checkpoint ancestry、target tracked files 与
  hashes、workflow source SHA/shape、CLI/server JAR SHA-256 与 mtime；fresh package 后清理旧 captcha-auth probe 资源，
  验证 `127.0.0.1:55432` 隔离 PostgreSQL health 与 `SELECT 1`。独立 Gate review PASS 并提交 clean SHA 后，才可在
  单独授权下运行 Claude/Codex minimal probes；两者 PASS 后才创建一个 fresh workstream。创建后立即核对 persisted
  workflow identity。
- PASS: 产品主结论要求 workflow=`COMPLETED`，terminal acceptance=`ACCEPT`，或 typed
  `ACCEPT_WITH_NOTES + blocking=false + must_fix=[]`；业务功能、PostgreSQL-backed verification、`mvn test`、README、
  scope、lineage 与 completion audit 全部通过。human-authority 条件子结论要求首次合法 architecture review 后：
  workflow=`WAITING_FOR_HUMAN`，唯一 open gate=`gate.human_design_approval`，review verdict 与 referenced sidecars 完整
  投影；在 operator 决定前无 plan/design successor、无新 iteration、无额外 Agent run；operator `approve` 后只激活
  plan 且 iteration 不变，或 operator 带 note 的 `revise` 后只创建一个新 design iteration、reason 逐字保留 note 并
  消耗 gate-owned budget。
- FAIL: 产品未完成、acceptance 拒绝或保留 must-fix、业务/测试/scope/lineage/completion 任一失败；architecture review
  contract malformed；合法 review verdict 自动直达 plan 或 design；进入 human gate 时已创建 iteration/Agent run；
  autorun 自动 resolve design gate；空 note revision 被接受；人工 action 未经 canonical `resolveOpenGate`；budget 超限
  后仍创建 design run；或 typed ledger facts 与副作用矛盾。
- INVALID: Dual/target dirty，identity/hash/workflow/JAR/probe 不符；复用旧 captcha-auth workstream、worktree 或 named
  session；operator 在运行中修改 Dual、workflow、prompt、provider 配置或 target；运行工件不能证明包含本 Gate clean
  SHA；在 workstream 前缺少 preflight evidence；人为要求 reviewer 输出某个 verdict；或手工编辑 gate/node/workflow
  JSON。自然 review verdict 是 READY、NEEDS_REVISION 或 BLOCKED 中任一种均有效。
- Stop-loss: freshness/identity/probe 不符时在 provider 前停止。architecture review 一旦合法完成却未进入 human gate，
  或 gate 被 autorun 自动解决，立即终止并保存证据，不审批、不 resume、不创建第二样本。`WAITING_FOR_HUMAN` 是合法
  暂停且不计入活跃运行时长。strict contract、lineage、failed correction、terminal contradiction、session closure
  failure、typed revision budget 耗尽或同类技术失败重复两次时停止，并走既有 terminate/reconcile/session-reap。
- Run artifact freshness: 从独立 review 后的 clean Gate SHA fresh package，记录 CLI/server JAR SHA-256 与 mtime；不得
  使用旧 server/JAR 或 `--skip-build`。runner 必须创建新的 worktree、workstream id 与 scoped named sessions；
  persisted workflow identity 在 workstream 创建后立即核对；它是创建后的完整性检查，不冒充启动前已知事实，
  不符则 INVALID 并 quiesce。

## Non-Provider PostgreSQL Preflight

证据目录固定为 Git ignored 的
`dual-orchestrator/target/verification-preflight/captcha-auth-v14/`。按以下边界执行并保存每步 command、stdout、stderr、
exit code、时间戳与最终 JSON 摘要：

1. 使用 `docker ps -a --filter name=^/dual-captcha-auth-v14-postgres$` 检查同名旧容器；存在时先
   `docker stop dual-captcha-auth-v14-postgres`，再 `docker rm dual-captcha-auth-v14-postgres`。
2. 使用 `lsof -nP -iTCP:55432 -sTCP:LISTEN` 检查端口；仍被占用则 preflight `FAIL`，不得启动 provider。
3. 使用 `postgres:16-alpine` 创建 `dual-captcha-auth-v14-postgres`，映射
   `127.0.0.1:55432:5432`，数据库 `captcha_auth_test`、用户 `captcha_auth`，密码只用于本地 probe evidence，
   不进入 target 仓库。
4. 使用容器内 `pg_isready -U captcha_auth -d captcha_auth_test` bounded 等待健康；超时为 `FAIL`。
5. 使用容器内 `psql -U captcha_auth -d captcha_auth_test -Atqc 'SELECT 1'`，stdout 必须精确为 `1`。
6. 保存 `docker image inspect postgres:16-alpine` identity、container id、endpoint、health/query 输出与 exit code。

本节是单次 Gate procedure，不新增 runner 或平台能力。

## Frozen Baseline

```text
Dual checkpoint: d5a36483b5fc5a21bcf666371872f093a462f428
variant: terminal-delivery
workflow: agent-lifecycle-terminal-delivery@5
source sha256: bcf1763a75cc03a118c636590441d5e7f447dd71d157e388934de1f6ce54fcdb
nodes: 31
edges: 51
nominal acpx.agent_run nodes: 9
handoff nodes: 0
design.decision READY_FOR_ARCH_REVIEW -> gate.human_design_approval
design.decision NEEDS_REVISION -> gate.human_design_approval
design.decision BLOCKED -> gate.human_design_approval
design gate allowed actions: approve, revise

target HEAD: 7c7e35e89a66b14c29a335958ad90c8a92779fa0
target tree: bab065a1ff9fe4d7032d1d573b7d1b6c1181a574
requirement sha256: 42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0
historical target verification file sha256: 0dc1ee67b5979fdc43d0d6e250ab2eb825516fa216e75ea25dab5a1761d7a9d7
```

## Human Interaction

当 workflow 到达 `WAITING_FOR_HUMAN` 时，operator 必须先读取当前 review packet、完整 referenced design/review
sidecars、allowed actions 与 revision budget。LLM verdict 是建议，不是默认动作。

UI：

1. 使用本 Gate fresh JAR 启动的 Dual server 打开当前 workstream；
2. 在 Attention/Review 中打开唯一 open design gate；
3. 核对 source outcome、review evidence、must-fix、missing evidence、完整 design 与剩余 budget；
4. 选择 `approve` 或 `revise`；`revise` 必须填写具体且可执行的 note；
5. 提交后重新读取 ledger/UI，确认只激活预期 successor。

CLI 使用 fresh JAR，避免 PATH 中旧 `dualctl` 的 schema 漂移：

```text
java -jar /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar \
  review decide --action <approve|revise> --note "<人工决定；revise 必须具体>" \
  --workdir <workspace> <workstream-id>
```

UI 与 CLI 必须汇聚同一个 `resolveOpenGate` ledger boundary。每次出现 human gate，执行方先报告 packet 摘要、完整
证据位置、allowed actions 与两种动作的后果，再等待用户操作；不得替用户决定。本轮 CLI 不使用 `--run`，必须先
读取决定后的 ledger，确认 successor 与 iteration 正确，再显式继续同一 workstream。

## Execution Order

1. 独立只读同行评审本 Gate 与 active plan；PASS 后只提交这两份文档，获得 clean Gate SHA。
2. fresh package；记录 Dual/target/workflow/JAR identity，执行 non-provider preflight 与隔离 PostgreSQL probe。
3. 用户另行授权后运行一次 Claude/Codex minimal probes。
4. probes PASS 后创建且仅创建一个 fresh terminal-delivery@5 workstream。
5. 首次 design review 后必须停在 human gate；保存 pre-decision ledger 快照并向用户提供 UI/CLI 操作步骤。
6. 人工决定后继续同一 workstream，直至产品 terminal 或 stop-loss；不得创建替代样本。
7. 保存产品结果、human-authority 条件结论、Agent 调用/token/duration/revision/correction 与全部 freshness evidence。

## Maturity Boundary

- architecture subject projection 与 strict contract enforcement：`PROVEN`；
- single-turn semantic completeness：`REJECTED`；
- advisory/human-authority routing `d5a36483`：`ENFORCED / NOT PROVEN`；
- captcha-auth：`NOT ACHIEVED`。

本 Gate PASS 前不得把 human-authority 标为 `PROVEN`。若 human-authority 条件通过但产品后续失败，必须分别报告
“authority boundary PROVEN”和“product FAIL”，不能相互覆盖。本 Gate 不新增 workflow、runner、judge、retry、parser
fallback、module、registry、engine 或平台能力。

独立 Gate review 结论：`PASS（P0=0 / P1=0 / P2=4 非阻塞观察项）`。历史 target gate 若被 Agent 引用为 @1 控制
事实，必须在结果中记录为 context noise；真实 workflow 判定仍只读取 bundled/persisted @5 identity。其余承袭观察为
`gate_purpose` 跨模块谓词分叉，bundled definitions 已显式声明 config，不阻塞本 Gate。
