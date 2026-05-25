package dev.dual.adapter.acpx;

import java.nio.file.Path;

public record AcpxRunRequest(
        Path workdir,
        String sessionName,
        Path promptPath,
        Path stdoutPath,
        Path stderrPath,
        boolean approveAll
) {
}
