package dev.dual.adapter.acpx;

import java.nio.file.Path;

public record AcpxRunRequest(
        Path workdir,
        String sessionName,
        Path promptPath,
        Path stdoutPath,
        Path stderrPath,
        AcpxPermissionProfile permissionProfile
) {
    public AcpxRunRequest(
            Path workdir,
            String sessionName,
            Path promptPath,
            Path stdoutPath,
            Path stderrPath,
            boolean approveAll
    ) {
        this(workdir, sessionName, promptPath, stdoutPath, stderrPath, AcpxPermissionProfile.fromName("", approveAll));
    }

    boolean approveAll() {
        return permissionProfile.approveAll();
    }
}
