package com.github.dfauth.socketio

import com.github.dfauth.socketio.avro.AvroUtils
import org.apache.avro.specific.SpecificRecordBase

case class Blah(ackId:Long) extends Event with Acknowledgement {
  val eventId:String = "left"
  override def toString: String = ackId.toString
}
case class BlahChar(c:Char, ackId:Long) extends Event with Acknowledgement {
  val eventId:String = "right"
  override def toString: String = s""""${c.toString}""""
}

case class BlahString(msg:String, ackId:Long) extends Event with Acknowledgement {
  val eventId:String = "ack"
  override def toString: String = s""""${msg} ack id: ${ackId}""""
}

case class BlahObject[T <: SpecificRecordBase](t:T, eventId:String, ackId:Long) extends Event with Acknowledgement{
  override def toString: String = new String(AvroUtils.toByteArray(t.getSchema, t))
}



