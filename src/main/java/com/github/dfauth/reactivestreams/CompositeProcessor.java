package com.github.dfauth.reactivestreams;

import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Function;

public class CompositeProcessor<I,O> implements Processor<I,O>, Publisher<O>, Subscriber<I>, Subscription {

    private static final Logger logger = LoggerFactory.getLogger(CompositeProcessor.class);

    private final Function<I, O> f;
    private final Subscriber<I> subscriber;
    private Optional<Subscriber<? super O>> subscriberOpt = Optional.empty();
    private Optional<Subscription> subscriptionOpt = Optional.empty();

    public CompositeProcessor(Subscriber<I> subscriber, Function<I,O> f) {
        this.subscriber = subscriber;
        this.f = f;
    }

    @Override
    public void subscribe(Subscriber<? super O> s) {
        subscriberOpt = Optional.of(s);
        s.onSubscribe(this);
    }

    public void request(long n) {
        logger.info("downstream requests: "+n);
    }

    @Override
    public void cancel() {
        subscriptionOpt.ifPresent(s -> s.cancel());
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        subscriptionOpt = Optional.of(subscription);
        this.subscriber.onSubscribe(subscription);
    }

    @Override
    public void onNext(I i) {
        this.subscriber.onNext(i);
        subscriberOpt.ifPresent(s -> s.onNext(f.apply(i)));
    }

    @Override
    public void onError(Throwable t) {
        this.subscriber.onError(t);
        subscriberOpt.ifPresent(s -> s.onError(t));
    }

    @Override
    public void onComplete() {
        this.subscriber.onComplete();
        subscriberOpt.ifPresent(s -> s.onComplete());
    }
}
