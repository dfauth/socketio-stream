package com.github.dfauth.socketio.avro;

import avro.shaded.com.google.common.collect.ImmutableMap;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;


public abstract class SpecificRecordDeserializer<T> implements Deserializer<T> {

    private static final Logger logger = LoggerFactory.getLogger(SpecificRecordDeserializer.class);

    public static class Builder {
        private static final int DEFAULT_CAPACITY = 1024;

        private final String schemaRegistryUrl;
        private final int capacity;
        private final boolean isAutoRegisterSchema;
        private final SchemaRegistryClient schemaRegistryClient;

        public static Builder builder() {
            return new Builder(null, DEFAULT_CAPACITY, true, null);
        }

        private Builder(final String schemaRegistryUrl, final int capacity, boolean isAutoRegisterSchema, final SchemaRegistryClient client) {
            this.schemaRegistryUrl = schemaRegistryUrl;
            this.capacity = capacity;
            this.isAutoRegisterSchema = isAutoRegisterSchema;
            this.schemaRegistryClient = client;
        }

        public Builder withSchemaRegistryURL(final String schemaRegistryUrl) {
            return new Builder(schemaRegistryUrl, capacity, isAutoRegisterSchema, schemaRegistryClient);
        }

        public Builder withCapacity(final int capacity) {
            return new Builder(schemaRegistryUrl, capacity, isAutoRegisterSchema, schemaRegistryClient);
        }

        public Builder withAutoRegisterSchemas(final boolean isSchemaAutoRegistered) {
            return new Builder(schemaRegistryUrl, capacity, isSchemaAutoRegistered, schemaRegistryClient);
        }

        public Builder withClient(SchemaRegistryClient client) {
            return new Builder(schemaRegistryUrl, capacity, isAutoRegisterSchema, client);
        }

        public <T> SpecificRecordDeserializer<T> build() {
            final SchemaRegistryClient client = schemaRegistryClient != null ? schemaRegistryClient :
                    new CachedSchemaRegistryClient(Objects.requireNonNull(schemaRegistryUrl, "need to Supply schemaRegistryUrl"), capacity);

            return new ConfluentSpecificRecordDeserializer(client, ImmutableMap.of(
                    "specific.avro.reader", true,
                    "auto.register.schema", isAutoRegisterSchema,
                    "schema.registry.url", schemaRegistryUrl
            ));
        }
    }


}
