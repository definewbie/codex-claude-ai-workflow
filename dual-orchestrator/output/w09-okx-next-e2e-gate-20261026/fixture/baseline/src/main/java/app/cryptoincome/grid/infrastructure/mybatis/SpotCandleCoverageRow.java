package app.cryptoincome.grid.infrastructure.mybatis;

import java.time.Instant;

public class SpotCandleCoverageRow {
    private Long actualMinutes;
    private Instant firstOpenTime;
    private Instant lastOpenTime;

    public Long getActualMinutes() { return actualMinutes; }
    public void setActualMinutes(Long actualMinutes) { this.actualMinutes = actualMinutes; }
    public Instant getFirstOpenTime() { return firstOpenTime; }
    public void setFirstOpenTime(Instant firstOpenTime) { this.firstOpenTime = firstOpenTime; }
    public Instant getLastOpenTime() { return lastOpenTime; }
    public void setLastOpenTime(Instant lastOpenTime) { this.lastOpenTime = lastOpenTime; }
}
