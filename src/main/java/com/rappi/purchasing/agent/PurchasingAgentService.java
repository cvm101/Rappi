package com.rappi.purchasing.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rappi.purchasing.dto.Dtos.*;
import com.rappi.purchasing.entity.AgentDecision;
import com.rappi.purchasing.entity.PoStatus;
import com.rappi.purchasing.entity.PurchaseOrder;
import com.rappi.purchasing.repository.AgentDecisionRepository;
import com.rappi.purchasing.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The agent loop:
 *   investigate (LLM + read-only tools) -> decide -> act (create DRAFT POs) -> VALIDATE the result
 *   -> if invalid: cancel drafts, feed violations back to the LLM, retry (max 3)
 *   -> if valid: auto-execute, or hold for human approval; if never valid: escalate.
 */
@Service
@RequiredArgsConstructor
public class PurchasingAgentService {
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_TOOL_STEPS = 15;

    private final LlmClient defaultLlm;
    private final PurchasingTools tools;
    private final PoValidationService validator;
    private final PurchaseOrderService poService;
    private final SupplierService supplierService;
    private final RequirementService requirements;
    private final BudgetService budgetService;
    private final PurchasingPolicy policy;
    private final AgentDecisionRepository decisionRepo;
    private final ObjectMapper mapper;

    public AgentRunResult run(AgentRequest request) {
        return run(request, defaultLlm);
    }

    public AgentRunResult run(AgentRequest request, LlmClient llm) {
        List<TraceStep> trace = new ArrayList<>();
        AgentRequest req = request;
        ObjectNode requestJson = mapper.valueToTree(req);
        String description;

        if (request.scenarioType() == ScenarioType.SUPPLIER_SHORTFALL) {
            // The supplier's answer is a fact: shrink the PO first, then let the agent deal with the gap.
            PurchaseOrder po = poService.get(request.poId());
            int original = poService.applyShortfall(po.getId(), request.confirmedQty());
            req = new AgentRequest(request.scenarioType(), po.getProductId(), po.getNodeId(), po.getSupplierId(),
                    null, po.getId(), request.confirmedQty());
            requestJson = mapper.valueToTree(req);
            requestJson.put("originalQty", original).put("shortfallQty", original - request.confirmedQty());
            description = "PO " + po.getId() + " was created for " + original + " units of product " + po.getProductId()
                    + " from supplier " + po.getSupplierId() + " for node " + po.getNodeId() + ", but the supplier can only supply "
                    + request.confirmedQty() + ". The PO was reduced to " + request.confirmedQty() + ". Decide what should happen next.";
            trace.add(new TraceStep(0, "EVENT", "supplier_shortfall", description, "PO reduced to " + request.confirmedQty()));
        } else {
            description = "The purchasing system recommends buying " + req.recommendedQty() + " units of product " + req.productId()
                    + " from supplier " + req.supplierId() + " for node " + req.nodeId() + ". Review this recommendation.";
        }

        List<ObjectNode> messages = new ArrayList<>();
        messages.add(message("system", AgentPrompts.SYSTEM));
        messages.add(message("user", description + "\nREQUEST_JSON: " + requestJson));

        Proposal proposal = null;
        ValidationReport report = null;
        List<PurchaseOrder> drafts = new ArrayList<>();
        String error = null;
        int attempt = 0;

        while (attempt < MAX_ATTEMPTS) {
            attempt++;
            try {
                proposal = think(llm, messages, trace, attempt);
            } catch (RuntimeException e) {
                error = "LLM call failed: " + e.getMessage();
                trace.add(new TraceStep(attempt, "ERROR", "llm", "", error));
                proposal = null;
                break;
            }
            if (proposal == null) {
                messages.add(message("user", "VALIDATION FAILED: your final answer was not valid JSON in the required format. Answer again."));
                report = null;
                continue;
            }

            drafts = new ArrayList<>();
            report = actAndValidate(req, proposal, drafts, trace, attempt);
            trace.add(new TraceStep(attempt, "VALIDATION", "validator", "", json(report)));
            if (report.passed()) break;

            poService.setStatus(drafts, PoStatus.CANCELLED);
            drafts = new ArrayList<>();
            messages.add(message("user", "VALIDATION FAILED for your proposal. Violations:\n" + report.failureSummary()
                    + "\nRevise your plan so that every check passes, or answer INVESTIGATE if impossible."));
        }

        boolean passed = report != null && report.passed();
        return finish(req, proposal, report, passed, drafts, attempt, trace, error);
    }

    private Proposal think(LlmClient llm, List<ObjectNode> messages, List<TraceStep> trace, int attempt) {
        for (int step = 0; step < MAX_TOOL_STEPS; step++) {
            ObjectNode msg = llm.chat(messages, tools.specs());
            messages.add(msg);
            JsonNode calls = msg.path("tool_calls");
            if (calls.isArray() && !calls.isEmpty()) {
                for (JsonNode c : calls) {
                    String name = c.path("function").path("name").asText();
                    String argText = c.path("function").path("arguments").asText("{}");
                    String out;
                    try {
                        out = tools.execute(name, mapper.readTree(argText.isBlank() ? "{}" : argText));
                    } catch (Exception e) {
                        out = "{\"error\":\"bad arguments\"}";
                    }
                    trace.add(new TraceStep(attempt, "TOOL", name, argText, out));
                    messages.add(mapper.createObjectNode().put("role", "tool").put("tool_call_id", c.path("id").asText()).put("content", out));
                }
                continue;
            }
            Proposal p = parseProposal(msg.path("content").asText(""));
            trace.add(new TraceStep(attempt, "DECISION", p == null ? "unparseable" : p.decision().name(), "",
                    p == null ? msg.path("content").asText("") : p.reasoning()));
            return p;
        }
        return null;
    }

    private Proposal parseProposal(String content) {
        try {
            int s = content.indexOf('{'), e = content.lastIndexOf('}');
            JsonNode n = mapper.readTree(content.substring(s, e + 1));
            List<OrderLine> orders = new ArrayList<>();
            for (JsonNode o : n.path("orders")) orders.add(new OrderLine(o.path("supplierId").asLong(), o.path("quantity").asInt()));
            return new Proposal(DecisionType.valueOf(n.path("decision").asText().toUpperCase()), orders,
                    n.path("escalate").asBoolean(false), n.path("reasoning").asText(""));
        } catch (Exception ex) {
            return null;
        }
    }

    private ValidationReport actAndValidate(AgentRequest req, Proposal p, List<PurchaseOrder> drafts, List<TraceStep> trace, int attempt) {
        if (p.decision() == DecisionType.INVESTIGATE) {
            return new ValidationReport(true, List.of());
        }
        try {
            if (p.decision() != DecisionType.REJECT) {
                for (OrderLine l : p.orders()) {
                    if (l.quantity() <= 0) continue;
                    SupplierTerms t = supplierService.terms(req.productId(), l.supplierId());
                    drafts.add(poService.createDraft(req.productId(), l.supplierId(), req.nodeId(), l.quantity(), t.unitPrice(),
                            "Created by agent: " + p.decision()));
                }
                trace.add(new TraceStep(attempt, "ACTION", "create_draft_po", json(p.orders()), "draft PO ids " + ids(drafts)));
                if (drafts.isEmpty()) {
                    return new ValidationReport(false, List.of(new Check("ORDER_PRESENT", false, "decision " + p.decision() + " but no order with quantity > 0")));
                }
            }
            return validator.validate(req.productId(), req.nodeId(), drafts, req.supplierId());
        } catch (IllegalArgumentException e) {
            return new ValidationReport(false, List.of(new Check("SUPPLIER_LISTING", false, e.getMessage())));
        }
    }

    private AgentRunResult finish(AgentRequest req, Proposal p, ValidationReport report, boolean passed,
                                  List<PurchaseOrder> drafts, int attempts, List<TraceStep> trace, String error) {
        DecisionType decision = p == null ? DecisionType.INVESTIGATE : p.decision();
        String reasoning = p == null ? (error != null ? error : "Agent produced no usable answer") : p.reasoning();
        List<String> reasons = new ArrayList<>();
        String status;

        if (!passed) {
            decision = DecisionType.INVESTIGATE;
            status = "ESCALATED";
            reasons.add("No valid plan after " + attempts + " attempt(s); a buyer must handle this manually");
        } else if (decision == DecisionType.INVESTIGATE) {
            status = "ESCALATED";
            reasons.add("Agent could not decide safely; human investigation required");
        } else if (decision == DecisionType.REJECT) {
            status = "NO_ACTION";
        } else {
            if (req.scenarioType() == ScenarioType.RECOMMENDATION_REVIEW) {
                boolean same = drafts.size() == 1 && drafts.get(0).getSupplierId().equals(req.supplierId())
                        && drafts.get(0).getQuantity() == req.recommendedQty();
                decision = same ? DecisionType.ACCEPT : DecisionType.MODIFY;
            }
            int bought = drafts.stream().mapToInt(PurchaseOrder::getQuantity).sum();
            int needed = requirements.calculate(req.productId(), req.nodeId(), drafts.get(0).getSupplierId()).netNeed();
            double value = drafts.stream().mapToDouble(PurchaseOrder::getValue).sum();
            if (p.escalate()) reasons.add("Agent flagged the plan for human review");
            if (bought < needed) reasons.add("Partial coverage: " + bought + " of " + needed + " needed units can be bought");
            if (value > policy.getApprovalValue()) reasons.add("PO value " + value + " exceeds auto-approval limit " + policy.getApprovalValue());
            if (reasons.isEmpty()) {
                poService.setStatus(drafts, PoStatus.APPROVED);
                budgetService.spend(req.nodeId(), value);
                status = "AUTO_EXECUTED";
                trace.add(new TraceStep(attempts, "ACTION", "approve_po", ids(drafts), "auto-approved within policy, budget reduced by " + value));
            } else {
                poService.setStatus(drafts, PoStatus.PENDING_APPROVAL);
                status = "PENDING_APPROVAL";
                trace.add(new TraceStep(attempts, "ACTION", "hold_for_approval", ids(drafts), String.join("; ", reasons)));
            }
        }

        List<OrderLine> orders = drafts.stream().map(d -> new OrderLine(d.getSupplierId(), d.getQuantity())).toList();
        List<Long> poIds = drafts.stream().map(PurchaseOrder::getId).toList();

        AgentDecision row = new AgentDecision();
        row.setScenario(req.scenarioType().name());
        row.setProductId(req.productId());
        row.setNodeId(req.nodeId());
        row.setDecision(decision.name());
        row.setReasoning(reasoning);
        row.setFinalStatus(status);
        row.setAttempts(attempts);
        row.setPoIds(ids(drafts));
        row.setTraceJson(json(trace));
        row.setValidationJson(json(report));
        decisionRepo.save(row);

        return new AgentRunResult(row.getId(), req.scenarioType(), decision, orders, reasoning, status, attempts, reasons, poIds, report, trace);
    }

    private ObjectNode message(String role, String content) {
        return mapper.createObjectNode().put("role", role).put("content", content);
    }

    private String ids(List<PurchaseOrder> pos) {
        return pos.stream().map(po -> String.valueOf(po.getId())).collect(Collectors.joining(","));
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
