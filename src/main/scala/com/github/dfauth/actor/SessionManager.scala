package com.github.dfauth.actor

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{Behavior, PostStop, Signal}
import com.typesafe.scalalogging.LazyLogging

object SessionManager {
  def apply(namespace:String): Behavior[Command] = Behaviors.setup[Command](context => new SessionManager(context, namespace))
}

class SessionManager(ctx: ActorContext[Command], namespace:String) extends AbstractBehavior[Command](ctx) with LazyLogging {

  ctx.log.info(s"session manager started namespace: ${namespace}")

  var namespaces:List[String] = List(namespace)

  override def onMessage(msg: Command): Behavior[Command] = {
    logger.info(s"session manager received message ${msg}")
    msg match {
      case AddNamespace(id, namespace) => {
        namespaces ::= namespace
        Behaviors.same
      }
      case FetchSession(id, replyTo) => {
        replyTo ! FetchSessionReply(id, namespace)
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

