package me.bechberger.demo.bus;

import java.util.ArrayList;
import java.util.List;

/** Collects events for inspection — useful for testing or audit logs. */
public class EventRecorder {

    private final List<Event> recorded = new ArrayList<>();
    private final EventBus bus;

    public EventRecorder(EventBus bus, String... topics) {
        this.bus = bus;
        for (String topic : topics) {
            bus.subscribe(topic, recorded::add);
        }
    }

    public List<Event> all() {
        return List.copyOf(recorded);
    }

    public List<Event> forTopic(String topic) {
        return recorded.stream().filter(e -> e.hasTopic(topic)).toList();
    }

    public int count() {
        return recorded.size();
    }

    public void clear() {
        recorded.clear();
    }
}
