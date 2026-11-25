package app.cryptoincome.grid.domain;

public final class GridSourceException extends RuntimeException {
    public enum Category { TIMEOUT, INTERRUPTED, NETWORK, HTTP_ERROR, API_REJECTED, INVALID_RESPONSE }

    private final Category category;
    private final String providerCode;
    private final boolean retryable;

    public GridSourceException(Category category, String providerCode, boolean retryable,
                               String message, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.providerCode = providerCode;
        this.retryable = retryable;
    }

    public Category category() { return category; }

    public String providerCode() { return providerCode; }

    public boolean retryable() { return retryable; }
}
