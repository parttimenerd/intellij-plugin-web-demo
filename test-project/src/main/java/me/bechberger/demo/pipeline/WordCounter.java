package me.bechberger.demo.pipeline;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Counts word frequencies in a stream of strings. */
public class WordCounter {

    private final Map<String, Integer> counts = new HashMap<>();

    public void add(String text) {
        for (String word : text.toLowerCase().split("\\W+")) {
            if (!word.isBlank()) counts.merge(word, 1, Integer::sum);
        }
    }

    public int count(String word) {
        return counts.getOrDefault(word.toLowerCase(), 0);
    }

    public List<String> topN(int n) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    public int uniqueWords() {
        return counts.size();
    }

    public void reset() {
        counts.clear();
    }
}
