package com.rappi.purchasing.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor
public class Budget {
    @Id private Long id;
    private String nodeId;
    private double remainingAmount;
}

