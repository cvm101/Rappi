package com.rappi.purchasing.repository;

import com.rappi.purchasing.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SupplierProductRepository extends JpaRepository<SupplierProduct, Long> {
    Optional<SupplierProduct> findBySupplierIdAndProductId(Long supplierId, Long productId);
    List<SupplierProduct> findByProductId(Long productId);
}

