package com.github.dfauth.socketio

import java.util.concurrent.CompletionStage

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.japi.function.Function
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscription, Subscriptions}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.github.dfauth.socketio.avro.{SpecificRecordDeserializer, SpecificRecordSerializer}
import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag
import com.github.dfauth.socketio.Functions.asyncUnwrapper

trait StreamService[T<: SpecificRecordBase] {
  def subscribeSource()(implicit system: ActorSystem): Source[WithKafkaContext[T], Consumer.Control]
  def subscribeSink(): Sink[java.lang.Long, Future[Done]]
  def subscribeFlow()(implicit system: ActorSystem): Flow[java.lang.Long, WithKafkaContext[T], NotUsed]
}

class StreamServiceImpl[T <: SpecificRecordBase](consumerSettings: ConsumerSettings[String, Envelope], subscription: Subscription, envelopeHandler: EnvelopeHandler[T]) extends StreamService[T] with LazyLogging {

  val NO_MESSAGE: String = "no-message"

  def subscribeSource()(implicit system: ActorSystem): Source[WithKafkaContext[T], Consumer.Control] = {
    system.log.info(s"starting the subscription.")
    val source: Source[WithKafkaContext[T], Consumer.Control] = Consumer.plainSource(consumerSettings, subscription).asJava.
      mapAsync[WithKafkaContext[T]](1, asyncUnwrapper(envelopeHandler))
      //      mapAsync(1, r => Future {
      //        authorize(r) match {
      //          case Some(event) => event
      //          case None => NO_MESSAGE
      //        }
      //      }).filterNot(_ == NO_MESSAGE).
      //      mapAsync(1)(r => Future {
      //        scrub(r)
      //      })
      .map { e =>
      logger.error(s"WOOZ ${e}")
      e
    }
      .buffer(1024, OverflowStrategy.dropHead).asScala
    source
  }

  def subscribeSink(): Sink[java.lang.Long, Future[Done]] = {
    Sink.foreach[java.lang.Long]( l =>
      logger.info(s"subscribeSink got offset: $l")
    )
  }

  def subscribeFlow()(implicit system: ActorSystem): Flow[java.lang.Long, WithKafkaContext[T], NotUsed] = Flow.fromSinkAndSource(subscribeSink(), subscribeSource())

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
  def apply[T <: SpecificRecordBase](brokerList: String)(implicit system: ActorSystem):StreamService[T] = {

    val schemaRegUrl = system.settings.config.getString("schemaRegUrl")
    val deserializer:SpecificRecordDeserializer[Envelope] = SpecificRecordDeserializer.Builder
      .builder()
      .withSchemaRegistryURL(schemaRegUrl)
      .build()

    val serializer:SpecificRecordSerializer[T] = SpecificRecordSerializer.Builder
      .builder()
      .withSchemaRegistryURL(schemaRegUrl)
      .build()

    val topics: Set[String] = system.settings.config.getStringList("kafka.topics").asScala.toSet
    lazy val subscription: Subscription = Subscriptions.topics(topics)
    val stringDeserializer: Deserializer[String] = new StringDeserializer
    val envelopeHandler = new EnvelopeHandler[T](serializer, deserializer.asInstanceOf[SpecificRecordDeserializer[T]])

    def consumerSettings: ConsumerSettings[String, Envelope] = ConsumerSettings(system, stringDeserializer, deserializer)
      .withBootstrapServers(brokerList)
      .withGroupId("akka_streams_group")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

    new StreamServiceImpl[T](consumerSettings, subscription, envelopeHandler)
  }
}