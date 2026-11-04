package com.rappi.purchasing.controller;

import com.rappi.purchasing.agent.PurchasingAgentService;
import com.rappi.purchasing.dto.Dtos.AgentRequest;
import com.rappi.purchasing.dto.Dtos.AgentRunResult;
import com.rappi.purchasing.dto.Dtos.EvalReport;
import com.rappi.purchasing.dto.Dtos.ScenarioPreset;
import com.rappi.purchasing.entity.AgentDecision;
import com.rappi.purchasing.eval.EvaluationService;
import com.rappi.purchasing.eval.ScenarioCatalog;
import com.rappi.purchasing.agent.LlmClient;
import com.rappi.purchasing.repository.AgentDecisionRepository;
import com.rappi.purchasing.service.DemoDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentController {
    private final PurchasingAgentService agent;
    private final ScenarioCatalog catalog;
    private final EvaluationService evaluation;
    private final DemoDataService demoData;
    private final AgentDecisionRepository decisions;
    private final LlmClient llm;

    @GetMapping("/scenarios")
    public List<ScenarioPreset> scenarios() {
        return catalog.all();
    }

    @PostMapping("/agent/run")
    public AgentRunResult run(@RequestBody AgentRequest request) {
        return agent.run(request);
    }

    /** Audit log of past decisions. */
    @GetMapping("/agent/decisions")
    public List<AgentDecision> decisions() {
        return decisions.findTop20ByOrderByIdDesc();
    }

    @PostMapping("/admin/reset")
    public void reset() {
        demoData.reset();
    }

    @PostMapping("/eval/run")
    public EvalReport eval() {
        return evaluation.run(llm);
    }
}
