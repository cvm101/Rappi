package com.rappi.purchasing.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor
public class SupplierProduct {
    @Id private Long id;
    private Long supplierId;
    private Long productId;
    private double unitPrice;
    private int availableQty;
}

