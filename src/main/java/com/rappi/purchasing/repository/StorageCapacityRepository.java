package com.rappi.purchasing.repository;

import com.rappi.purchasing.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface StorageCapacityRepository extends JpaRepository<StorageCapacity, Long> {
    Optional<StorageCapacity> findByNodeId(String nodeId);
}

