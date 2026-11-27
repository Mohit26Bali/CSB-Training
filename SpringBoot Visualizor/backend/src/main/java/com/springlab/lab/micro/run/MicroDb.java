package com.springlab.lab.micro.run;

import com.springlab.lab.micro.core.ServiceName;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * The database access of the simulated services. Each service gets its OWN connections (its own DataSource wrapper and its own
 * transaction manager), so a service's local transaction never joins another service's: calling Payment from inside an Inventory
 * transaction opens a second, independent transaction, exactly as two real services would. Every statement is captured for the run.
 */
@Component
public class MicroDb {

    public JdbcTemplate template(ServiceName service) {
        return MicroRunContext.require().jdbc(service.id());
    }

    /** For the comparison "monolith" only: the same capture and local transactions under a label that is not a service. */
    public JdbcTemplate templateFor(String label) {
        return MicroRunContext.require().jdbc(label);
    }

    public <T> T localFor(String label, Supplier<T> work) {
        return MicroRunContext.require().transaction(label).execute(status -> work.get());
    }

    /** The key every row written by this run carries; repositories use it in every predicate. */
    public String runKey() {
        return MicroRunContext.require().runKey();
    }

    /** Runs {@code work} in a short LOCAL transaction of {@code service}: committed when it returns, rolled back when it throws. */
    public <T> T local(ServiceName service, Supplier<T> work) {
        return MicroRunContext.require().transaction(service.id()).execute(status -> work.get());
    }
}
