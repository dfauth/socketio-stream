package com.github.dfauth.actor

import akka.NotUsed
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{Behavior, PostStop, Signal}
import akka.stream.Materializer
import akka.stream.scaladsl.{MergeHub, Sink}
import com.github.dfauth.socketio
import com.github.dfauth.socketio.Processors._
import com.github.dfauth.socketio._

object SessionManager {
  def apply[U](userCtx:UserContext[U], sourceFactories:Seq[SourceFactory]): Behavior[Command] = Behaviors.setup[Command](context => new SessionManager(context, userCtx, sourceFactories))
}

class SessionManager[U](ctx: ActorContext[Command], userCtx:UserContext[U], sourceFactories:Seq[SourceFactory]) extends AbstractBehavior[Command](ctx) {

  implicit val mat = Materializer(ctx.system)
  ctx.log.info(s"session manager started with user ctx: ${userCtx}")

  val (sink, source) = sinkToSource[Command]
  val streamSink:Sink[Command, NotUsed] = MergeHub.source[Command](16).to(sink).run()

  def initializeSources() =
    sourceFactories.foreach { f =>
      f.create.map { (m:Ackable with Eventable) =>
        MessageCommand(userCtx.token, f.namespace, socketio.EventWrapper(m.eventId, m, Some(m.ackId)))
      }.runWith(streamSink)
    }

  override def onMessage(msg: Command): Behavior[Command] = {
    ctx.log.info(s"session manager received message ${msg}")
    msg match {
      case AddNamespace(id, namespace) => {
        Behaviors.unhandled // cannot support this in stateless polling model
      }
      case FetchSessionCommand(id, replyTo) => {
        replyTo ! FetchSessionReply(id, sourceFactories.map {_.namespace}, ctx.self, source)
        Behaviors.same
      }
      case EventCommand(id, namespace, payload) => {
        initializeSources()
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

