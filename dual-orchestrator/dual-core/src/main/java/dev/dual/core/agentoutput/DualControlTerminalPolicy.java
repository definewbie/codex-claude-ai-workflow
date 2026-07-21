package dev.dual.core.agentoutput;

/** Process-stop policy layered on top of the shared dual-control syntax result. */
public final class DualControlTerminalPolicy {
    public boolean isComplete(DualControlProtocolResult result) {
        if (result == null || !result.valid() || result.controlSpan() == null || result.terminalMarkerSpan() == null) {
            return false;
        }
        var source = result.source();
        var control = result.controlSpan();
        var marker = result.terminalMarkerSpan();
        return marker.start() >= control.endExclusive()
                && source.substring(control.endExclusive(), marker.start()).isBlank()
                && source.substring(marker.endExclusive()).isBlank();
    }
}
