package dev.dual.adapter.acpx;

import java.nio.file.Files;
import java.nio.file.Path;

/** Explicit synthetic sessions-show response for legacy shell fixtures; not native capability proof. */
final class SessionMetadataFixture {
    static final String RESPONSE = """
            case " $* " in
              *' sessions show '*)
                for arg in "$@"; do session="$arg"; done
                printf '{"schema":"acpx.session.v1","acpxRecordId":"fixture-record","acpSessionId":"fixture-native","cwd":"%s","name":"%s","closed":false,"protocolVersion":1,"agentCapabilities":{}}\\n' "$PWD" "$session"
                exit 0;;
            esac
            """;

    static void install(Path executable) throws Exception {
        var script = Files.readString(executable);
        int firstLine = script.indexOf('\n') + 1;
        Files.writeString(executable, script.substring(0, firstLine) + RESPONSE + script.substring(firstLine));
    }
}
