package com.rappi.purchasing.service;

import com.rappi.purchasing.dto.Dtos.ValidationReport;
import com.rappi.purchasing.entity.PoStatus;
import com.rappi.purchasing.entity.PurchaseOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Human-in-the-loop step. Approving re-validates, because the world may have changed since the agent ran. */
@Service
@RequiredArgsConstructor
public class ApprovalService {
    public record ApprovalResult(boolean approved, PurchaseOrder po, ValidationReport validation) {}

    private final PurchaseOrderService poService;
    private final PoValidationService validator;
    private final BudgetService budgetService;

    public ApprovalResult approve(Long poId) {
        PurchaseOrder po = pendingPo(poId);
        ValidationReport report = validator.validate(po.getProductId(), po.getNodeId(), List.of(po), po.getSupplierId());
        if (!report.passed()) {
            return new ApprovalResult(false, po, report);     // stays PENDING_APPROVAL
        }
        poService.setStatus(List.of(po), PoStatus.APPROVED);
        budgetService.spend(po.getNodeId(), po.getValue());
        return new ApprovalResult(true, po, report);
    }

    public PurchaseOrder reject(Long poId) {
        PurchaseOrder po = pendingPo(poId);
        poService.setStatus(List.of(po), PoStatus.REJECTED);
        return po;
    }

    private PurchaseOrder pendingPo(Long poId) {
        PurchaseOrder po = poService.get(poId);
        if (po.getStatus() != PoStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("PO " + poId + " is " + po.getStatus() + ", not PENDING_APPROVAL");
        }
        return po;
    }
}
