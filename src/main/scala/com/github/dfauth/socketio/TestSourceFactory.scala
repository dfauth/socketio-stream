package com.github.dfauth.socketio

import akka.actor.{ActorSystem, Cancellable}
import akka.stream.scaladsl.Source
import com.github.dfauth.socketio.avro.AvroUtils
import com.github.dfauth.socketio.utils.StreamUtils
import com.github.dfauth.socketio.utils.StreamUtils._
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.specific.SpecificRecordBase

import scala.concurrent.duration._

case class Blah(ackId:Long) extends Ackable with Eventable {
  val eventId:String = "left"
  override def toString: String = ackId.toString
}
case class BlahChar(c:Char, ackId:Long) extends Ackable with Eventable {
  val eventId:String = "right"
  override def toString: String = s""""${c.toString}""""
}

case class BlahString(msg:String, ackId:Long) extends Ackable with Eventable {
  val eventId:String = "ack"
  override def toString: String = s""""${msg} ack id: ${ackId}""""
}

case class BlahObject[T <: SpecificRecordBase](t:T, eventId:String, ackId:Long) extends Ackable with Eventable {
  override def toString: String = new String(AvroUtils.toByteArray(t.getSchema, t))
}

case class TestSourceFactory(namespace:String, f:()=>Ackable with Eventable) extends SourceFactory {

  override def create[T >: Ackable with Eventable]: Source[T, Cancellable] = {

    Source.tick(ONE_SECOND, ONE_SECOND, f).map {g => g() }
  }
}

case class TestFlowFactory(namespace:String, f:()=>Ackable with Eventable, delay:FiniteDuration) extends FlowFactory {

  override def create[T >: Ackable with Eventable, U](ctx: UserContext[U]) = {
    val a = StreamUtils.loggingSink[T](s"\n\n *** ${namespace} *** \n\n received: ")
    val b = Source.tick(delay, delay, f).map {g => g() }
    (a,b)
  }
}

case class KafkaFlowFactory(namespace:String, eventId:String, schemaRegClient: SchemaRegistryClient)(implicit system:ActorSystem) extends FlowFactory {

  override def create[T >: Ackable with Eventable,U](ctx: UserContext[U]) = {
    val a = StreamUtils.loggingSink[T](s"\n\n *** ${namespace} *** \n\n received: ")

    val brokerList = system.settings.config.getString("bootstrap.servers")
    val b = StreamService(brokerList, schemaRegClient).subscribeSource().map((e:KafkaContext[_ <: SpecificRecordBase]) => BlahObject(e.payload, eventId, e.offset))
    (a,b)
  }
}
