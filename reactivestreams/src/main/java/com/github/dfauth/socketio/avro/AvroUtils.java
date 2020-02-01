package com.github.dfauth.socketio.avro;

import org.apache.avro.Conversion;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class AvroUtils {

    private static final Logger logger = LoggerFactory.getLogger(AvroUtils.class);

    public static byte [] toByteArray(Schema schema, GenericRecord genericRecord) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(schema);
        writer.getData().addLogicalTypeConversion(new MyTimestampConversion());
        JsonEncoder encoder = EncoderFactory.get().jsonEncoder(schema, baos);
        writer.write(genericRecord, encoder);
        encoder.flush();
        return baos.toByteArray();
    }

    public static class MyTimestampConversion extends Conversion<DateTime> {
        public MyTimestampConversion() {
        }
        public Class<DateTime> getConvertedType() {
            return DateTime.class;
        }
        public String getLogicalTypeName() {
            return "timestamp-millis";
        }
        public DateTime fromLong(Long millisFromEpoch, Schema schema, LogicalType type) {
            return new DateTime(millisFromEpoch, DateTimeZone.UTC);
        }
        public Long toLong(DateTime dt, Schema schema, LogicalType type) {
            return dt.toInstant().getMillis();
        }
    }
}

