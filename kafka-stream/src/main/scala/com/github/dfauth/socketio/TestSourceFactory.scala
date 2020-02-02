package com.github.dfauth.socketio

import akka.actor.{Cancellable}
import akka.stream.scaladsl.{Source}
import com.github.dfauth.socketio.avro.AvroUtils
import com.github.dfauth.socketio.utils.{StreamUtils}
import com.github.dfauth.socketio.utils.StreamUtils._
import org.apache.avro.specific.SpecificRecordBase
import scala.concurrent.duration._

case class Blah(ackId:Long) extends Eventable {
  val eventId:String = "left"
  override def toString: String = ackId.toString
}
case class BlahChar(c:Char, ackId:Long) extends Eventable {
  val eventId:String = "right"
  override def toString: String = s""""${c.toString}""""
}

case class BlahString(msg:String, ackId:Long) extends Eventable with Ackable {
  val eventId:String = "ack"
  override def toString: String = s""""${msg} ack id: ${ackId}""""
}

case class BlahObject[T <: SpecificRecordBase](t:T, eventId:String, ackId:Long) extends Eventable {
  override def toString: String = new String(AvroUtils.toByteArray(t.getSchema, t))
}

case class TestSourceFactory(namespace:String, f:()=>Eventable) extends SourceFactory {

  override def create[T >: Eventable]: Source[T, Cancellable] = {

    Source.tick(ONE_SECOND, ONE_SECOND, f).map {g => g() }
  }
}

case class TestFlowFactory(namespace:String, f:()=>Eventable, delay:FiniteDuration) extends FlowFactory {

  override def create[U](ctx: UserContext[U]) = {
    val a = StreamUtils.loggingSink[Ackable](s"\n\n *** ${namespace} *** \n\n received: ")
    val b = Source.tick(delay, delay, f).map {g => g() }
    (a,b)
  }
}


