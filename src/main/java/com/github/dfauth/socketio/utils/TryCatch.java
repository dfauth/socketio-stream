package com.github.dfauth.socketio.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class TryCatch {

    private static final Logger logger = LoggerFactory.getLogger(TryCatch.class);

    public static <T> T tryCatch(Supplier<T> supplier) {
        return tryCatch(supplier, e -> {
            throw e;
        });
    }

    public static <T> T tryCatch(Supplier<T> supplier, java.util.function.Function<RuntimeException, T> handler) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            logger.error(e.getMessage(), e);
            return handler.apply(e);
        }
    }

}
