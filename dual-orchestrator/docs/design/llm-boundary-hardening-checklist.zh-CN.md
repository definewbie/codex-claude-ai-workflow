# LLM 边界硬化 Checklist

本文件记录本轮针对 “CC / Codex 边界卡不住、自然语言被当成判断依据” 的整改状态。

## 边界原则

- Authoritative source of truth：workflow definition、typed structured artifact、contract sidecar、ledger event。
- Diagnostic / projection only：transcript、markdown prose、summary 文本、UI/CLI/report 展示。
- LLM 输出必须先进入 typed artifact / failure signal / event，再参与 route 或状态转换。
- prompt 约束不是完成条件；完成条件必须是 validator、runtime guard 或 regression test。

## Checklist

| ID | 优先级 | 问题 | Owner | 状态 | 完成证据 |
| --- | --- | --- | --- | --- | --- |
| P0-1 | P0 | `plan.claude_plan` 可把计划正文只写在 transcript，正式 artifact 只有 contract/control | Artifact contract validator | DONE | `ArtifactContractValidator` 拒绝缺正式计划正文；`ArtifactContractValidatorTest` 覆盖缺正文失败 / 正文齐全通过 |
| P0-2 | P0 | Markdown section fallback 仍可能把自然语言升格为 authoritative sidecar 字段 | Artifact contract validator | DONE | `ContractRegistry` 节点默认要求 `agent_output_contract`；markdown-only agent output 不再通过主线 validator |
| P0-3 | P0 | `ReviewNoiseSemantics` 按 summary 文本判断 review noise | Failure classification | DONE | 删除 summary 文本判断；`REVIEW_NOISE` 必须来自 typed `category_hint` |
| P0-4 | P0 | `category_hint` 用 substring 判断 verification insufficient | Failure classification | DONE | `category_hint` 改为 catalog 精确匹配/alias，不做 substring |
| P0-5 | P0 | adapter 可把普通 JSON fence 包装成 canonical artifact | ACP artifact extraction | DONE | `AgentFinalArtifactExtractor` 不再 canonicalize JSON fence；只有 canonical `<dual-artifact>` 会作为 structured artifact 输出 |
| P1-1 | P1 | `reread_reasons` missing 目前只是 telemetry，不是 contract failure | Read reuse contract | DONE | Validator 在有 run `toolUsage.readFiles` + `read-reuse-contract` 时强制 reusable re-read 必须有 matching reason |
| P1-2 | P1 | WorkstreamMemory 的自然语言 desc/summary 未来可能被误用于 route | Workstream memory model | DONE | open issue 写入 typed `sourceField`；memory 写出 `fieldSemantics` 标明 machine/projection 字段 |

## 修复记录

- P0-1：完成。新增 `plan.claude_plan` 正式 artifact body 校验，要求计划正文包含 Implementation Steps、File-Level Changes、Validation Plan、Rollback Notes、Known Risks、Human Review Checklist。正文只存在 transcript 或只存在 structured JSON 时会以 `FORMAL_ARTIFACT_BODY_MISSING` 失败。验证：`rtk mvn -pl dual-runtime -am -Dtest=ArtifactContractValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- P0-2/P0-5：完成。`ArtifactContractValidator` 对所有 `ContractRegistry` agent 节点默认要求 structured `agent_output_contract`；`AgentFinalArtifactExtractor` 不再把普通 JSON fence 包装成 canonical artifact。验证：`rtk mvn -pl dual-adapter-acpx,dual-runtime -am -Dtest=AgentFinalArtifactExtractorTest,ArtifactContractValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- P0-3/P0-4：完成。删除 `ReviewNoiseSemantics` 文本分类入口；`FailureArbiter` 只按 typed `category_hint` catalog 识别 `REVIEW_NOISE` / `VERIFICATION_INSUFFICIENT`，不再读取 summary 或 substring。验证：`rtk mvn -pl dual-runtime -am -Dtest=FailureArbiterTest,FailureClassifyHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- P1-1：完成。`ArtifactContractValidator` 在具备 typed run metadata 和 read-reuse-contract 时，校验实际重读的 reusable files 必须在 `agent_output_contract.reread_reasons` 中有 matching path。验证：`rtk mvn -pl dual-runtime -am -Dtest=ArtifactContractValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- P1-2：完成。`WorkstreamMemoryUpdater` 写入 open issues 时记录 `sourceField`，并在 memory artifact 中写入 `fieldSemantics`，明确 `desc/summary/verificationSummary` 只作 projection/prompt。验证：`rtk mvn -pl dual-adapter-acpx,dual-runtime -am -Dtest=WorkstreamMemoryUpdaterTest,WorkstreamMemoryProjectionTest -Dsurefire.failIfNoSpecifiedTests=false test`。
