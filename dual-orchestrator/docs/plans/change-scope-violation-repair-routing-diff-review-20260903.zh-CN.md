# Change-Scope Violation Repair Routing 独立 Diff Review

日期：2026-09-03  
方式：独立只读控制流追踪、focused tests、full reactor、diff-check；未调用 provider，未恢复 OKX workstream。

## Findings

P0：无。P1：无。

P2：Gate 测试已覆盖主要 lineage 伪造、删除、多 ref 与 stable-only，但对 packet 中
`violations/missingDeclared` 的字段级展示断言，以及 status/ref 不一致的独立命名用例仍可更细。production 控制流已强制这些
条件，不阻塞提交。

## 首轮问题关闭

1. Definition invariant 与 Gate producer contract 已统一：二者都固定要求
   `implement.artifact_validation + artifact.validate`。
2. Definition 负向矩阵已覆盖 flag、source、violation edge、Gate 语义与 repair loop；Gate lineage 已覆盖主要绕过形态。
3. Scope-violation Gate 只展示 occurrence-backed evaluation，不再读取 stable latest changed-files/diff/status。

## Boundary Review

- TaskContract.scope 仍是唯一 scope authority，human note 不修改或扩张 contract。
- Occurrence evidence 以 node instance 隔离并由内容 SHA 锚定；无扫描、mtime 或 stable fallback。
- Gate 在 packet 写入前验证 producer definition、handler、status、iteration、output refs、typed disposition 与 artifact SHA。
- Definition 构造期强制唯一 unconditional violation edge、revise-only repair Gate 和唯一 unconditional repair loop。
- 三个 bundled definition 与一个 experiment 一致。

验证：focused delta 126/0/0/0；full reactor 1323/0/0/0；`git diff --check` clean。

结论：`Boundary Review PASS / ALLOWED TO COMMIT`。提交必须排除旧的 OKX recovery Gate 草案。
