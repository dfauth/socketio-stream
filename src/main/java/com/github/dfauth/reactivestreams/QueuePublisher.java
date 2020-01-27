package com.github.dfauth.reactivestreams;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class QueuePublisher<T> implements Publisher<T>, Subscription {

    private static final Logger logger = LoggerFactory.getLogger(QueuePublisher.class);

    private final Duration delay;
    private final Queue<T> queue;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private TerminatingCondition terminatingCondition = new DefaultTerminatingCondition(running);
    private Optional<Subscriber> subscriberOptional = Optional.empty();

    public QueuePublisher(Queue<T> queue, ScheduledExecutorService executor) {
        this(queue, executor, Duration.of(100, ChronoUnit.MILLIS));
    }

    public QueuePublisher(Queue<T> queue, ScheduledExecutorService executor, Duration delay) {
        this.queue = queue;
        this.executor = executor;
        this.delay = delay;
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        subscriberOptional = Optional.of(s);
        s.onSubscribe(this);
    }

    @Override
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
        T t = queue.poll();
        if(t != null) {
            subscriberOptional.ifPresent(s ->s.onNext(t));
            return true;
        }
        return false;
    }

    @Override
    public void cancel() {
        running.set(false);
    }

    public void stop() {
        cancel();
    }

    public void stopWhenEmpty() {
        terminatingCondition = new WhenEmptyTerminatingCondition<>(running, queue);
        cancel();
    }

    static interface TerminatingCondition {
        Boolean eval();
    }

    static class DefaultTerminatingCondition implements TerminatingCondition {

        private final AtomicBoolean running;

        DefaultTerminatingCondition(AtomicBoolean running) {
            this.running = running;
        }

        @Override
        public Boolean eval() {
            return !running.get();
        }
    }

    static class WhenEmptyTerminatingCondition<T> extends DefaultTerminatingCondition {

        private Queue<T> queue;

        WhenEmptyTerminatingCondition(AtomicBoolean running, Queue<T> queue) {
            super(running);
            this.queue = queue;
        }

        @Override
        public Boolean eval() {
            return super.eval() && queue.isEmpty();
        }
    }
}
