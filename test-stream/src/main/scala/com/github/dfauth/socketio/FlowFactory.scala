package com.github.dfauth.socketio

import com.github.dfauth.socketio.avro.AvroUtils
import com.github.dfauth.socketio.utils.StreamUtils._
import org.apache.avro.specific.SpecificRecordBase

import scala.concurrent.duration._
import akka.stream.scaladsl.{Flow, Source}
import com.github.dfauth.auth.AuthenticationContext


case class LongEvent(ackId:Long) extends Event {
  val eventId:String = "left"
  override def toString: String = ackId.toString
}
case class CharEvent(c:Char, ackId:Long) extends Event {
  val eventId:String = "right"
  override def toString: String = s""""${c.toString}""""
}

case class StringEvent(msg:String, ackId:Long) extends Event with Acknowledgement {
  val eventId:String = "ack"
  override def toString: String = s""""${msg} ack id: ${ackId}""""
}

case class AvroEvent[T <: SpecificRecordBase](t:T, eventId:String, ackId:Long) extends Event {
  override def toString: String = new String(AvroUtils.toByteArray(t.getSchema, t))
}

case class TestFlowFactory[U](namespace:String, f:()=>Event, delay:FiniteDuration) extends FlowFactory[U] {

  override def create(ctx: AuthenticationContext[U]) = {
    val a = loggingSink[StreamMessage](s"\n\n *** ${namespace} *** \n\n received: ")
    val b = Source.tick(delay, delay, f).map {g => g() }
    Flow.fromSinkAndSource(a,b)
  }
}

