package com.github.dfauth.socketio

import com.github.dfauth.socketio.protocol.EngineIOTransport
import com.typesafe.config.Config

import scala.collection.JavaConverters._

case class SocketIOConfig(config: Config) {

  def transportsFiltering(activeTransport: EngineIOTransport): Array[String] = transports.filterNot(_ == activeTransport.name)
  val transports = config.getStringList("engineio.transports").asScala.toArray
  val pingInterval = config.getTemporal("engineio.ping-interval")
  val pingTimeout = config.getTemporal("engineio.ping-timeout")
  val longPollTimeout = config.getTemporal("engineio.long-poll-timeout")
  val namespaces = config.getStringList("engineio.namespaces").asScala
}
