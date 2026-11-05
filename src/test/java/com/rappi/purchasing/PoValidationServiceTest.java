package com.rappi.purchasing;

import com.rappi.purchasing.dto.Dtos.ValidationReport;
import com.rappi.purchasing.entity.PoStatus;
import com.rappi.purchasing.entity.PurchaseOrder;
import com.rappi.purchasing.service.DemoDataService;
import com.rappi.purchasing.service.PoValidationService;
import com.rappi.purchasing.service.PurchaseOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Documents the two coverage horizons with milk at BLR-01: 40 on hand, 70/day, supplier 2 (lead time 5 days).
 * Hard STOCKOUT guard needs on hand + incoming + new >= 70 x 5 = 350.
 * Advisory POLICY_COVER needs it to reach 70 x (5 + 7 + 2) = 980.
 */
@SpringBootTest
class PoValidationServiceTest {
    private static final long MILK = 1, NODE_SUPPLIER = 2;
    private static final String NODE = "BLR-01";

    @Autowired PoValidationService validator;
    @Autowired PurchaseOrderService poService;
    @Autowired DemoDataService demoData;

    @BeforeEach
    void freshData() {
        demoData.reset();
        PurchaseOrder open = poService.createDraft(MILK, 1L, NODE, 300, 50, "existing");
        poService.setStatus(List.of(open), PoStatus.OPEN);       // 300 incoming
    }

    private ValidationReport validateNewQty(int qty) {
        PurchaseOrder draft = poService.createDraft(MILK, NODE_SUPPLIER, NODE, qty, 52, "test");
        return validator.validate(MILK, NODE, List.of(draft), NODE_SUPPLIER);
    }

    @Test
    void passesHardGuardButWarnsWhenBelowPolicyHorizon() {
        ValidationReport r = validateNewQty(460);                  // cover 40 + 300 + 460 = 800
        assertTrue(r.passed(), r.failureSummary());
        assertEquals(1, r.warnings().size(), "800 < 980 policy target must be flagged");
    }

    @Test
    void noWarningWhenPolicyHorizonIsCovered() {
        ValidationReport r = validateNewQty(640);                  // cover 980
        assertTrue(r.passed(), r.failureSummary());
        assertTrue(r.warnings().isEmpty());
    }

    @Test
    void rejectingIntoAStockoutFailsHardGuard() {
        ValidationReport r = validator.validate(MILK, NODE, List.of(), NODE_SUPPLIER);   // buy nothing: 340 < 350
        assertFalse(r.passed());
        assertTrue(r.checks().stream().anyMatch(c -> c.name().equals("STOCKOUT") && !c.passed()));
    }
}
