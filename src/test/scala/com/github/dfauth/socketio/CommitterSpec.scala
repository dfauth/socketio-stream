package com.github.dfauth.socketio

import java.util
import java.util.concurrent._
import java.util.concurrent.atomic.{AtomicLong}

import akka.{NotUsed}
import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffset}
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.kafka._
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import com.github.dfauth.socketio.kafka.{KafkaSink, OffsetAndMetadata, OffsetKey, OffsetKeyDeserializer, OffsetValueDeserializer}
import com.github.dfauth.socketio.reactivestreams.{QueuePublisher}
import com.github.dfauth.socketio.utils.{Ackker, FilteringQueue}
import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.{FlatSpec, Matchers}
import com.github.dfauth.socketio.utils.StreamUtils._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{Deserializer, LongDeserializer, LongSerializer, Serializer, StringDeserializer}

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.collection.JavaConverters._

class CommitterSpec extends FlatSpec
  with Matchers
  with EmbeddedKafka
  with LazyLogging {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  type KafkaRecord = Tuple3[CommittableOffset, String, Long]

  "akka streams" should "allow support committing the offset" in {

    try {

      val topic = "topic"

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val props = connectionProperties(config)

        val brokerList = system.settings.config.getString("bootstrap.servers")
        val groupId = system.settings.config.getString("kafka.consumer.groupId")
        val offsetReset = system.settings.config.getString("kafka.consumer.auto.offset.reset")

        val s = new LongSerializer
        val serializer:Serializer[Long] = (t,l) => s.serialize(t,l)
        val d = new LongDeserializer
        val deserializer:Deserializer[Long] = (t,l) => d.deserialize(t,l)
        def consumerSettings: ConsumerSettings[String, Long] = ConsumerSettings(system, new StringDeserializer, deserializer)
          .withBootstrapServers(brokerList)
          .withGroupId(groupId)
          .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetReset)

        def offsetConsumerSettings: ConsumerSettings[OffsetKey, OffsetAndMetadata] = ConsumerSettings(system, new OffsetKeyDeserializer, new OffsetValueDeserializer(groupId))
          .withBootstrapServers(brokerList)
          .withGroupId(groupId)
          .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetReset)

        val subscription = Subscriptions.topics("topic")
        val offsetSubscription = Subscriptions.topics("__consumer_offsets")

        val source = Consumer.committableSource(consumerSettings, subscription).buffer(1024, OverflowStrategy.dropHead)
        val offsetSource = Consumer.plainSource(offsetConsumerSettings, offsetSubscription).buffer(1024, OverflowStrategy.dropHead)
        offsetSource.runWith(loggingSink("offset record: "))

        val q = new util.ArrayDeque[CommittableMessage[String,Long]](100)

        val ackQ = new FilteringQueue[Ackker[CommittableMessage[String, Long]]](100, a => a.isAcked)

        source
          .map {Ackker.enqueueFn(ackQ)}.runWith(Sink.foreach((t:CommittableMessage[String, Long]) => {
          Future {
            Thread.sleep((Math.random()*1500).toInt)
            q.offer(t)
          }
        }))

        implicit val ec = Executors.newSingleThreadScheduledExecutor()

        Source.fromPublisher(QueuePublisher(ackQ))
          .map(_.payload.committableOffset)
          .map(loggingFn("processing record "))
          .runWith(Committer.sink(CommitterSettings(system)))

        val i = new AtomicLong()

        type SupplierOfLong = () => Long
        val f:SupplierOfLong = () => i.getAndIncrement()

        val sink = KafkaSink("topic", props, serializer)

        Source.tick(ONE_SECOND, ONE_SECOND, f).map{f => f()}.runWith(sink)

        val backSrc:Source[CommittableMessage[String, Long], NotUsed] = Source.fromPublisher(QueuePublisher(q))

        backSrc.runWith( Sink.foreach { Ackker.process(ackQ.asScala)})

        Await.result(system.whenTerminated, secondsOf(20))
      }
    } finally {
      EmbeddedKafka.stop()
    }
  }
}