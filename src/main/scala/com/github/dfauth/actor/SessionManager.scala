package com.github.dfauth.actor

import akka.actor.typed.{Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.typesafe.scalalogging.LazyLogging

object SessionManager {
  def apply(namespace:String): Behavior[SessionMessage] = Behaviors.setup[SessionMessage](context => new SessionManager(context, namespace))
}

class SessionManager(ctx: ActorContext[SessionMessage], namespace:String) extends AbstractBehavior[SessionMessage](ctx) with LazyLogging {
  ctx.log.info(s"session manager started namespace: ${namespace}")

  override def onMessage(msg: SessionMessage): Behavior[SessionMessage] = {
    logger.info(s"session manager received message ${msg}")
    Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[SessionMessage]] = {
    case PostStop =>
      context.log.info("session manager stopped")
      this
  }
}

trait SessionMessage

