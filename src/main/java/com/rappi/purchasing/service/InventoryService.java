package com.rappi.purchasing.service;

import com.rappi.purchasing.entity.DemandForecast;
import com.rappi.purchasing.entity.Inventory;
import com.rappi.purchasing.repository.DemandForecastRepository;
import com.rappi.purchasing.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventoryRepo;
    private final DemandForecastRepository demandRepo;

    public int onHand(Long productId, String nodeId) {
        return inventoryRepo.findByProductIdAndNodeId(productId, nodeId).map(Inventory::getOnHand).orElse(0);
    }

    public double dailyDemand(Long productId, String nodeId) {
        return demandRepo.findByProductIdAndNodeId(productId, nodeId).map(DemandForecast::getDailyDemand).orElse(0.0);
    }
}
