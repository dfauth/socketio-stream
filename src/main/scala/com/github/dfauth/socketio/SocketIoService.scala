package com.github.dfauth.socketio

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

case class SocketIoService(override val system:ActorSystem, override val materializer: ActorMaterializer, override val hostname:String = "0.0.0.0", override val port:Int = 8081, override val sslConfig: Option[SslConfig] = None) extends ServiceLifecycle {
  override val route:Route = SocketIoStream(system).route
}
