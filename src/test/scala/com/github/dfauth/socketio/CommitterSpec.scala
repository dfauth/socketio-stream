package com.github.dfauth.socketio

import java.util
import java.util.concurrent._
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong}

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffset}
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.kafka._
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import com.github.dfauth.socketio.kafka.{KafkaSink, OffsetAndMetadata, OffsetKey, OffsetKeyDeserializer, OffsetValueDeserializer}
import com.github.dfauth.socketio.reactivestreams.{QueuePublisher, Throttlers, ThrottlingSubscriber}
import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.{FlatSpec, Matchers}
import com.github.dfauth.socketio.utils.StreamUtils._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{Deserializer, LongDeserializer, LongSerializer, Serializer, StringDeserializer}

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

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
        offsetSource.runWith(loggingSink("WOOZ2"))

        val q = new util.ArrayDeque[CommittableMessage[String,Long]](100)

        val ackQ = new CustomQueue[Ackker[CommittableMessage[String, Long], String, Long]](100, a => a.isAcked)

        source
          .map {r => {
            ackQ.offer(Ackker(r))
            r
          }}.runWith(Sink.foreach((t:CommittableMessage[String, Long]) => {
          Future {
            Thread.sleep((Math.random()*1500).toInt)
            q.offer(t)
          }
        }))

        implicit val ec = Executors.newSingleThreadScheduledExecutor()

        Source.fromPublisher(QueuePublisher(ackQ))
          .map(a =>
            a.payload.committableOffset
          )
          .map(loggingFn("processing record "))
          .runWith(Committer.sink(CommitterSettings(system)))

        val i = new AtomicLong()

        type SupplierOfLong = () => Long
        val f:SupplierOfLong = () => i.getAndIncrement()

        val sink = KafkaSink("topic", props, serializer)

        Source.tick(ONE_SECOND, ONE_SECOND, f).map{f => f()}.runWith(sink)

        val backSrc:Source[CommittableMessage[String, Long], NotUsed] = Source.fromPublisher(QueuePublisher(q))

        backSrc.runWith( Sink.foreach{ kr =>
          val found = new AtomicBoolean(true)
          ackQ.stream().filter(a => {
            found.get && a.matches(kr)
          }).forEach(a => {
            a.ack
            found.set(false)
          })
          if(found.get) {
            logger.error(s"failed to find record ${kr}")
          }
        })

//        backSrc.runWith(loggingSink(" WOOZ this is the back channel"))

//        backSrc.runWith(ThrottlingSubscriber.sink(Throttlers.fixed(5, loggingConsumer[KafkaRecord](s"WOOZ this is the back channel"))))

        Await.result(system.whenTerminated, secondsOf(20))
      }
    } finally {
      EmbeddedKafka.stop()
    }
  }
}

case class Ackker[T <: CommittableMessage[K,V], K, V](t:T, acked:AtomicBoolean = new AtomicBoolean(false)) {
  def ack:Unit = acked.set(true)
  def isAcked:Boolean = acked.get()
  def payload:T = t
  def matches(t1:T): Boolean = t.equals(t1)
  override def toString: String = s"Ackker(${t.committableOffset.partitionOffset.offset}, ${acked.get()})"
}

class CustomQueue[T](capacity:Int, f:T=>Boolean) extends util.ArrayDeque[T](capacity) with LazyLogging {
  override def poll(): T = Option(super.peek()).filter(e => f(e)).map(_ => super.poll()).getOrElse(null).asInstanceOf[T]
}