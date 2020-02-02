package com.github.dfauth.socketio

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives.{getFromResource, getFromResourceDirectory, path, pathPrefix, _}
import akka.http.scaladsl.server.Route
import akka.kafka.Subscriptions
import akka.stream.ActorMaterializer
import com.github.dfauth.socketio.SocketIoStream.TokenValidator
import com.github.dfauth.socketio.kafka.KafkaFlowFactory
import com.github.dfauth.socketio.utils.StreamUtils._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.rest.RestService
import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient, MockSchemaRegistryClient}

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
  val flowFactories:Seq[FlowFactory] = Seq(
    new KafkaFlowFactory("/left", "left", topic0, schemaRegClient),
    new KafkaFlowFactory("/right", "right", topic1, schemaRegClient)
  )

  new ServiceLifecycleImpl(system, materializer) {

    override val route: Route = SocketIoStream(system, validator, flowFactories).route ~ static
  }.start()

  Await.result(system.whenTerminated, Duration.Inf)

  def validator:TokenValidator[User] = t => Success(new UserContextImpl(t, User("fred", Seq("user"))))

}

case class User(name:String, roles:Seq[String] = Seq.empty)
case class UserContextImpl(token:String, payload:User) extends UserContext[User] {
  override val config: Config = SocketIOConfig(ConfigFactory.load()).getContextConfig(s"prefs.${payload.name}")
  override def userId: String = payload.name
}



