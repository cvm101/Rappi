package com.rappi.purchasing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappi.purchasing.agent.MockLlmClient;
import com.rappi.purchasing.dto.Dtos.CaseResult;
import com.rappi.purchasing.dto.Dtos.EvalReport;
import com.rappi.purchasing.eval.EvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the evaluation harness with the deterministic mock LLM (no API key needed).
 *  - smart mock : investigates, respects constraints -> every case must pass every criterion, 1 attempt.
 *  - naive mock : first answer blindly follows the recommendation -> the validator must catch it,
 *                 the feedback loop must produce a corrected plan, and the final outcome must be identical.
 */
@SpringBootTest
class EvaluationTest {
    @Autowired EvaluationService evaluation;
    @Autowired ObjectMapper mapper;

    @Test
    void smartAgentPassesAllScenarios() {
        EvalReport report = evaluation.run(new MockLlmClient(mapper, false));
        print("SMART", report);
        assertEquals(report.totalCases(), report.passedCases(), "all scenarios should pass");
        report.cases().forEach(c -> assertEquals(1, c.attempts(), c.id() + " should need one attempt"));
    }

    @Test
    void naiveFirstAttemptIsCaughtByValidatorAndRecovered() {
        EvalReport report = evaluation.run(new MockLlmClient(mapper, true));
        print("NAIVE-FIRST", report);
        assertEquals(report.totalCases(), report.passedCases(), "final outcomes must still be correct");
        // s1-accept is the only case where blindly following the recommendation is right
        for (CaseResult c : report.cases()) {
            if (c.id().equals("s1-accept")) assertEquals(1, c.attempts());
            else assertTrue(c.attempts() > 1, c.id() + " should have needed a correction, attempts=" + c.attempts());
        }
    }

    private void print(String label, EvalReport r) {
        System.out.println("===== EVAL " + label + ": " + r.passedCases() + "/" + r.totalCases() + " =====");
        r.cases().forEach(c -> System.out.printf("%-14s %-4s decision=%-11s status=%-17s qty=%-4d attempts=%d%n",
                c.id(), c.passed() ? "PASS" : "FAIL", c.decision(), c.finalStatus(), c.totalQty(), c.attempts()));
        r.cases().stream().filter(c -> !c.passed()).forEach(c -> c.criteria().stream().filter(k -> !k.passed())
                .forEach(k -> System.out.println("   " + c.id() + " " + k.name() + ": " + k.detail())));
    }
}
