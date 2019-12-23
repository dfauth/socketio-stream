package com.github.dfauth.socketio

import com.github.dfauth.engineio.Transport
import com.typesafe.config.Config
import scala.collection.JavaConverters._

case class SocketIOConfig(config: Config) {

  def transportsFiltering(activeTransport: Transport): Array[String] = transports.filterNot(_ == activeTransport.name)
  val transports = config.getStringList("engineio.transports").asScala.toArray
  val pingInterval = config.getTemporal("engineio.ping-interval")
  val pingTimeout = config.getTemporal("engineio.ping-timeout")
}
