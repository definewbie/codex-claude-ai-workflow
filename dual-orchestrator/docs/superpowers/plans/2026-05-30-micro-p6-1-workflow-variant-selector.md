# micro-P6.1: Workflow Variant Selector — Implementation Plan

## Context

P5 TaskContract and P6 FailureArbiter are merged to master. Both add workflow variants (`p5Mainline()`, `p6Mainline()`) with new nodes, edges, and handlers. However, `WorkflowDefinitionLoader.loadOrDefault()` hardcodes `thinMainline()` as the fallback. Users cannot trigger P5 or P6 features without manually writing a workflow JSON file.

This micro-patch makes p5/p6 variants selectable via CLI flag.

**Strategic framing:** 当前最值钱的不是反馈闭环，而是先让闭环能够启动。P7 的前提是：P5/P6 可达、已有真实样本、已有事件流。

---

## Scope

**IN scope:**
1. `LifecycleWorkflowDefinitionFactory.forVariant(String)` — switch over variant name
2. `WorkflowDefinitionLoader.loadOrDefault(Path, String)` — variant-aware fallback
3. CLI `--workflow-variant` flag on `StartCommand`
4. Tests for variant routing and backward compatibility

**OUT of scope (strict):**
- Event emission (micro-P6.2, separate branch)
- P7 calibration / pattern analysis
- Changes to thinMainline / p5Mainline / p6Mainline internals
- Config file support (future; CLI flag is the first mechanism)
- Any handler / registry / report changes

---

## Decisions (confirmed)

1. **Variant selection**: CLI flag `--workflow-variant` (values: `thin`, `p5`, `p6`)
2. **Default**: `thin` (backward compatible)
3. **Future config property**: Loader accepts variant param; CLI flag passes it; config file can be added later without changing the loader
4. **Event emission**: Strictly separate — not in this patch

---

## Files

### Create
- `dual-runtime/src/test/java/dev/dual/runtime/lifecycle/WorkflowVariantSelectorTest.java`
- `dual-cli/src/test/java/dev/dual/cli/StartCommandVariantTest.java`

### Modify
- `dual-runtime/src/main/java/dev/dual/runtime/lifecycle/LifecycleWorkflowDefinitionFactory.java` — add `forVariant(String)`
- `dual-runtime/src/main/java/dev/dual/runtime/lifecycle/WorkflowDefinitionLoader.java` — add variant param to `loadOrDefault`
- `dual-cli/src/main/java/dev/dual/cli/StartCommand.java` — add `--workflow-variant` option

---

## Task-by-Task Plan

### Task 1: LifecycleWorkflowDefinitionFactory.forVariant()

**Files:**
- Modify: `dual-runtime/.../LifecycleWorkflowDefinitionFactory.java`
- Create: `dual-runtime/.../lifecycle/WorkflowVariantSelectorTest.java`

Add method after `p6Mainline()`:
```java
public WorkflowDefinition forVariant(String variant) {
    if (variant == null || variant.isBlank() || "thin".equals(variant)) {
        return thinMainline();
    }
    return switch (variant) {
        case "p5" -> p5Mainline();
        case "p6" -> p6Mainline();
        default -> throw new IllegalArgumentException(
                "unknown workflow variant: " + variant + ". Supported: thin, p5, p6");
    };
}
```

Tests:
1. `forVariant_null_returnsThinMainline` — null → thinMainline (workflowId = "agent-lifecycle")
2. `forVariant_blank_returnsThinMainline` — "" → thinMainline
3. `forVariant_thin_returnsThinMainline` — "thin" → thinMainline
4. `forVariant_p5_returnsP5Mainline` — "p5" → p5Mainline (workflowId = "agent-lifecycle-p5")
5. `forVariant_p6_returnsP6Mainline` — "p6" → p6Mainline (workflowId = "agent-lifecycle-p6")
6. `forVariant_unknown_throws` — "p7" → IllegalArgumentException with "unknown workflow variant"

### Task 2: WorkflowDefinitionLoader variant support

**Files:**
- Modify: `dual-runtime/.../WorkflowDefinitionLoader.java`

Add overloaded method:
```java
public WorkflowDefinition loadOrDefault(Path workflowPath, String variant) throws IOException {
    if (workflowPath != null) {
        return load(workflowPath);
    }
    return new LifecycleWorkflowDefinitionFactory().forVariant(variant);
}
```

Keep existing `loadOrDefault(Path)` as backward-compatible wrapper:
```java
public WorkflowDefinition loadOrDefault(Path workflowPath) throws IOException {
    return loadOrDefault(workflowPath, null);
}
```

Tests (add to `WorkflowVariantSelectorTest` or existing loader test):
7. `loadOrDefault_withP6Variant_returnsP6Mainline` — null path + "p6" variant
8. `loadOrDefault_withNullVariant_returnsThinMainline` — null path + null variant
9. `loadOrDefault_withPath_ignoresVariant` — valid path + "p6" → loads from file, not p6Mainline

### Task 3: StartCommand --workflow-variant flag

**Files:**
- Modify: `dual-cli/.../StartCommand.java`
- Create: `dual-cli/.../StartCommandVariantTest.java`

Add option:
```java
@Option(names = "--workflow-variant", description = "Built-in workflow variant: thin (default), p5, p6.")
String workflowVariant;
```

Modify `call()` method — change line 58-60:
```java
var workflowDefinition = new WorkflowDefinitionLoader().loadOrDefault(
        workflowPath == null ? null : workflowPath.toAbsolutePath().normalize(),
        workflowVariant
);
```

Add output line after existing "Workflow:" line:
```java
if (workflowVariant != null && !workflowVariant.isBlank()) {
    System.out.println("Variant: " + workflowVariant);
}
```

Tests:
10. `startCommand_defaultVariant_usesThinMainline` — no flag → workflowId contains "agent-lifecycle" (not p5/p6)
11. `startCommand_p6Variant_usesP6Mainline` — --workflow-variant=p6 → workflowId contains "agent-lifecycle-p6"

Note: These tests may need to verify via the WorkflowDefinitionLoader directly, since StartCommand creates git worktrees. If StartCommand integration tests are too heavy, test via the loader and validate the CLI flag wiring in a lighter test.

### Task 4: Verification

Run `mvn test`, confirm BUILD SUCCESS, 0 failures. Verify thinMainline/p5Mainline/p6Mainline internals unchanged.

---

## Commit Boundaries

Single commit: `feat(micro-P6.1): workflow variant selector — make p5/p6 variants reachable via --workflow-variant flag`

## Merge Base / Branch

- Base: `master` (post P6 merge)
- New branch: `dev-p6-1-workflow-variant-selector`
