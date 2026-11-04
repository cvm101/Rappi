package com.rappi.purchasing.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor
public class Inventory {
    @Id private Long id;
    private Long productId;
    private String nodeId;
    private int onHand;
}

