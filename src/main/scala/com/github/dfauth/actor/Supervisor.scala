package com.github.dfauth.actor

import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.typesafe.scalalogging.LazyLogging

object Supervisor {
  def apply(): Behavior[Command] = Behaviors.setup[Command](context => new Supervisor(context))
}

class Supervisor(ctx: ActorContext[Command]) extends AbstractBehavior[Command](ctx) with LazyLogging {

  var cache:Map[String, ActorRef[Command]] = Map.empty

  ctx.log.info("supervisor started")

  override def onMessage(msg: Command): Behavior[Command] = {
    logger.info(s"supervisor received message ${msg}")
    msg match {
      case AddNamespace(id, namespace) => {
        cache.get(id).map { ref =>
          logger.info(s"existing session forwarding ${msg} to ${ref}")
          ref ! msg
        } getOrElse {
          val ref = ctx.spawn[Command](SessionManager(namespace), id)
          cache = cache + (id -> ref)
          ref
        }
      }
      case m:Command => cache.get(m.id).map { ref => ref ! m }
    }
    Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("supervisor stopped")
      this
  }
}


