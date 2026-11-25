package app.cryptoincome.grid.domain;

public interface GridParameterSource {
    GridParameterSnapshot fetch(String instrumentId, GridDuration duration);
}
