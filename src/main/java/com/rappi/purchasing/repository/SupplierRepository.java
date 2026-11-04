package com.rappi.purchasing.repository;

import com.rappi.purchasing.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {}

