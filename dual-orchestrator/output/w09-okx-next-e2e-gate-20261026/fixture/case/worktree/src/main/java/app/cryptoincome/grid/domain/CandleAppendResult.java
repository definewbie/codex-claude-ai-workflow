package app.cryptoincome.grid.domain;

public record CandleAppendResult(int received, int newFacts, int duplicateFacts) {
    public CandleAppendResult {
        if (received < 0 || newFacts < 0 || duplicateFacts < 0 || newFacts + duplicateFacts != received) {
            throw new IllegalArgumentException("K 线追加计数不一致");
        }
    }
}
