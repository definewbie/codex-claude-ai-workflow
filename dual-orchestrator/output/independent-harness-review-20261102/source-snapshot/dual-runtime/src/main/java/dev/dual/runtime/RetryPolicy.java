package dev.dual.runtime;

public record RetryPolicy(
        int maxRetries,
        long baseDelayMs,
        double backoffMultiplier
) {
    public long delayForAttempt(int attempt) {
        if (attempt <= 0 || baseDelayMs <= 0) {
            return 0;
        }
        return (long) (baseDelayMs * Math.pow(backoffMultiplier, attempt - 1));
    }
}
