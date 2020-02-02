package com.github.dfauth.socketio.kafka

import com.github.dfauth.socketio.avro.AvroUtils
import com.github.dfauth.socketio.{Acknowledgement, Event}
import org.apache.avro.specific.SpecificRecordBase

case class LongEvent(ackId:Long) extends Event with Acknowledgement {
  val eventId:String = "left"
  override def toString: String = ackId.toString
}
case class CharEvent(c:Char, ackId:Long) extends Event with Acknowledgement {
  val eventId:String = "right"
  override def toString: String = s""""${c.toString}""""
}

case class StringEvent(msg:String, ackId:Long) extends Event with Acknowledgement {
  val eventId:String = "ack"
  override def toString: String = s""""${msg} ack id: ${ackId}""""
}

case class AvroEvent[T <: SpecificRecordBase](t:T, eventId:String, ackId:Long) extends Event with Acknowledgement{
  override def toString: String = new String(AvroUtils.toByteArray(t.getSchema, t))
}



