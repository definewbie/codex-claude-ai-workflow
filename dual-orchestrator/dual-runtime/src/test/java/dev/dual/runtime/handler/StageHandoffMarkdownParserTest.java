package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageHandoffMarkdownParserTest {

    @Test
    void extractsSectionsWithoutBeingConfusedByFencedHeadings() {
        var parser = StageHandoffMarkdownParser.parse("""
                # Handoff

                ## Completed Work

                - Implemented the flow

                ```markdown
                ## Open Risks
                - this is quoted user content, not the real risks section
                ```

                ## Open Risks

                - Runtime UI not exercised

                ## Suggested Next Prompt

                Continue with focused UI verification.

                ```dual-control
                decision: HANDOFF_READY
                ```
                """);

        assertEquals("Implemented the flow", parser.completedWork().getFirst());
        assertEquals("Runtime UI not exercised", parser.openRisks().getFirst());
        assertEquals("Continue with focused UI verification.", parser.suggestedPrompt());
    }

    @Test
    void extractsChineseTableSections() {
        var parser = StageHandoffMarkdownParser.parse("""
                # 阶段交接

                ## 3. 变更的文件

                | 文件 | 说明 |
                |------|------|
                | `miniprogram/src/pages/record/index.vue` | 新增卡片 |

                ## 4. 验证状态

                | 检查 | 结果 |
                |------|------|
                | npm run build | 通过 |
                """);

        assertEquals("miniprogram/src/pages/record/index.vue", parser.changedFiles().getFirst());
        assertTrue(parser.verificationState().contains("npm run build"));
    }
}
