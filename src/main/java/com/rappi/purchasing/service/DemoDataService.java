package com.rappi.purchasing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

/** Re-applies schema.sql + data.sql so every demo/eval run starts from the same state. */
@Service
@RequiredArgsConstructor
public class DemoDataService {
    private final DataSource dataSource;

    public void reset() {
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql"), new ClassPathResource("data.sql"))
                .execute(dataSource);
    }
}
