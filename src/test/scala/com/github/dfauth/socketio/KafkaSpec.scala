package com.github.dfauth.socketio

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.util.function.Supplier

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives.{getFromResource, getFromResourceDirectory, path, pathPrefix, _}
import akka.http.scaladsl.server.Route
import akka.kafka.Subscriptions
import akka.stream.scaladsl.Flow
import akka.stream.{ActorMaterializer, FlowShape, Graph}
import com.github.dfauth.socketio.SocketIoStream.TokenValidator
import com.github.dfauth.socketio.avro.{SpecificRecordDeserializer, SpecificRecordSerializer}
import com.github.dfauth.socketio.kafka.KafkaSink
import com.github.dfauth.socketio.reactivestreams.ControllingProcessor
import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.{FlatSpec, Matchers}
import com.github.dfauth.socketio.utils.StreamUtils._
import com.typesafe.config.{Config, ConfigFactory}
import io.confluent.kafka.schemaregistry.client.{MockSchemaRegistryClient, SchemaRegistryClient}
import org.joda.time.DateTime

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Success

class KafkaSpec extends FlatSpec
  with Matchers
  with EmbeddedKafka
  with LazyLogging {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val controller0 = new ControllingProcessor[Envelope]()
  val controller1 = new ControllingProcessor[Envelope]()

  "akka streams" should "allow objects to be streamed to and from kafka preserving the order" in {

    try {

      val topics = system.settings.config.getStringList("kafka.topics")
      val topic0 = topics.get(0)
      val topic1 = topics.get(1)

      logger.info(s"using topic ${topic0} of declared topics ${topics} using schemaRegUrl: ${schemaRegUrl}")

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val props = connectionProperties(config)

        val src0 = tickingSupplyOf(testEventSupplier(topic0)).via(Flow.fromProcessor(() => controller0))
        val src1 = tickingSupplyOf(anotherTestEventSupplier(topic1), secondsOf(0.917)).via(Flow.fromProcessor(() => controller1))

        val sink0 = KafkaSink(topic0, props, envSerializer)
        val sink1 = KafkaSink(topic1, props, envSerializer)

        SocketIOServer(schemaRegistryClient)

        System.in.read();
        logger.info("running streams...")

        src0.runWith(sink0)
        src1.runWith(sink1)

        System.in.read();
        logger.info("toggling...")
        controller0.off
        controller1.off

        Await.result(system.whenTerminated, Duration.Inf)
      }
    } finally {
      EmbeddedKafka.stop()
    }
  }

  def schemaRegUrl:String = system.settings.config.getString("schemaRegUrl")

  val schemaRegistryClient = new MockSchemaRegistryClient

  val envSerializer:SpecificRecordSerializer[Envelope] = SpecificRecordSerializer.Builder
    .builder()
    .withSchemaRegistryClient(schemaRegistryClient)
    .withSchemaRegistryURL(schemaRegUrl) // this will be ignored when setting the mock client above
    .build()

  def testEventSupplier(topic:String): Supplier[Envelope] = {

    val serializer:SpecificRecordSerializer[TestEvent] = SpecificRecordSerializer.Builder
      .builder()
      .withSchemaRegistryClient(schemaRegistryClient)
      .withSchemaRegistryURL(schemaRegUrl) // this will be ignored when setting the mock client above
      .build()

    val deserializer:SpecificRecordDeserializer[TestEvent] = SpecificRecordDeserializer.Builder
      .builder()
      .withSchemaRegistryClient(schemaRegistryClient)
      .withSchemaRegistryURL(schemaRegUrl) // this will be ignored when setting the mock client above
      .build()

    val envHandler = new EnvelopeHandler[TestEvent](serializer, deserializer)

    val i = new AtomicLong()

    () => {
      val e = new TestEvent()
      e.setName(topic)
      e.setPayload(('A'.toInt + i.incrementAndGet()%26).toChar.toString)
      e.setAckId(i.getAndIncrement())
      envHandler.envelope(e)
    }
  }

  def anotherTestEventSupplier(topic:String): Supplier[Envelope] = {

    val serializer:SpecificRecordSerializer[AnotherTestEvent] = SpecificRecordSerializer.Builder
      .builder()
      .withSchemaRegistryClient(schemaRegistryClient)
      .withSchemaRegistryURL(schemaRegUrl) // this will be ignored when setting the mock client above
      .build()

    val deserializer:SpecificRecordDeserializer[AnotherTestEvent] = SpecificRecordDeserializer.Builder
      .builder()
      .withSchemaRegistryClient(schemaRegistryClient)
      .withSchemaRegistryURL(schemaRegUrl) // this will be ignored when setting the mock client above
      .build()

    val envHandler = new EnvelopeHandler[AnotherTestEvent](serializer, deserializer)

    val i = new AtomicLong()

    () => {
      val e = new AnotherTestEvent()
      e.setName(topic)
      e.setPayload(i.get())
      e.setAckId(i.getAndIncrement())
      e.setTs(DateTime.now())
      envHandler.envelope(e)
    }
  }

}

object SocketIOServer {

  def apply(schemaRegClient: SchemaRegistryClient) = {
    implicit val system: ActorSystem = ActorSystem("socketioService")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val i = new AtomicInteger()
    val topic0 = Subscriptions.topics(Set("left"))
    val topic1 = Subscriptions.topics(Set("right"))
    val flowFactories:Seq[FlowFactory] = Seq(
      new KafkaFlowFactory("/left", "left", topic0, schemaRegClient)
//      new KafkaFlowFactory("/right", "right", topic1, schemaRegClient)
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
case class UserContextImpl(token:String, payload:User) extends UserContext[User] {
  override val config: Config = SocketIOConfig(ConfigFactory.load()).getContextConfig(s"prefs.${payload.name}")
  override def userId: String = payload.name
}
