package me.bechberger.demo.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** Fluent data pipeline: transform, filter, collect. */
public class Pipeline<T> {

    private final List<T> data;

    private Pipeline(List<T> data) {
        this.data = List.copyOf(data);
    }

    public static <T> Pipeline<T> of(List<T> source) {
        return new Pipeline<>(source);
    }

    public <R> Pipeline<R> map(Function<T, R> transform) {
        List<R> result = new ArrayList<>();
        for (T item : data) result.add(transform.apply(item));
        return new Pipeline<>(result);
    }

    public Pipeline<T> filter(Predicate<T> predicate) {
        return new Pipeline<>(data.stream().filter(predicate).toList());
    }

    public Pipeline<T> limit(int max) {
        return new Pipeline<>(data.stream().limit(max).toList());
    }

    public List<T> collect() {
        return List.copyOf(data);
    }

    public int size() {
        return data.size();
    }
}
