package dev.dual.adapter.acpx;

import java.nio.file.Path;

public record AcpxRunRequest(
        Path workdir,
        String sessionName,
        Path promptPath,
        Path stdoutPath,
        Path stderrPath,
        AcpxPermissionProfile permissionProfile,
        AcpxExecutionEventSink executionEventSink
) {
    public AcpxRunRequest(
            Path workdir,
            String sessionName,
            Path promptPath,
            Path stdoutPath,
            Path stderrPath,
            AcpxPermissionProfile permissionProfile
    ) {
        this(workdir, sessionName, promptPath, stdoutPath, stderrPath, permissionProfile, AcpxExecutionEventSink.NOOP);
    }

    public AcpxRunRequest {
        executionEventSink = executionEventSink == null ? AcpxExecutionEventSink.NOOP : executionEventSink;
    }

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
