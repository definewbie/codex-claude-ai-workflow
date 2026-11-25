package dev.dual.adapter.git;

import java.nio.file.Path;

public record GitRepositoryInfo(Path gitRoot, String baseBranch, String baseCommit) {
}
