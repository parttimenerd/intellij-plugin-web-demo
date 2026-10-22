package me.bechberger.demo.bus;

import java.time.Instant;

/** Immutable event envelope published on the bus. */
public record Event(String topic, Object payload, Instant timestamp) {

    public Event(String topic, Object payload) {
        this(topic, payload, Instant.now());
    }

    public boolean hasTopic(String t) {
        return topic.equals(t);
    }

    public <T> T payloadAs(Class<T> type) {
        return type.cast(payload);
    }

    @Override
    public String toString() {
        return "[" + timestamp + "] " + topic + " -> " + payload;
    }
}
