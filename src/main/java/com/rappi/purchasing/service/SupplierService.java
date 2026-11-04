package com.rappi.purchasing.service;

import com.rappi.purchasing.dto.Dtos.SupplierTerms;
import com.rappi.purchasing.entity.Supplier;
import com.rappi.purchasing.entity.SupplierProduct;
import com.rappi.purchasing.repository.SupplierProductRepository;
import com.rappi.purchasing.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {
    private final SupplierRepository supplierRepo;
    private final SupplierProductRepository supplierProductRepo;

    /** Commercial terms of one supplier for one product. Throws if the supplier does not sell it. */
    public SupplierTerms terms(Long productId, Long supplierId) {
        Supplier s = supplierRepo.findById(supplierId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown supplier " + supplierId));
        SupplierProduct sp = supplierProductRepo.findBySupplierIdAndProductId(supplierId, productId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Supplier " + supplierId + " does not sell product " + productId));
        return toTerms(s, sp);
    }

    /** All other suppliers that sell the product, best reliability first. */
    public List<SupplierTerms> alternates(Long productId, Long excludeSupplierId) {
        return supplierProductRepo.findByProductId(productId).stream()
                .filter(sp -> !sp.getSupplierId().equals(excludeSupplierId))
                .map(sp -> toTerms(supplierRepo.findById(sp.getSupplierId()).orElseThrow(), sp))
                .sorted((a, b) -> Double.compare(b.reliability(), a.reliability()))
                .toList();
    }

    private SupplierTerms toTerms(Supplier s, SupplierProduct sp) {
        return new SupplierTerms(s.getId(), s.getName(), s.getLeadTimeDays(), s.getMoq(),
                s.getReliabilityScore(), sp.getUnitPrice(), sp.getAvailableQty());
    }
}
