# T4 Context Requirement Subtraction

日期：2026-07-29
状态：`DETERMINISTIC PASS / T5 ATTEMPTED, BENEFIT NOT PROVEN`

## 1. 唯一目标

删除 full workflow 中重复的 requirement context 投影，不改变 Agent 调用数、
workflow、gate、typed artifact、lineage 或业务质量语义。

```text
requirement.md
  -> AgentPromptRenderer
  -> final Agent prompt exactly once
```

不再是：

```text
requirement.md
  -> AgentPromptRenderer
  -> ContextBuildPackageHandler active profile
  -> final Agent prompt twice
```

## 2. Boundary Review

| 问题 | 结论 |
| --- | --- |
| Source of truth | `.agent/workstreams/<id>/requirement.md` |
| Authoritative injection owner | `AgentPromptRenderer` |
| Deleted path | six active `ContextProfiles` requirement sections |
| Preserved path | explicit `full` profile and custom section selection |
| Blocked failure | active context build no longer fails because requirement is absent |
| Cannot-bypass proof | final rendered prompt contains requirement body exactly once |
| Out of scope | Agent calls, nodes, gates, schema, lineage, recovery, pricing |

没有新增 module、engine、registry、DTO 或状态模型。

## 3. Production Change

`ContextProfiles` 中以下 profile 删除默认 `requirement`：

- `intake`
- `product_review`
- `architecture_review`
- `plan_review`
- `implementation`
- `acceptance`

`full` profile 保留 requirement。因此 standalone/custom context build 能力没有被删除，
但 full workflow 的六个 active profile 不再形成第二个默认投影。

## 4. Deterministic Evidence

Cannot-bypass tests 证明：

1. 六个 active profile 的 section manifest 均不含 `requirement`；
2. `full` profile 仍含 `requirement`；
3. active context package 不含 requirement body；
4. `AgentPromptRenderer` 生成的最终 prompt 含 requirement body 且只出现一次；
5. populated implementation context 不再因重复 requirement 预算压力降级
   `recent_agent_outputs`；
6. oversized requirement 在显式 `full` profile 中仍可 adaptive compression。

验证结果：

```text
ContextBuildPackageHandlerTest: 52/52 PASS
AgentPromptRendererTest: 30/30 PASS
WorkflowIntegrationTest + BuiltinHandlerTest: 69/69 PASS
Full Maven reactor: 9/9 modules PASS
git diff --check: PASS
```

## 5. Closure

```text
Declared by:
  requirement.md

Consumed by:
  AgentPromptRenderer

Enforced before:
  ACP Agent invocation

Failure route:
  missing requirement remains visible through AgentPromptRenderer;
  active context build no longer invents a second requirement contract

Cannot-bypass:
  exact-once final prompt assertion

Residual raw path:
  ContextBuildPackageHandler still supports explicit full/custom requirement rendering,
  but no active profile selects it
```

T4 可以声明 deterministic boundary `PASS`。它不能声明真实 token、cache、duration
或金额收益。T5 已使用与 T1 相同的 A3 controlled real-case 启动验证，但在首次
protocol correction 时按预先定义的 stop-loss 中断，因此收益仍为
`NOT PROVEN`。详见
`docs/plans/post-phase-d-t5-a3-comparison-20260729.zh-CN.md`。
