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

    /**
     * Resets the demo world (products, suppliers, stock, budget, POs...) to its seed state,
     * WITHOUT touching the agent_decision audit log: past decisions are history and must
     * survive demo resets and evaluation runs, otherwise the UI audit panel would be empty.
     *
     * DML-only by design (DELETE + re-seed, see reset.sql): on a persistent Postgres
     * (Supabase) re-running DDL through the pooler mid-session is unreliable, and the
     * tables already exist from startup init. On H2 it behaves identically.
     */
    public void reset() {
        new ResourceDatabasePopulator(new ClassPathResource("reset.sql"), new ClassPathResource("data.sql"))
                .execute(dataSource);
    }
}
