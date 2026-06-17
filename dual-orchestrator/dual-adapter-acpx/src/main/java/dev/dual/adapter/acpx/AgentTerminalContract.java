package dev.dual.adapter.acpx;

final class AgentTerminalContract {
    static final String DONE_MARKER = "[done] end_turn";
    static final String CONTROL_FENCE = "```dual-control";

    boolean isComplete(String stdout) {
        if (stdout == null || !stdout.contains(DONE_MARKER)) {
            return false;
        }
        var fenceStart = stdout.lastIndexOf(CONTROL_FENCE);
        if (fenceStart < 0) {
            return false;
        }
        var fenceEnd = stdout.indexOf("```", fenceStart + CONTROL_FENCE.length());
        var body = fenceEnd < 0
                ? stdout.substring(fenceStart)
                : stdout.substring(fenceStart, fenceEnd);
        body = body.replace(DONE_MARKER, "");
        return body.contains("\ndecision:") && body.contains("\nsummary:");
    }
}
