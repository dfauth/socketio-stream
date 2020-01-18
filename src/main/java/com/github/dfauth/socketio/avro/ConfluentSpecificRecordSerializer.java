package com.github.dfauth.socketio.avro;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.common.errors.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;


public class ConfluentSpecificRecordSerializer<T> extends SpecificRecordSerializer<T> {

    private static final Logger logger = LoggerFactory.getLogger(ConfluentSpecificRecordSerializer.class);

    private final KafkaAvroSerializer kafkaAvroSerializer;

    public ConfluentSpecificRecordSerializer(SchemaRegistryClient schemaRegistryClient, Map<String, ?> properties) {
        requireNonNull(properties, "null properties").forEach((s, o) -> logger.info("Properties entry [{}] -> [{}]", s, o));

        this.kafkaAvroSerializer = new KafkaAvroSerializer(schemaRegistryClient, properties) {
            @Override
            protected String getSubjectName(String topic, boolean isKey, Object value) {
                return topic + "-" + value.getClass().getCanonicalName();
            }
        };

    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        tryCatch(() -> {
            kafkaAvroSerializer.configure(configs, isKey);
            return null;
        }, "configure");
    }

    @Override
    public byte[] serialize(String topic, T data) {
        return tryCatch(() -> kafkaAvroSerializer.serialize(topic, data), "serialize");
    }

    @Override
    public void close() {
        kafkaAvroSerializer.close();
    }

    private <T> T tryCatch(final Supplier<T> supplier, final String methodName) {
        try {
            return supplier.get();
        } catch (Exception e) {
            handleException(methodName, e);
            return null;
        }
    }

    private static void handleException(String methodName, Exception e) {
        final String message = "Unable to perform operation [" + methodName + "]";
        logger.error(message, e);
        throw new SerializationException(message, e);
    }


}
