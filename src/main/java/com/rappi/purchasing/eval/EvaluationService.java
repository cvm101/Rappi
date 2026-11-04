package com.rappi.purchasing.eval;

import com.rappi.purchasing.agent.LlmClient;
import com.rappi.purchasing.agent.PurchasingAgentService;
import com.rappi.purchasing.dto.Dtos.*;
import com.rappi.purchasing.service.DemoDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs every preset on fresh data and scores the agent against the hand-computed expectation.
 * Criteria map 1:1 to the assignment's questions:
 *   decision_correct, information_gathered, constraints_respected, action_correct, result_validated.
 * "attempts" is reported separately: attempts > 1 means the feedback loop had to correct the agent.
 */
@Service
@RequiredArgsConstructor
public class EvaluationService {
    private final ScenarioCatalog catalog;
    private final PurchasingAgentService agent;
    private final DemoDataService demoData;

    public EvalReport run(LlmClient llm) {
        List<CaseResult> cases = new ArrayList<>();
        for (ScenarioPreset preset : catalog.all()) {
            demoData.reset();
            cases.add(score(preset, agent.run(preset.request(), llm)));
        }
        demoData.reset();
        return new EvalReport((int) cases.stream().filter(CaseResult::passed).count(), cases.size(), cases);
    }

    private CaseResult score(ScenarioPreset preset, AgentRunResult r) {
        Expectation e = preset.expected();
        int qty = r.orders().stream().mapToInt(OrderLine::quantity).sum();
        Set<String> called = r.trace().stream().filter(t -> t.type().equals("TOOL")).map(TraceStep::name).collect(Collectors.toSet());
        boolean forbidden = e.forbiddenSupplierId() != null
                && r.orders().stream().anyMatch(o -> o.supplierId().equals(e.forbiddenSupplierId()));

        List<Criterion> c = new ArrayList<>();
        c.add(new Criterion("decision_correct", e.decisions().contains(r.decision()),
                "got " + r.decision() + ", expected one of " + e.decisions()));
        c.add(new Criterion("information_gathered", called.containsAll(e.requiredTools()),
                "called " + called + ", required " + e.requiredTools()));
        c.add(new Criterion("constraints_respected", qty >= e.minQty() && qty <= e.maxQty() && !forbidden,
                "qty " + qty + " expected in [" + e.minQty() + "," + e.maxQty() + "]" + (forbidden ? ", used forbidden supplier" : "")));
        c.add(new Criterion("action_correct", e.finalStatus().equals(r.finalStatus()),
                "got " + r.finalStatus() + ", expected " + e.finalStatus()));
        boolean validated = r.decision() == DecisionType.INVESTIGATE || (r.validation() != null && r.validation().passed());
        c.add(new Criterion("result_validated", validated, "validator " + (r.validation() == null ? "not run" : r.validation().passed() ? "passed" : "failed")));

        return new CaseResult(preset.id(), preset.title(), c.stream().allMatch(Criterion::passed), r.attempts(),
                r.decision(), r.finalStatus(), qty, c);
    }
}
