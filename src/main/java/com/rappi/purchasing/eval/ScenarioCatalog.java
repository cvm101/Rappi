package com.rappi.purchasing.eval;

import com.rappi.purchasing.dto.Dtos.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static com.rappi.purchasing.dto.Dtos.DecisionType.*;
import static com.rappi.purchasing.dto.Dtos.ScenarioType.*;

/**
 * Preset situations (used by the UI dropdown and by the evaluation). Each one carries the
 * hand-computed ground truth, see seed data in data.sql and the README for the math.
 */
@Component
public class ScenarioCatalog {
    private static final Set<String> CORE = Set.of("calculate_requirement");

    public List<ScenarioPreset> all() {
        return List.of(
            new ScenarioPreset("s1-accept", "S1 - Recommendation is right (accept)",
                "Milk, 70/day demand, 40 on hand, no open PO. System recommends 800. Policy need is exactly 800.",
                review(1, "BLR-01", 1, 800),
                new Expectation(Set.of(ACCEPT, MODIFY), "AUTO_EXECUTED", 780, 860, CORE, null)),

            new ScenarioPreset("s1-overstock", "S1 - Recommendation is wrong (reject)",
                "Rice, 20/day demand but 600 already on hand. System recommends 800: pure overstock.",
                review(2, "BLR-01", 1, 800),
                new Expectation(Set.of(REJECT), "NO_ACTION", 0, 0, CORE, null)),

            new ScenarioPreset("s1-open-po", "S1 - Open PO already covers most of it (modify)",
                "Cooking oil, 200 on hand + 300 on an open PO. System recommends 800, real need is only ~100.",
                review(3, "BLR-01", 1, 800),
                new Expectation(Set.of(MODIFY), "AUTO_EXECUTED", 100, 150, Set.of("calculate_requirement", "get_open_purchase_orders"), null)),

            new ScenarioPreset("s4-budget", "S4 - Budget constraint",
                "Coffee at node BLR-02: need 800 units (240k) but only 90k budget left. Agent must buy what it can afford and escalate the rest.",
                review(4, "BLR-02", 1, 800),
                new Expectation(Set.of(MODIFY), "PENDING_APPROVAL", 250, 300, Set.of("calculate_requirement", "get_budget"), null)),

            new ScenarioPreset("s4-storage", "S4 - Storage constraint",
                "Bulky detergent at node BLR-03: need 800 units but free storage fits only 350. Agent must cap the quantity and escalate.",
                review(5, "BLR-03", 1, 800),
                new Expectation(Set.of(MODIFY), "PENDING_APPROVAL", 300, 350, Set.of("calculate_requirement", "get_storage_capacity"), null)),

            new ScenarioPreset("s2-shortfall", "S2 - Supplier ships only 250 of 500",
                "Eggs PO for 500 from Prime Foods; supplier confirms 250 and has no more stock. Agent must find another supplier for the gap.",
                new AgentRequest(SUPPLIER_SHORTFALL, null, null, null, null, 2L, 250),
                new Expectation(Set.of(MODIFY), "PENDING_APPROVAL", 250, 350, Set.of("calculate_requirement", "get_alternate_suppliers"), 1L)),

            new ScenarioPreset("s4-no-supply", "S4 - Nobody can supply (escalate)",
                "Ice cream is needed, but the only supplier has 50 units in stock and an MOQ of 100. No alternates. Agent must escalate, not force an order.",
                review(7, "BLR-01", 1, 350),
                new Expectation(Set.of(INVESTIGATE), "ESCALATED", 0, 0, Set.of("get_supplier_terms"), null))
        );
    }

    private AgentRequest review(long product, String node, long supplier, int qty) {
        return new AgentRequest(RECOMMENDATION_REVIEW, product, node, supplier, qty, null, null);
    }
}
