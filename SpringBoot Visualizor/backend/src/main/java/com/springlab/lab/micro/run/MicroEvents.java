package com.springlab.lab.micro.run;

import com.springlab.lab.micro.core.EventMessage;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * How a service publishes an event to the run's SIMULATED channel. Publishing is NOT part of the service's database transaction:
 * the dual-write problem this creates is real, and the transactional outbox (Order.placeWithOutbox plus the relay) is the pattern
 * that solves it. Event data holds identifiers and plain numbers only.
 */
@Component
public class MicroEvents {

    public EventMessage publish(String topic, String type, long orderRef) {
        return MicroRunContext.require().channel().publish(topic, type, orderRef);
    }

    public EventMessage publish(String topic, String type, long orderRef, Map<String, String> data) {
        return MicroRunContext.require().channel().publish(topic, type, orderRef, null, data);
    }

    /** Publishes with a STABLE message id chosen by the publisher (an outbox row), so a republication carries the same id. */
    public EventMessage publishWithId(String topic, String type, long orderRef, String messageId) {
        return MicroRunContext.require().channel().publish(topic, type, orderRef, messageId, Map.of());
    }
}
