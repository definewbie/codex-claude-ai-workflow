package dev.dual.runtime.handler;

public final class ContractEventTypes {
    private ContractEventTypes() {}
    public static final String COMPILATION_STARTED = "contract_compilation_started";
    public static final String COMPILATION_COMPLETED = "contract_compilation_completed";
    public static final String REVIEW_STARTED = "contract_review_started";
    public static final String REVIEW_COMPLETED = "contract_review_completed";
    public static final String APPROVED = "contract_approved";
    public static final String REQUIRES_CLARIFICATION = "contract_requires_clarification";
    public static final String SCOPE_REDUCTION_REQUESTED = "contract_scope_reduction_requested";
    public static final String REJECTED = "contract_rejected";
}
