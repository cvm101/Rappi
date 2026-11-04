package com.rappi.purchasing.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor
public class PurchaseOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long productId;
    private Long supplierId;
    private String nodeId;
    private int quantity;
    private double unitPrice;
    @Enumerated(EnumType.STRING) private PoStatus status;
    private String source;   // SYSTEM or AGENT
    private String note;
    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();

    public double getValue() { return quantity * unitPrice; }
}


