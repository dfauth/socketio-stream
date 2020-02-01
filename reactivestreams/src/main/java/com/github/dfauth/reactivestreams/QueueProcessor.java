package com.github.dfauth.reactivestreams;

import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class QueueProcessor<I,O> extends QueueSubscriber<I> implements Publisher<O>, Subscriber<I>, Processor<I,O>, Subscription {

    private static final Logger logger = LoggerFactory.getLogger(QueueProcessor.class);

    private final Function<I, O> f;
    private Optional<Subscriber<? super O>> subscriberOptional = Optional.empty();
    private final Duration delay;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private TerminatingCondition terminatingCondition = new TerminatingCondition.DefaultTerminatingCondition(running);

    public QueueProcessor(Queue<I> queue, int capacity, Function<I,O> f) {
        this(queue, capacity, f, Executors.newSingleThreadScheduledExecutor(), Duration.of(100, ChronoUnit.MILLIS));
    }

    public QueueProcessor(Queue<I> queue, int capacity, Function<I,O> f, ScheduledExecutorService executor, Duration delay) {
        super(queue, capacity);
        this.f = f;
        this.executor = executor;
        this.delay = delay;
    }

    @Override
    public void subscribe(Subscriber<? super O> s) {
        subscriberOptional = Optional.of(s);
        s.onSubscribe(this);
    }

    public void request(long n) {
        executor.execute(() -> _request(n));
    }

    private void _request(long i) {
        if(i <= 0) {
            return;
        } else {
            if(terminatingCondition.eval()) {
                subscriberOptional.ifPresent(s -> s.onComplete());
                return;
            }
            if(dequeueOne()) {
                _request(i-1L);
            } else {
                executor.schedule(() -> _request(i), delay.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    private boolean dequeueOne() {
        I i = queue.poll();
        if(i != null) {
            subscriberOptional.ifPresent(s ->s.onNext(f.apply(i)));
            return true;
        }
        return false;
    }

    @Override
    public void cancel() {
        running.set(false);
    }
}
