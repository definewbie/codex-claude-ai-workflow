package app.cryptoincome.funding.domain;

public class FundingRateSourceException extends RuntimeException {

    public enum Category {
        TIMEOUT,
        NETWORK,
        INTERRUPTED,
        HTTP_ERROR,
        API_REJECTED,
        INVALID_RESPONSE
    }

    private final Category category;
    private final String providerCode;
    private final boolean retryable;

    public FundingRateSourceException(
            Category category,
            String providerCode,
            boolean retryable,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.category = category;
        this.providerCode = providerCode;
        this.retryable = retryable;
    }

    public FundingRateSourceException(Category category, String providerCode, boolean retryable, String message) {
        this(category, providerCode, retryable, message, null);
    }

    public Category category() {
        return category;
    }

    public String providerCode() {
        return providerCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
