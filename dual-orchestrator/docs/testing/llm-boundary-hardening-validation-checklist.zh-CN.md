# LLM 边界硬化验证 Checklist

> 2026-10-07 状态：以下为历史方案/验证记录。旧 smoke 已退役，不作为当前执行或准入指南；现行入口见 [当前验证说明](smoke-test-e2e.md)。历史 PASS 不外推到当前 D05。

本文件记录 P0/P1 边界修复提交后的验收标准与验证结果。

## 验收标准

| ID | 验收项 | 验收标准 | 结果 | 证据 |
| --- | --- | --- | --- | --- |
| V1 | Structured artifact 主线 | `ContractRegistry` agent 节点 markdown-only 输出不能通过；canonical `<dual-artifact>` 才能作为 authoritative artifact | PASS | `ArtifactContractValidatorTest` |
| V2 | Plan 正式 artifact body | `plan.claude_plan` 缺正式计划正文必须失败；正文段落齐全必须通过 | PASS | `ArtifactContractValidatorTest` |
| V3 | ACP extractor 边界 | 普通 JSON fence 不能被 adapter 包装成 canonical artifact | PASS | `AgentFinalArtifactExtractorTest` |
| V4 | Failure routing typed-only | review noise 必须来自 typed `category_hint=REVIEW_NOISE`；summary 自然语言不能放行 | PASS | `FailureArbiterTest`、`FailureClassifyHandlerTest` |
| V5 | Verification 分类精确匹配 | `category_hint` 只能按 catalog/alias 精确匹配；substring 不能触发 verification-insufficient 路由 | PASS | `FailureArbiterTest` |
| V6 | Reread reason contract | 实际重读 read-reuse-contract 中 reusable 文件时，必须在 `reread_reasons` 中有 matching path | PASS | `ArtifactContractValidatorTest` |
| V7 | Memory 字段语义 | `WorkstreamMemory` open issue 必须带 `sourceField`；`fieldSemantics` 必须区分 machine/projection 字段 | PASS | `WorkstreamMemoryUpdaterTest`、`WorkstreamMemoryProjectionTest` |
| V8 | 残留旧入口扫描 | 不得残留 `ReviewNoiseSemantics`、JSON fence canonicalization 入口、旧 canonical envelope 代码 | PASS | `rg` 扫描无结果 |
| V9 | 受影响模块回归 | `dual-runtime`、`dual-adapter-acpx`、`dual-ledger-file`、`dual-cli`、`dual-server` 测试必须全绿 | PASS | 五模块 + 依赖 reactor `BUILD SUCCESS` |
| V10 | Patch 质量 | `git diff --check` 必须通过 | PASS | `git diff --check` 无输出 |
| V11 | Simple semantic real-case | controlled fake acpx 走真实 `dualctl` / worktree / ledger path；必须分别证明 plan 正文缺失、review noise summary、reread reason 缺失会命中对应 typed 边界 | PASS | `scripts/semantic-boundary-smoke.sh all` |

## 验证记录

- 定向边界测试：
  `rtk mvn -pl dual-adapter-acpx,dual-runtime -am -Dtest=ArtifactContractValidatorTest,AgentFinalArtifactExtractorTest,FailureArbiterTest,FailureClassifyHandlerTest,WorkstreamMemoryUpdaterTest,WorkstreamMemoryProjectionTest -Dsurefire.failIfNoSpecifiedTests=false test`
  结果：BUILD SUCCESS；runtime 65 tests，adapter 15 tests。
- 全量受影响模块回归：
  `rtk mvn -pl dual-adapter-acpx,dual-runtime,dual-ledger-file,dual-cli,dual-server -am test`
  结果：BUILD SUCCESS；runtime 370，adapter 192，ledger 123，cli 57，server 52。
- Simple semantic real-case：
  `rtk proxy dual-orchestrator/scripts/semantic-boundary-smoke.sh all`
  结果：BUILD SUCCESS；`plan-body` 命中 `FORMAL_ARTIFACT_BODY_MISSING`；`review-noise` 未因 summary 自然语言进入 `REVIEW_NOISE`；`reread-reason` 命中 `REREAD_REASON_MISSING`。
- 残留扫描：
  `rtk proxy rg -n "ReviewNoiseSemantics|canonicalEnvelope|JSON_FENCE|jsonFenceArtifactBeforeControl" ...`
  结果：无匹配。
- Patch 检查：
  `rtk git diff --check`
  结果：无输出。
