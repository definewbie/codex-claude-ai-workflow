package app.cryptoincome.grid.application;

import app.cryptoincome.grid.domain.GridParameterSnapshot;

public record CapturedGridParameter(long id, GridParameterSnapshot snapshot) {
    public CapturedGridParameter {
        if (id <= 0) throw new IllegalArgumentException("id 必须大于 0");
        if (snapshot == null) throw new IllegalArgumentException("snapshot 不能为空");
    }
}
