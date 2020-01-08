package com.github.dfauth.actor

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{Behavior, PostStop, Signal}
import com.github.dfauth.socketio.UserContext
import com.typesafe.scalalogging.LazyLogging

object SessionManager {
  def apply[U](userCtx:UserContext[U], namespaces: Iterable[String]): Behavior[Command] = Behaviors.setup[Command](context => new SessionManager(context, userCtx, namespaces))
}

class SessionManager[U](ctx: ActorContext[Command], userCtx:UserContext[U], namespaces:Iterable[String]) extends AbstractBehavior[Command](ctx) with LazyLogging {

  ctx.log.info(s"session manager started with user ctx: ${userCtx}")

  override def onMessage(msg: Command): Behavior[Command] = {
    logger.info(s"session manager received message ${msg}")
    msg match {
      case AddNamespace(id, namespace) => {
        Behaviors.unhandled // cannot support this in stateless polling model
      }
      case FetchSessionCommand(id, replyTo) => {
        replyTo ! FetchSessionReply(id, namespaces, ctx.self)
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

