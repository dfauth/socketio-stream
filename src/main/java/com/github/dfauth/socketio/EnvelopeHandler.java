package com.github.dfauth.socketio;

import com.github.dfauth.socketio.avro.SpecificRecordDeserializer;
import com.github.dfauth.socketio.avro.SpecificRecordSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class EnvelopeHandler<T> {

    private static final Logger logger = LoggerFactory.getLogger(EnvelopeHandler.class);

    private final SpecificRecordSerializer<T> serializer;
    private final SpecificRecordDeserializer<T> deserializer;

    public EnvelopeHandler(SpecificRecordSerializer<T> serializer, SpecificRecordDeserializer<T> deserializer) {
        this.serializer = serializer;
        this.deserializer = deserializer;
    }


    public T extractRecord(Envelope e) {
        return null;
    }

}
