package com.springlab.lab.micro.core;

import java.util.Map;

/**
 * An event on the simulated channel. It carries identifiers and plain numbers only (order, product, quantity, amount): never names,
 * addresses or credentials. {@code messageId} is stable: an outbox relay that publishes the same row twice produces the same id,
 * which is what lets a consumer recognise a duplicate.
 */
public record EventMessage(String messageId, String topic, String type, long orderRef, Map<String, String> data) {

    public EventMessage(String messageId, String topic, String type, long orderRef) {
        this(messageId, topic, type, orderRef, Map.of());
    }

    public long number(String key) {
        return Long.parseLong(data.get(key));
    }
}
