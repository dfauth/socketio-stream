package com.github.dfauth.socketio

import java.util
import java.util.concurrent.Executors
import java.util.function

import akka.Done
import akka.actor.{ActorSystem, Cancellable}
import akka.kafka.{CommitterSettings, Subscription}
import akka.kafka.scaladsl.Committer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.github.dfauth.reactivestreams.{QueueProcessor, QueueSubscriber}
import com.github.dfauth.socketio.avro.AvroUtils
import com.github.dfauth.socketio.reactivestreams.{Processors, QueuePublisher}
import com.github.dfauth.socketio.utils.{Ackker, FilteringQueue, SplittingGraph, StreamUtils}
import com.github.dfauth.socketio.utils.StreamUtils._
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.specific.SpecificRecordBase
import org.reactivestreams.Processor

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.compat.java8.FunctionConverters._
import scala.collection.JavaConverters._

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

  override def create[U](ctx: UserContext[U]) = {
    val a = StreamUtils.loggingSink[Ackable with Eventable](s"\n\n *** ${namespace} *** \n\n received: ")
    val b = Source.tick(delay, delay, f).map {g => g() }
    (a,b)
  }
}

case class KafkaFlowFactory(namespace:String, eventId:String, subscription: Subscription, schemaRegClient: SchemaRegistryClient)(implicit system:ActorSystem) extends FlowFactory with LazyLogging {

  def matcher[T <: Ackable with Eventable] = (c:CommittableKafkaContext[_ <: SpecificRecordBase], t:T) => {
    logger.info(s" WOOZ match: ${c.ackId == t.ackId} t: ${t} c: ${c.ackId} groupId: ${c.committableOffset.partitionOffset.key.groupId} partition: ${c.committableOffset.partitionOffset.key.partition} offset: ${c.committableOffset.partitionOffset.offset}")
    c.ackId == t.ackId
  }

  def unwrapper[T]: Ackker[T] => T = a => a.payload

  val javaUnWrapper: function.Function[Ackker[CommittableKafkaContext[SpecificRecordBase]], CommittableKafkaContext[SpecificRecordBase]] = unwrapper[CommittableKafkaContext[SpecificRecordBase]].asJava

  override def create[U](ctx: UserContext[U]) = {

    val ackQ:util.Queue[Ackker[CommittableKafkaContext[SpecificRecordBase]]] = new FilteringQueue[Ackker[CommittableKafkaContext[SpecificRecordBase]]](100, a => a.isAcked)
    implicit val ec = Executors.newSingleThreadScheduledExecutor()


    val sink:Sink[Ackable with Eventable, Future[Done]] = Sink.foreach{ Ackker.process(ackQ.asScala, matcher)}

    Source.fromPublisher(QueuePublisher(ackQ))
      .map(_.payload.committableOffset)
      .map(loggingFn("processing record "))
      .runWith(Committer.sink(CommitterSettings(system)))

    val brokerList = system.settings.config.getString("bootstrap.servers")

    val in:Source[CommittableKafkaContext[SpecificRecordBase], Any] = StreamService(brokerList, subscription, schemaRegClient).subscribeSource()

    val qProcessor:Processor[Ackker[CommittableKafkaContext[SpecificRecordBase]], CommittableKafkaContext[SpecificRecordBase]] =
      new QueueProcessor[Ackker[CommittableKafkaContext[SpecificRecordBase]], CommittableKafkaContext[SpecificRecordBase]](ackQ, 100, javaUnWrapper)

    val src = in.map(loggingFn("WOOZ0"))
      .via(Flow.fromFunction((e:CommittableKafkaContext[SpecificRecordBase]) => Ackker(e)).map(loggingFn("WOOZ1"))
      .via(Flow.fromProcessor(() => qProcessor).map(loggingFn("WOOZ2"))
      .via(Flow.fromFunction((e:CommittableKafkaContext[_ <: SpecificRecordBase]) => BlahObject(e.payload, eventId, e.ackId)).map(loggingFn("WOOZ3")))))

    (sink,src)
  }
}
