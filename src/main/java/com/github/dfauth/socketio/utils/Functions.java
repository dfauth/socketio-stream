package com.github.dfauth.socketio.utils;

import akka.japi.function.Function;
import akka.kafka.ConsumerMessage;
import com.github.dfauth.socketio.CommittableKafkaContext;
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

    public static <T extends SpecificRecordBase> Function<ConsumerMessage.CommittableMessage<String, Envelope>, CompletionStage<CommittableKafkaContext<T>>> committableAsyncUnwrapper(EnvelopeHandler<T> envelopeHandler) {
        return r -> CompletableFuture.supplyAsync(() -> tryCatch(() -> CommittableKafkaContext.apply(r.committableOffset(), envelopeHandler.extractRecord(r.record().value()))));
    }

}
