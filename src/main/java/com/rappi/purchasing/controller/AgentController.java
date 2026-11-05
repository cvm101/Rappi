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
import com.rappi.purchasing.repository.BudgetRepository;
import com.rappi.purchasing.repository.ProductRepository;
import com.rappi.purchasing.repository.SupplierRepository;
import com.rappi.purchasing.service.DemoDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final BudgetRepository budgets;

    /** Names and terms the UI needs to explain the situation in plain language. */
    @GetMapping("/reference")
    public Map<String, Object> reference() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("products", products.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(p -> String.valueOf(p.getId()), p -> Map.of(
                        "name", p.getName() == null ? ("Product " + p.getId()) : p.getName(),
                        "unitVolume", p.getUnitVolume()))));
        out.put("suppliers", suppliers.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(s -> String.valueOf(s.getId()), s -> Map.of(
                        "name", s.getName() == null ? ("Supplier " + s.getId()) : s.getName(),
                        "leadTimeDays", s.getLeadTimeDays(),
                        "moq", s.getMoq()))));
        out.put("nodes", budgets.findAll().stream().map(b -> b.getNodeId()).sorted().toList());
        return out;
    }

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
