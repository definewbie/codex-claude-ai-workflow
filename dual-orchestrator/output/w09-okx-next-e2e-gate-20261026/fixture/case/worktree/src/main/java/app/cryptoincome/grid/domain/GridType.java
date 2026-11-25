package app.cryptoincome.grid.domain;

public enum GridType {
    ARITHMETIC("1"),
    GEOMETRIC("2");

    private final String providerValue;

    GridType(String providerValue) { this.providerValue = providerValue; }

    public String providerValue() { return providerValue; }

    public static GridType fromProvider(String value) {
        for (GridType type : values()) {
            if (type.providerValue.equals(value)) return type;
        }
        throw new IllegalArgumentException("未知网格类型: " + value);
    }
}
