package app.cryptoincome.income;

public class SimpleEarnSourceException extends RuntimeException {
    private final boolean retryable;
    public SimpleEarnSourceException(String message, boolean retryable, Throwable cause) {
        super(message, cause); this.retryable = retryable;
    }
    public boolean retryable() { return retryable; }
}
