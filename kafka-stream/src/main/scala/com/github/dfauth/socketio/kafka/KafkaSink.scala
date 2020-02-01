package com.github.dfauth.socketio.kafka

import java.util.Properties

import akka.NotUsed
import akka.stream.scaladsl.Sink
import com.github.dfauth.socketio.Envelope
import com.github.dfauth.socketio.reactivestreams.AbstractBaseSubscriber
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.{Serializer, StringSerializer}

object KafkaSink {

  def producer[V](props:Properties, serializer:Serializer[V]) = new org.apache.kafka.clients.producer.KafkaProducer[String, V](props, new StringSerializer, serializer)

  def apply[V](topic:String, producer:KafkaProducer[String, V]):Sink[V, NotUsed] = Sink.fromSubscriber(new AbstractBaseSubscriber[V]() {
    override def onNext(e:V): Unit = producer.send(new ProducerRecord[String, V](topic, e))
    override def onComplete(): Unit = {}
  })

  def apply[V](topic:String, props:Properties, serializer:Serializer[V]):Sink[V, NotUsed] = apply(topic, producer[V](props, serializer))

}
