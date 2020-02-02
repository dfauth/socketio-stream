package com.github.dfauth.socketio;

public interface Acknowledgement extends StreamMessage {
    long ackId();
}
