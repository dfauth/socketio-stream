package com.github.dfauth.socketio.kafka

import java.util
import java.util.concurrent.Executors
import java.util.function

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Committer
import akka.kafka.{CommitterSettings, Subscription}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.github.dfauth.auth.AuthenticationContext
import com.github.dfauth.reactivestreams.{CompositeProcessor, QueueSubscriber}
import com.github.dfauth.socketio.{Acknowledgement, CommittableKafkaContext, FlowFactory, KafkaStreamConfig, StreamMessage, StreamService}
import com.github.dfauth.socketio.reactivestreams.QueuePublisher
import com.github.dfauth.socketio.utils.StreamUtils.loggingFn
import com.github.dfauth.socketio.utils.{Ackker, FilteringQueue}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.avro.specific.SpecificRecordBase
import org.reactivestreams.Processor

import scala.compat.java8.FunctionConverters._
import scala.collection.JavaConverters._
import scala.concurrent.Future

case class KafkaFlowFactory(namespace:String, eventId:String, subscription: Subscription, schemaRegClient: SchemaRegistryClient)(implicit system:ActorSystem) extends FlowFactory[User] with LazyLogging {

  def matcher = (c:CommittableKafkaContext[_ <: SpecificRecordBase], t:Acknowledgement) => {
    logger.info(s"matcher match: ${c.ackId == t.ackId} t: ${t} c: ${c.ackId} groupId: ${c.committableOffset.partitionOffset.key.groupId} partition: ${c.committableOffset.partitionOffset.key.partition} offset: ${c.committableOffset.partitionOffset.offset}")
    c.ackId == t.ackId
  }

  def unwrapper[T]: Ackker[T] => T = a => a.payload

  val javaUnWrapper: function.Function[Ackker[CommittableKafkaContext[SpecificRecordBase]], CommittableKafkaContext[SpecificRecordBase]] = unwrapper[CommittableKafkaContext[SpecificRecordBase]].asJava

  val tidied = "[A-Za-z0-9]+".r.findFirstIn(namespace).getOrElse("")

  override def create(ctx: AuthenticationContext[User]) = {

    val bufferSize = ctx.payload.config.getInt("kafka.acknowledgment.buffer.size")

    val ackQ:util.Queue[Ackker[CommittableKafkaContext[SpecificRecordBase]]] = new FilteringQueue[Ackker[CommittableKafkaContext[SpecificRecordBase]]](bufferSize, a =>
      !ctx.payload.config.getBoolean(s"kafka.topics.${tidied}.acknowledge") || a.isAcked)

    implicit val ec = Executors.newSingleThreadScheduledExecutor()


    val sink:Sink[StreamMessage, Future[Done]] = Sink.foreach{ Ackker.process(ackQ.asScala, matcher)}.asInstanceOf[Sink[StreamMessage, Future[Done]]]

    Source.fromPublisher(QueuePublisher(ackQ))
      .map(_.payload.committableOffset)
      .map(loggingFn("processing record "))
      .runWith(Committer.sink(CommitterSettings(system)))

    val brokerList = system.settings.config.getString("bootstrap.servers")

    val in:Source[CommittableKafkaContext[SpecificRecordBase], Any] = StreamService(ctx, brokerList, subscription, schemaRegClient).subscribeSource()

    val qProcessor:Processor[Ackker[CommittableKafkaContext[SpecificRecordBase]], CommittableKafkaContext[SpecificRecordBase]] =
      new CompositeProcessor[Ackker[CommittableKafkaContext[SpecificRecordBase]], CommittableKafkaContext[SpecificRecordBase]](new QueueSubscriber[Ackker[CommittableKafkaContext[SpecificRecordBase]]](ackQ, bufferSize), javaUnWrapper)

    val src = in
      .via(Flow.fromFunction((e:CommittableKafkaContext[SpecificRecordBase]) => Ackker(e)))
      .via(Flow.fromProcessor(() => qProcessor))
      .via(Flow.fromFunction((e:CommittableKafkaContext[_ <: SpecificRecordBase]) => AvroEvent(e.payload, eventId, e.ackId)))

    (sink,src)
  }

}

case class User(name:String, roles:Seq[String] = Seq.empty) {
  val config: Config = KafkaStreamConfig(ConfigFactory.load()).getContextConfig(s"prefs.${name}")
}

