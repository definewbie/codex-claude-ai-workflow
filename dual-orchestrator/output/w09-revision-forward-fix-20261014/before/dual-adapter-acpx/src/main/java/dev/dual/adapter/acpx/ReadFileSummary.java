package dev.dual.adapter.acpx;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic, structural summary of a file's contents for WorkstreamMemory.
 *
 * <p>No LLM, no RAG, no embeddings — only regex/structural extraction of public
 * symbols, headings, and test names. The result is auxiliary memory that helps
 * an agent decide a file need not be re-read; it is NOT a business source of
 * truth. Returns an empty string when no structure can be extracted, so the
 * caller can fall back to a metadata-only summary.</p>
 */
final class ReadFileSummary {
    private static final int MARKDOWN_CAP = 400;
    private static final int CODE_CAP = 500;
    private static final int MAX_COLLECT = 48;
    private static final int MAX_KEEP = 24;
    private static final int MAX_HEADINGS = 5;
    private static final int MAX_RELEVANT = 4;

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^#{1,6}\\s+(.+?)\\s*$");
    private static final Pattern EXPORTED = Pattern.compile(
            "export\\s+(?:default\\s+)?(?:async\\s+)?(?:function|class|const|let|var)\\s+(\\w+)");
    private static final Pattern TOP_FUNCTION = Pattern.compile(
            "(?m)^\\s*(?:export\\s+)?(?:async\\s+)?function\\s+(\\w+)");
    private static final Pattern TOP_CLASS = Pattern.compile(
            "(?m)^\\s*(?:export\\s+)?(?:default\\s+)?class\\s+(\\w+)");
    private static final Pattern PY_DEF = Pattern.compile("(?m)^\\s*(?:async\\s+)?def\\s+(\\w+)");
    private static final Pattern PY_CLASS = Pattern.compile("(?m)^\\s*class\\s+(\\w+)");
    private static final Pattern DESCRIBE = Pattern.compile("(?:describe|context)\\s*\\(\\s*['\"`]([^'\"`]+)");
    private static final Pattern IT_TEST = Pattern.compile("\\b(?:it|test)\\s*\\(\\s*['\"`]([^'\"`]+)");
    private static final Pattern PY_TEST_DEF = Pattern.compile("(?m)^\\s*def\\s+(test_\\w+)");

    private ReadFileSummary() {
    }

    static String summarize(String content, String path, Set<String> keywords) {
        if (content == null || content.isBlank()) {
            return "";
        }
        var lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (isMarkdown(lower)) {
            return markdownSummary(content, keywords);
        }
        if (isSource(lower)) {
            return codeSymbols(content, isTest(lower));
        }
        return "";
    }

    private static String markdownSummary(String content, Set<String> keywords) {
        var headings = new ArrayList<String>();
        var headingMatcher = MARKDOWN_HEADING.matcher(content);
        while (headingMatcher.find() && headings.size() < MAX_HEADINGS) {
            var heading = headingMatcher.group(1).trim();
            if (!heading.isBlank()) {
                headings.add(heading);
            }
        }
        var relevant = new ArrayList<String>();
        var kws = keywords == null ? Set.<String>of() : keywords;
        for (var raw : content.split("\n", -1)) {
            if (relevant.size() >= MAX_RELEVANT) {
                break;
            }
            var line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("|")
                    || line.startsWith("```") || line.startsWith("-")) {
                continue;
            }
            var low = line.toLowerCase(Locale.ROOT);
            if (kws.stream().anyMatch(k -> !k.isBlank() && low.contains(k.toLowerCase(Locale.ROOT)))) {
                relevant.add(cap(line, 120));
            }
        }
        var sb = new StringBuilder();
        if (!headings.isEmpty()) {
            sb.append("headings: ").append(String.join("; ", headings));
        }
        if (!relevant.isEmpty()) {
            sb.append(sb.length() == 0 ? "" : " | ").append("relevant: ").append(String.join("; ", relevant));
        }
        return cap(sb.toString(), MARKDOWN_CAP);
    }

    private static String codeSymbols(String content, boolean testFile) {
        var names = new LinkedHashSet<String>();
        addMatches(names, EXPORTED, content);
        addMatches(names, TOP_FUNCTION, content);
        addMatches(names, TOP_CLASS, content);
        addMatches(names, PY_DEF, content);
        addMatches(names, PY_CLASS, content);
        if (testFile) {
            addMatches(names, DESCRIBE, content);
            addMatches(names, IT_TEST, content);
            addMatches(names, PY_TEST_DEF, content);
        }
        if (names.isEmpty()) {
            return "";
        }
        var joined = String.join("; ", names.stream().limit(MAX_KEEP).toList());
        return cap((testFile ? "defs/tests: " : "defs: ") + joined, CODE_CAP);
    }

    private static void addMatches(LinkedHashSet<String> names, Pattern pattern, String content) {
        var matcher = pattern.matcher(content);
        while (matcher.find() && names.size() < MAX_COLLECT) {
            var name = matcher.group(1).trim();
            if (!name.isBlank()) {
                names.add(name);
            }
        }
    }

    private static boolean isMarkdown(String lower) {
        return lower.endsWith(".md") || lower.endsWith(".mdx");
    }

    private static boolean isTest(String lower) {
        return lower.contains("/test/") || lower.contains("/tests/") || lower.contains("\\test\\")
                || lower.endsWith(".test.js") || lower.endsWith(".test.ts")
                || lower.endsWith(".test.tsx") || lower.endsWith(".test.jsx")
                || lower.endsWith(".spec.js") || lower.endsWith(".spec.ts")
                || lower.endsWith(".spec.tsx") || lower.endsWith(".spec.jsx")
                || lower.endsWith("_test.py") || lower.contains("/test_");
    }

    private static boolean isSource(String lower) {
        return lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".tsx") || lower.endsWith(".jsx")
                || lower.endsWith(".py") || lower.endsWith(".java") || lower.endsWith(".kt")
                || lower.endsWith(".go") || lower.endsWith(".rs") || lower.endsWith(".rb")
                || lower.endsWith(".php") || lower.endsWith(".cs") || lower.endsWith(".swift")
                || lower.endsWith(".c") || lower.endsWith(".cc") || lower.endsWith(".cpp")
                || lower.endsWith(".h") || lower.endsWith(".hpp") || lower.endsWith(".m")
                || lower.endsWith(".scala") || lower.endsWith(".clj") || lower.endsWith(".ex")
                || lower.endsWith(".lua") || lower.endsWith(".sh");
    }

    private static String cap(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 1)).stripTrailing() + "…";
    }
}
