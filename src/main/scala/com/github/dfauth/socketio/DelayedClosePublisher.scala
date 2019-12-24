package com.github.dfauth.socketio

import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class DelayedClosePublisher[T](payload: T, delay:Long = 1000L) extends Publisher[T] {

  override def subscribe(s: Subscriber[_ >: T]): Unit = {
    s.onSubscribe(new Subscription {
      override def request(n: Long): Unit = {}

      override def cancel(): Unit = {}
    })
    Future {
      s.onNext(payload)
      Thread.sleep(delay)
      s.onComplete()
    }
  }

}
