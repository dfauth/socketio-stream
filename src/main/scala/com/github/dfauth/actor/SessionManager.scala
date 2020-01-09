package com.github.dfauth.actor

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorSource
import com.github.dfauth.socketio.UserContext
import com.typesafe.scalalogging.LazyLogging

object SessionManager {
  def apply[U](userCtx:UserContext[U], namespaces: Iterable[String]): Behavior[Command] = Behaviors.setup[Command](context => new SessionManager(context, userCtx, namespaces))
}

class SessionManager[U](ctx: ActorContext[Command], userCtx:UserContext[U], namespaces:Iterable[String]) extends AbstractBehavior[Command](ctx) with LazyLogging {

  ctx.log.info(s"session manager started with user ctx: ${userCtx}")

  def asSource[T]():Source[T, ActorRef[T]] =
    ActorSource.actorRef(
      { case StreamComplete(_) => },
      { case ErrorMessage(_, t) => t},
      0,
      OverflowStrategy.fail
    )

  override def onMessage(msg: Command): Behavior[Command] = {
    logger.info(s"session manager received message ${msg}")
    msg match {
      case AddNamespace(id, namespace) => {
        Behaviors.unhandled // cannot support this in stateless polling model
      }
      case FetchSessionCommand(id, replyTo) => {
        val src:Source[Command, ActorRef[Command]] = asSource[Command]()
        replyTo ! FetchSessionReply(id, namespaces, ctx.self, src)
        Behaviors.same
      }
      case EventCommand(id, payload) => {
        Behaviors.same
      }
      case EndSession(id) => {
        logger.info(s"session ${id} stopped")
        Behaviors.stopped
      }
      case x => {
        logger.error(s"received unhandled message ${x}")
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

