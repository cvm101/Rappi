package com.rappi.purchasing.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor
public class DemandForecast {
    @Id private Long id;
    private Long productId;
    private String nodeId;
    private double dailyDemand;
}

