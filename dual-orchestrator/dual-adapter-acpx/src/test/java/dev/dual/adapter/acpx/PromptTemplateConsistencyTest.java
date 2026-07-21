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
            "workstream_id", "run_id", "node_id", "prompt_template",
            "ledger_path", "requirement", "context_package",
            "contract_review_principles", "allowed_decisions",
            "required_control_keys", "decision_example", "agent_result_contract"
    );
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([a-z_]+)}}");
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
    void allTemplatesDelegateToTheStrictAgentResultContract(String templateName) {
        var content = loadTemplate(templateName);
        assertTrue(content.contains("{{agent_result_contract}}"),
                templateName + " must inject the strict AgentResultEnvelope contract");
        assertFalse(content.contains("dual-control"), templateName + " retains the old Markdown protocol");
        assertFalse(content.contains("<dual-artifact"), templateName + " retains the old artifact envelope");
    }

    @ParameterizedTest
    @MethodSource("allTemplates")
    void templatesDoNotDuplicateDecisionOrFieldRules(String templateName) {
        var content = loadTemplate(templateName);
        assertTrue(content.contains("{{agent_result_contract}}"));
        assertFalse(content.contains("{{allowed_decisions}}"));
        assertFalse(content.contains("{{required_control_keys}}"));
        assertFalse(content.contains("{{decision_example}}"));
        assertFalse(content.contains("|NEEDS_REVISION|"),
                templateName + " must not hardcode pipe-delimited decision values");
        assertFalse(content.contains("ACCEPT|ACCEPT_WITH_NOTES|REJECT"),
                templateName + " must not hardcode acceptance decision values");
    }

    @Test
    void reviewerTemplatesDoNotEmbedASecondResultSchema() {
        for (var templateName : List.of(
                "intake-codex-product-review",
                "design-codex-review",
                "plan-codex-review"
        )) {
            var content = loadTemplate(templateName);
            assertTrue(content.contains("{{agent_result_contract}}"));
            assertFalse(content.contains("\"lifecycle_status\""),
                    templateName + " must not duplicate fields owned by AgentResultPromptContract");
        }
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
