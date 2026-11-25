package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import org.junit.jupiter.api.Test;

import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Test-only drift guard; the index is never a runtime source of rules or approval. */
class ContractQualificationRejectionInventoryTest {
    @Test void everyRejectionSiteIsIndexedAndNewSitesCannotSilentlyEscape() throws Exception {
        JsonNode index;
        try (var input = getClass().getResourceAsStream("/contract-qualification-rejection-sites.json")) {
            assertNotNull(input);
            index = new ObjectMapper().readTree(input);
        }
        var module = Path.of(System.getProperty("basedir", ".")).toAbsolutePath();
        var sourcePath = module.resolve("src/main/java/dev/dual/runtime/handler/TaskContractProjectionVerifier.java");
        if (!Files.isRegularFile(sourcePath)) sourcePath = module.resolve(index.path("path").asText());
        var bytes = Files.readAllBytes(sourcePath);
        var source = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(index.path("sha256").asText(), HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)), "update the reviewed site index with source changes");
        var expected = new ArrayList<String>();
        for (var row : index.path("rows")) {
            assertFalse(row.path("site_id").asText().isBlank());
            assertFalse(row.path("proposed_rule_id").asText().isBlank());
            if ("EXPLICIT_UNCOVERED_PASSTHROUGH".equals(row.path("coverage").asText()))
                assertFalse(row.path("uncovered_reason").asText().isBlank());
            else {
                assertEquals("MAPPED_TYPED_PRODUCER", row.path("coverage").asText());
                var rule = ContractQualificationTrace.Rule.valueOf(row.path("mapped_rule").asText());
                assertEquals(rule.id, row.path("proposed_rule_id").asText());
            }
            var consumer = row.path("consumer").asText();
            expected.add(key(consumer.substring(consumer.lastIndexOf('.') + 1), row.path("kind").asText(),
                    row.path("original_arguments").asText()));
        }
        assertEquals(index.path("count").asInt(), expected.size());
        expected.sort(String::compareTo);
        assertEquals(expected, rejectionSites(source));
        // Counterproof: both result-based and exception-based new rejection paths must be detected.
        var extra = "\nstatic void unindexed() throws java.io.IOException {"
                + "Failure.of(\"NEW_REJECTION\", \"new branch\"); throw new IOException(\"new throw\"); }\n";
        var changed = source.substring(0, source.lastIndexOf('}')) + extra + "}\n";
        var extraSites = rejectionSites(changed);
        assertEquals(expected.size() + 2, extraSites.size());
        assertNotEquals(expected, extraSites);
        // Comment text and string literals are not executable rejection sites.
        var harmless = source.substring(0, source.lastIndexOf('}'))
                + "\n// Failure.of(\"COMMENT\", \"not a rejection\")\n"
                + "static String commentControl() { return \"throw new IOException(\\\"text\\\")\"; }\n}\n";
        assertEquals(expected, rejectionSites(harmless));
    }

    private static List<String> rejectionSites(String source) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "offline source guard requires the test JDK, not a JRE");
        var input = new SimpleJavaFileObject(URI.create("string:///TaskContractProjectionVerifier.java"),
                javax.tools.JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignored) { return source; }
        };
        var diagnostics = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
        var task = (JavacTask) compiler.getTask(null, null, diagnostics, List.of("-proc:none"), null, List.of(input));
        var units = task.parse();
        assertTrue(diagnostics.getDiagnostics().stream().noneMatch(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR),
                () -> diagnostics.getDiagnostics().toString());
        var positions = Trees.instance(task).getSourcePositions();
        var found = new ArrayList<String>();
        var factories = new int[1];
        for (var unit : units) new TreePathScanner<Void, Void>() {
            String method = "";
            String owner = "";
            @Override public Void visitClass(ClassTree tree, Void ignored) {
                var previous = owner; owner = tree.getSimpleName().toString();
                super.visitClass(tree, ignored); owner = previous; return null;
            }
            @Override public Void visitMethod(MethodTree tree, Void ignored) {
                var previous = method; method = tree.getName().toString();
                super.visitMethod(tree, ignored); method = previous; return null;
            }
            @Override public Void visitMethodInvocation(MethodInvocationTree tree, Void ignored) {
                var select = tree.getMethodSelect().toString();
                if ("Failure.of".equals(select) || select.endsWith(".Failure.of")) add("Failure.of", tree.getArguments());
                if ("trace.failure".equals(select)) add("trace.failure", tree.getArguments());
                return super.visitMethodInvocation(tree, ignored);
            }
            @Override public Void visitThrow(ThrowTree tree, Void ignored) {
                if (tree.getExpression() instanceof NewClassTree value) add("throw", value.getArguments());
                else {
                    var start = (int) positions.getStartPosition(unit, tree.getExpression());
                    var end = (int) positions.getEndPosition(unit, tree.getExpression());
                    found.add(key(method, "throw-expression", source.substring(start, end)));
                }
                return super.visitThrow(tree, ignored);
            }
            @Override public Void visitNewClass(NewClassTree tree, Void ignored) {
                var type = tree.getIdentifier().toString();
                // The one existing factory implementation is not a separate rejection. Any direct
                // construction elsewhere must be explicitly indexed, rather than bypassing Failure.of.
                if ("Failure".equals(type) || type.endsWith(".Failure")) {
                    if ("Failure".equals(owner) && "of".equals(method)) {
                        assertEquals(List.of("reason", "message", "null"), tree.getArguments().stream().map(Object::toString).toList(),
                                "the existing forwarding factory is the sole constructor exemption");
                        factories[0]++;
                    } else add("new-Failure", tree.getArguments());
                }
                return super.visitNewClass(tree, ignored);
            }
            void add(String kind, List<? extends com.sun.source.tree.ExpressionTree> arguments) {
                assertFalse(arguments.isEmpty(), "new zero-argument rejection needs an explicit mapping");
                var start = (int) positions.getStartPosition(unit, arguments.getFirst());
                var end = (int) positions.getEndPosition(unit, arguments.getLast());
                found.add(key(method, kind, source.substring(start, end)));
            }
        }.scan(unit, null);
        assertEquals(1, factories[0], "do not silently expand the forwarding factory exemption");
        found.sort(String::compareTo);
        return found;
    }

    /** Ignore formatting outside quoted values; changing message literals still changes the indexed site. */
    private static String key(String method, String kind, String arguments) {
        var result = new StringBuilder(method + ":" + kind + ":");
        boolean quoted = false, escaped = false;
        for (char c : arguments.toCharArray()) {
            if (quoted || !Character.isWhitespace(c)) result.append(c);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && quoted) escaped = true;
            else if (c == '"') quoted = !quoted;
        }
        return result.toString();
    }
}
