package dev.dual.adapter.acpx;

record AcpxFailureEvidence(String stdout, String stderr, int exitCode) {
    AcpxFailureEvidence {
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
    }

    String combinedTextLowercase() {
        return (stdout + "\n" + stderr).toLowerCase();
    }
}
