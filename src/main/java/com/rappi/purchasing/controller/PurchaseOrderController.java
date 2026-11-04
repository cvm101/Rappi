package com.rappi.purchasing.controller;

import com.rappi.purchasing.entity.PoStatus;
import com.rappi.purchasing.entity.PurchaseOrder;
import com.rappi.purchasing.service.ApprovalService;
import com.rappi.purchasing.service.ApprovalService.ApprovalResult;
import com.rappi.purchasing.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {
    private final PurchaseOrderService poService;
    private final ApprovalService approvalService;

    @GetMapping
    public List<PurchaseOrder> byStatus(@RequestParam(defaultValue = "PENDING_APPROVAL") PoStatus status) {
        return poService.byStatus(status);
    }

    /** 409 + validation report if the PO is no longer acceptable at approval time. */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApprovalResult> approve(@PathVariable Long id) {
        ApprovalResult result = approvalService.approve(id);
        return ResponseEntity.status(result.approved() ? HttpStatus.OK : HttpStatus.CONFLICT).body(result);
    }

    @PostMapping("/{id}/reject")
    public PurchaseOrder reject(@PathVariable Long id) {
        return approvalService.reject(id);
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> bad(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
}
