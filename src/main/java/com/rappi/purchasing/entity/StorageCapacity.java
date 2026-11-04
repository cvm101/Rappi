package com.rappi.purchasing.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor
public class StorageCapacity {
    @Id private Long id;
    private String nodeId;
    private double capacityVolume;
    private double usedVolume;
}

