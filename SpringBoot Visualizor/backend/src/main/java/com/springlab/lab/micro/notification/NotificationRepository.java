package com.springlab.lab.micro.notification;

import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.run.MicroDb;
import org.springframework.stereotype.Repository;

/** The Notification service's own data access: micro_lab_notifications and micro_lab_processed_events (checked by a test). */
@Repository
class NotificationRepository {

    private static final ServiceName SERVICE = ServiceName.NOTIFICATION;
    private final MicroDb db;

    NotificationRepository(MicroDb db) {
        this.db = db;
    }

    void insertNotification(long orderRef, String kind) {
        db.template(SERVICE).update("insert into micro_lab_notifications (run_key, order_ref, kind) values (?, ?, ?)", db.runKey(), orderRef, kind);
    }

    boolean alreadyProcessed(String consumer, String messageId) {
        Integer n = db.template(SERVICE).queryForObject("select count(*) from micro_lab_processed_events where run_key = ? and consumer = ? and message_id = ?",
                Integer.class, db.runKey(), consumer, messageId);
        return n != null && n > 0;
    }

    void markProcessed(String consumer, String messageId) {
        db.template(SERVICE).update("insert into micro_lab_processed_events (run_key, consumer, message_id) values (?, ?, ?)", db.runKey(), consumer, messageId);
    }
}
