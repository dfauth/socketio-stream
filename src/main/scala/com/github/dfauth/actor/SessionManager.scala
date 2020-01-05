package com.github.dfauth.actor

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{Behavior, PostStop, Signal}
import com.github.dfauth.socketio.UserContext
import com.typesafe.scalalogging.LazyLogging

object SessionManager {
  def apply[U](userCtx:UserContext[U]): Behavior[Command] = Behaviors.setup[Command](context => new SessionManager(context, userCtx))
}

class SessionManager[U](ctx: ActorContext[Command], userCtx:UserContext[U]) extends AbstractBehavior[Command](ctx) with LazyLogging {

  ctx.log.info(s"session manager started with user ctx: ${userCtx}")

  var namespaces:List[String] = List.empty[String]

  override def onMessage(msg: Command): Behavior[Command] = {
    logger.info(s"session manager received message ${msg}")
    msg match {
      case AddNamespace(id, namespace) => {
        namespaces = namespaces :+ namespace
        Behaviors.same
      }
      case FetchSessionCommand(id, replyTo) => {
        namespaces.headOption.map(replyTo ! FetchSessionReply(id, _))
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

