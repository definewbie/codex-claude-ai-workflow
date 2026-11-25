package dev.dual.ledger.file;

final class IterationRef {
    private final int number;

    private IterationRef(int number) {
        if (number < 1) {
            throw new IllegalArgumentException("iteration number must be positive");
        }
        this.number = number;
    }

    static IterationRef first() {
        return new IterationRef(1);
    }

    static IterationRef ofNumber(int number) {
        return new IterationRef(number);
    }

    static IterationRef parse(String iterationId) {
        if (iterationId == null || !iterationId.matches("iter-\\d{3}")) {
            throw new IllegalArgumentException("invalid iteration id: " + iterationId);
        }
        return new IterationRef(Integer.parseInt(iterationId.substring("iter-".length())));
    }

    int number() {
        return number;
    }

    String id() {
        return "iter-" + dirName();
    }

    String dirName() {
        return "%03d".formatted(number);
    }

    String artifactSuffix() {
        return dirName();
    }

    IterationRef next() {
        return new IterationRef(number + 1);
    }
}
