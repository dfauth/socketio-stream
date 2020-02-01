package com.github.dfauth

import java.util.Properties

import com.typesafe.scalalogging.LazyLogging
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.clients.producer.RecordMetadata

import scala.util.{Failure, Success, Try}

package object socketio extends LazyLogging {

  def connectionProperties(config: EmbeddedKafkaConfig):Properties = {
    val props = new Properties()
    props.setProperty("zookeeperConnectString", s"localhost:${config.zooKeeperPort}")
    props.setProperty("bootstrap.servers", s"localhost:${config.kafkaPort}")
    props
  }

  val logSuccess: Try[RecordMetadata] => Unit = t => t match {
    case Success(r) => {
      logger.info(s"success: ${r}")
    }
    case Failure(f) => {
      logger.error(f.getMessage, f)
    }
  }
}
