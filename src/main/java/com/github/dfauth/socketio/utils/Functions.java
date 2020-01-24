package com.github.dfauth.socketio.utils;

import akka.japi.function.Function;
import com.github.dfauth.socketio.Envelope;
import com.github.dfauth.socketio.EnvelopeHandler;
import com.github.dfauth.socketio.KafkaContext;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static com.github.dfauth.socketio.utils.TryCatch.tryCatch;

public class Functions {

    public static <T extends SpecificRecordBase> Function<ConsumerRecord<String, Envelope>, CompletionStage<KafkaContext<T>>> asyncUnwrapper(EnvelopeHandler<T> envelopeHandler) {
        return r -> CompletableFuture.supplyAsync(() -> tryCatch(() -> KafkaContext.apply(r.topic(), r.partition(), r.offset(), envelopeHandler.extractRecord(r.value()))));
    }

}
