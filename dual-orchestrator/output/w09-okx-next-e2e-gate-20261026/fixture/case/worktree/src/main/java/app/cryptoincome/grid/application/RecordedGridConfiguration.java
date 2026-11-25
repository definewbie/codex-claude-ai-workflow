package app.cryptoincome.grid.application;

import app.cryptoincome.grid.domain.GridConfigurationSnapshot;

public record RecordedGridConfiguration(long id, GridConfigurationSnapshot snapshot) {}
