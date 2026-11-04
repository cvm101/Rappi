package com.rappi.purchasing.service;

import com.rappi.purchasing.entity.Product;
import com.rappi.purchasing.entity.PurchaseOrder;
import com.rappi.purchasing.entity.StorageCapacity;
import com.rappi.purchasing.repository.ProductRepository;
import com.rappi.purchasing.repository.StorageCapacityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StorageService {
    private final StorageCapacityRepository repo;
    private final ProductRepository productRepo;
    private final PurchaseOrderService poService;

    public double unitVolume(Long productId) {
        return productRepo.findById(productId).map(Product::getUnitVolume).orElse(1.0);
    }

    /** Free volume = capacity - what is stored now - what is already on its way (all products of the node). */
    public double freeVolume(String nodeId) {
        StorageCapacity sc = repo.findByNodeId(nodeId).orElseThrow();
        double incoming = poService.committedForNode(nodeId).stream()
                .mapToDouble(po -> po.getQuantity() * unitVolume(po.getProductId())).sum();
        return sc.getCapacityVolume() - sc.getUsedVolume() - incoming;
    }

    public int maxUnitsThatFit(String nodeId, Long productId) {
        return (int) Math.max(0, Math.floor(freeVolume(nodeId) / unitVolume(productId)));
    }
}
