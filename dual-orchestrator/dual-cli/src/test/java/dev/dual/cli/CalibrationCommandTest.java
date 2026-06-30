package dev.dual.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibrationCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void listShowsCalibrationCandidates() throws Exception {
        writeCandidate("candidate-1", "DRAFT");

        var command = new CalibrationListCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-cal";

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("Candidate ID\tStatus\tFailure\tUpdate Type\tDecision\tSource Node"));
        assertTrue(printed.contains("candidate-1\tDRAFT\tCONTRACT_GAP\tCONTRACT_TEMPLATE\tREJECT\tacceptance.classify_failure"));
    }

    @Test
    void showPrintsCandidateMarkdownByDefault() throws Exception {
        writeCandidate("candidate-2", "DRAFT");

        var command = new CalibrationShowCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-cal";
        command.candidateId = "candidate-2";

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("Calibration Candidate"));
        assertTrue(printed.contains("Status: DRAFT"));
        assertTrue(printed.contains("# Candidate candidate-2"));
    }

    @Test
    void decideUpdatesCandidateStatusAndNotes() throws Exception {
        writeCandidate("candidate-3", "DRAFT");

        var command = new CalibrationDecideCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-cal";
        command.candidateId = "candidate-3";
        command.status = "accepted";
        command.note = "Promote this into the contract template later.";

        assertEquals(0, command.call());

        var json = Files.readString(candidateJson("candidate-3"), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"status\" : \"ACCEPTED\""));
        assertTrue(json.contains("\"decisionNotes\" : \"Promote this into the contract template later.\""));
        assertTrue(json.contains("\"decidedAt\""));
    }

    @Test
    void planWritesApplyPlanWithoutChangingCandidate() throws Exception {
        writeCandidate("candidate-4", "ACCEPTED");
        var before = Files.readString(candidateJson("candidate-4"), StandardCharsets.UTF_8);

        var command = new CalibrationPlanCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-cal";
        command.candidateId = "candidate-4";

        var printed = captureStdout(command::call);

        var planJson = tempDir.resolve(".agent/workstreams/ws-cal/calibration/apply-plans/candidate-4.json");
        var planMd = tempDir.resolve(".agent/workstreams/ws-cal/calibration/apply-plans/candidate-4.md");
        assertTrue(Files.isRegularFile(planJson));
        assertTrue(Files.isRegularFile(planMd));

        var json = Files.readString(planJson, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"candidateId\" : \"candidate-4\""));
        assertTrue(json.contains("\"candidateStatus\" : \"ACCEPTED\""));
        assertTrue(json.contains("\"proposedTargetArea\" : \"Task contract template or contract compiler checklist\""));
        assertTrue(json.contains("\"status\" : \"PROPOSED\""));
        assertTrue(json.contains("\"templateProposal\""));
        assertTrue(json.contains("\"proposalType\" : \"CONTRACT_TEMPLATE\""));
        assertTrue(json.contains("\"status\" : \"REVIEW_REQUIRED\""));

        var markdown = Files.readString(planMd, StandardCharsets.UTF_8);
        assertTrue(markdown.contains("# Calibration Apply Plan"));
        assertTrue(markdown.contains("## Recommended Human Action"));
        assertTrue(markdown.contains("## Template Proposal"));
        assertTrue(markdown.contains("### Checklist Items"));
        assertTrue(markdown.contains("Add one reusable contract principle"));
        assertTrue(markdown.contains("## Not Applied Automatically"));
        assertTrue(markdown.contains("This command does not modify prompts"));

        assertEquals(before, Files.readString(candidateJson("candidate-4"), StandardCharsets.UTF_8));
        assertTrue(printed.contains("Calibration apply plan created"));
        assertTrue(printed.contains("candidate-4"));
    }

    @Test
    void promoteRequiresAcceptedCandidate() throws Exception {
        writeCandidate("candidate-5", "DRAFT");
        writePlan("candidate-5");

        var command = new CalibrationPromoteCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-cal";
        command.candidateId = "candidate-5";

        var printed = captureStderr(command::call, 1);
        assertTrue(printed.contains("must be ACCEPTED"));
    }

    @Test
    void promoteAppendsAcceptedPlanToProjectTemplateAndMarksArtifactsPromoted() throws Exception {
        writeCandidate("candidate-6", "ACCEPTED");
        writePlan("candidate-6");

        var command = new CalibrationPromoteCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-cal";
        command.candidateId = "candidate-6";

        var printed = captureStdout(command::call);

        var template = tempDir.resolve(".agent/templates/contract-review-principles.md");
        assertTrue(Files.isRegularFile(template));
        var templateText = Files.readString(template, StandardCharsets.UTF_8);
        assertTrue(templateText.contains("Calibration Candidate: candidate-6"));
        assertTrue(templateText.contains("<!-- calibration-candidate: candidate-6 -->"));
        assertTrue(templateText.contains("Prefer contract principles that name observable acceptance evidence."));
        assertTrue(templateText.contains(".agent/workstreams/ws-cal/artifacts/failure-classification.json"));

        var candidateJson = Files.readString(candidateJson("candidate-6"), StandardCharsets.UTF_8);
        assertTrue(candidateJson.contains("\"status\" : \"PROMOTED\""));
        assertTrue(candidateJson.contains("\"promotedTarget\" : \".agent/templates/contract-review-principles.md\""));

        var planJson = Files.readString(planJson("candidate-6"), StandardCharsets.UTF_8);
        assertTrue(planJson.contains("\"status\" : \"PROMOTED\""));
        assertTrue(planJson.contains("\"promotedTarget\" : \".agent/templates/contract-review-principles.md\""));
        assertTrue(printed.contains("Calibration candidate promoted"));
    }

    @Test
    void promoteDoesNotAppendSameCandidateTwice() throws Exception {
        writeCandidate("candidate-7", "ACCEPTED");
        writePlan("candidate-7");
        var command = new CalibrationPromoteCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-cal";
        command.candidateId = "candidate-7";

        captureStdout(command::call);
        writeCandidate("candidate-7", "ACCEPTED");
        writePlan("candidate-7");
        captureStdout(command::call);

        var templateText = Files.readString(
                tempDir.resolve(".agent/templates/contract-review-principles.md"),
                StandardCharsets.UTF_8
        );
        assertEquals(1, templateText.split("calibration-candidate: candidate-7", -1).length - 1);
    }

    @Test
    void billingCsvComparesProviderExportWithConfiguredPricingCatalog() throws Exception {
        writeProviderPricing();
        var csv = tempDir.resolve("deepseek-billing.csv");
        Files.writeString(csv, """
                provider,model,input_cache_hit_tokens,input_cache_miss_tokens,output_tokens,cache_write_tokens,amount,currency
                deepseek,deepseek-pro-v4,728704,85606,24774,0,0.423680,CNY
                """, StandardCharsets.UTF_8);

        var command = new CalibrationBillingCsvCommand();
        command.workdir = tempDir;
        command.csv = csv;

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("Billing CSV Calibration"));
        assertTrue(printed.contains("Rows: 1"));
        assertTrue(printed.contains("Exported total: 0.423680"));
        assertTrue(printed.contains("Dual calculated total: 0.423680"));
        assertTrue(printed.contains("Consistency: matches_official_pricing"));
        assertTrue(printed.contains("deepseek\tdeepseek-pro-v4\tCNY\t0.423680\t0.423680\tmatches_official_pricing"));
    }

    @Test
    void billingCsvCanCompareAmountExportAgainstCostSummaryExport() throws Exception {
        writeProviderPricing();
        var amountCsv = tempDir.resolve("amount-2026-6.csv");
        Files.writeString(amountCsv, """
                user_id,utc_date,model,api_key_name,api_key,type,price,amount
                user,2026-06-01,deepseek-v4-pro,key,sk,input_cache_hit_tokens,0.000000025,196992
                user,2026-06-01,deepseek-v4-pro,key,sk,input_cache_miss_tokens,0.000003,228427
                user,2026-06-01,deepseek-v4-pro,key,sk,output_tokens,0.000006,20572
                """, StandardCharsets.UTF_8);
        var costCsv = tempDir.resolve("cost-2026-6.csv");
        Files.writeString(costCsv, """
                user_id,utc_date,model,wallet_type,cost,currency
                user,2026-06-01,deepseek-v4-pro,Paid,0.8136378000000000,CNY
                """, StandardCharsets.UTF_8);

        var command = new CalibrationBillingCsvCommand();
        command.workdir = tempDir;
        command.csv = amountCsv;
        command.costCsv = costCsv;

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("Usage exported total: 0.813638"));
        assertTrue(printed.contains("Cost summary exported total: 0.813638"));
        assertTrue(printed.contains("Usage vs cost summary: matches_cost_summary"));
        assertTrue(printed.contains("Exported delta: 0.000000"));
        assertTrue(printed.contains("Provider amount/cost files are consistent."));
    }

    @Test
    void billingCsvReportsUnknownPricingWithoutInventingCost() throws Exception {
        writeProviderPricing();
        var csv = tempDir.resolve("unknown-billing.csv");
        Files.writeString(csv, """
                provider,model,input_tokens,output_tokens,amount,currency
                unknown,model-x,1000,100,0.123456,USD
                """, StandardCharsets.UTF_8);

        var command = new CalibrationBillingCsvCommand();
        command.workdir = tempDir;
        command.csv = csv;

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("Rows: 1"));
        assertTrue(printed.contains("Exported total: 0.123456"));
        assertTrue(printed.contains("Dual calculated total: 0.000000"));
        assertTrue(printed.contains("Consistency: pricing_unknown"));
        assertTrue(printed.contains("unknown\tmodel-x\tUSD\t0.123456\t0\tpricing_unknown"));
    }

    private String captureStdout(ThrowingCallable callable) throws Exception {
        var originalOut = System.out;
        var out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            assertEquals(0, callable.call());
        } finally {
            System.setOut(originalOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private String captureStderr(ThrowingCallable callable, int expectedExitCode) throws Exception {
        var originalErr = System.err;
        var err = new ByteArrayOutputStream();
        try {
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            assertEquals(expectedExitCode, callable.call());
        } finally {
            System.setErr(originalErr);
        }
        return err.toString(StandardCharsets.UTF_8);
    }

    private void writeCandidate(String candidateId, String status) throws Exception {
        Files.createDirectories(candidateJson(candidateId).getParent());
        Files.writeString(candidateJson(candidateId), """
                {
                  "schemaVersion": 1,
                  "candidateId": "%s",
                  "sourceWorkstreamId": "ws-cal",
                  "sourceNodeId": "acceptance.classify_failure",
                  "failureCategory": "CONTRACT_GAP",
                  "reviewDecision": "REJECT",
                  "matchedRule": 1,
                  "proposedUpdateType": "CONTRACT_TEMPLATE",
                  "reasoning": "Missing contract detail",
                  "suggestedAction": "Revise contract template",
                  "evidenceRefs": [".agent/workstreams/ws-cal/artifacts/failure-classification.json"],
                  "status": "%s"
                }
                """.formatted(candidateId, status), StandardCharsets.UTF_8);
        Files.writeString(candidateJson(candidateId).resolveSibling(candidateId + ".md"),
                "# Candidate " + candidateId + "\n", StandardCharsets.UTF_8);
    }

    private void writePlan(String candidateId) throws Exception {
        Files.createDirectories(planJson(candidateId).getParent());
        Files.writeString(planJson(candidateId), """
                {
                  "schemaVersion": 1,
                  "candidateId": "%s",
                  "sourceWorkstreamId": "ws-cal",
                  "candidateStatus": "ACCEPTED",
                  "proposedUpdateType": "CONTRACT_TEMPLATE",
                  "status": "PROPOSED",
                  "templateProposal": {
                    "status": "REVIEW_REQUIRED",
                    "proposalType": "CONTRACT_TEMPLATE",
                    "targetArea": "Task contract template or contract compiler checklist",
                    "proposedChange": "Prefer contract principles that name observable acceptance evidence.",
                    "checklistItems": [
                      "Keep the principle domain-neutral",
                      "Add a regression fixture"
                    ],
                    "regressionSuggestion": "Add a fixture for this evidence pattern."
                  }
                }
                """.formatted(candidateId), StandardCharsets.UTF_8);
    }

    private void writeProviderPricing() throws Exception {
        Files.createDirectories(tempDir.resolve(".agent"));
        Files.writeString(tempDir.resolve(".agent/provider-pricing.json"), """
                {
                  "models": [
                    {
                      "providerId": "deepseek",
                      "model": "deepseek-pro-v4",
                      "currency": "CNY",
                      "inputCacheHitPer1M": 0.025,
                      "inputCacheMissPer1M": 3.0,
                      "outputPer1M": 6.0,
                      "cacheWritePer1M": 0.0,
                      "sourceUrl": "https://api-docs.deepseek.com/quick_start/pricing/"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
    }

    private Path candidateJson(String candidateId) {
        return tempDir.resolve(".agent/workstreams/ws-cal/calibration/candidates")
                .resolve(candidateId + ".json");
    }

    private Path planJson(String candidateId) {
        return tempDir.resolve(".agent/workstreams/ws-cal/calibration/apply-plans")
                .resolve(candidateId + ".json");
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        int call() throws Exception;
    }
}
