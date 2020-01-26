package com.github.dfauth.socketio.reactivestreams

import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.{Subscriber, Subscription}

trait AbstractBaseSubscriber[T] extends Subscriber[T] with LazyLogging {

  val name:Option[String] = None
  var subscription:Option[Subscription] = None

  def onSubscribe(s: Subscription): Unit = {
    subscription = Some(s)
    logger.debug(withName("onSubscribe"))
    init()
  }

  def onNext(t: T): Unit

  def onError(t: Throwable): Unit = {
    logger.error(t.getMessage, t)
  }

  def onComplete(): Unit

  def withName(str: String): String = name.map {s => s"${s} ${str}"}.getOrElse {str}

  protected def init(): Unit = {
    subscription.map(s => s.request(Int.MaxValue))
  }
}

