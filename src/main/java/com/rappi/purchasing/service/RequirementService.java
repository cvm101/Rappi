package com.rappi.purchasing.service;

import com.rappi.purchasing.dto.Dtos.Requirement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Deterministic replenishment math, so the LLM does not have to do arithmetic.
 * target = daily demand * (lead time + review days + safety days)
 * netNeed = target - on hand - incoming (committed POs)
 */
@Service
@RequiredArgsConstructor
public class RequirementService {
    private final InventoryService inventory;
    private final PurchaseOrderService poService;
    private final SupplierService supplierService;
    private final PurchasingPolicy policy;

    public Requirement calculate(Long productId, String nodeId, Long supplierId) {
        int lead = supplierService.terms(productId, supplierId).leadTimeDays();
        double daily = inventory.dailyDemand(productId, nodeId);
        int onHand = inventory.onHand(productId, nodeId);
        int incoming = poService.incomingQty(productId, nodeId);
        double target = daily * (lead + policy.getReviewDays() + policy.getSafetyDays());
        int netNeed = (int) Math.max(0, Math.ceil(target - onHand - incoming));
        return new Requirement(productId, nodeId, supplierId, lead, daily, onHand, incoming, target, netNeed);
    }
}
