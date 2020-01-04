package com.github.dfauth.socketio

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.github.dfauth.socketio.SocketIoStream.TokenValidator

case class SocketIoService[U](override val system:ActorSystem, override val materializer: ActorMaterializer, override val hostname:String = "0.0.0.0", override val port:Int = 8081, override val sslConfig: Option[SslConfig] = None, tokenValidator:TokenValidator[U]) extends ServiceLifecycle {
  override val route:Route = SocketIoStream(system, tokenValidator).route
}

