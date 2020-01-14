package com.github.dfauth.actor

import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{Behavior, PostStop, Signal}
import akka.stream.Materializer
import akka.stream.scaladsl.{MergeHub, Sink, Source}
import com.github.dfauth.socketio
import com.github.dfauth.socketio.Processors._
import com.github.dfauth.socketio.{FunctionProcessor, UserContext}
import com.github.dfauth.utils.StreamUtils._

import scala.concurrent.duration._

object SessionManager {
  def apply[U](userCtx:UserContext[U], namespaces: Iterable[String]): Behavior[Command] = Behaviors.setup[Command](context => new SessionManager(context, userCtx, namespaces))
}

class SessionManager[U](ctx: ActorContext[Command], userCtx:UserContext[U], namespaces:Iterable[String]) extends AbstractBehavior[Command](ctx) {

  implicit val mat = Materializer(ctx.system)
  ctx.log.info(s"session manager started with user ctx: ${userCtx}")

  val (sink, source) = sinkAndSourceOf[Command,Command](FunctionProcessor[Command]())
  val streamSink:Sink[Command, NotUsed] = MergeHub.source[Command](16).to(sink).run()

  override def onMessage(msg: Command): Behavior[Command] = {
    ctx.log.info(s"session manager received message ${msg}")
    msg match {
      case AddNamespace(id, namespace) => {
        Behaviors.unhandled // cannot support this in stateless polling model
      }
      case FetchSessionCommand(id, replyTo) => {
        replyTo ! FetchSessionReply(id, namespaces, ctx.self, source)
        Behaviors.same
      }
      case EventCommand(id, namespace, payload) => {
        var i = new AtomicInteger()
        var j = new AtomicInteger()
        Source.tick(ONE_SECOND, ONE_SECOND,() => i.incrementAndGet()).map{s => MessageCommand(id, namespace, socketio.EventWrapper("left", s(), Some(j.incrementAndGet())))}.runWith(streamSink)
        Source.tick(ONE_SECOND, 900 millis,() => ('A'.toInt + i.incrementAndGet()%26).toChar).map{s => MessageCommand(id, namespace, socketio.EventWrapper("right", s()))}.runWith(streamSink)
        Behaviors.same
      }
      case AckCommand(id, nsp, ackId, payload) => {
        ctx.log.info(s"message with id ${ackId} in namespace ${nsp} acked")
        Behaviors.same
      }
      case EndSession(id) => {
        ctx.log.info(s"session ${id} stopped")
        Behaviors.stopped
      }
      case x => {
        ctx.log.error(s"received unhandled message ${x}")
        Behaviors.unhandled
      }
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("session manager stopped")
      this
  }
}

