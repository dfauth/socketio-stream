package com.github.dfauth.socketio

import java.time.Duration
import java.time.temporal.{ChronoUnit, TemporalAmount}

import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class DelayedClosePublisher[T](payload: T, delay:TemporalAmount = Duration.ofSeconds(2)) extends Publisher[T] {

  override def subscribe(s: Subscriber[_ >: T]): Unit = {
    s.onSubscribe(new Subscription {
      override def request(n: Long): Unit = {}

      override def cancel(): Unit = {}
    })
    Future {
      s.onNext(payload)
      Thread.sleep(delay.get(ChronoUnit.SECONDS)*1000)
      s.onComplete()
    }
  }

}
