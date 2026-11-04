package com.rappi.purchasing.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor
public class AgentDecision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String scenario;
    private Long productId;
    private String nodeId;
    private String decision;
    @Column(columnDefinition = "TEXT") private String reasoning;
    private String finalStatus;
    private int attempts;
    private String poIds;
    @Column(columnDefinition = "TEXT") private String traceJson;
    @Column(columnDefinition = "TEXT") private String validationJson;
    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();
}

