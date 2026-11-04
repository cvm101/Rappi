package com.rappi.purchasing.service;

import com.rappi.purchasing.entity.Budget;
import com.rappi.purchasing.repository.BudgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BudgetService {
    private final BudgetRepository repo;

    public double remaining(String nodeId) {
        return repo.findByNodeId(nodeId).map(Budget::getRemainingAmount).orElse(0.0);
    }

    /** Called when a PO becomes committed. */
    public void spend(String nodeId, double amount) {
        Budget b = repo.findByNodeId(nodeId).orElseThrow();
        b.setRemainingAmount(b.getRemainingAmount() - amount);
        repo.save(b);
    }
}
