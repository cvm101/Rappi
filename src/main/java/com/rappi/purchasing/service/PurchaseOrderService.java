package com.rappi.purchasing.service;

import com.rappi.purchasing.entity.PoStatus;
import com.rappi.purchasing.entity.PurchaseOrder;
import com.rappi.purchasing.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {
    /** Only these count as stock that is really on its way. */
    public static final List<PoStatus> COMMITTED = List.of(PoStatus.OPEN, PoStatus.APPROVED);

    private final PurchaseOrderRepository repo;

    public PurchaseOrder get(Long id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown PO " + id));
    }

    public List<PurchaseOrder> committed(Long productId, String nodeId) {
        return repo.findByProductIdAndNodeIdAndStatusIn(productId, nodeId, COMMITTED);
    }

    public List<PurchaseOrder> committedForNode(String nodeId) {
        return repo.findByNodeIdAndStatusIn(nodeId, COMMITTED);
    }

    public int incomingQty(Long productId, String nodeId) {
        return committed(productId, nodeId).stream().mapToInt(PurchaseOrder::getQuantity).sum();
    }

    public List<PurchaseOrder> byStatus(PoStatus status) {
        return repo.findByStatusOrderByIdDesc(status);
    }

    public PurchaseOrder createDraft(Long productId, Long supplierId, String nodeId, int qty, double unitPrice, String note) {
        PurchaseOrder po = new PurchaseOrder();
        po.setProductId(productId);
        po.setSupplierId(supplierId);
        po.setNodeId(nodeId);
        po.setQuantity(qty);
        po.setUnitPrice(unitPrice);
        po.setStatus(PoStatus.DRAFT);
        po.setSource("AGENT");
        po.setNote(note);
        return repo.save(po);
    }

    /** Supplier told us it can only deliver confirmedQty: shrink the PO. Returns the original quantity. */
    public int applyShortfall(Long poId, int confirmedQty) {
        PurchaseOrder po = get(poId);
        int original = po.getQuantity();
        po.setQuantity(confirmedQty);
        po.setNote("Supplier confirmed only " + confirmedQty + " of " + original);
        repo.save(po);
        return original;
    }

    public void setStatus(List<PurchaseOrder> pos, PoStatus status) {
        pos.forEach(p -> p.setStatus(status));
        repo.saveAll(pos);
    }
}
