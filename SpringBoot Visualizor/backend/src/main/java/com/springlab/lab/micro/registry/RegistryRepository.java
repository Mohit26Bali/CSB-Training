package com.springlab.lab.micro.registry;

import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.run.MicroDb;
import org.springframework.stereotype.Repository;

import java.util.List;

/** The Registry service's own data access. It touches only micro_lab_registry (checked by a test). */
@Repository
class RegistryRepository {

    record Row(String instance, String address, String status, long lastHeartbeat) {
    }

    private static final ServiceName SERVICE = ServiceName.REGISTRY;
    private final MicroDb db;

    RegistryRepository(MicroDb db) {
        this.db = db;
    }

    void upsert(String service, String instance, String address, long now) {
        int updated = db.template(SERVICE).update(
                "update micro_lab_registry set address = ?, status = ?, last_heartbeat = ? where run_key = ? and service = ? and instance = ?",
                address, "UP", now, db.runKey(), service, instance);
        if (updated == 0) {
            db.template(SERVICE).update("insert into micro_lab_registry (run_key, service, instance, address, status, last_heartbeat) values (?, ?, ?, ?, ?, ?)",
                    db.runKey(), service, instance, address, "UP", now);
        }
    }

    int heartbeat(String service, String instance, long now) {
        return db.template(SERVICE).update("update micro_lab_registry set last_heartbeat = ? where run_key = ? and service = ? and instance = ?",
                now, db.runKey(), service, instance);
    }

    int setStatus(String service, String instance, String status) {
        return db.template(SERVICE).update("update micro_lab_registry set status = ? where run_key = ? and service = ? and instance = ?",
                status, db.runKey(), service, instance);
    }

    int delete(String service, String instance) {
        return db.template(SERVICE).update("delete from micro_lab_registry where run_key = ? and service = ? and instance = ?", db.runKey(), service, instance);
    }

    int deleteHeartbeatOlderThan(long cutoff) {
        return db.template(SERVICE).update("delete from micro_lab_registry where run_key = ? and last_heartbeat < ?", db.runKey(), cutoff);
    }

    List<Row> list(String service) {
        return db.template(SERVICE).query("select instance, address, status, last_heartbeat from micro_lab_registry where run_key = ? and service = ? order by id",
                (rs, n) -> new Row(rs.getString("instance"), rs.getString("address"), rs.getString("status"), rs.getLong("last_heartbeat")),
                db.runKey(), service);
    }
}
