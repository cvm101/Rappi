package com.rappi.purchasing.repository;

import com.rappi.purchasing.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductIdAndNodeId(Long productId, String nodeId);
}

