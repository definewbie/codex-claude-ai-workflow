package dev.dual.runtime.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AgentArtifactEnvelopeParser {
    private static final Pattern ENVELOPE = Pattern.compile(
            "(?ms)^<dual-artifact\\s+type=\"(?<type>[^\"]+)\"\\s+version=\"(?<version>\\d+)\"\\s*>\\s*(?<json>.*?)\\s*^</dual-artifact>"
    );

    public Optional<AgentArtifactEnvelope> latest(String markdown, String type) {
        var matches = all(markdown).stream()
                .filter(envelope -> type.equals(envelope.type()))
                .toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(matches.get(matches.size() - 1));
    }

    public List<AgentArtifactEnvelope> all(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<AgentArtifactEnvelope> matches = new ArrayList<>();
        var matcher = ENVELOPE.matcher(markdown);
        while (matcher.find()) {
            matches.add(new AgentArtifactEnvelope(
                    matcher.group("type"),
                    Integer.parseInt(matcher.group("version")),
                    matcher.group("json").trim()
            ));
        }
        return List.copyOf(matches);
    }
}
