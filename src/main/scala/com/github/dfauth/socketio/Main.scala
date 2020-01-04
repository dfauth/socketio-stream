package com.github.dfauth.socketio

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.dfauth.socketio.SocketIoStream.TokenValidator
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Success

object Main extends App with LazyLogging {

  implicit val system: ActorSystem = ActorSystem("socketioService")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  SocketIoService(system, materializer, sslConfig = Some(SslConfig()), tokenValidator = validator).start()
  Await.result(system.whenTerminated, Duration.Inf)

  def validator:TokenValidator[User] = t => Success(new UserContextImpl(t, User("fred")))

}

case class User(name:String)
case class UserContextImpl(token:String, payload:User) extends UserContext[User]


