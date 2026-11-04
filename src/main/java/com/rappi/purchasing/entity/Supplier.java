package com.rappi.purchasing.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor
public class Supplier {
    @Id private Long id;
    private String name;
    private int leadTimeDays;
    private int moq;
    private double reliabilityScore;
}

