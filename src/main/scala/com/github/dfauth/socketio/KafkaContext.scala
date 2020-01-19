package com.github.dfauth.socketio

import org.apache.avro.specific.SpecificRecordBase

case class KafkaContext[T <: SpecificRecordBase](topic:String, offset:Long, payload:T)
