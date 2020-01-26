package com.github.dfauth.socketio.reactivestreams

import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.stream.scaladsl.Sink
import com.github.dfauth.socketio.reactivestreams.Throttlers.ThrottlingLogic
import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.{Subscriber, Subscription}

trait AbstractBaseSubscriber[T] extends Subscriber[T] with LazyLogging {

  val name:Option[String] = None
  var subscription:Option[Subscription] = None

  def onSubscribe(s: Subscription): Unit = {
    subscription = Some(s)
    logger.debug(withName("onSubscribe"))
    init(s)
  }

  def onNext(t: T): Unit

  def onError(t: Throwable): Unit = {
    logger.error(t.getMessage, t)
  }

  def onComplete(): Unit

  def withName(str: String): String = name.map {s => s"${s} ${str}"}.getOrElse {str}

  protected def init(s:Subscription): Unit = s.request(Int.MaxValue)
}

object Throttlers {

  type ThrottlingLogic[T] = Subscription => T => Unit

  def fixed[T](cnt:Int, c:T=>Unit):ThrottlingLogic[T] = s => {
    val i = new AtomicInteger
    s.request(5)
    t => {
      if(i.incrementAndGet() <= cnt) {
        c(t)
      } else {
        s.cancel()
      }
    }
  }
}

object ThrottlingSubscriber {
  def apply[T](logic: ThrottlingLogic[T]) = new ThrottlingSubscriber[T](logic)
  def sink[T](logic: ThrottlingLogic[T]):Sink[T,NotUsed] = Sink.fromSubscriber(apply(logic))
}


class ThrottlingSubscriber[T](logic:ThrottlingLogic[T]) extends AbstractBaseSubscriber[T] {

  var optLogic:Option[T=>Unit] = None

  override protected def init(s:Subscription): Unit = {
    optLogic = Some(logic(s))
  }

  override def onNext(t: T): Unit = optLogic.foreach(_(t))

  override def onComplete(): Unit = {}
}