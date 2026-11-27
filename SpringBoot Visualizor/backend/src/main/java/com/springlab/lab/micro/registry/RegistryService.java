package com.springlab.lab.micro.registry;

import com.springlab.lab.micro.contract.RegistryContract;
import com.springlab.lab.micro.contract.RegistryContract.Ack;
import com.springlab.lab.micro.contract.RegistryContract.Evict;
import com.springlab.lab.micro.contract.RegistryContract.Evicted;
import com.springlab.lab.micro.contract.RegistryContract.HealthReport;
import com.springlab.lab.micro.contract.RegistryContract.InstanceKey;
import com.springlab.lab.micro.contract.RegistryContract.InstanceView;
import com.springlab.lab.micro.contract.RegistryContract.Instances;
import com.springlab.lab.micro.contract.RegistryContract.Query;
import com.springlab.lab.micro.contract.RegistryContract.Register;
import com.springlab.lab.micro.contract.RegistryContract.Resolved;
import com.springlab.lab.micro.core.CallContext;
import com.springlab.lab.micro.core.HandlerRegistry;
import com.springlab.lab.micro.core.ServiceModule;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.core.ServiceResult;
import com.springlab.lab.micro.registry.RegistryRepository.Row;
import com.springlab.lab.micro.run.MicroDb;
import com.springlab.lab.micro.run.MicroRunState;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Service Registry: a SIMULATED model of service discovery (a table and some code, not Eureka or Consul). Instances register,
 * send heartbeats and report health; clients resolve a service name to a healthy instance, round-robin.
 *
 * <p>The registry only knows what instances TELL it. An instance that crashes without deregistering stays listed as healthy
 * until its heartbeat is older than the TTL (a stale registration); that gap is the point of the lab.
 */
@Service
public class RegistryService implements ServiceModule {

    private final RegistryRepository repository;
    private final MicroDb db;
    private final MicroRunState state;

    RegistryService(RegistryRepository repository, MicroDb db, MicroRunState state) {
        this.repository = repository;
        this.db = db;
        this.state = state;
    }

    @Override
    public ServiceName service() {
        return ServiceName.REGISTRY;
    }

    @Override
    public void register(HandlerRegistry.Registrar registrar) {
        registrar.on(RegistryContract.REGISTER, this::register);
        registrar.on(RegistryContract.HEARTBEAT, this::heartbeat);
        registrar.on(RegistryContract.DEREGISTER, this::deregister);
        registrar.on(RegistryContract.REPORT_HEALTH, this::reportHealth);
        registrar.on(RegistryContract.RESOLVE, this::resolve);
        registrar.on(RegistryContract.LIST, this::list);
        registrar.on(RegistryContract.EVICT_EXPIRED, this::evict);
    }

    ServiceResult<Ack> register(CallContext context, Register request) {
        long now = context.bus().clock().now();
        db.local(ServiceName.REGISTRY, () -> {
            repository.upsert(request.service(), request.instance(), request.address(), now);
            return null;
        });
        return ServiceResult.ok(new Ack(true));
    }

    ServiceResult<Ack> heartbeat(CallContext context, InstanceKey request) {
        long now = context.bus().clock().now();
        int changed = db.local(ServiceName.REGISTRY, () -> repository.heartbeat(request.service(), request.instance(), now));
        return changed == 0 ? ServiceResult.failure("NOT_REGISTERED") : ServiceResult.ok(new Ack(true));
    }

    ServiceResult<Ack> deregister(CallContext context, InstanceKey request) {
        int removed = db.local(ServiceName.REGISTRY, () -> repository.delete(request.service(), request.instance()));
        return removed == 0 ? ServiceResult.failure("NOT_REGISTERED") : ServiceResult.ok(new Ack(true));
    }

    ServiceResult<Ack> reportHealth(CallContext context, HealthReport request) {
        int changed = db.local(ServiceName.REGISTRY, () -> repository.setStatus(request.service(), request.instance(), request.up() ? "UP" : "DOWN"));
        return changed == 0 ? ServiceResult.failure("NOT_REGISTERED") : ServiceResult.ok(new Ack(true));
    }

    ServiceResult<Instances> list(CallContext context, Query request) {
        long now = context.bus().clock().now();
        return ServiceResult.ok(new Instances(repository.list(request.service()).stream().map(r -> view(r, now)).toList()));
    }

    ServiceResult<Resolved> resolve(CallContext context, Query request) {
        long now = context.bus().clock().now();
        List<Row> all = repository.list(request.service());
        if (all.isEmpty()) {
            return ServiceResult.failure("UNKNOWN_SERVICE");
        }
        List<Row> healthy = all.stream().filter(r -> isHealthy(r, now)).toList();
        if (healthy.isEmpty()) {
            return ServiceResult.failure("NO_HEALTHY_INSTANCE");
        }
        Map<String, Integer> positions = state.of("registry.roundRobin", HashMap::new);
        int next = positions.merge(request.service(), 1, Integer::sum) - 1;
        Row chosen = healthy.get(next % healthy.size());
        return ServiceResult.ok(new Resolved(chosen.instance(), chosen.address()));
    }

    ServiceResult<Evicted> evict(CallContext context, Evict request) {
        long cutoff = context.bus().clock().now() - RegistryContract.TTL_MILLIS;
        return ServiceResult.ok(new Evicted(db.local(ServiceName.REGISTRY, () -> {
            // The reaper looks at every service of this run.
            return repository.deleteHeartbeatOlderThan(cutoff);
        })));
    }

    private static boolean isHealthy(Row row, long now) {
        return "UP".equals(row.status()) && RegistryContract.isFresh(now - row.lastHeartbeat());
    }

    private static InstanceView view(Row row, long now) {
        return new InstanceView(row.instance(), row.address(), row.status(), now - row.lastHeartbeat(), isHealthy(row, now));
    }
}
