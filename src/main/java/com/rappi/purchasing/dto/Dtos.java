package com.rappi.purchasing.dto;

import java.util.List;
import java.util.Set;

/** All small data carriers in one place to keep the project easy to navigate. */
public final class Dtos {
    private Dtos() {}

    public enum ScenarioType { RECOMMENDATION_REVIEW, SUPPLIER_SHORTFALL }

    /** ACCEPT/MODIFY = buy, REJECT = do not buy, INVESTIGATE = hand over to a human. */
    public enum DecisionType { ACCEPT, MODIFY, REJECT, INVESTIGATE }

    /** Trigger sent to the agent. RECOMMENDATION_REVIEW uses recommendedQty; SUPPLIER_SHORTFALL uses poId + confirmedQty. */
    public record AgentRequest(ScenarioType scenarioType, Long productId, String nodeId, Long supplierId,
                               Integer recommendedQty, Long poId, Integer confirmedQty) {}

    public record OrderLine(Long supplierId, int quantity) {}

    /** What the LLM decided (parsed from its final JSON answer). */
    public record Proposal(DecisionType decision, List<OrderLine> orders, boolean escalate, String reasoning) {
        public int totalQty() { return orders.stream().mapToInt(OrderLine::quantity).sum(); }
    }

    public record Check(String name, boolean passed, String detail) {}

    public record ValidationReport(boolean passed, List<Check> checks) {
        public String failureSummary() {
            return checks.stream().filter(c -> !c.passed())
                    .map(c -> c.name() + ": " + c.detail()).reduce((a, b) -> a + "\n" + b).orElse("");
        }
    }

    /** One entry of the audit trail (tool call, validation, action ...). */
    public record TraceStep(int attempt, String type, String name, String input, String output) {}

    public record SupplierTerms(Long supplierId, String supplierName, int leadTimeDays, int moq,
                                double reliability, double unitPrice, int availableQty) {}

    public record Requirement(Long productId, String nodeId, Long supplierId, int leadTimeDays, double dailyDemand,
                              int onHand, int incoming, double targetStock, int netNeed) {}

    public record AgentRunResult(Long decisionId, ScenarioType scenario, DecisionType decision, List<OrderLine> orders,
                                 String reasoning, String finalStatus, int attempts, List<String> approvalReasons,
                                 List<Long> poIds, ValidationReport validation, List<TraceStep> trace) {}

    // ---------- evaluation ----------
    /** Hand-computed ground truth for a preset scenario. */
    public record Expectation(Set<DecisionType> decisions, String finalStatus, int minQty, int maxQty,
                              Set<String> requiredTools, Long forbiddenSupplierId) {}

    public record ScenarioPreset(String id, String title, String description, AgentRequest request, Expectation expected) {}

    public record Criterion(String name, boolean passed, String detail) {}

    public record CaseResult(String id, String title, boolean passed, int attempts, DecisionType decision,
                             String finalStatus, int totalQty, List<Criterion> criteria) {}

    public record EvalReport(int passedCases, int totalCases, List<CaseResult> cases) {}
}
