package dev.dual.runtime.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class MarkdownArtifactFields {
    private MarkdownArtifactFields() {
    }

    static List<String> listSection(String markdown, String... aliases) {
        var section = sectionText(markdown, aliases);
        if (section.isBlank()) {
            return List.of();
        }
        var items = new ArrayList<String>();
        var sawListItem = false;
        for (var line : section.lines().toList()) {
            var trimmed = line.strip();
            if (trimmed.matches("^[-*]\\s+.+")) {
                sawListItem = true;
                addItem(items, trimmed.substring(2).strip());
            } else if (trimmed.matches("^\\d+[.)]\\s+.+")) {
                sawListItem = true;
                addItem(items, trimmed.replaceFirst("^\\d+[.)]\\s+", "").strip());
            }
        }
        if (sawListItem && items.isEmpty()) {
            return List.of();
        }
        if (!items.isEmpty()) {
            return List.copyOf(items);
        }
        var compact = compact(section);
        return compact.isBlank() ? List.of() : List.of(compact);
    }

    static String textSection(String markdown, String... aliases) {
        return compact(sectionText(markdown, aliases));
    }

    private static String sectionText(String markdown, String... aliases) {
        var lines = markdown == null ? List.<String>of() : markdown.lines().toList();
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (headingMatches(lines.get(i), aliases)) {
                start = i + 1;
                break;
            }
        }
        if (start < 0) {
            return "";
        }
        int end = lines.size();
        for (int i = start; i < lines.size(); i++) {
            if (isHeading(lines.get(i))) {
                end = i;
                break;
            }
        }
        return String.join("\n", lines.subList(start, end)).strip();
    }

    private static boolean headingMatches(String line, String... aliases) {
        if (!isHeading(line)) {
            return false;
        }
        var normalized = normalizeHeading(line);
        for (var alias : aliases) {
            if (normalized.equals(normalize(alias))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHeading(String line) {
        return line != null && line.stripLeading().matches("^#{1,6}\\s+.+");
    }

    private static String normalizeHeading(String line) {
        return normalize(line.stripLeading()
                .replaceFirst("^#{1,6}\\s+", "")
                .replaceFirst("^\\d+[.)]\\s+", ""));
    }

    private static String normalize(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
    }

    private static String compact(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return String.join("\n", text.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .toList());
    }

    private static void addItem(List<String> items, String item) {
        if (item != null && !item.isBlank()) {
            items.add(item);
        }
    }
}
