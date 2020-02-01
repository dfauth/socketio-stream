package com.github.dfauth.socketio;

public interface AuthenticationContext<U> {

    String token();
    String userId();
    U payload();
}
