package com.github.dfauth.socketio

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main extends App with LazyLogging {

  implicit val system: ActorSystem = ActorSystem("socketioService")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  SocketIoService(system, materializer, sslConfig = Some(SslConfig())).start()
  Await.result(system.whenTerminated, Duration.Inf)

}
