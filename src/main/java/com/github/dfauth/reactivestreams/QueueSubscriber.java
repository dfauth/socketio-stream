package com.github.dfauth.reactivestreams;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

public class QueueSubscriber<T> implements Subscriber<T> {

    private static final Logger logger = LoggerFactory.getLogger(QueueSubscriber.class);
    protected final Queue<T> queue;
    private final int capacity;
    private final AtomicLong cnt = new AtomicLong();
    private Optional<Subscription> subscriptionOptional = Optional.empty();

    public QueueSubscriber(Queue<T> queue, int capacity) {
        this.queue = queue;
        this.capacity = capacity;
    }

    @Override
    public void onSubscribe(Subscription s) {
        subscriptionOptional = Optional.of(s);
        s.request(freespace());
    }

    @Override
    public void onNext(T t) {
        queue.offer(t);
        decrement();
    }

    @Override
    public void onError(Throwable t) {
        logger.error(t.getMessage(), t);
    }

    @Override
    public void onComplete() {
        logger.info("completed");
    }

    private long freespace() {
        cnt.set(capacity - queue.size());
        return cnt.get();
    }

    private void decrement() {
        if(cnt.decrementAndGet() <= 0) {
            subscriptionOptional.ifPresent(s -> {
                long f = freespace();
                if (f >= 0) {
                    s.request(f);
                }
            });
        }
    }

}
