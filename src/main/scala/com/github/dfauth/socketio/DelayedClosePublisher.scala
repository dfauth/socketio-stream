package com.github.dfauth.socketio

import java.time.Duration
import java.time.temporal.{ChronoUnit, TemporalAmount}

import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

case class DelayedClosePublisher[T](payload: Future[T], delay:TemporalAmount = Duration.ofSeconds(2)) extends Publisher[T] {

  override def subscribe(s: Subscriber[_ >: T]): Unit = {
    s.onSubscribe(new Subscription {
      override def request(n: Long): Unit = {}

      override def cancel(): Unit = {}
    })
    payload.onComplete {
      case Success(p) => s.onNext(p)
      case Failure(t) => s.onError(t)
    }
    Future {
      Thread.sleep(delay.get(ChronoUnit.SECONDS)*1000)
      if(payload.isCompleted) {
        s.onComplete()
      }
    }
  }

}
