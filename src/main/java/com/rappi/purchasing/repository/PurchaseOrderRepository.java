package com.rappi.purchasing.repository;

import com.rappi.purchasing.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByProductIdAndNodeIdAndStatusIn(Long productId, String nodeId, Collection<PoStatus> statuses);
    List<PurchaseOrder> findByNodeIdAndStatusIn(String nodeId, Collection<PoStatus> statuses);
    List<PurchaseOrder> findByStatusOrderByIdDesc(PoStatus status);
}

