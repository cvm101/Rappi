package com.rappi.purchasing.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor
public class Product {
    @Id private Long id;
    private String name;
    private double unitVolume;
    private double unitCost;
}

