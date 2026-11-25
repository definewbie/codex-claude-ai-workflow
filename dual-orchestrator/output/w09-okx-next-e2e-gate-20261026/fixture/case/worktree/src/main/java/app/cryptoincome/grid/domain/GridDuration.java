package app.cryptoincome.grid.domain;

public enum GridDuration {
    D7("7D", 7),
    D30("30D", 30),
    D180("180D", 180);

    private final String providerValue;
    private final int days;

    GridDuration(String providerValue, int days) {
        this.providerValue = providerValue;
        this.days = days;
    }

    public String providerValue() { return providerValue; }

    public int days() { return days; }

    public static GridDuration fromProvider(String value) {
        for (GridDuration duration : values()) {
            if (duration.providerValue.equals(value)) return duration;
        }
        throw new IllegalArgumentException("未知网格回测期限: " + value);
    }
}
