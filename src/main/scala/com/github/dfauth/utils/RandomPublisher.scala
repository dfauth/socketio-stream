package com.github.dfauth.utils

import java.time.Duration

import akka.NotUsed
import akka.stream.scaladsl.Source
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

object RandomSource {
  def apply[T](d:FiniteDuration, supplier: ()=>T)(implicit ec:ExecutionContext):Source[T, NotUsed] = Source.fromPublisher(new RandomPublisher(d, supplier))
}

class RandomPublisher[T](d:FiniteDuration, supplier: () => T)(implicit val ec:ExecutionContext) extends Publisher[T] {

  var running = false
  var subscriber:Option[Subscriber[_ >: T]] = None

  override def subscribe(s: Subscriber[_ >: T]): Unit = {
    subscriber = Some(s)
    s.onSubscribe(new Subscription {
      override def request(l: Long): Unit = {
        start()
      }

      override def cancel(): Unit = {}
    })
  }

  def start(): Unit = {
    running = true
    Future {
      while(running) {
        val r = Math.random()
        Thread.sleep((r * d.toMillis).toLong)
        subscriber.map {s => s.onNext(supplier())}
      }
    }
    subscriber.map {s => s.onComplete()}
  }

  def stop(): Unit = running = false
}
