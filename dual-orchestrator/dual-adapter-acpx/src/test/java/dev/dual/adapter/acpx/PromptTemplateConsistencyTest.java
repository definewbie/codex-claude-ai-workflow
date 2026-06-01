package dev.dual.adapter.acpx;

import dev.dual.runtime.handler.ContractRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTemplateConsistencyTest {
    private static final String TEMPLATE_ROOT = "dev/dual/adapter/acpx/prompts/";
    private static final Set<String> KNOWN_VARIABLES = Set.of(
            "workstream_id", "node_id", "prompt_template",
            "ledger_path", "requirement", "context_package"
    );
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([a-z_]+)}}");
    private static final Pattern CONTROL_DECISION_PATTERN = Pattern.compile("(?m)^decision:\\s*([A-Z_|]+)\\s*$");
    private static final Map<String, String> TEMPLATE_NODE_IDS = Map.of(
            "intake-claude-brainstorm", "intake.claude_brainstorm",
            "intake-codex-product-review", "intake.codex_product_review",
            "design-claude", "design.claude_design",
            "design-codex-review", "design.codex_arch_review",
            "plan-claude", "plan.claude_plan",
            "plan-codex-review", "plan.codex_plan_review",
            "implement-claude", "implement.claude_implement",
            "implement-claude-self-test", "implement.claude_self_test_report",
            "acceptance-codex-review", "acceptance.codex_verifier_review",
            "stage-claude-handoff", "stage.claude_handoff"
    );

    static Stream<String> allTemplates() {
        return Stream.of(
                "intake-claude-brainstorm", "intake-codex-product-review",
                "design-claude", "design-codex-review",
                "plan-claude", "plan-codex-review",
                "implement-claude", "implement-claude-self-test",
                "acceptance-codex-review", "stage-claude-handoff"
        );
    }

    @ParameterizedTest
    @MethodSource("allTemplates")
    void allTemplatesUseOnlyKnownVariables(String templateName) {
        var content = loadTemplate(templateName);
        var matcher = VARIABLE_PATTERN.matcher(content);
        var unknown = new java.util.LinkedHashSet<String>();
        while (matcher.find()) {
            var variable = matcher.group(1);
            if (!KNOWN_VARIABLES.contains(variable)) {
                unknown.add(variable);
            }
        }
        assertTrue(unknown.isEmpty(),
                templateName + " references unknown variables: " + unknown);
    }

    @ParameterizedTest
    @MethodSource("allTemplates")
    void allTemplatesDeclareDualControlContract(String templateName) {
        var content = loadTemplate(templateName);
        assertTrue(content.contains("```dual-control"),
                templateName + " missing dual-control block in Output Contract");
        assertTrue(content.contains("decision:"),
                templateName + " missing 'decision:' key in dual-control contract");
        assertTrue(content.contains("summary:"),
                templateName + " missing 'summary:' key in dual-control contract");
    }

    @ParameterizedTest
    @MethodSource("allTemplates")
    void templateDecisionValuesMatchRuntimeContract(String templateName) {
        var content = loadTemplate(templateName);
        var matcher = CONTROL_DECISION_PATTERN.matcher(content);
        var declared = new java.util.LinkedHashSet<String>();
        while (matcher.find()) {
            declared.clear();
            declared.addAll(List.of(matcher.group(1).split("\\|")));
        }
        assertFalse(declared.isEmpty(), templateName + " missing terminal decision value list");

        var nodeId = TEMPLATE_NODE_IDS.get(templateName);
        var contract = ContractRegistry.forNode(nodeId);
        assertEquals(contract.allowedDecisions(), declared,
                templateName + " decision values do not match " + nodeId + " runtime contract");
    }

    @ParameterizedTest
    @MethodSource("allTemplates")
    void allTemplatesIncludeContextPackageSection(String templateName) {
        var content = loadTemplate(templateName);
        assertTrue(content.contains("{{context_package}}"),
                templateName + " missing {{context_package}} injection point");
    }

    @ParameterizedTest
    @MethodSource("allTemplates")
    void allTemplatesIncludeRequirementSection(String templateName) {
        var content = loadTemplate(templateName);
        assertTrue(content.contains("{{requirement}}"),
                templateName + " missing {{requirement}} injection point");
    }

    @Test
    void templateCountIs10() {
        assertEquals(10, allTemplates().count());
    }

    private static String loadTemplate(String templateName) {
        var resource = TEMPLATE_ROOT + templateName + ".md";
        try (var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalArgumentException("missing template: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
