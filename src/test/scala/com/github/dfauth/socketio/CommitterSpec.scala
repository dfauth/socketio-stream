package com.github.dfauth.socketio

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicLong

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer
import akka.kafka._
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import com.github.dfauth.socketio.kafka.{KafkaSink, OffsetAndMetadata, OffsetKey, OffsetKeyDeserializer, OffsetValueDeserializer}
import com.github.dfauth.socketio.utils.{QueuePublisher, StreamUtils}
import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.{FlatSpec, Matchers}
import com.github.dfauth.socketio.utils.StreamUtils._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{LongDeserializer, LongSerializer, StringDeserializer}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

class CommitterSpec extends FlatSpec
  with Matchers
  with EmbeddedKafka
  with LazyLogging {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  type KafkaRecord = Tuple4[String, Int, Long, java.lang.Long]

  "akka streams" should "allow support committing the offset" in {

    try {

      val topic = "topic"

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val props = connectionProperties(config)

        val brokerList = system.settings.config.getString("bootstrap.servers")
        val groupId = system.settings.config.getString("kafka.consumer.groupId")
        val offsetReset = system.settings.config.getString("kafka.consumer.auto.offset.reset")

        def consumerSettings: ConsumerSettings[String, java.lang.Long] = ConsumerSettings(system, new StringDeserializer, new LongDeserializer)
          .withBootstrapServers(brokerList)
          .withGroupId(groupId)
          .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetReset)

        def offsetConsumerSettings: ConsumerSettings[OffsetKey, OffsetAndMetadata] = ConsumerSettings(system, new OffsetKeyDeserializer, new OffsetValueDeserializer(groupId))
          .withBootstrapServers(brokerList)
          .withGroupId(groupId)
          .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetReset)

        val subscription = Subscriptions.topics("topic")
        val offsetSubscription = Subscriptions.topics("__consumer_offsets")

        val source = Consumer.plainSource(consumerSettings, subscription).buffer(1024, OverflowStrategy.dropHead)
        val offsetSource = Consumer.plainSource(offsetConsumerSettings, offsetSubscription).buffer(1024, OverflowStrategy.dropHead)
        offsetSource.runWith(loggingSink("WOOZ1"))

        val q = new ArrayBlockingQueue[KafkaRecord](100)

        source.map {r => (r.topic(), r.partition(), r.offset(), r.value())}
          .runWith(Sink.fromSubscriber(
            StreamUtils.fromConsumer( (t:KafkaRecord) =>
              Future {
                Thread.sleep((Math.random()*1500).toInt)
                q.offer(t)
              }
            )))

        val i = new AtomicLong()

        type SupplierOfLong = () => java.lang.Long
        val f:SupplierOfLong = () => i.getAndIncrement()

        val sink = KafkaSink("topic", props, new LongSerializer)

        Source.tick(ONE_SECOND, ONE_SECOND, f).map{f => f()}.runWith(sink)

        val qPub = QueuePublisher(q)
        val backSrc:Source[KafkaRecord, NotUsed] = Source.fromPublisher(qPub)



        backSrc.runWith(loggingSink(" WOOZ this is the back channel"))
        Future {
          qPub.start
        }

        Await.result(system.whenTerminated, Duration.Inf)
      }
    } finally {
      EmbeddedKafka.stop()
    }
  }
}
