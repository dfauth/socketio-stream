package com.github.dfauth.socketio

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer}
import com.github.dfauth.socketio.kafka._
import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await}

class RunKafkaSpec extends FlatSpec
  with Matchers
  with EmbeddedKafka
  with LazyLogging {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  "this test" should "allow us to run kafka and schema registry" in {

    try {

      withRunningKafkaOnFoundPort(EmbeddedKafkaConfig(kafkaPort = 9092, zooKeeperPort = 2181)) { implicit config =>
        val props = connectionProperties(config)

        Await.result(system.whenTerminated, Duration.Inf)
      }
    } finally {
      EmbeddedKafka.stop()
    }
  }
}