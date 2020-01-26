package com.github.dfauth.socketio.reactivestreams

import java.util.concurrent.{BlockingQueue, Semaphore, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean

import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.{ExecutionContext, Future}

object QueuePublisher {
  def apply[T](q:BlockingQueue[T])(implicit ec:ExecutionContext) = new QueuePublisher[T](q)(ec)
}

class QueuePublisher[T](queue:BlockingQueue[T])(implicit ec:ExecutionContext) extends Publisher[T] with Subscription {

  var optSubscriber:Option[Subscriber[_ >: T]] = None
  val shouldContinue = new AtomicBoolean(true)
  val latch = new Semaphore(0)
  var optRunningThread:Option[Thread] = None

  private def dequeOne:Unit = {
    while(shouldContinue.get) {
      val t = queue.poll(100, TimeUnit.MILLISECONDS)
      if (t != null) {
        optSubscriber.map {
          _.onNext(t)
        }
      }
    }
  }

  override def subscribe(subscriber: Subscriber[_ >: T]): Unit = {
    optSubscriber = Some(subscriber)
    subscriber.onSubscribe(this)
  }

  override def request(l: Long): Unit = Future {
    l match {
      case 0 =>
      case n => dequeOne
        request(n-1)
    }
  }

  override def cancel(): Unit = shouldContinue.set(false)
}
