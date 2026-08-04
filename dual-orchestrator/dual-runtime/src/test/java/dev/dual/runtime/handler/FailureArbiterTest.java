package dev.dual.runtime.handler;

import dev.dual.core.arbitration.FailureCategory;
import dev.dual.core.arbitration.FailureClassification;
import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureArbiterTest {

    private FailureArbiter arbiter;

    @BeforeEach
    void setUp() {
        arbiter = new FailureArbiter();
    }

    // --- Rule 1: CONTRACT_GAP ---

    @Test
    void rule1_contractGap() {
        var result = arbiter.classify(
                List.of("missing acceptance criteria"),
                null, null,
                null,
                "some summary", null,
                3, "REJECT"
        );
        assertEquals(FailureCategory.CONTRACT_GAP, result.category());
        assertEquals(1, result.matchedRule());
        assertEquals("REJECT", result.reviewDecision());
        assertNotNull(result.classifiedAt());
    }

    // --- Rule 2: CONTRACT_AMBIGUITY ---

    @Test
    void rule2_contractAmbiguity_ambiguityNotes() {
        var contract = minimalContract(
                List.of("unclear requirement"),
                List.of("file1.java"),
                List.of("must pass tests", "must compile")
        );
        var result = arbiter.classify(
                null, null, null,
                null,
                "summary", contract,
                3, "REJECT"
        );
        assertEquals(FailureCategory.CONTRACT_AMBIGUITY, result.category());
        assertEquals(2, result.matchedRule());
    }

    @Test
    void rule2_contractAmbiguity_emptyScope() {
        var contract = minimalContract(
                List.of(),
                List.of(), // empty included scope
                List.of("must pass tests", "must compile")
        );
        var result = arbiter.classify(
                null, null, null,
                null,
                "summary", contract,
                3, "REJECT"
        );
        assertEquals(FailureCategory.CONTRACT_AMBIGUITY, result.category());
        assertEquals(2, result.matchedRule());
    }

    @Test
    void rule2_contractAmbiguity_weakCriteria() {
        var contract = minimalContract(
                List.of(),
                List.of("file1.java"),
                List.of("only one criterion") // fewer than 2 acceptance criteria
        );
        var result = arbiter.classify(
                null, null, null,
                null,
                "summary", contract,
                3, "REJECT"
        );
        assertEquals(FailureCategory.CONTRACT_AMBIGUITY, result.category());
        assertEquals(2, result.matchedRule());
    }

    // --- Rule 3: IMPLEMENTATION_BUG (out of scope files) ---

    @Test
    void rule3_implementationBug() {
        var contract = minimalContract(
                List.of(),
                List.of("file1.java"),
                List.of("must pass tests", "must compile")
        );
        var result = arbiter.classify(
                null,
                List.of("UnexpectedFile.java"),
                null,
                null,
                "summary", contract,
                3, "REJECT"
        );
        assertEquals(FailureCategory.IMPLEMENTATION_BUG, result.category());
        assertEquals(3, result.matchedRule());
    }

    // --- Rule 4: CONTEXT_INSUFFICIENT ---

    @Test
    void rule4_contextInsufficient() {
        var contract = minimalContract(
                List.of(),
                List.of("file1.java"),
                List.of("must pass tests", "must compile")
        );
        var result = arbiter.classify(
                null, null, null,
                List.of("missing treatment event API usage doc"),
                "The requirement is unclear and hard to follow", contract,
                3, "REJECT"
        );
        assertEquals(FailureCategory.CONTEXT_INSUFFICIENT, result.category());
        assertEquals(5, result.matchedRule());
    }

    @Test
    void summaryKeywordDoesNotClassifyContextInsufficientWithoutStructuredField() {
        var contract = minimalContract(
                List.of(),
                List.of("file1.java"),
                List.of("must pass tests", "must compile")
        );
        var result = arbiter.classify(
                null, null, null,
                null,
                "Insufficient detail in the spec", contract,
                3, "REJECT"
        );
        assertEquals(FailureCategory.IMPLEMENTATION_BUG, result.category());
        assertEquals(8, result.matchedRule());
    }

    // --- Rule 5: VERIFICATION_INSUFFICIENT ---

    @Test
    void rule5_verificationInsufficientFromMissingTests() {
        var contract = minimalContract(
                List.of(),
                List.of("file1.java"),
                List.of("must pass tests", "must compile")
        );
        var result = arbiter.classify(
                null, null,
                List.of("backend pytest evidence missing"),
                null,
                "Verification evidence is incomplete", contract,
                3, "REJECT"
        );
        assertEquals(FailureCategory.VERIFICATION_INSUFFICIENT, result.category());
        assertEquals(4, result.matchedRule());
    }

    @Test
    void testCodeChangeRemediationRoutesToImplementationInsteadOfSelfTest() {
        var contract = minimalContract(
                List.of(),
                List.of("file1.java"),
                List.of("must pass tests", "must compile")
        );

        var result = arbiter.classify(
                null, null,
                List.of("add the equal-date cycle-number tie-breaker regression test"),
                null,
                "A regression test must be added.", contract,
                3, "REJECT", "VERIFICATION_INSUFFICIENT",
                AgentResultEnvelope.VerificationRemediation.TEST_CODE_CHANGE
        );

        assertEquals(FailureCategory.IMPLEMENTATION_BUG, result.category());
        assertEquals(4, result.matchedRule());
        assertTrue(result.suggestedAction().contains("test changes"));
    }

    @Test
    void evidenceCollectionRemediationStaysOnSelfTestRoute() {
        var contract = minimalContract(
                List.of(),
                List.of("file1.java"),
                List.of("must pass tests", "must compile")
        );

        var result = arbiter.classify(
                null, null,
                List.of("attach the existing backend test output"),
                null,
                "The existing test evidence is absent.", contract,
                3, "REJECT", "VERIFICATION_INSUFFICIENT",
                AgentResultEnvelope.VerificationRemediation.EVIDENCE_COLLECTION
        );

        assertEquals(FailureCategory.VERIFICATION_INSUFFICIENT, result.category());
        assertEquals(4, result.matchedRule());
    }

    @Test
    void rule5_verificationInsufficientFromCategoryHintCatalogAlias() {
        var contract = minimalContract(
                List.of(),
                List.of("file1.java"),
                List.of("must pass tests", "must compile")
        );
        var result = arbiter.classify(
                null, null,
                null,
                null,
                "Verification evidence is incomplete", contract,
                3, "REJECT", "insufficient_verification"
        );
        assertEquals(FailureCategory.VERIFICATION_INSUFFICIENT, result.category());
        assertEquals(4, result.matchedRule());
    }

    @Test
    void verificationEvidenceOutranksGenericContextGap() {
        var contract = minimalContract(
                List.of(),
                List.of("file1.java"),
                List.of("must pass tests", "must compile")
        );
        var result = arbiter.classify(
                null, null,
                List.of("node --test"),
                List.of("self-test report is absent from review context"),
                "Verification evidence is incomplete", contract,
                3, "REJECT", "MISSING_TEST_EVIDENCE"
        );

        assertEquals(FailureCategory.VERIFICATION_INSUFFICIENT, result.category());
        assertEquals(4, result.matchedRule());
    }

    @Test
    void missingTestEvidenceIsAnExactVerificationAlias() {
        var contract = minimalContract(
                List.of(),
                List.of("file1.java"),
                List.of("must pass tests", "must compile")
        );
        var result = arbiter.classify(
                null, null, null, null,
                "No summary inference", contract,
                3, "REJECT", "MISSING_TEST_EVIDENCE"
        );

        assertEquals(FailureCategory.VERIFICATION_INSUFFICIENT, result.category());
        assertEquals(4, result.matchedRule());
    }

    @Test
    void categoryHintSubstringDoesNotClassifyVerificationInsufficient() {
        var contract = minimalContract(
                List.of(),
                List.of("file1.java"),
                List.of("must pass tests", "must compile")
        );
        var result = arbiter.classify(
                null, null,
                null,
                null,
                "Verification evidence is incomplete", contract,
                3, "REJECT", "not_actually_insufficient_verification_noise"
        );
        assertEquals(FailureCategory.IMPLEMENTATION_BUG, result.category());
        assertEquals(8, result.matchedRule());
    }

    // --- Rule 6: TOOL_LIMITATION ---

    @Test
    void rule5_toolLimitation() {
        var contract = minimalContract(
                List.of(),
                List.of("file1.java"),
                List.of("must pass tests", "must compile")
        );
        var result = arbiter.classify(
                null, null, null,
                null,
                "summary", contract,
                0, "REJECT"  // changedFileCount == 0
        );
        assertEquals(FailureCategory.TOOL_LIMITATION, result.category());
        assertEquals(6, result.matchedRule());
    }

    // --- Rule 7: REVIEW_NOISE ---

    @Test
    void rule6_reviewNoiseFromTypedCategoryHint() {
        var result = arbiter.classify(
                null, null, null,
                null,
                "minor stylistic issue", null,
                5, "REJECT", "REVIEW_NOISE"
        );
        assertEquals(FailureCategory.REVIEW_NOISE, result.category());
        assertEquals(7, result.matchedRule());
    }

    @Test
    void summaryTextDoesNotClassifyReviewNoiseWithoutTypedHint() {
        var result = arbiter.classify(
                null, null, null,
                null,
                "minor stylistic issue", null,
                5, "REJECT"
        );
        assertEquals(FailureCategory.IMPLEMENTATION_BUG, result.category());
        assertEquals(8, result.matchedRule());
    }

    @Test
    void rejectWithoutStructuredFieldsDefaultsToImplementationBug() {
        var result = arbiter.classify(
                null, null, null,
                null,
                "Acceptance remains blocked because README.md still says 16 pages while the project has 17 pages.",
                null,
                1,
                "REJECT"
        );
        assertEquals(FailureCategory.IMPLEMENTATION_BUG, result.category());
        assertEquals(8, result.matchedRule());
    }

    // --- Rule 8: fallback IMPLEMENTATION_BUG ---

    @Test
    void rule7_fallback_implementationBug() {
        var contract = minimalContract(
                List.of(),  // no ambiguity notes
                List.of("file1.java"),
                List.of("must pass tests", "must compile")
        );
        var result = arbiter.classify(
                null,  // no contract gaps
                null,  // no out of scope files
                null,
                null,
                "doesn't pass", contract,
                3, "REJECT"
        );
        assertEquals(FailureCategory.IMPLEMENTATION_BUG, result.category());
        assertEquals(8, result.matchedRule());
    }

    // --- Cross-cutting: ACCEPT_WITH_NOTES ---

    @Test
    void acceptWithNotesClassification() {
        var result = arbiter.classify(
                List.of("gap in contract"),
                null, null,
                null,
                "summary", null,
                3, "ACCEPT_WITH_NOTES"
        );
        assertEquals(FailureCategory.CONTRACT_GAP, result.category());
        assertEquals("ACCEPT_WITH_NOTES", result.reviewDecision());
    }

    // --- Priority: contract gaps win over out-of-scope ---

    @Test
    void priority_order_contractGapOverImplementationBug() {
        var result = arbiter.classify(
                List.of("missing constraint"),
                List.of("OutOfScope.java"),
                null,
                null,
                "summary", null,
                3, "REJECT"
        );
        assertEquals(FailureCategory.CONTRACT_GAP, result.category());
        assertEquals(1, result.matchedRule());
    }

    // --- Helper ---

    private TaskContract minimalContract(List<String> ambiguityNotes, List<String> included,
                                         List<String> acceptanceCriteria) {
        return new TaskContract(
                "tc-test", 1, Instant.parse("2026-05-30T10:00:00Z"),
                "Test objective",
                new TaskContract.Scope(
                        included.stream()
                                .map(path -> new TaskContract.ScopeEntry(
                                        path, TaskContract.ScopeOperation.MODIFY))
                                .toList(),
                        List.of()),
                acceptanceCriteria, List.of(), List.of(), List.of(), List.of(),
                ambiguityNotes, List.of(),
                ContractStatus.READY_FOR_REVIEW, 1,
                new TaskContract.Source("req.md", "test")
        );
    }
}
