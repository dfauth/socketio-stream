package com.github.dfauth.actor

import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.typesafe.scalalogging.LazyLogging

trait SupervisorMessage
case object CreateSession extends SupervisorMessage
case class CreateSessionWithNamespace(id:String, namespace:String) extends SupervisorMessage


object Supervisor {
  def apply(): Behavior[SupervisorMessage] = Behaviors.setup[SupervisorMessage](context => new Supervisor(context))
}

class Supervisor(ctx: ActorContext[SupervisorMessage]) extends AbstractBehavior[SupervisorMessage](ctx) with LazyLogging {

  var cache:Map[String, ActorRef[SupervisorMessage]] = Map.empty

  ctx.log.info("supervisor started")

  override def onMessage(msg: SupervisorMessage): Behavior[SupervisorMessage] = {
    logger.info(s"supervisor received message ${msg}")
    msg match {
      case CreateSessionWithNamespace(id, namespace) => {
        val ref = ctx.spawn[SupervisorMessage](SessionManager(namespace), id)
        cache = cache + (id -> ref)
      }
      case m:FetchSession => cache.get(m.id).map { ref => ref ! m }
    }
    Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[SupervisorMessage]] = {
    case PostStop =>
      context.log.info("supervisor stopped")
      this
  }
}

