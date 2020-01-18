package com.github.dfauth.socketio

import org.apache.avro.specific.SpecificRecordBase

case class WithKafkaContext[T <: SpecificRecordBase](topic:String, offset:Long, payload:T)
