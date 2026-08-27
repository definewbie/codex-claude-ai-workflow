# Review: Captcha-Auth Mailpit Fact-Acquisition Verification Gate

日期：2026-08-28
评审性质：独立、只读；未修改文件；未调用 provider；未创建/恢复 workstream

## Findings

**P0：无。P1：无。**

### P2-1 PASS.1 的 "inventory SHA 与重新执行同一算法所得一致"未指明执行机制
该断言有两条现成机械路径：(a) `TaskContractProposalValidator` 内建的 re-capture 比对（漂移
即 typed 拒绝，已在本 checkpoint 实现并被 `rejectsTrackedInventoryThatChangedAfterProjection`
钉住）；(b) operator 侧重跑 `GitTrackedPathInventory.capture` 等价命令。建议 Gate 补一句注明
采用哪条（推荐 (a) 为准、(b) 为报告交叉核对），避免执行时自行发明比对脚本。

### P2-2（知会）单样本三重目标的重复试错风险
PASS 要求 fact-acquisition、8-call/0-revision、Mailpit 产品验收**同时**成立；若 reviewer 针对
产品语义（非事实链）合法提出 refine（如 FX2Y must_fix #2 的隔离语义），样本 FAIL——此时
fact-acquisition 的 deterministic 边界仍然 ENFORCED，但 REAL-CASE NOT PROVEN 无法从 FAIL
样本升级。这是既定 single-sample 纪律的已知代价，Maturity Boundary 已如实分层；仅记录，
不要求修改。

## Frozen Baseline 独立复算（全部通过）

| 项 | 实测 |
|---|---|
| checkpoint `cca00827…9d93` | 存在，= "feat(orchestrator): ground initial contracts in repository facts"（fact-acquisition checkpoint）✓ |
| workflow SHA `d41b835e…` | 逐位一致（文件未变）✓；28/46/8/6 与 checkpoint graph identity 一致 ✓ |
| target HEAD/tree | `dc0ea592…`/`6b71d91d…` 一致；clean ✓ |
| task/check SHA | `32a6f679…`/`a1902c10…` 逐位一致 ✓ |
| baseline 99/0/0/0 | 前一 Mailpit Gate 评审中已独立复跑确认（同一冻结基线）✓ |
| FX2Y 处置 | 仅作 defect evidence、禁 resume——理由正确且被 active plan 显式记录：其 initial occurrence 由旧 checkpoint 执行，resume 不能冒充 cold-start proof ✓ |

## 重点判断

**1. 断言全部来自 typed/durable SoT ✓** — PASS.1（repo-map `source/inventoryHead/SHA/count`，
durable 文档字段）、PASS.2（`run.json.toolUsage` 工具计数 + `actualReadEvidence` path/SHA/size，
均为持久化执行事实）、PASS.3（strict validated envelope + `evidence_refs`∩repo-map——即
validator 的 typed 强制）、PASS.4（typed decision/blocking/must_fix + lineage，明确"不扫描
自然语言猜拒绝原因"）、PASS.5/6（persisted graph/ledger/audit）。SoT 清单显式排除 Markdown/
console/模型自述/**文件名推断**。Boundary Ownership 表六行与 production owner 一一对应。

**2. PASS/FAIL/INVALID/stop-loss/授权顺序可执行且不可绕过 ✓** —
- INVALID/FAIL 对 inventory drift 的两段划分精确：首次业务调用前漂移 → INVALID；有效执行内
  被 validator 拒绝 → FAIL（作为 enforcement 记录）——与实现的 stale 检测语义一致；
- FAIL 显式含 provider/adapter fatal（沿袭）、initial 事实断言失败（新增，机械可判）；
- stop-loss 新增 "initial fact-acquisition assertion failure" 触发项，全部机械可判；
- 授权顺序消除前一 Mailpit Gate 的 P2-1 歧义：§4 显式把 workspace capture/verify 归入
  fresh-workstream 独立授权；§2 仅允许 package/validation/identity 复算。

**3. fresh workstream 是必要条件 ✓** — cold-start 边界（无 TaskContract、无 git diff、无
revision context）只在 fresh workstream 存在；FX2Y 已有 proposal/revision/recovery 状态且其
initial occurrence 由旧 checkpoint 执行——resume 既不 fresh 也测不到新边界。Gate 的禁止与
active plan 的理由都正确。

**4. 三重约束同时成立 ✓** — PASS.2/3/4（事实链）+ PASS.5（8 calls/0 revision/0 correction、
A6 缺席、A5 经 human gate）+ PASS.7-9（Mailpit 产品/安全/回归）在同一合取内；任何一环失败
即 FAIL，不得以其它环成功解释为部分通过（INVALID 不得解释为部分成功亦有明示）。

**5. 无过度设计/单 case 特判/成熟度夸大 ✓** — 骨架复用前一 Mailpit Gate；新增断言全部映射
到刚实施的 typed 机制（无新 schema/workflow/provider 变更）；Dual 侧无 Mailpit 特判；同任务
第二次尝试是 FX2Y 未完成的自然延续而非制造 fixture；Maturity 四行与本 diff review 的判定
精确一致（fact acquisition ENFORCED / REAL-CASE NOT PROVEN；9→8 与产品完成 NOT PROVEN）。

## End-to-end invariant

| 维度 | 结论 |
|---|---|
| Declared by | frozen 六项 identity + contract-discovery profile/prompt 只读边界（Gate 复述 production 声明） |
| Proven by | durable repo-map identity、toolUsage 计数、actualReadEvidence path/SHA、validated proposal、typed review、ledger/audit |
| Consumed by | PASS 合取判定与 maturity 升级决策（REAL-CASE PROVEN 仅可由本 Gate PASS 样本授予） |
| Enforced before | 首次业务 run 前（preflight verify + graph identity）；reviewer/compile/business write 前（validator typed 强制） |
| Failure route | 事实断言失败/revision/correction → FAIL 止损；identity/env/precondition → INVALID；单样本纪律 |
| Cannot-bypass | 前一 checkpoint 的 deterministic 测试集 + 本 Gate 的 typed SoT 断言 + frozen SHA 复算 |
| Residual | 单样本试错风险（P2-2）；agent 仍管 relevance；产品语义 refine 可致合法 FAIL |

## 结论

1. **Boundary Review: PASS**
2. **Gate 可执行：是**（建议随附 P2-1 一句话明确 SHA 复核机制）
3. **仅提交 Gate + active plan 的条件：满足**（+本评审；working tree 恰为该集合）
4. **提交后只授权 fresh package 与 non-provider preflight：是**——§2 明确：fresh Maven
   package、external workflow validation、source-side identity/hash 复算；不创建 workstream、
   不调用 provider。
5. **provider probes 与唯一 fresh workstream 仍需分别授权：是**——§3/§4 分列；capture/verify
   随 fresh-workstream 授权执行；FX2Y 禁 resume；单样本、禁 autorun、分段人工执行。
