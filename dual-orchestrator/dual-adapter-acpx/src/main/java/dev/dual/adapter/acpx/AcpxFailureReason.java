package dev.dual.adapter.acpx;

import dev.dual.core.execution.AdapterSignalType;

/**
 * acpx adapter 输出的稳定失败语义。
 *
 * Provider 或 ACP wrapper 的原始错误文案可以变化，但进入 runtime/ledger
 * 之前必须先归一化成这里的 reason。这样 workflow 和 recovery 只消费
 * typed failure fact，不依赖供应商自然语言。
 */
enum AcpxFailureReason {
    AGENT_MODEL_MISMATCH("RECOVERABLE_AGENT_MODEL_MISMATCH"),
    AGENT_OVERLOADED("RECOVERABLE_AGENT_OVERLOADED"),
    AGENT_RECONNECT("RECOVERABLE_AGENT_RECONNECT"),
    AGENT_OUTPUT_INTERRUPTED("RECOVERABLE_AGENT_OUTPUT_INTERRUPTED"),
    AGENT_PERMISSION_BLOCKED("RECOVERABLE_AGENT_PERMISSION_BLOCKED"),
    AGENT_AUTH_FAILED("RECOVERABLE_AGENT_AUTH_FAILED"),
    PROVIDER_CONNECTION_FAILED("RECOVERABLE_PROVIDER_CONNECTION_FAILED"),
    SESSION_BOOTSTRAP_FAILED("RECOVERABLE_SESSION_BOOTSTRAP_FAILED"),
    SESSION_MISSING("RECOVERABLE_SESSION_MISSING"),
    RATE_LIMIT("RECOVERABLE_RATE_LIMIT"),
    CONTEXT_WINDOW("RECOVERABLE_CONTEXT_WINDOW"),
    TRANSPORT_FAILED("RECOVERABLE_TRANSPORT_FAILED");

    private final String code;

    AcpxFailureReason(String code) {
        this.code = code;
    }

    String code() {
        return code;
    }

    AdapterSignalType adapterSignalType() {
        return switch (this) {
            case AGENT_MODEL_MISMATCH -> AdapterSignalType.MODEL_MISMATCH;
            case AGENT_OVERLOADED, RATE_LIMIT -> AdapterSignalType.RATE_LIMITED;
            case AGENT_RECONNECT -> AdapterSignalType.AGENT_RECONNECT_REQUIRED;
            case AGENT_OUTPUT_INTERRUPTED, TRANSPORT_FAILED -> AdapterSignalType.PROCESS_STALLED;
            case AGENT_PERMISSION_BLOCKED -> AdapterSignalType.PERMISSION_BLOCKED;
            case AGENT_AUTH_FAILED -> AdapterSignalType.AUTH_FAILED;
            case PROVIDER_CONNECTION_FAILED -> AdapterSignalType.PROVIDER_CONNECTION_FAILED;
            case SESSION_BOOTSTRAP_FAILED -> AdapterSignalType.SESSION_BOOTSTRAP_FAILED;
            case SESSION_MISSING -> AdapterSignalType.SESSION_RESUME_FAILED;
            case CONTEXT_WINDOW -> AdapterSignalType.CONTEXT_WINDOW_EXCEEDED;
        };
    }
}
