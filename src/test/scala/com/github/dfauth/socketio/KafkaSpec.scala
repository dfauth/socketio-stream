package com.github.dfauth.socketio

import java.awt.event.TextEvent
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives.{getFromResource, getFromResourceDirectory, path, pathPrefix, _}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.github.dfauth.socketio.SocketIoStream.TokenValidator
import com.github.dfauth.socketio.avro.{SpecificRecordDeserializer, SpecificRecordSerializer}
import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.{FlatSpec, Matchers}
import com.github.dfauth.socketio.utils.StreamUtils._
import io.confluent.kafka.schemaregistry.client.{MockSchemaRegistryClient, SchemaRegistryClient}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Success

class KafkaSpec extends FlatSpec
  with Matchers
  with EmbeddedKafka
  with LazyLogging {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  "akka streams" should "allow objects to be streamed to and from kafka preserving the order" in {

    try {

      val schemaRegistryClient = new MockSchemaRegistryClient()

      val topics = system.settings.config.getStringList("kafka.topics")
      val topic = topics.get(0)
      val schemaRegUrl = system.settings.config.getString("schemaRegUrl")

      logger.info(s"using topic ${topic} of declared topics ${topics} using schemaRegUrl: ${schemaRegUrl}")

      val serializer:SpecificRecordSerializer[TestEvent] = SpecificRecordSerializer.Builder
        .builder()
        .withSchemaRegistryClient(schemaRegistryClient)
        .withSchemaRegistryURL(schemaRegUrl) // this will be ignored when setting the mock client above
        .build()

      val envSerializer:SpecificRecordSerializer[Envelope] = SpecificRecordSerializer.Builder
        .builder()
        .withSchemaRegistryClient(schemaRegistryClient)
        .withSchemaRegistryURL(schemaRegUrl) // this will be ignored when setting the mock client above
        .build()

      val deserializer:SpecificRecordDeserializer[TestEvent] = SpecificRecordDeserializer.Builder
        .builder()
        .withSchemaRegistryClient(schemaRegistryClient)
        .withSchemaRegistryURL(schemaRegUrl) // this will be ignored when setting the mock client above
        .build()

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val props = connectionProperties(config)

        val i = new AtomicInteger()

        val envHandler = new EnvelopeHandler[TestEvent](serializer, deserializer)

        val src = Source.tick(ONE_SECOND, ONE_SECOND, () => {
          val e = new TestEvent()
          e.setName(topic)
          e.setTopic(topic)
          e.setPayload(i.get().toString)
          e.setAckId(i.getAndIncrement())
          envHandler.envelope(e)
        }).map(g => g())

        val producer = new org.apache.kafka.clients.producer.KafkaProducer[String, Envelope](props, new StringSerializer, envSerializer)

        val sink = Sink.fromSubscriber(new AbstractBaseSubscriber[Envelope]() {
          override def onNext(e:Envelope): Unit = producer.send(new ProducerRecord[String, Envelope](topic, e))
          override def onComplete(): Unit = {}
        })

        SocketIOServer(schemaRegistryClient)

        src.map {i => {
          logger.info(s"\n\n\n *** WOOZ i: ${i} *** \n\n\n")
          i
        }}.runWith(sink)

        Await.result(system.whenTerminated, Duration.Inf)
      }
    } finally {
      EmbeddedKafka.stop()
    }
  }

}

object SocketIOServer {

  def apply(schemaRegClient: SchemaRegistryClient) = {
    implicit val system: ActorSystem = ActorSystem("socketioService")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val i = new AtomicInteger()
    val flowFactories:Seq[FlowFactory] = Seq(
      new KafkaFlowFactory("/left", "left", schemaRegClient)
    )

    new ServiceLifecycleImpl(system, materializer) {

      override val route: Route = SocketIoStream(system, validator, flowFactories).route ~ static
    }.start()

    def validator:TokenValidator[User] = t => Success(new UserContextImpl(t, User("fred", Seq("user"))))

    def static =
      path("") {
        getFromResource("static/index.html")
      } ~ pathPrefix("") {
        getFromResourceDirectory("static")
      }

  }

}

case class User(name:String, roles:Seq[String] = Seq.empty)
case class UserContextImpl(token:String, payload:User) extends UserContext[User]
