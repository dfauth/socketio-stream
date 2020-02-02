package com.github.dfauth.auth;

public interface AuthenticationContext<U> {
    String token();
    String userId();
    U payload();
}
