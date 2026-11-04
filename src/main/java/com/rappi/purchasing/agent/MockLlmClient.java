package com.rappi.purchasing.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scripted stand-in for the LLM so the whole system (tools, validator, feedback loop, eval) runs
 * deterministically without an API key. It is stateless: every call re-reads the conversation,
 * calls the next missing tool, and once it has the facts it answers with a decision.
 * naiveFirst=true makes the first attempt blindly follow the recommendation, to demonstrate recovery
 * after "VALIDATION FAILED" feedback.
 */
public class MockLlmClient implements LlmClient {
    private record Call(String name, ObjectNode args) {}

    private final ObjectMapper mapper;
    private final boolean naiveFirst;

    public MockLlmClient(ObjectMapper mapper, boolean naiveFirst) {
        this.mapper = mapper;
        this.naiveFirst = naiveFirst;
    }

    @Override
    public ObjectNode chat(List<ObjectNode> messages, List<ToolSpec> tools) {
        try {
            JsonNode req = parseRequest(messages);
            Map<String, JsonNode> res = toolResults(messages);
            boolean feedback = messages.stream().anyMatch(m -> "user".equals(m.path("role").asText())
                    && m.path("content").asText().contains("VALIDATION FAILED"));
            boolean shortfall = "SUPPLIER_SHORTFALL".equals(req.path("scenarioType").asText());
            long p = req.path("supplierId").asLong();

            for (Call c : plan(req, res, shortfall, p)) {
                if (!res.containsKey(key(c.name(), c.args()))) return toolCall(c, messages);
            }
            return shortfall ? decideShortfall(req, res, p, naiveFirst && !feedback)
                             : decideReview(req, res, p, naiveFirst && !feedback);
        } catch (Exception e) {
            throw new IllegalStateException("Mock LLM failed", e);
        }
    }

    // ---------- investigation plan ----------
    private List<Call> plan(JsonNode req, Map<String, JsonNode> res, boolean shortfall, long p) {
        long product = req.path("productId").asLong();
        String node = req.path("nodeId").asText();
        List<Call> plan = new ArrayList<>();
        plan.add(call("calculate_requirement", "productId", product, "nodeId", node, "supplierId", p));
        plan.add(call("get_open_purchase_orders", "productId", product, "nodeId", node));
        plan.add(call("get_budget", "nodeId", node));
        plan.add(call("get_storage_capacity", "nodeId", node, "productId", product));
        if (shortfall) {
            plan.add(call("get_alternate_suppliers", "productId", product, "excludeSupplierId", p));
            JsonNode alts = res.get("get_alternate_suppliers");
            if (alts != null) {
                for (JsonNode alt : alts) {
                    plan.add(call("calculate_requirement", "productId", product, "nodeId", node,
                            "supplierId", alt.path("supplierId").asLong()));
                }
            }
        } else {
            plan.add(call("get_supplier_terms", "productId", product, "supplierId", p));
        }
        return plan;
    }

    // ---------- Scenario 1 / 4: review a recommendation ----------
    private ObjectNode decideReview(JsonNode req, Map<String, JsonNode> res, long p, boolean naive) {
        int rec = req.path("recommendedQty").asInt();
        if (naive) return decision("ACCEPT", List.of(line(p, rec)), false, "Accepting the system recommendation as-is.");

        JsonNode r = res.get("calculate_requirement#" + p);
        JsonNode t = res.get("get_supplier_terms#" + p);
        int need = r.path("netNeed").asInt();
        String facts = "Net need " + need + " (target " + r.path("targetStock").asDouble() + ", on hand " + r.path("onHand").asInt()
                + ", incoming " + r.path("incoming").asInt() + ", lead time " + r.path("leadTimeDays").asInt() + "d).";
        if (need <= 0) {
            return decision("REJECT", List.of(), false, facts + " Stock plus open POs already cover the target; recommendation of " + rec + " would overstock.");
        }
        int moq = t.path("moq").asInt();
        int affordable = (int) Math.floor(res.get("get_budget").path("remainingAmount").asDouble() / t.path("unitPrice").asDouble());
        int fits = res.get("get_storage_capacity").path("maxUnitsThatFit").asInt();
        int available = t.path("availableQty").asInt();
        int cap = Math.min(Math.max(need, moq), Math.min(available, Math.min(affordable, fits)));
        String limits = " Limits: supplier stock " + available + ", budget affords " + affordable + ", storage fits " + fits + ", MOQ " + moq + ".";
        if (cap < moq) {
            return decision("INVESTIGATE", List.of(), true, facts + limits + " Cannot satisfy MOQ within the constraints; a human must decide.");
        }
        boolean partial = cap < need;
        String name = cap == rec ? "ACCEPT" : "MODIFY";
        return decision(name, List.of(line(p, cap)), partial,
                facts + limits + " Recommended " + rec + ", buying " + cap + "." + (partial ? " Constraint-limited, shortfall needs approval." : ""));
    }

    // ---------- Scenario 2: supplier cannot fulfil ----------
    private ObjectNode decideShortfall(JsonNode req, Map<String, JsonNode> res, long p, boolean naive) {
        int original = req.path("originalQty").asInt();
        int confirmed = req.path("confirmedQty").asInt();
        if (naive) {
            return decision("MODIFY", List.of(line(p, original - confirmed)), false, "Re-order the missing units from the same supplier.");
        }
        int need = res.get("calculate_requirement#" + p).path("netNeed").asInt();
        if (need <= 0) {
            return decision("REJECT", List.of(), false, "Remaining stock plus the confirmed " + confirmed + " units still cover the target; no extra PO needed.");
        }
        int affordable = res.get("get_budget").path("remainingAmount").asInt();
        int fits = res.get("get_storage_capacity").path("maxUnitsThatFit").asInt();
        JsonNode best = null;
        int bestQty = 0;
        boolean bestFull = false;
        for (JsonNode alt : res.get("get_alternate_suppliers")) {
            long id = alt.path("supplierId").asLong();
            int altNeed = res.get("calculate_requirement#" + id).path("netNeed").asInt();
            int qty = Math.min(Math.max(altNeed, alt.path("moq").asInt()),
                    Math.min(alt.path("availableQty").asInt(), Math.min((int) (affordable / alt.path("unitPrice").asDouble()), fits)));
            if (altNeed <= 0 || qty < alt.path("moq").asInt()) continue;
            boolean full = qty >= altNeed;
            boolean better = best == null || (full && !bestFull) || (full == bestFull && (full ? qty < bestQty : qty > bestQty));
            if (better) { best = alt; bestQty = qty; bestFull = full; }
        }
        if (best == null) {
            return decision("INVESTIGATE", List.of(), true, "Still short by " + need + " units but no alternate supplier can fulfil it within constraints.");
        }
        return decision("MODIFY", List.of(line(best.path("supplierId").asLong(), bestQty)), !bestFull,
                "Supplier " + p + " confirmed only " + confirmed + " of " + original + ". Remaining need is covered by "
                        + best.path("supplierName").asText() + " for " + bestQty + " units.");
    }

    // ---------- helpers ----------
    private JsonNode parseRequest(List<ObjectNode> messages) throws Exception {
        for (ObjectNode m : messages) {
            if ("user".equals(m.path("role").asText())) {
                String c = m.path("content").asText();
                return mapper.readTree(c.substring(c.indexOf("REQUEST_JSON:") + "REQUEST_JSON:".length()).trim());
            }
        }
        throw new IllegalStateException("no request found");
    }

    private Map<String, JsonNode> toolResults(List<ObjectNode> messages) throws Exception {
        Map<String, String> idToKey = new HashMap<>();
        Map<String, JsonNode> results = new HashMap<>();
        for (ObjectNode m : messages) {
            for (JsonNode tc : m.path("tool_calls")) {
                JsonNode fn = tc.path("function");
                idToKey.put(tc.path("id").asText(), key(fn.path("name").asText(), mapper.readTree(fn.path("arguments").asText())));
            }
            if ("tool".equals(m.path("role").asText())) {
                results.put(idToKey.get(m.path("tool_call_id").asText()), mapper.readTree(m.path("content").asText()));
            }
        }
        return results;
    }

    private String key(String name, JsonNode args) {
        return args.has("supplierId") ? name + "#" + args.get("supplierId").asLong() : name;
    }

    private Call call(String name, Object... kv) {
        ObjectNode args = mapper.createObjectNode();
        for (int i = 0; i < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v instanceof Long l) args.put((String) kv[i], l); else args.put((String) kv[i], String.valueOf(v));
        }
        return new Call(name, args);
    }

    private ObjectNode toolCall(Call c, List<ObjectNode> messages) {
        long n = messages.stream().filter(m -> "tool".equals(m.path("role").asText())).count();
        ObjectNode msg = mapper.createObjectNode().put("role", "assistant");
        msg.putNull("content");
        ObjectNode tc = msg.putArray("tool_calls").addObject();
        tc.put("id", "mock_" + n).put("type", "function");
        tc.putObject("function").put("name", c.name()).put("arguments", c.args().toString());
        return msg;
    }

    private record Line(long supplierId, int quantity) {}

    private Line line(long supplierId, int qty) { return new Line(supplierId, qty); }

    private ObjectNode decision(String decision, List<Line> orders, boolean escalate, String reasoning) {
        ObjectNode body = mapper.createObjectNode().put("decision", decision);
        ArrayNode arr = body.putArray("orders");
        orders.forEach(o -> arr.addObject().put("supplierId", o.supplierId()).put("quantity", o.quantity()));
        body.put("escalate", escalate).put("reasoning", reasoning);
        return mapper.createObjectNode().put("role", "assistant").put("content", body.toString());
    }
}
