package me.bechberger.demo.bus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Lightweight publish/subscribe event bus. */
public class EventBus {

    private final Map<String, List<Consumer<Event>>> subscribers = new ConcurrentHashMap<>();

    public void subscribe(String topic, Consumer<Event> handler) {
        subscribers.computeIfAbsent(topic, k -> new ArrayList<>()).add(handler);
    }

    public void unsubscribe(String topic) {
        subscribers.remove(topic);
    }

    public void publish(String topic, Object payload) {
        Event event = new Event(topic, payload);
        List<Consumer<Event>> handlers = subscribers.getOrDefault(topic, List.of());
        handlers.forEach(h -> h.accept(event));
    }

    public int subscriberCount(String topic) {
        return subscribers.getOrDefault(topic, List.of()).size();
    }

    public List<String> activeTopics() {
        return subscribers.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();
    }
}
