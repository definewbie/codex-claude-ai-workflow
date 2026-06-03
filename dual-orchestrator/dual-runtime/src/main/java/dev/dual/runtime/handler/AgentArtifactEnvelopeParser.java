package dev.dual.runtime.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AgentArtifactEnvelopeParser {
    private static final Pattern ENVELOPE = Pattern.compile(
            "(?s)<dual-artifact\\s+type=\"(?<type>[^\"]+)\"\\s+version=\"(?<version>\\d+)\"\\s*>\\s*(?<json>.*?)\\s*</dual-artifact>"
    );

    public Optional<AgentArtifactEnvelope> latest(String markdown, String type) {
        if (markdown == null || markdown.isBlank()) {
            return Optional.empty();
        }
        List<AgentArtifactEnvelope> matches = new ArrayList<>();
        var matcher = ENVELOPE.matcher(markdown);
        while (matcher.find()) {
            if (!type.equals(matcher.group("type"))) {
                continue;
            }
            matches.add(new AgentArtifactEnvelope(
                    matcher.group("type"),
                    Integer.parseInt(matcher.group("version")),
                    matcher.group("json").trim()
            ));
        }
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(matches.get(matches.size() - 1));
    }
}
