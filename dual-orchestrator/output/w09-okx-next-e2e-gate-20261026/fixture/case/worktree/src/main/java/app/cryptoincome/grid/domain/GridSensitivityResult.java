package app.cryptoincome.grid.domain;

import java.math.BigDecimal;

public record GridSensitivityResult(int gridCount, BigDecimal worsePathIncome) {}
