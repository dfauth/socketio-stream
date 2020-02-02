package com.github.dfauth.socketio

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.kafka.Subscriptions
import akka.stream.ActorMaterializer
import com.github.dfauth.socketio.SocketIoStream.TokenValidator
import com.github.dfauth.socketio.kafka.{KafkaFlowFactory, User}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Success

object Main extends App with LazyLogging {

  implicit val system: ActorSystem = ActorSystem("socketioService")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val regUrl = system.settings.config.getString("schemaRegUrl")
  val capacity = system.settings.config.getInt("schemaReg.capacity")
  val schemaRegClient = new CachedSchemaRegistryClient(regUrl, capacity)

  val topic0 = Subscriptions.topics(Set("left"))
  val topic1 = Subscriptions.topics(Set("right"))
  val flowFactories:Seq[FlowFactory[User]] = Seq(
    new KafkaFlowFactory("/left", "left", topic0, schemaRegClient),
    new KafkaFlowFactory("/right", "right", topic1, schemaRegClient)
  )

  new ServiceLifecycleImpl(system, materializer) {

    override val route: Route = SocketIoStream[User](system, validator, flowFactories).route ~ static
  }.start()

  Await.result(system.whenTerminated, Duration.Inf)

  def validator:TokenValidator[User] = t => Success(new AuthenticationContextImpl(t, User("fred", Seq("user"))))

}

case class AuthenticationContextImpl(token:String, payload:User) extends AuthenticationContext[User] {
  override def userId: String = payload.name
}



