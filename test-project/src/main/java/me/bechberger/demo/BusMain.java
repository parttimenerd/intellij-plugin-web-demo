package me.bechberger.demo;

import me.bechberger.demo.bus.EventBus;
import me.bechberger.demo.bus.EventRecorder;
import me.bechberger.demo.pipeline.Pipeline;
import me.bechberger.demo.pipeline.WordCounter;
import me.bechberger.demo.pipeline.RingBuffer;

import java.util.List;

/** Wires the bus and pipeline together for a quick smoke test. */
public class BusMain {


    public static void main(String[] args) {
        EventBus bus = new EventBus();
        EventRecorder recorder = new EventRecorder(bus, "words", "stats");

        WordCounter counter = new WordCounter();
        RingBuffer<String> recent = new RingBuffer<>(5);

        bus.subscribe("words", e -> {
            String text = e.payloadAs(String.class);
            counter.add(text);
            recent.push(text);
        });

        bus.subscribe("stats", e -> {
            System.out.println("Top words: " + counter.topN(3));
            System.out.println("Recent:    " + recent.snapshot());
        });

        List<String> sentences = List.of(
                "the quick brown fox",
                "the fox jumped over the lazy dog",
                "the dog barked at the fox"
        );

        Pipeline.of(sentences)
                .filter(s -> s.contains("fox"))
                .map(String::trim)
                .collect()
                .forEach(s -> bus.publish("words", s));

        bus.publish("stats", null);
        System.out.println("Events recorded: " + recorder.count());
    }
}
