# Verification Gate: Small Workflow / Captcha-Auth Mailpit Occurrence Durability

日期：2026-08-28  
状态：`INDEPENDENT REVIEW PASS / AUTHORIZED FOR SELECTIVE COMMIT / PROVIDER NOT AUTHORIZED`

## Verification Gate

- Target: 从 clean checkpoint `b25d211` 创建唯一 fresh external small-workflow workstream，在 frozen captcha-auth-app
  baseline 上完成 `MAILPIT-LOCAL-EMAIL-OTP-TASK.md`。本轮必须同时证明：每次 repository fact execution occurrence 都有
  不可变 binding，后续 sibling context build、revision/retry 与 current projection 变化不能覆盖或替代历史 authority；
  workflow 最终 `COMPLETED`；Mailpit 产品功能和既有安全行为通过真实测试。
- Source of truth: frozen Dual/workflow/target/task/workspace-check identity；persisted workflow definition/instance；每次
  context/agent occurrence 的 `binding.json`、binding 内 artifact ref/SHA、context metadata、`run.json`、actual governance
  report；proposal envelope/sidecar/reference；typed review、human gate、scope、lineage、self-test、acceptance 与 completion
  audit；target Git diff、Mailpit-backed test、`mvn test` 与 `docker compose config`。`workstream-memory.json.refs` 仅是
  `latest_projection_refs`；Markdown、console、模型自述、mtime/latest 与全局 repo-map 都不是 authority。
- Trigger precondition: Dual clean HEAD 必须等于 Gate commit 且包含 `b25d211`；workflow、target HEAD/tree、task 与
  workspace-check SHA 必须与 Frozen Baseline 一致。fresh package、workflow validation、source identity、Claude/Codex
  minimal probes 与 execution-workspace capture/verify 必须依次 PASS。历史 workstream
  `ws-20260827T232026Z-EFCB` 只作 defect evidence，不得 resume、批准或复用 output/session。
- How precondition is checked before run: Gate 独立评审 PASS 并提交 clean SHA 后复算 frozen identity，执行 fresh Maven
  package 与 external workflow validation。probes 另获授权后各执行一次并关闭 session。fresh workstream 另获授权后，
  使用 fresh JAR `start` 且不带 `--run`，从 persisted documents 解析 effective workspace；在真实 workspace 执行 configured
  workspace capture，并紧邻首次业务 run 前 verify。只有 typed workspace evidence 为 `status=passed` 且
  `verificationStatus=passed` 才能开始 provider execution。
- PASS: Boundary PASS 与 Product PASS 必须同时成立：
  1. initial contract-discovery binding 记录 workstream/node/run、Git inventory identity，以及 repo-map、plan、policy、
     working-set、file-summaries、read-reuse、capability catalog、planned report 的 occurrence-local ref/SHA；binding 与所有
     artifact 均可按字节复算；不存在 authoritative global repo-map；
  2. initial proposal sidecar 的 `repository_fact_binding` 与 initial run binding ref/SHA 相同；proposal reference 的
     sidecar SHA、producer lineage 有效；inventory re-capture 与 evidence traceability PASS；
  3. 每个后续 context/agent occurrence 使用自己的 binding。相同 node instance 的 retry/revision 必须产生不同 run-bound
     binding；后续 sibling stage 执行后，所有历史 binding/artifact SHA 仍不变；
  4. actual governance report 的 binding ref/SHA 与该 run 相同，读取事实只来自该 binding；current
     `workstream-memory.refs` 可以指向新 occurrence，但 `refsSemantics=latest_projection_refs`，不得改变历史审计；
  5. 在 requirement/design/plan 等每个 `WAITING_FOR_HUMAN` 暂停点，operator 在决定前执行 cold audit：从持久化
     proposal/reference 与 actual report 重新定位 binding，不使用进程内对象；复算 binding 与全部 artifact SHA，并核对
     workstream/node/run identity。任一不符不得审批；
  6. terminal human acceptance 前再次对 initial proposal、全部合法 revision proposal 与对应 actual reports 执行 cold
     audit。binding 缺失、ref 越界、SHA 不符、artifact 被改写或 global projection 冲突时必须 fail closed；
  7. workflow=`COMPLETED`，completion audit=`ready` 且 `gaps=[]`；change scope=`PASS`、lineage 有效；terminal
     acceptance=`ACCEPT`，或 `ACCEPT_WITH_NOTES + blocking=false + must_fix=[] + missing_evidence=[]`；
  8. Docker Compose 提供 PostgreSQL 与 pinned Mailpit；自动化测试通过真实 Mailpit container/API 取得邮件 OTP 并完成邮箱
     注册；不得新增 OTP 泄漏 endpoint、日志、console adapter 或 parser fallback；
  9. `mvn test` 全绿且 frozen baseline 的 99 个测试不退化；`docker compose config` PASS；phone registration、Captcha、
     OTP lifecycle、session、CSRF、normalization、rate limit 与 fake adapters 不退化；README 记录本地 Mailpit 用法及
     development-only 边界。
- Profile-efficiency sub-result: 若完整样本同时满足 Agent runs/provider occurrences 恰为 8、correction=0、semantic
  revision=0，则 small-profile `9 -> 8` 可单独标 `PROVEN`。合法 typed review/human revision 或成功 correction 不使主 Gate
  自动 FAIL，但该子结果保持 `NOT PROVEN`，并必须记录额外 occurrence 的原因与 lineage。不得为了 8-call 数字拒绝合法
  human authority 或提前停止可完成的产品任务。
- FAIL: frozen/preflight 正确的有效样本中，任一 occurrence binding 缺失、不可解析、identity/SHA 不符、被 sibling 覆盖、
  actual report 与 binding 不一致、proposal/reference 冷读取无法重建、global/latest fallback 生效，或 operator 在 sentinel
  未通过时仍路由；strict contract、scope、lineage、self-test、acceptance、completion audit 失败且未沿正式 workflow 修复；
  provider/adapter fatal；workflow 未完成；Mailpit 仅有文档无真实 SMTP-backed test；OTP 泄漏或既有安全行为退化。
  binding 直写中断导致 parse/SHA failure 属 production FAIL，不归为环境 INVALID。
- INVALID: 任一 frozen identity/hash 不符；Gate 未经评审或不是 clean commit；旧 JAR/server；workspace preflight 未配置或
  非 PASS；workspace check 因 Docker/Maven/Java/Testcontainers 或环境失败；probe 失败；使用 bundled variant、autorun、
  `--skip-build`、旧 workstream/worktree/session；运行中修改 Gate/task/workflow/Dual、provider/model/timeout；手工编辑
  ledger；target 创建前不 clean。INVALID 不得解释为部分成功，也不得创建第二个样本。
- Stop-loss: precondition 失败时不得开始业务 Agent。运行后 occurrence sentinel 首次失败、provider fatal、session closure
  failure、同一 review stage 连续第二次仍返回同一 blocker、连续第二次 correction 仍无合法 contract，或 90 分钟 active
  runtime 达到时，立即终止并闭合 process/session，保存完整证据。`WAITING_FOR_HUMAN` 不计 active runtime；合法且产生新
  事实的 revision 可继续，但必须保留每次独立 binding。
- Run artifact freshness: Gate review PASS 并提交 clean SHA 后 fresh package；记录 source commit、JAR、workflow、target、
  task、workspace-check SHA 与构建时间。保存 non-provider preflight、probes、workspace capture/verify、每个 human sentinel
  audit、terminal cold audit、完整 ledger、target diff、测试日志、metrics 与 session closure。无论 PASS/FAIL/INVALID 都只
  消费这个唯一 fresh 样本。

## Boundary Coverage

| 项 | 本轮要求 |
|---|---|
| Authority under test | immutable `RepositoryFactBinding` + proposal sidecar/reference lineage |
| Occurrence identity | `workstreamId + nodeInstanceId + runId + binding SHA` |
| Sibling writers crossed | product-review、requirement/design/plan context builds、revision/retry、implementation/self-test/acceptance |
| Pre-human cold audit | 每个 human gate 决策前从 durable refs 重建并逐 SHA 核对 |
| Terminal cold audit | human acceptance 前重验 initial + revision bindings 与 actual reports |
| Projection contradiction check | current refs 可变化；历史 SHA 必须保持，global/latest 不得救回缺失 binding |
| Natural lifecycle branch | typed human approve/revise/reject 按 packet allowed actions；不强制制造 revision |

## Cold Audit Procedure

每个 sentinel 使用相同的只读步骤，输出保存到 case report：

1. 从 proposal `.ref.json` 读取 sidecar ref/SHA，复算 sidecar SHA，并按 typed reader 等价规则核对 producer lineage；
2. 从 sidecar 读取 `repository_fact_binding.artifact_ref/sha256`，复算 binding SHA；
3. 解析 binding，核对 workstream/node/run；逐项确认 artifact ref 位于同一 workstream 的
   `artifacts/context-governance/occurrences/` 下并复算 SHA；
4. 核对 binding inventory identity 与 bound repo-map 一致；initial proposal 另核对 validation 时保存的 Git inventory
   identity 与 frozen execution workspace；
5. 对该 run 的 actual report 核对 binding ref/SHA 与 inventory identity；
6. 记录此时 `workstream-memory.refs`，允许指向最新 occurrence，但不得替代历史 binding；
7. 输出 `PASS/FAIL`、检查时间、occurrence 列表与逐文件 SHA。任一缺失或不一致即 FAIL，不允许人工解释通过。

### Case-report command checklist

下面的命令清单对每个 proposal/run 元组执行一次。它只读取 production 已持久化的 authority，并把复算结果写入
case report；它不是新的 validator 或路由 owner。`PROPOSAL_REF` 来自 ledger-routed proposal `.ref.json`，其余三个
expected identity 值来自同一 node/run 的持久化 ledger 文档，不从 console 文本猜测。

```bash
set -euo pipefail

WORKSPACE_ROOT="<effective-workspace-absolute-path>"
WORKSTREAM_ID="<workstream-id>"
PROPOSAL_REF="<workspace-relative-proposal-ref-json>"
EXPECTED_NODE_INSTANCE_ID="<producer-node-instance-id>"
EXPECTED_RUN_ID="<run-id>"
REPORT_DIR="<case-report>/cold-audit/${EXPECTED_RUN_ID}"

sha256_file() { shasum -a 256 "$1" | awk '{print $1}'; }
mkdir -p "$REPORT_DIR"

PROPOSAL_PATH="$WORKSPACE_ROOT/$PROPOSAL_REF"
test -f "$PROPOSAL_PATH"
test "$(jq -er '.workstream_id' "$PROPOSAL_PATH")" = "$WORKSTREAM_ID"
test "$(jq -er '.producer_node_instance_id' "$PROPOSAL_PATH")" = "$EXPECTED_NODE_INSTANCE_ID"

SIDECAR_REF="$(jq -er '.artifact_ref' "$PROPOSAL_PATH")"
SIDECAR_SHA="$(jq -er '.sha256' "$PROPOSAL_PATH")"
SIDECAR_PATH="$WORKSPACE_ROOT/$SIDECAR_REF"
test -f "$SIDECAR_PATH"
test "$(sha256_file "$SIDECAR_PATH")" = "$SIDECAR_SHA"
test "$(jq -er '.workstream_id' "$SIDECAR_PATH")" = "$WORKSTREAM_ID"
test "$(jq -er '.node_instance_id' "$SIDECAR_PATH")" = "$EXPECTED_NODE_INSTANCE_ID"
test "$(jq -er '.run_id' "$SIDECAR_PATH")" = "$EXPECTED_RUN_ID"

BINDING_REF="$(jq -er '.repository_fact_binding.artifact_ref' "$SIDECAR_PATH")"
BINDING_SHA="$(jq -er '.repository_fact_binding.sha256' "$SIDECAR_PATH")"
BINDING_PATH="$WORKSPACE_ROOT/$BINDING_REF"
test -f "$BINDING_PATH"
test "$(sha256_file "$BINDING_PATH")" = "$BINDING_SHA"
case "$BINDING_REF" in
  .agent/workstreams/"$WORKSTREAM_ID"/artifacts/context-governance/occurrences/*/binding.json) ;;
  *) echo "binding ref is outside the occurrence authority root" >&2; exit 1 ;;
esac
jq -e \
  --arg workstream "$WORKSTREAM_ID" \
  --arg node "$EXPECTED_NODE_INSTANCE_ID" \
  --arg run "$EXPECTED_RUN_ID" \
  '.schema_version == 1 and .workstream_id == $workstream
   and .node_instance_id == $node and .run_id == $run
   and (.artifacts | keys | sort) ==
     (["agentCapabilityCatalog", "fileRelevancePlan", "fileSummaries", "plannedReport",
       "readReuseContract", "repoMap", "stageScopePolicy", "workingSet"] | sort)' \
  "$BINDING_PATH" >/dev/null

: > "$REPORT_DIR/artifact-sha.tsv"
jq -er '.artifacts | to_entries[] | [.key, .value.ref, .value.sha256] | @tsv' "$BINDING_PATH" |
while IFS=$'\t' read -r name ref expected_sha; do
  case "$ref" in
    .agent/workstreams/"$WORKSTREAM_ID"/artifacts/context-governance/occurrences/*) ;;
    *) echo "artifact ref is outside the occurrence authority root: $name" >&2; exit 1 ;;
  esac
  actual_sha="$(sha256_file "$WORKSPACE_ROOT/$ref")"
  test "$actual_sha" = "$expected_sha"
  printf '%s\t%s\t%s\n' "$name" "$ref" "$actual_sha"
done > "$REPORT_DIR/artifact-sha.tsv"
test "$(wc -l < "$REPORT_DIR/artifact-sha.tsv" | tr -d ' ')" = "8"

REPO_MAP_REF="$(jq -er '.artifacts.repoMap.ref' "$BINDING_PATH")"
REPO_MAP_PATH="$WORKSPACE_ROOT/$REPO_MAP_REF"
test "$(jq -er '.inventoryHead' "$REPO_MAP_PATH")" = "$(jq -er '.inventory_head' "$BINDING_PATH")"
test "$(jq -er '.inventorySha256' "$REPO_MAP_PATH")" = "$(jq -er '.inventory_sha256' "$BINDING_PATH")"
test "$(jq -er '.inventoryPathCount' "$REPO_MAP_PATH")" = "$(jq -er '.inventory_path_count' "$BINDING_PATH")"

RUN_JSON="$WORKSPACE_ROOT/.agent/workstreams/$WORKSTREAM_ID/runs/$EXPECTED_RUN_ID/run.json"
ACTUAL_REPORT_REF="$(jq -er '.contextGovernanceActualReportPath' "$RUN_JSON")"
ACTUAL_REPORT_PATH="$WORKSPACE_ROOT/$ACTUAL_REPORT_REF"
test "$(jq -er '.repositoryFactBinding' "$ACTUAL_REPORT_PATH")" = "$BINDING_REF"
test "$(jq -er '.repositoryFactBindingSha256' "$ACTUAL_REPORT_PATH")" = "$BINDING_SHA"
test "$(jq -er '.inventoryHead' "$ACTUAL_REPORT_PATH")" = "$(jq -er '.inventory_head' "$BINDING_PATH")"
test "$(jq -er '.inventorySha256' "$ACTUAL_REPORT_PATH")" = "$(jq -er '.inventory_sha256' "$BINDING_PATH")"
test "$(jq -er '.inventoryPathCount' "$ACTUAL_REPORT_PATH")" = "$(jq -er '.inventory_path_count' "$BINDING_PATH")"

MEMORY_PATH="$WORKSPACE_ROOT/.agent/workstreams/$WORKSTREAM_ID/memory/workstream-memory.json"
test "$(jq -er '.refsSemantics' "$MEMORY_PATH")" = "latest_projection_refs"
jq -c '.refs' "$MEMORY_PATH" > "$REPORT_DIR/current-projection-refs.json"

jq -n \
  --arg checked_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --arg workstream_id "$WORKSTREAM_ID" \
  --arg node_instance_id "$EXPECTED_NODE_INSTANCE_ID" \
  --arg run_id "$EXPECTED_RUN_ID" \
  --arg proposal_ref "$PROPOSAL_REF" \
  --arg sidecar_ref "$SIDECAR_REF" \
  --arg sidecar_sha256 "$SIDECAR_SHA" \
  --arg binding_ref "$BINDING_REF" \
  --arg binding_sha256 "$BINDING_SHA" \
  --arg actual_report_ref "$ACTUAL_REPORT_REF" \
  '{status:"PASS", checked_at:$checked_at, workstream_id:$workstream_id,
    node_instance_id:$node_instance_id, run_id:$run_id, proposal_ref:$proposal_ref,
    sidecar_ref:$sidecar_ref, sidecar_sha256:$sidecar_sha256,
    binding_ref:$binding_ref, binding_sha256:$binding_sha256,
    actual_report_ref:$actual_report_ref}' > "$REPORT_DIR/audit-result.json"
```

命令任一步非零即本 occurrence cold audit `FAIL`；不得生成或保留伪 `PASS` 的 `audit-result.json`。initial proposal 还必须
以 validator 内建 inventory re-capture 为 authoritative 判定，operator 对 frozen execution workspace 的 Git identity
复算只作交叉核对。对 revision proposal 重复此清单，并在 terminal cold audit 汇总所有 `audit-result.json` 与
`artifact-sha.tsv`。

这是一次性 Gate 证据步骤，不新增 production audit schema、runner phase 或通用 evidence platform。

## Frozen Baseline

```text
Dual occurrence-ownership checkpoint:
  b25d211cd41c992da015c5e483f9fde6536a80df
workflow:
  dual-runtime/src/test/resources/dev/dual/runtime/lifecycle/
  agent-lifecycle.small-plan-human-review-experiment.json
workflow identity:
  agent-lifecycle-small-plan-human-review-experiment@1
workflow SHA-256:
  d41b835e4d8a8347b64984e085385030246976bbc485a866f215dfafc97faaec
workflow shape:
  28 nodes / 46 edges / 8 acpx.agent_run / 6 human.review
target:
  /Users/kriswang/work/projects/ai/captcha-auth-app
target HEAD:
  dc0ea5925f06bdfc30a8656148d6d21468ca12b6
target tree:
  6b71d91d572a30bae8e26083074094d8a35c84e3
task:
  MAILPIT-LOCAL-EMAIL-OTP-TASK.md
task SHA-256:
  32a6f679c1db24f2475edf50c9b5935faa7e059f63f718c049056d7853972a0c
workspace check:
  scripts/verify-workspace.sh
workspace check SHA-256:
  a1902c10e7a9fb1a9abda6c6650814cdf1fdd7273a2676878ff2313b238466c8
target baseline verification:
  scripts/verify-workspace.sh -> 99 tests / 0 failures / 0 errors / 0 skipped
  scoped Docker API workaround: -Dapi.version=1.44
historical defect evidence only:
  ws-20260827T232026Z-EFCB (WAITING_FOR_HUMAN; MUST NOT RESUME OR APPROVE)
```

## Procedure And Authorization Boundary

1. 本 Gate 与 active plan 先做独立只读同行评审；关闭 P0/P1 后只提交文档 checkpoint。
2. Gate clean SHA 后仅可执行 fresh package、workflow validation 与 source-side identity/hash 复算；不创建 workstream、
   不调用 provider。
3. Claude/Codex minimal probes 分别独立授权，每个只运行一次并关闭 fresh session。
4. probes PASS 后，唯一 fresh workstream、workspace capture/verify 与首次业务 run 仍需独立授权；不得 resume EFCB。
5. 使用 fresh JAR 分段执行，禁 autorun；每个 human gate 先完成 cold audit，再向用户展示 typed packet、allowed actions 与
   后果，由用户本人决定。operator 不代替人作业务判断。
6. 无论 PASS/FAIL/INVALID 都只消费一个 fresh 样本；不换模型、不改 Gate、不创建第二个 workstream。

## Maturity Boundary

- repository fact occurrence ownership：`ENFORCED / REAL-CASE NOT PROVEN`；
- initial contract fact acquisition：`ENFORCED / REAL-CASE NOT PROVEN`；
- execution-workspace eligibility：`ENFORCED WHEN CONFIGURED / NOT PROVEN`；
- Mailpit 产品完成与 complex real-case support：`NOT PROVEN`；
- small-profile `9 -> 8`：独立子结果，当前 `NOT PROVEN`。
