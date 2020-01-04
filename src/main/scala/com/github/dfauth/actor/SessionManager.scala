package com.github.dfauth.actor

import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.typesafe.scalalogging.LazyLogging

object SessionManager {
  def apply(namespace:String): Behavior[SupervisorMessage] = Behaviors.setup[SupervisorMessage](context => new SessionManager(context, namespace))
}

class SessionManager(ctx: ActorContext[SupervisorMessage], namespace:String) extends AbstractBehavior[SupervisorMessage](ctx) with LazyLogging {
  ctx.log.info(s"session manager started namespace: ${namespace}")

  override def onMessage(msg: SupervisorMessage): Behavior[SupervisorMessage] = {
    logger.info(s"session manager received message ${msg}")
    msg match {
      case FetchSession(id, replyTo) => {
        val response = FetchSessionReply()
        logger.info(s"replying to ${replyTo} msg ${response}")
        replyTo ! response
      }
      case x => logger.error(s"received unhandled message ${x}")
    }
    Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[SupervisorMessage]] = {
    case PostStop =>
      context.log.info("session manager stopped")
      this
  }
}

case class FetchSession(id:String, replyTo:ActorRef[SupervisorMessage]) extends SupervisorMessage with RequestRequiringResponse[SupervisorMessage, SupervisorMessage] {
  def apply(ref:ActorRef[SupervisorMessage]): SupervisorMessage = this
}

case class FetchSessionReply() extends SupervisorMessage
case class ErrorMessage(t:Throwable) extends SupervisorMessage
