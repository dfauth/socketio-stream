package com.github.dfauth.reactivestreams;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;


public abstract class AbstractProcessor<I, O> implements Publisher<I>, Subscriber<O> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractProcessor.class);

    private Optional<String> name = Optional.empty();
    protected Optional<Subscriber> subscriberOpt = Optional.empty();
    private Optional<Subscription> subscriptionOpt = Optional.empty();

    @Override
    public void subscribe(Subscriber<? super I> s) {
        subscriberOpt = Optional.of(s);
        logger.debug(withName("subscribe"));
        subscriptionOpt.ifPresent(q -> init(q));
    }

    @Override
    public void onSubscribe(Subscription s) {

    }

    @Override
    public void onNext(O o) {
        subscriberOpt.ifPresent(s -> s.onNext(o));
    }

    @Override
    public void onError(Throwable t) {
        subscriberOpt.ifPresent(s -> s.onError(t));
    }

    @Override
    public void onComplete() {
        subscriberOpt.ifPresent(s -> s.onComplete());
    }

    protected void init(Subscription q) {
        subscriberOpt.ifPresent(s -> {
            s.onSubscribe(withSubscription(q));
            logger.info(withName("subscribed"));
        });
    }

    protected String withName(String str){
        return name.map(s -> s+" "+str).orElse(str);
    }

    protected Subscription withSubscription(Subscription s){
        return s;
    }
}

