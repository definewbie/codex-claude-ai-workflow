package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstreamMemoryProjectionTest {
    @TempDir
    Path tempDir;

    private Path workstreamDir(String ws) {
        return tempDir.resolve(".agent/workstreams/" + ws);
    }

    private void writeMemory(String ws, String json) throws Exception {
        var dir = workstreamDir(ws).resolve("memory");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("workstream-memory.json"), json);
    }

    private void writeContract(String ws, String json) throws Exception {
        var dir = workstreamDir(ws).resolve("artifacts");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("task-contract.json"), json);
    }

    private void writeGitChangedFiles(String ws, String body) throws Exception {
        var dir = workstreamDir(ws).resolve("artifacts/git-evidence");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("changed-files.txt"), body);
    }

    @Test
    void agentHandoffIncludesScopeReadSummaryDecisionsOpenIssuesAndDirective() throws Exception {
        writeMemory("ws-handoff", """
                {"schemaVersion":1,"artifactType":"workstream_memory","workstreamId":"ws-handoff",
                 "decidedScope":["add login"],"nonGoals":["SSO"],
                 "readFiles":[{"path":"src/a.py","sha":"s1","summary":"A file","trusted":true,"invalidationReason":"","readRunId":"run-1"}],
                 "decisions":[{"stage":"plan","nodeId":"plan.claude_plan","runId":"run-1","decision":"READY_TO_IMPLEMENT","summary":"approved"}],
                 "openIssues":[{"id":"abc","desc":"handle edge","status":"open"}]}
                """);
        writeContract("ws-handoff", """
                {"objective":"o","status":"active","scope":{"included":[],"excluded":[]},
                 "nonGoals":[],"acceptanceCriteria":["login works"],"constraints":[],"riskAreas":[]}
                """);
        var b = new StringBuilder();
        WorkstreamMemoryProjection.appendAgentHandoff(b, workstreamDir("ws-handoff"), tempDir);
        var text = b.toString();
        assertTrue(text.contains("add login"), text);
        assertTrue(text.contains("SSO"), text);
        assertTrue(text.contains("src/a.py") && text.contains("A file"), text);
        assertTrue(text.contains("READY_TO_IMPLEMENT"), text);
        assertTrue(text.contains("handle edge"), text);
        assertTrue(text.toLowerCase().contains("reuse"), text);
        assertFalse(text.contains("(no stage handoff evidence yet)"), text);
        assertTrue(text.contains("Qualified scope source: unavailable"), text);
    }

    @Test
    void agentHandoffMarksInvalidatedReadsAsFreshReadAllowed() throws Exception {
        writeMemory("ws-inv", """
                {"schemaVersion":1,"artifactType":"workstream_memory","workstreamId":"ws-inv",
                 "decidedScope":[],"nonGoals":[],
                 "readFiles":[{"path":"src/config.py","sha":"s1","summary":"Old config","trusted":false,"invalidationReason":"file changed since prior read","readRunId":"run-1"}],
                 "decisions":[],"openIssues":[]}
                """);
        writeContract("ws-inv", """
                {"objective":"o","status":"active","scope":{"included":[],"excluded":[]},
                 "nonGoals":[],"acceptanceCriteria":[],"constraints":[],"riskAreas":[]}
                """);
        var b = new StringBuilder();
        WorkstreamMemoryProjection.appendAgentHandoff(b, workstreamDir("ws-inv"), tempDir);
        var text = b.toString();
        assertTrue(text.contains("src/config.py"), text);
        assertTrue(text.toLowerCase().contains("fresh read") || text.toLowerCase().contains("invalidated"), text);
    }

    @Test
    void reviewBriefIncludesPriorVerdictOpenIssuesChangedFilesAcceptanceAndDirective() throws Exception {
        writeMemory("ws-review", """
                {"schemaVersion":1,"artifactType":"workstream_memory","workstreamId":"ws-review",
                 "priorVerdicts":[{"nodeId":"acceptance.codex_verifier_review","stage":"acceptance","decision":"REJECTED","blocking":true,"mustFix":["fix token race"]}],
                 "openIssues":[{"id":"x","desc":"verify refresh","status":"open"}],
                 "readFiles":[{"path":"src/auth.py","sha":"s1","summary":"Auth module","trusted":true,"invalidationReason":""}],
                 "decisions":[]}
                """);
        writeContract("ws-review", """
                {"objective":"o","status":"active","scope":{"included":[],"excluded":[]},
                 "non_goals":[],"acceptance_criteria":["tokens refreshed"],"constraints":[],"risk_areas":[]}
                """);
        writeGitChangedFiles("ws-review", "src/auth.py\nsrc/main.py\n");
        var b = new StringBuilder();
        WorkstreamMemoryProjection.appendReviewEvidenceBrief(b, workstreamDir("ws-review"), tempDir);
        var text = b.toString();
        assertTrue(text.contains("Prior verdict") && text.contains("REJECTED"), text);
        assertTrue(text.contains("fix token race"), text);
        assertTrue(text.contains("verify refresh"), text);
        assertTrue(text.contains("TaskContract projection: unavailable"), text);
        assertFalse(text.contains("tokens refreshed"), text);
        assertTrue(text.contains("src/auth.py"), text);
        assertTrue(text.toLowerCase().contains("incremental"), text);
        assertTrue(text.contains("changed-files.txt"), text);
    }

    @Test
    void reviewBriefUsesMemoryWhenGitEvidenceAbsent() throws Exception {
        writeMemory("ws-nogit", """
                {"schemaVersion":1,"artifactType":"workstream_memory","workstreamId":"ws-nogit",
                 "priorVerdicts":[],"openIssues":[],
                 "readFiles":[{"path":"src/x.py","sha":"s","summary":"X module","trusted":true,"invalidationReason":""}],
                 "decisions":[]}
                """);
        writeContract("ws-nogit", """
                {"objective":"o","status":"active","scope":{"included":[],"excluded":[]},
                 "nonGoals":[],"acceptanceCriteria":[],"constraints":[],"riskAreas":[]}
                """);
        var b = new StringBuilder();
        WorkstreamMemoryProjection.appendReviewEvidenceBrief(b, workstreamDir("ws-nogit"), tempDir);
        var text = b.toString();
        assertTrue(text.contains("src/x.py"), text);
    }

    @Test
    void emptyMemoryReviewBriefRendersMinimalRefs() throws Exception {
        writeMemory("ws-empty-r", """
                {"schemaVersion":1,"artifactType":"workstream_memory","workstreamId":"ws-empty-r",
                 "priorVerdicts":[],"openIssues":[],"readFiles":[],"decisions":[]}
                """);
        var b = new StringBuilder();
        WorkstreamMemoryProjection.appendReviewEvidenceBrief(b, workstreamDir("ws-empty-r"), tempDir);
        var text = b.toString();
        assertFalse(text.contains("(no structured review evidence yet)"), text);
        assertTrue(text.contains("workstream-memory.json"), text);
    }

    @Test
    void acceptanceWithoutVerdictsDoesNotClaimIncrementalReviewMemory() throws Exception {
        writeMemory("ws-contract-only", """
                {"schemaVersion":1,"artifactType":"workstream_memory","workstreamId":"ws-contract-only",
                 "priorVerdicts":[],"openIssues":[],"readFiles":[],"decisions":[]}
                """);
        writeContract("ws-contract-only", """
                {"objective":"o","status":"active","scope":{"included":[],"excluded":[]},
                 "non_goals":[],"acceptance_criteria":["OTP remains single-use"],"constraints":[],"risk_areas":[]}
                """);

        var builder = new StringBuilder();
        WorkstreamMemoryProjection.appendReviewEvidenceBrief(
                builder, workstreamDir("ws-contract-only"), tempDir);
        var text = builder.toString();

        assertTrue(text.contains("no cumulative review verdict recorded"), text);
        assertTrue(text.contains("TaskContract projection: unavailable"), text);
        assertFalse(text.contains("OTP remains single-use"), text);
        assertFalse(text.contains("Incremental acceptance from orchestrator memory"), text);
        assertFalse(text.contains("Prior must-fix"), text);
    }

    @Test
    void agentHandoffRendersSemanticReadSummary() throws Exception {
        writeMemory("ws-sem-proj", """
                {"schemaVersion":1,"artifactType":"workstream_memory","workstreamId":"ws-sem-proj",
                 "decidedScope":[],"nonGoals":[],
                 "readFiles":[{"path":"src/auth.js","sha":"s1","summary":"defs: refreshToken; AuthClient","trusted":true,"invalidationReason":"","readRunId":"run-1"}],
                 "decisions":[],"openIssues":[]}
                """);
        writeContract("ws-sem-proj", """
                {"objective":"o","status":"READY_FOR_REVIEW","scope":{"included":[],"excluded":[]},
                 "non_goals":[],"acceptance_criteria":[],"constraints":[],"risk_areas":[]}
                """);
        var b = new StringBuilder();
        WorkstreamMemoryProjection.appendAgentHandoff(b, workstreamDir("ws-sem-proj"), tempDir);
        var text = b.toString();
        assertTrue(text.contains("refreshToken"), text);
        assertTrue(text.contains("AuthClient"), text);
        assertFalse(text.contains("Previously read"), "handoff must render semantic summary, not metadata: " + text);
    }

    @Test
    void emptyMemoryRendersMinimalFallbackRefsNotDump() throws Exception {
        writeMemory("ws-empty", """
                {"schemaVersion":1,"artifactType":"workstream_memory","workstreamId":"ws-empty",
                 "decidedScope":[],"nonGoals":[],"readFiles":[],"decisions":[],"openIssues":[]}
                """);
        var b = new StringBuilder();
        WorkstreamMemoryProjection.appendAgentHandoff(b, workstreamDir("ws-empty"), tempDir);
        var text = b.toString();
        assertFalse(text.contains("(no stage handoff evidence yet)"), text);
        assertTrue(text.contains("task-contract.json") || text.contains("workstream-memory.json"), text);
    }
}
