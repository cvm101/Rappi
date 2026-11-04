package com.rappi.purchasing.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rappi.purchasing.dto.Dtos.OrderLine;
import com.rappi.purchasing.dto.Dtos.SupplierTerms;
import com.rappi.purchasing.entity.PurchaseOrder;
import com.rappi.purchasing.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The agent's read-only view of the world (+ a dry-run validator).
 * The agent cannot write to the database: creating POs is done by PurchasingAgentService
 * after the agent has produced its decision, so every write goes through validation.
 */
@Component
@RequiredArgsConstructor
public class PurchasingTools {
    private final ObjectMapper mapper;
    private final InventoryService inventory;
    private final SupplierService suppliers;
    private final PurchaseOrderService poService;
    private final BudgetService budget;
    private final StorageService storage;
    private final RequirementService requirements;
    private final PoValidationService validator;

    public List<ToolSpec> specs() {
        List<ToolSpec> list = new ArrayList<>();
        list.add(spec("get_inventory", "Current on-hand units of a product at a node.", "productId:integer", "nodeId:string"));
        list.add(spec("get_demand_forecast", "Forecast daily demand (units/day) of a product at a node.", "productId:integer", "nodeId:string"));
        list.add(spec("get_open_purchase_orders", "Committed purchase orders (incoming stock) for a product at a node.", "productId:integer", "nodeId:string"));
        list.add(spec("get_supplier_terms", "Lead time, MOQ, price, reliability and currently available quantity of a supplier for a product.", "productId:integer", "supplierId:integer"));
        list.add(spec("get_alternate_suppliers", "Other suppliers that sell this product, with their terms.", "productId:integer", "excludeSupplierId:integer"));
        list.add(spec("get_budget", "Remaining purchasing budget of a node.", "nodeId:string"));
        list.add(spec("get_storage_capacity", "Free storage volume of a node and how many units of the product fit.", "nodeId:string", "productId:integer"));
        list.add(spec("calculate_requirement", "Policy replenishment math: target stock and net units still needed, given the supplier's lead time.", "productId:integer", "nodeId:string", "supplierId:integer"));

        ObjectNode vp = schema("productId:integer", "nodeId:string", "referenceSupplierId:integer");
        ObjectNode order = mapper.createObjectNode().put("type", "object");
        ObjectNode op = order.putObject("properties");
        op.putObject("supplierId").put("type", "integer");
        op.putObject("quantity").put("type", "integer");
        ((ObjectNode) vp.get("properties")).putObject("orders").put("type", "array").set("items", order);
        ((com.fasterxml.jackson.databind.node.ArrayNode) vp.get("required")).add("orders");
        list.add(new ToolSpec("validate_proposal",
                "Dry-run the deterministic validator (MOQ, supplier stock, budget, storage, stockout, overstock) on a proposed order list. Empty orders = buy nothing.",
                vp));
        return list;
    }

    /** Executes a tool; errors are returned as {"error": ...} so the LLM can react. */
    public String execute(String name, JsonNode a) {
        try {
            Object result = switch (name) {
                case "get_inventory" -> Map.of("productId", a.path("productId").asLong(), "nodeId", a.path("nodeId").asText(),
                        "onHand", inventory.onHand(a.path("productId").asLong(), a.path("nodeId").asText()));
                case "get_demand_forecast" -> Map.of("dailyDemand",
                        inventory.dailyDemand(a.path("productId").asLong(), a.path("nodeId").asText()));
                case "get_open_purchase_orders" -> openPos(a.path("productId").asLong(), a.path("nodeId").asText());
                case "get_supplier_terms" -> suppliers.terms(a.path("productId").asLong(), a.path("supplierId").asLong());
                case "get_alternate_suppliers" -> suppliers.alternates(a.path("productId").asLong(), a.path("excludeSupplierId").asLong());
                case "get_budget" -> Map.of("remainingAmount", budget.remaining(a.path("nodeId").asText()));
                case "get_storage_capacity" -> storageInfo(a.path("nodeId").asText(), a.path("productId").asLong());
                case "calculate_requirement" -> requirements.calculate(a.path("productId").asLong(),
                        a.path("nodeId").asText(), a.path("supplierId").asLong());
                case "validate_proposal" -> dryRun(a);
                default -> Map.of("error", "unknown tool " + name);
            };
            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}";
        }
    }

    private Map<String, Object> openPos(Long productId, String nodeId) {
        List<PurchaseOrder> pos = poService.committed(productId, nodeId);
        List<Map<String, Object>> rows = pos.stream().map(p -> Map.<String, Object>of("poId", p.getId(),
                "supplierId", p.getSupplierId(), "quantity", p.getQuantity(), "status", p.getStatus().name())).toList();
        return Map.of("incomingQty", poService.incomingQty(productId, nodeId), "orders", rows);
    }

    private Map<String, Object> storageInfo(String nodeId, Long productId) {
        return Map.of("freeVolume", storage.freeVolume(nodeId), "unitVolume", storage.unitVolume(productId),
                "maxUnitsThatFit", storage.maxUnitsThatFit(nodeId, productId));
    }

    private Object dryRun(JsonNode a) {
        Long productId = a.path("productId").asLong();
        List<PurchaseOrder> transientPos = new ArrayList<>();
        for (JsonNode o : a.path("orders")) {
            OrderLine line = new OrderLine(o.path("supplierId").asLong(), o.path("quantity").asInt());
            SupplierTerms t = suppliers.terms(productId, line.supplierId());
            PurchaseOrder po = new PurchaseOrder();   // not saved
            po.setProductId(productId);
            po.setNodeId(a.path("nodeId").asText());
            po.setSupplierId(line.supplierId());
            po.setQuantity(line.quantity());
            po.setUnitPrice(t.unitPrice());
            transientPos.add(po);
        }
        return validator.validate(productId, a.path("nodeId").asText(), transientPos, a.path("referenceSupplierId").asLong());
    }

    private ToolSpec spec(String name, String description, String... params) {
        return new ToolSpec(name, description, schema(params));
    }

    private ObjectNode schema(String... params) {
        ObjectNode root = mapper.createObjectNode().put("type", "object");
        ObjectNode props = root.putObject("properties");
        var required = root.putArray("required");
        for (String p : params) {
            String[] kv = p.split(":");
            props.putObject(kv[0]).put("type", kv[1]);
            required.add(kv[0]);
        }
        return root;
    }
}
