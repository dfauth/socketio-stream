package com.github.dfauth.reactivestreams;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.testng.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

public class AssertingSubscriber<T> implements Subscriber<T> {

    private final List<T> received = new ArrayList();
    private final List<Throwable> exceptions = new ArrayList<>();
    private CompletableFuture<Subscriber<T>> complete = new CompletableFuture<>();

    public void reset() {
        received.clear();
        exceptions.clear();
        complete = new CompletableFuture<>();
    }

    @Override
    public void onSubscribe(Subscription s) {
        s.request(Integer.MAX_VALUE);
    }

    @Override
    public void onNext(T t) {
        received.add(t);
    }

    @Override
    public void onError(Throwable t) {
        exceptions.add(t);
    }

    @Override
    public void onComplete() {
        complete.complete(this);
    }

    public Asserter asserter() {
        return new Asserter(received, exceptions, complete);
    }

    public Asserter assertReceived(T t) {
        Asserter asserter = new Asserter(received, exceptions, complete, Optional.of(t));
        return asserter.assertReceivedSubject();
    }

    public Asserter assertNothingReceived() {
        Asserter asserter = new Asserter(received, exceptions, complete);
        return asserter.assertNothingReceived();
    }

    private class Asserter<T> {

        private final List<T> received;
        private final List<Throwable> exceptions;
        private final Optional<T> subject;
        private final CompletableFuture<Subscriber<T>> complete;

        public Asserter(List<T> received, List<Throwable> exceptions, CompletableFuture<Subscriber<T>> complete) {
            this(received, exceptions, complete, Optional.empty());
        }

        public Asserter(List<T> received, List<Throwable> exceptions, CompletableFuture<Subscriber<T>> complete, Optional<T> tOpt) {
            this.received = received;
            this.exceptions = exceptions;
            this.complete = complete;
            this.subject = tOpt;
        }

        public Asserter assertReceivedSubject() {
            Assert.assertTrue(subject.map(e ->received.contains(e)).orElseGet(() ->false));
            return this;
        }

        public Asserter assertNothingReceived() {
            Assert.assertFalse(subject.map(e ->received.contains(e)).orElseGet(() ->false));
            return this;
        }

        public Asserter only() {
            Assert.assertTrue(subject.map(t -> received.size() == 1).orElseGet(() -> received.isEmpty()));
            Assert.assertTrue(exceptions.isEmpty());
            return this;
        }

        public Asserter yet() {
            Assert.assertTrue(exceptions.isEmpty());
            Assert.assertFalse(complete.isDone());
            return this;
        }

        public Asserter andDone() {
            Assert.assertTrue(AssertingSubscriber.this.complete.isDone());
            return this;
        }

        public CompletionStage<Subscriber<T>> onComplete() {
            return complete;
        }
    }
}
