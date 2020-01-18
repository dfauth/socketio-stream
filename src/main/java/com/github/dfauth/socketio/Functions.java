package com.github.dfauth.socketio;

import akka.japi.function.Function;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static com.github.dfauth.socketio.TryCatch.tryCatch;

public class Functions {

    public static <T extends SpecificRecordBase> Function<ConsumerRecord<String, Envelope>, CompletionStage<WithKafkaContext<T>>> asyncUnwrapper(EnvelopeHandler<T> envelopeHandler) {
        return r -> CompletableFuture.supplyAsync(() -> tryCatch(() -> WithKafkaContext.apply(r.topic(), r.offset(), envelopeHandler.extractRecord(r.value()))));
    }

}
