package me.bechberger.demo.pipeline;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/** Bounded ring buffer that drops oldest entries when full. */
public class RingBuffer<T> {

    private final int capacity;
    private final LinkedList<T> buffer = new LinkedList<>();

    public RingBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        this.capacity = capacity;
    }

    public void push(T item) {
        if (buffer.size() == capacity) buffer.pollFirst();
        buffer.addLast(item);
    }

    public T peek() {
        return buffer.peekLast();
    }

    public List<T> snapshot() {
        return List.copyOf(buffer);
    }

    public boolean isFull() {
        return buffer.size() == capacity;
    }

    public int size() {
        return buffer.size();
    }
}
