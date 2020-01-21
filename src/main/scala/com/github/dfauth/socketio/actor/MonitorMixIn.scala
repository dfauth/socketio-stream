package com.github.dfauth.socketio.actor

import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}

object ActorMonitor {

  def lookup[T,U](ctx:ActorContext[T])(key:ServiceKey[T])(pf:PartialFunction[List[ActorRef[T]], Try[U]]) = {

    val p = Promise[U]()
    val ref = Behaviors.receive[Listing] { (c, msg) =>
      msg match {
        case x:Listing => {
          val l = x.serviceInstances[T](x.key.asInstanceOf[ServiceKey[T]]).toList
          if(pf.isDefinedAt(l)) {
            pf(l) match {
              case Success(v) => p.success(v)
              case Failure(t) => p.failure(t)
            }
          } else {
            p.failure(new RuntimeException(s"partial function is not defined at ${l}"))
          }
        }
      }
      Behaviors.stopped
    }
    val tmp: ActorRef[Receptionist.Listing] = ctx.spawnAnonymous(ref)
    ctx.system.receptionist ! Receptionist.find[T](key, tmp)
    p.future
  }

  def subscribeToActor[T](ctx:ActorContext[T], f:ActorMonitorCommand[T] => T)(key: ServiceKey[T], ref:ActorRef[T]) = ctx.system.receptionist ! Receptionist.Subscribe(key, listingAdapter(ctx, f)(key, ref))

  def listingAdapter[T](ctx:ActorContext[T], f:ActorMonitorCommand[T] => T)(key:ServiceKey[T], ref:ActorRef[T]) = {
    val b = Behaviors.receive[Listing] { (ctx1, msg) =>
      val s = msg.serviceInstances[T](key).toList
      s match {
        case Nil => {
          ref ! f(ActorTerminated(key))
          Behaviors.stopped
        }
        case t :: Nil => {
          ref ! f(ActorCreated(key, t))
          Behaviors.same
        }
        case x => {
          ctx1.log.error(s"Too many subscriptions for key ${key} s: ${s}")
          Behaviors.unhandled
        }
      }
    }
    ctx.spawnAnonymous(b)
  }

  trait ActorMonitorCommand[T] {
    val key: ServiceKey[T]
  }
  case class ActorCreated[T](key: ServiceKey[T], ref: ActorRef[T]) extends ActorMonitorCommand[T]
  case class ActorTerminated[T](key: ServiceKey[T]) extends ActorMonitorCommand[T]
}

trait MonitorMixIn[T] {
  
  val ctx:ActorContext[T]

  val adapter: ActorMonitor.ActorMonitorCommand[T] => T

  def lookup[U](key:ServiceKey[T])(pf:PartialFunction[List[ActorRef[T]], Try[U]]) = ActorMonitor.lookup(ctx)(key)(pf)

  def subscribeToActor(key: ServiceKey[T], ref:ActorRef[T]) = ActorMonitor.subscribeToActor(ctx, adapter)(key, ref)

  def listingAdapter(key:ServiceKey[T], ref:ActorRef[T]) = ActorMonitor.listingAdapter(ctx, adapter)(key, ref)

}
