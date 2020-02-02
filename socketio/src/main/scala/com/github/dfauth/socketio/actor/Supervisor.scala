package com.github.dfauth.socketio.actor

import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.github.dfauth.socketio.FlowFactory
import com.github.dfauth.socketio.actor.ActorMonitor.{ActorCreated, ActorTerminated}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

object Supervisor {
  def apply[U](flowFactories:Seq[FlowFactory[U]]): Behavior[Command] = Behaviors.setup[Command](context => new Supervisor(context, flowFactories))
}

class Supervisor[U](val ctx: ActorContext[Command], flowFactories:Seq[FlowFactory[U]]) extends AbstractBehavior[Command](ctx) with LazyLogging with MonitorMixIn[Command] {

  ctx.log.info("supervisor: started")

  private var cache:Map[ServiceKey[Command], Future[ActorRef[Command]]] = Map.empty

  def serviceKey(id:String):ServiceKey[Command] = ServiceKey[Command](id)

  val adapter: ActorMonitor.ActorMonitorCommand[Command] => Command = {
    case ActorCreated(key, ref) => ActorCreatedCommand(key, ref)
    case ActorTerminated(key) => ActorTerminatedCommand(key)
  }

  override def onMessage(msg: Command): Behavior[Command] = {
    logger.info(s"supervisor: received message ${msg}")
    msg match {
      case c:CreateSessionCommand[U] => {
        val id = c.id
        val userCtx = c.userCtx
        val replyTo = c.replyTo
        cache.get(serviceKey(id)).map { ref =>
          // lookup in progress use its on completion value
          ref.onComplete {
            case Success(ref) => {
              replyTo ! SessionExists(id)
              logger.info(s"supervisor: lookup pending replied ${SessionExists(id)}")
            }
            case Failure(t) => {
              logger.error(t.getMessage, t)
              replyTo ! SessionCreationError(id, t)
            }
          }
        } getOrElse {
          val f = lookup(serviceKey(id)) {
            case Nil => {
              logger.info(s"supervisor: no actor found for id: ${id}")
              val ref = ctx.spawn[Command](SessionManager[U](userCtx, flowFactories), id)
              registerActor(serviceKey(id), ref)
              subscribeToActor(serviceKey(id), ctx.self)
              replyTo ! SessionCreated(id)
              logger.info(s"supervisor: replied ${SessionCreated(id)}")
              Success(ref)
            }
            case ref :: Nil => {
              logger.info(s"supervisor: found id: ${ref}")
              replyTo ! SessionExists(id)
              logger.info(s"supervisor: replied ${SessionExists(id)}")
              Success(ref)
            }
            case ref :: tail => {
              val t = new IllegalStateException(s"Found multiple actor references: ${ref}, ${tail} for key ${id}")
              logger.error(t.getMessage, t)
              Failure(t)
            }
          }
          val key = serviceKey(id)
          cache = cache + (key -> f)
          logger.info(s"supervisor: added cache key ${key} -> ${f}")
        }
      }
      case c:ActorCreatedCommand => {
        logger.info(s"supervisor: received ${c}")
      }
      case ActorTerminatedCommand(key) => {
        cache = cache - key
        logger.info(s"supervisor: removed cache key ${key}")
      }
      case m:Command => {
        cache.get(serviceKey(m.id)).map { ref => ref.map(_ ! m) }
        logger.info(s"supervisor: forwarded command ${m}")
      }
    }
    Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("supervisor stopped")
      this
  }

}



