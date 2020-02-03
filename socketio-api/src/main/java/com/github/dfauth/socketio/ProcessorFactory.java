package com.github.dfauth.socketio;

import com.github.dfauth.auth.AuthenticationContext;
import org.reactivestreams.Processor;

public interface ProcessorFactory<U> {
    String namespace();
    Processor<StreamMessage, Event> create(AuthenticationContext<U> ctx);
}