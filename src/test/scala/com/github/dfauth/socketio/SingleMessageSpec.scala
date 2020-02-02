package com.github.dfauth.socketio

import java.util.concurrent.atomic.AtomicLong

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}
import com.github.dfauth.socketio.avro.{SpecificRecordDeserializer, SpecificRecordSerializer}
import com.github.dfauth.socketio.kafka.{KafkaSink, connectionProperties}
import com.github.dfauth.socketio.reactivestreams.ControllingProcessor
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class SingleMessageSpec extends FlatSpec
  with Matchers
  with EmbeddedKafka
  with LazyLogging {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val controller0 = new ControllingProcessor[Envelope]()

  "akka streams" should "allow a single to be streamed to and from kafka preserving the order" in {

    try {

      val topics = system.settings.config.getStringList("kafka.topics")
      val topic0 = topics.get(0)

      logger.info(s"using topic ${topic0} of declared topics ${topics} using schemaRegUrl: ${schemaRegUrl}")

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val props = connectionProperties(config)

        SocketIOServer(schemaRegistryClient)

        val i = new AtomicLong()

        while(true) {
          System.in.read();
          logger.info("running streams...")

          val j = i.incrementAndGet()
          val e = new TestEvent()
          e.setAckId(j)
          e.setName("left")
          e.setPayload(j.toString)

          val src0 = Source.single(envelopeHandler.envelope(e)).via(Flow.fromProcessor(() => controller0))

          val sink0 = KafkaSink(topic0, props, envSerializer)

          src0.runWith(sink0)
        }

        Await.result(system.whenTerminated, Duration.Inf)
      }
    } finally {
      EmbeddedKafka.stop()
    }
  }

  def schemaRegUrl:String = system.settings.config.getString("schemaRegUrl")

  val schemaRegistryClient = new MockSchemaRegistryClient

  val eventSerializer:SpecificRecordSerializer[TestEvent] = SpecificRecordSerializer.Builder
    .builder()
    .withSchemaRegistryClient(schemaRegistryClient)
    .withSchemaRegistryURL(schemaRegUrl) // this will be ignored when setting the mock client above
    .build()

  val eventDeserializer:SpecificRecordDeserializer[TestEvent] = SpecificRecordDeserializer.Builder
    .builder()
    .withSchemaRegistryClient(schemaRegistryClient)
    .withSchemaRegistryURL(schemaRegUrl) // this will be ignored when setting the mock client above
    .build()

  val envelopeHandler = new EnvelopeHandler[TestEvent](eventSerializer, eventDeserializer);

  val envSerializer: SpecificRecordSerializer[Envelope] = SpecificRecordSerializer.Builder
    .builder()
    .withSchemaRegistryClient(schemaRegistryClient)
    .withSchemaRegistryURL(schemaRegUrl) // this will be ignored when setting the mock client above
    .build()
}






