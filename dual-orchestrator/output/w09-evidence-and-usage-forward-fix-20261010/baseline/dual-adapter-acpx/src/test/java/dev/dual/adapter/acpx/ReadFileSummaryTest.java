package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the deterministic, structural read-file summary extractor
 * (no LLM, no RAG — regex/structural only).
 */
class ReadFileSummaryTest {
    @Test
    void markdownSummaryExtractsHeadingsAndScopeRelevantSentences() {
        var content = """
                # Auth Module

                Handles login.

                ## Token Refresh

                The token refresh runs on a 60s timer.
                """;
        var s = ReadFileSummary.summarize(content, "docs/auth.md", Set.of("token refresh"));
        assertTrue(s.contains("Auth Module"), s);
        assertTrue(s.contains("Token Refresh"), s);
        assertTrue(s.contains("token refresh runs"), s);
        assertTrue(s.length() <= 400, "len=" + s.length());
    }

    @Test
    void sourceSummaryExtractsExportsFunctionsAndClasses() {
        var content = """
                export function refreshToken() {}
                export class AuthClient {}
                function helper() {}
                const TIMEOUT = 60;
                """;
        var s = ReadFileSummary.summarize(content, "src/auth.js", Set.of());
        assertTrue(s.contains("refreshToken"), s);
        assertTrue(s.contains("AuthClient"), s);
        assertTrue(s.contains("helper"), s);
        assertTrue(s.length() <= 500, "len=" + s.length());
    }

    @Test
    void testSummaryExtractsDescribeAndItNames() {
        var content = """
                describe('AuthClient', () => {
                  it('refreshes the token', () => {});
                  test('rejects expired tokens', () => {});
                });
                """;
        var s = ReadFileSummary.summarize(content, "test/auth.test.js", Set.of());
        assertTrue(s.contains("AuthClient"), s);
        assertTrue(s.contains("refreshes the token"), s);
        assertTrue(s.contains("rejects expired tokens"), s);
        assertTrue(s.length() <= 500, "len=" + s.length());
    }

    @Test
    void pythonTestAndDefNamesExtracted() {
        var content = """
                def test_refresh_token():
                    pass

                class AuthClient:
                    def login(self):
                        pass
                """;
        var s = ReadFileSummary.summarize(content, "test/test_auth.py", Set.of());
        assertTrue(s.contains("test_refresh_token"), s);
        assertTrue(s.contains("AuthClient"), s);
    }

    @Test
    void emptyWhenNoExtractableStructure() {
        // config/JSON with no structural names or headings -> empty; updater falls back to metadata.
        var s = ReadFileSummary.summarize("{\"a\":1}", "config/app.json", Set.of());
        assertTrue(s.isBlank(), s);
    }

    @Test
    void deterministicAcrossCalls() {
        var content = "export function foo() {}\n";
        var a = ReadFileSummary.summarize(content, "src/x.ts", Set.of());
        var b = ReadFileSummary.summarize(content, "src/x.ts", Set.of());
        assertEquals(a, b);
        assertTrue(a.contains("foo"));
    }

    @Test
    void sourceSummaryCapEnforcedOnLargeFile() {
        var sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("export function funcNumber").append(i).append("() {}\n");
        }
        var s = ReadFileSummary.summarize(sb.toString(), "src/big.js", Set.of());
        assertTrue(s.length() <= 500, "len=" + s.length());
    }
}
