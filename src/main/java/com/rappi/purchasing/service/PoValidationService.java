package com.rappi.purchasing.service;

import com.rappi.purchasing.dto.Dtos.Check;
import com.rappi.purchasing.dto.Dtos.SupplierTerms;
import com.rappi.purchasing.dto.Dtos.ValidationReport;
import com.rappi.purchasing.entity.PurchaseOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * The feedback loop's judge. Plain Java, independent from the LLM: it re-reads the current state of the
 * database and checks whether a set of proposed POs (or the decision to buy nothing) is acceptable.
 * An empty list means "buy nothing" - then only the stockout risk is checked.
 */
@Service
@RequiredArgsConstructor
public class PoValidationService {
    private final InventoryService inventory;
    private final SupplierService supplierService;
    private final PurchaseOrderService poService;
    private final BudgetService budgetService;
    private final StorageService storageService;
    private final PurchasingPolicy policy;

    public ValidationReport validate(Long productId, String nodeId, List<PurchaseOrder> proposed, Long referenceSupplierId) {
        List<Check> checks = new ArrayList<>();
        int onHand = inventory.onHand(productId, nodeId);
        int incoming = poService.incomingQty(productId, nodeId);   // committed POs only (proposals excluded)
        double daily = inventory.dailyDemand(productId, nodeId);
        int newQty = proposed.stream().mapToInt(PurchaseOrder::getQuantity).sum();
        int leadTime = referenceSupplierId == null ? 0 : supplierService.terms(productId, referenceSupplierId).leadTimeDays();

        // 1. per-order supplier checks: quantity sanity, MOQ, supplier stock
        for (PurchaseOrder po : proposed) {
            SupplierTerms t = supplierService.terms(productId, po.getSupplierId());
            leadTime = Math.max(leadTime, t.leadTimeDays());
            checks.add(new Check("QUANTITY_POSITIVE", po.getQuantity() > 0, "qty=" + po.getQuantity()));
            checks.add(new Check("MOQ", po.getQuantity() >= t.moq(),
                    t.supplierName() + " MOQ is " + t.moq() + ", ordered " + po.getQuantity()));
            checks.add(new Check("SUPPLIER_AVAILABILITY", po.getQuantity() <= t.availableQty(),
                    t.supplierName() + " can supply " + t.availableQty() + ", ordered " + po.getQuantity()));
        }

        double cost = proposed.stream().mapToDouble(PurchaseOrder::getValue).sum();
        double budget = budgetService.remaining(nodeId);
        checks.add(new Check("BUDGET", cost <= budget, "cost " + cost + " vs remaining budget " + budget));

        double volume = newQty * storageService.unitVolume(productId);
        double free = storageService.freeVolume(nodeId);
        checks.add(new Check("STORAGE", volume <= free, "needs volume " + volume + " vs free volume " + free));

        // 4. stockout: stock + incoming + new must cover demand during the lead time
        double leadDemand = daily * leadTime;
        int cover = onHand + incoming + newQty;
        checks.add(new Check("STOCKOUT", cover >= leadDemand,
                "on hand+incoming+new=" + cover + " vs demand during lead time=" + leadDemand));

        // 5. overstock: do not hold more than max-cover-days of demand
        double maxStock = daily * policy.getMaxCoverDays();
        // (buying nothing can never make existing overstock worse, so it always passes)
        checks.add(new Check("OVERSTOCK", newQty == 0 || cover <= maxStock,
                "projected stock " + cover + " vs max " + maxStock + " (" + policy.getMaxCoverDays() + " days of demand)"));

        return new ValidationReport(checks.stream().allMatch(Check::passed), checks);
    }
}
