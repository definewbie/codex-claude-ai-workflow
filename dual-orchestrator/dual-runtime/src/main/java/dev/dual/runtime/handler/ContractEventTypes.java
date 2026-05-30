package dev.dual.runtime.handler;

/**
 * Domain event type constants for the contract lifecycle.
 *
 * <p>Emitted events (via WorkflowRuntime.emitDomainEvents):
 * <ul>
 *   <li>{@link #COMPILATION_COMPLETED} — after contract.compile handler succeeds</li>
 *   <li>{@link #FAILURE_CLASSIFIED} — after acceptance.failure_classify handler succeeds</li>
 * </ul>
 *
 * <p>Reserved constants below are <strong>not emitted</strong> in the current runtime.
 * They will be wired into RoutingEngine.resolveOpenGate() when downstream consumers
 * need semantic event aggregation for the contract review gate.
 */
public final class ContractEventTypes {
    private ContractEventTypes() {}

    public static final String COMPILATION_COMPLETED = "contract_compilation_completed";

    // Reserved for future gate-layer emission (gate.contract_review actions)
    // Not emitted in current runtime — do not add emission wiring without a consumer.
    public static final String REVIEW_COMPLETED = "contract_review_completed";
    public static final String APPROVED = "contract_approved";
    public static final String REQUIRES_CLARIFICATION = "contract_requires_clarification";
    public static final String SCOPE_REDUCTION_REQUESTED = "contract_scope_reduction_requested";
    public static final String REJECTED = "contract_rejected";

    public static final String FAILURE_CLASSIFIED = "failure_classified";
}
