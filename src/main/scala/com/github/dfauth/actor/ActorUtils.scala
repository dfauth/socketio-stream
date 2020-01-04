package com.github.dfauth.actor

import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

object ActorUtils {

  type Bootstrapper[T] = Behavior[T] => ActorRef[T]

  def bootstrapper[T](name:String):Bootstrapper[T] = {
    bootstrapper(Some(name))
  }

  def bootstrapper[T](name:String, systemName:String):Bootstrapper[T] = {
    bootstrapper(Some(name), Some(systemName))
  }

  def bootstrapper[T](name:String, ctx:ActorContext[T]):Bootstrapper[T] = {
    bootstrapper(Some(name), key = None, ctx = Some(ctx))
  }

  def bootstrapper[T](name:String, key:ServiceKey[T], ctx:ActorContext[T]):Bootstrapper[T] = {
    bootstrapper(Some(name), key = Some(key), ctx = Some(ctx))
  }

  def bootstrapper[T](name:String, key:ServiceKey[T], systemName:String):Bootstrapper[T] = {
    bootstrapper(Some(name), key = Some(key), systemName = Some(systemName))
  }

  def bootstrapper[T](name:Option[String] = None, systemName:Option[String] = None, key:Option[ServiceKey[T]] = None, ctx:Option[ActorContext[T]] = None):Bootstrapper[T] = b => {

    val f:ActorContext[T] => ActorRef[T] = c => {
      val ref = name.map { n =>
        c.spawn(b, n)
      }.getOrElse {
        c.spawnAnonymous(b)
      }

      key.foreach { k =>
        c.system.receptionist ! Register(k, ref)
      }
      ref
    }

    ctx.map { c =>
      f(c)
    }.getOrElse {
      systemName.map { n =>
        ActorSystem[T](b,n)
      }.getOrElse {
        ActorSystem[T](b, "bootstrap-actor-system")
      }
    }
  }

  def asActor[T](behavior:Behavior[T]) = {
    bootstrapper[T]()(behavior)
  }
}
