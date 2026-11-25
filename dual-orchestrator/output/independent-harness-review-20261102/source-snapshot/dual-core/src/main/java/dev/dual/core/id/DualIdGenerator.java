package dev.dual.core.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DualIdGenerator {
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(java.time.ZoneOffset.UTC);

    private final Clock clock;
    private final SecureRandom random;

    public DualIdGenerator(Clock clock) {
        this(clock, new SecureRandom());
    }

    DualIdGenerator(Clock clock, SecureRandom random) {
        this.clock = clock;
        this.random = random;
    }

    public DualId workstreamId() {
        return next("ws");
    }

    public DualId workspaceId() {
        return next("wsp");
    }

    public DualId runId() {
        return next("run");
    }

    public DualId next(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        return new DualId(prefix.toLowerCase(Locale.ROOT) + "-" + FORMATTER.format(clock.instant()) + "-" + suffix());
    }

    private String suffix() {
        var builder = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}
