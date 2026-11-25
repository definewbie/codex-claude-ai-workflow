package dev.dual.core.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PromptLanguageTest {
    @Test
    void normalizesSupportedPromptLanguages() {
        assertEquals("auto", PromptLanguage.normalize(null));
        assertEquals("auto", PromptLanguage.normalize("auto"));
        assertEquals("en", PromptLanguage.normalize("english"));
        assertEquals("zh-CN", PromptLanguage.normalize("zh"));
        assertEquals("zh-CN", PromptLanguage.normalize("简体中文"));
    }

    @Test
    void rejectsUnsupportedPromptLanguage() {
        assertThrows(IllegalArgumentException.class, () -> PromptLanguage.normalize("fr"));
    }
}
