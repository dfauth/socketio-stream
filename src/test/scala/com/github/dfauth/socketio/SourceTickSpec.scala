package com.github.dfauth.socketio

import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives.{getFromResource, getFromResourceDirectory, path, pathPrefix, _}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.github.dfauth.socketio.SocketIoStream.TokenValidator
import com.github.dfauth.socketio.reactivestreams.{ControllingProcessor, Processors}
import com.github.dfauth.socketio.utils.StreamUtils
import com.github.dfauth.socketio.utils.StreamUtils._
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.DateTime
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Success

class SourceTickSpec extends FlatSpec
  with Matchers
  with LazyLogging {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val controller0 = ControllingProcessor[TestEvent]("testEvent").off
  val (sink0, src0) = Processors.sinkAndSourceOf(controller0)
  val controller1 = ControllingProcessor[AnotherTestEvent]("anotherTestEvent").off
  val (sink1, src1) = Processors.sinkAndSourceOf(controller1)

  "akka streams" should "allow objects to be streamed from a ticking source" in {

    tickingSupplyOf(testEventSupplier("left")).runWith(sink0)
    val srcLeft = src0.map { t => BlahObject(t, t.getName.toString, t.getAckId)}
    tickingSupplyOf(anotherTestEventSupplier("right"), secondsOf(0.917)).runWith(sink1)
    val srcRight = src1.map { t => BlahObject(t, t.getName.toString, t.getAckId)}

    val flowFactories:Seq[FlowFactory] = Seq(
      new FlowFactory(){
        override val namespace: String = "/left"
        override def create[T >: Ackable with Eventable, U](ctx: UserContext[U]): (Sink[T, Any], Source[T, Any]) = (loggingSink[T](s"\n\n *** ${namespace} *** \n\n received: "), srcLeft)
      },
      new FlowFactory(){
        override val namespace: String = "/right"
        override def create[T >: Ackable with Eventable, U](ctx: UserContext[U]): (Sink[T, Any], Source[T, Any]) = (loggingSink[T](s"\n\n *** ${namespace} *** \n\n received: "), srcRight)
      }
    )

    SocketIOServer1(flowFactories)

    System.in.read();
    logger.info("running streams...")
    controller0.on
    controller1.on

    System.in.read();
    logger.info("toggling...")
    controller0.off
    controller1.off

    Await.result(system.whenTerminated, Duration.Inf)
  }

  def testEventSupplier(name:String): Supplier[TestEvent] = {

    val i = new AtomicLong()

    () => {
      val e = new TestEvent()
      e.setName(name)
      e.setPayload(('A'.toInt + i.incrementAndGet()%26).toChar.toString)
      e.setAckId(i.getAndIncrement())
      e
    }
  }

  def anotherTestEventSupplier(name:String): Supplier[AnotherTestEvent] = {

    val i = new AtomicLong()

    () => {
      val e = new AnotherTestEvent()
      e.setName(name)
      e.setPayload(i.get())
      e.setAckId(i.getAndIncrement())
      e.setTs(DateTime.now())
      e
    }
  }

}

object SocketIOServer1 {

  def apply[R <: Ackable with Eventable](flowFactories:Seq[FlowFactory]) = {
    implicit val system: ActorSystem = ActorSystem("socketioService")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    new ServiceLifecycleImpl(system, materializer) {

      override val route: Route = SocketIoStream(system, validator, flowFactories).route ~ static
    }.start()

    def validator:TokenValidator[User] = t => Success(UserContextImpl(t, User("fred", Seq("user"))))

    def static =
      path("") {
        getFromResource("static/index.html")
      } ~ pathPrefix("") {
        getFromResourceDirectory("static")
      }

  }

}







