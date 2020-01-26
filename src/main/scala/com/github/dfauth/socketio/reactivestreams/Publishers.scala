package com.github.dfauth.socketio.reactivestreams

import java.util.concurrent.{BlockingQueue, Semaphore}
import java.util.concurrent.atomic.AtomicBoolean

import org.reactivestreams.{Publisher, Subscriber, Subscription}

object QueuePublisher {
  def apply[T](q:BlockingQueue[T]) = new QueuePublisher[T](q)
}

class QueuePublisher[T](queue:BlockingQueue[T]) extends Publisher[T] with Subscription {

  var optSubscriber:Option[Subscriber[_ >: T]] = None
  val shouldContinue = new AtomicBoolean(true)
  val latch = new Semaphore(0)
  var optRunningThread:Option[Thread] = None

  def start:Unit = {
    try {
      optRunningThread = Some(Thread.currentThread())
      while (shouldContinue.get()) {
        latch.acquire()
        val t = queue.take()
        if (t != null) {
          optSubscriber.map {
            _.onNext(t)
          }
        }
      }
      optSubscriber.map {
        _.onComplete()
      }
    } catch {
      case t:Throwable => optSubscriber.map { _.onError(t)}
    }
  }

  override def subscribe(subscriber: Subscriber[_ >: T]): Unit = {
    optSubscriber = Some(subscriber)
    subscriber.onSubscribe(this)
  }

  override def request(l: Long): Unit = latch.release(l.toInt)

  override def cancel(): Unit = shouldContinue.set(false)
}
