package com.github.dfauth.socketio

import java.util
import java.util.concurrent.Executors

import akka.{Done, NotUsed}
import akka.actor.{ActorSystem, Cancellable}
import akka.kafka.{CommitterSettings, Subscription}
import akka.kafka.scaladsl.Committer
import akka.stream.scaladsl.{Sink, Source}
import com.github.dfauth.reactivestreams.QueueSubscriber
import com.github.dfauth.socketio.avro.AvroUtils
import com.github.dfauth.socketio.reactivestreams.QueuePublisher
import com.github.dfauth.socketio.reactivestreams.Processors._
import com.github.dfauth.socketio.utils.{Ackker, BranchingGraph, FilteringQueue, SplittingGraph, StreamUtils}
import com.github.dfauth.socketio.utils.StreamUtils._
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.specific.SpecificRecordBase

import scala.concurrent.Future
import scala.concurrent.duration._
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

case class KafkaFlowFactory(namespace:String, eventId:String, subscription: Subscription, schemaRegClient: SchemaRegistryClient)(implicit system:ActorSystem) extends FlowFactory {

  def matcher[T <: Ackable with Eventable] = (c:CommittableKafkaContext[_ <: SpecificRecordBase], t:T) => c.ackId == t.ackId

  override def create[U](ctx: UserContext[U]) = {

    val ackQ:util.Queue[Ackker[CommittableKafkaContext[_ <: SpecificRecordBase]]] = new FilteringQueue[Ackker[CommittableKafkaContext[_ <: SpecificRecordBase]]](100, a => a.isAcked)
    implicit val ec = Executors.newSingleThreadScheduledExecutor()


    val sink:Sink[Ackable with Eventable, Future[Done]] = Sink.foreach{ Ackker.process(() => ackQ.asScala, matcher)}

    Source.fromPublisher(QueuePublisher(ackQ))
      .map(_.payload.committableOffset)
      .map(loggingFn("processing record "))
      .runWith(Committer.sink(CommitterSettings(system)))

    val brokerList = system.settings.config.getString("bootstrap.servers")

//    val src = StreamService(brokerList, subscription, schemaRegClient)
//      .subscribeSource()
//      .map(Ackker.enqueueFn(ackQ))
//      .map((e:CommittableKafkaContext[_ <: SpecificRecordBase]) => BlahObject(e.payload, eventId, e.ackId))


    val src = sourceFromSinkConsumer[CommittableKafkaContext[SpecificRecordBase]](s => {
      SplittingGraph(
        StreamService[SpecificRecordBase](brokerList, subscription, schemaRegClient).subscribeSource(),
        mapSink((e:CommittableKafkaContext[SpecificRecordBase]) => Ackker(e), Sink.fromSubscriber(new QueueSubscriber(ackQ, 100))),
        s
      )
    })
    .map((e:CommittableKafkaContext[_ <: SpecificRecordBase]) => BlahObject(e.payload, eventId, e.ackId))
    (sink,src)
  }
}
