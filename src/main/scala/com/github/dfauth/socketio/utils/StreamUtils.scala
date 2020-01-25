package com.github.dfauth.socketio.utils

import java.time.Duration
import java.time.temporal.{ChronoUnit, TemporalAmount}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{BlockingQueue, Semaphore, TimeUnit}
import java.util.function.{Consumer, Supplier}

import akka.actor.Cancellable
import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.github.dfauth.socketio.AbstractBaseSubscriber
import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

object StreamUtils extends LazyLogging {

  def log[T](msg:String):Flow[T, T, NotUsed] = Flow.fromFunction(loggingFn(msg))

  def loggingSink[T](msg:String):Sink[T, Future[Done]] = Sink.foreach((t:T) => loggingFn(msg)(t))

  def loggingFn[T](msg:String):T => T = t => {
    logger.info(s"${msg} payload: ${t}")
    t
  }

  val ONE_SECOND = FiniteDuration(1, TimeUnit.SECONDS)

  def secondsOf(d:Double) = FiniteDuration((d*1000).toLong, TimeUnit.MILLISECONDS)

  def tickingSupplyOf[T](supplier:Supplier[T], delay:FiniteDuration = ONE_SECOND):Source[T, Cancellable] = Source.tick(delay, delay, supplier).map(s => s.get())

  def fromConsumer[T](consumer:Consumer[T]): Subscriber[T] = new AbstractBaseSubscriber[T] {
    override def onNext(t: T): Unit = consumer.accept(t)
    override def onComplete(): Unit = {}
  }
}

case class DelayedClosePublisher[T](payload: Future[T], delay:TemporalAmount = Duration.ofSeconds(2))(implicit ec:ExecutionContext) extends Publisher[T] {

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

case class QueuePublisher[T](queue:BlockingQueue[T]) extends Publisher[T] with Subscription {

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


