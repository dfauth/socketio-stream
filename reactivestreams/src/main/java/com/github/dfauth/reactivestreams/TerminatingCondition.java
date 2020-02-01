package com.github.dfauth.reactivestreams;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

interface TerminatingCondition {

    boolean eval();

    class DefaultTerminatingCondition implements TerminatingCondition {

        private final AtomicBoolean running;

        DefaultTerminatingCondition(AtomicBoolean running) {
            this.running = running;
        }

        @Override
        public boolean eval() {
            return !running.get();
        }
    }

    class WhenEmptyTerminatingCondition<T> extends DefaultTerminatingCondition {

        private Queue<T> queue;

        WhenEmptyTerminatingCondition(AtomicBoolean running, Queue<T> queue) {
            super(running);
            this.queue = queue;
        }

        @Override
        public boolean eval() {
            return super.eval() && queue.isEmpty();
        }
    }
}

