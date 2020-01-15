package com.github.dfauth.socketio

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives.{getFromResource, getFromResourceDirectory, path, pathPrefix, _}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.github.dfauth.socketio.SocketIoStream.TokenValidator
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Success

object Main extends App with LazyLogging {

  implicit val system: ActorSystem = ActorSystem("socketioService")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val sourceFactory: SourceFactory = new TestSourceFactory()

  new ServiceLifecycleImpl(system, materializer) {

    override val route: Route = SocketIoStream(system, validator, sourceFactory).route ~ static
  }.start()

  Await.result(system.whenTerminated, Duration.Inf)

  def validator:TokenValidator[User] = t => Success(new UserContextImpl(t, User("fred")))

  def static =
    path("") {
      getFromResource("static/index.html")
    } ~ pathPrefix("") {
      getFromResourceDirectory("static")
    }

}

case class User(name:String)
case class UserContextImpl(token:String, payload:User) extends UserContext[User]



