package com.github.dfauth.socketio

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscription}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.github.dfauth.socketio.avro.{SpecificRecordDeserializer, SpecificRecordSerializer}
import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}

import scala.concurrent.Future
import com.github.dfauth.socketio.utils.Functions._
import scala.compat.java8.FutureConverters._
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient

trait StreamService[T<: SpecificRecordBase] {
  def subscribeSource()(implicit system: ActorSystem): Source[CommittableKafkaContext[T], Any]
  def subscribeSink(): Sink[java.lang.Long, Future[Done]]
  def subscribeFlow()(implicit system: ActorSystem): Flow[java.lang.Long, CommittableKafkaContext[T], NotUsed]
}

class StreamServiceImpl[T <: SpecificRecordBase](consumerSettings: ConsumerSettings[String, Envelope], subscription: Subscription, envelopeHandler: EnvelopeHandler[T]) extends StreamService[T] with LazyLogging {

  def subscribeSource()(implicit system: ActorSystem): Source[CommittableKafkaContext[T], Any] = {
    system.log.info(s"starting the subscription.")
    val source: Source[CommittableKafkaContext[T], Any] = Consumer.committableSource(consumerSettings, subscription).
      mapAsync[CommittableKafkaContext[T]](1)(committableAsyncUnwrapper(envelopeHandler)(_).toScala)
      .buffer(1024, OverflowStrategy.dropHead)
    source
  }

  def subscribeSink(): Sink[java.lang.Long, Future[Done]] = {
    Sink.foreach[java.lang.Long]( l =>
      logger.info(s"subscribeSink got offset: $l")
    )
  }

  def subscribeFlow()(implicit system: ActorSystem): Flow[java.lang.Long, CommittableKafkaContext[T], NotUsed] = Flow.fromSinkAndSource(subscribeSink(), subscribeSource())

  // need to be implemented as per authorization
  private def authorize(r: String): Option[String] = {
    Some(r)
  }

  private def scrub(r: String): String = {
    logger.info(s"scrub($r)")
    r
  }
}

object StreamService {
  def apply[T <: SpecificRecordBase](brokerList: String, subscription: Subscription, schemaRegClient: SchemaRegistryClient)(implicit system: ActorSystem):StreamService[T] = {

    val schemaRegUrl = system.settings.config.getString("schemaRegUrl")
    val deserializer:SpecificRecordDeserializer[Envelope] = SpecificRecordDeserializer.Builder
      .builder()
      .withSchemaRegistryClient(schemaRegClient)
      .withSchemaRegistryURL(schemaRegUrl)
      .build()

    val serializer:SpecificRecordSerializer[T] = SpecificRecordSerializer.Builder
      .builder()
      .withSchemaRegistryURL(schemaRegUrl)
      .build()

    val stringDeserializer: Deserializer[String] = new StringDeserializer
    val envelopeHandler = new EnvelopeHandler[T](serializer, deserializer.asInstanceOf[SpecificRecordDeserializer[T]])

    def consumerSettings: ConsumerSettings[String, Envelope] = ConsumerSettings(system, stringDeserializer, deserializer)
      .withBootstrapServers(brokerList)
      .withGroupId("akka_streams_group")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

    new StreamServiceImpl[T](consumerSettings, subscription, envelopeHandler)
  }
}