package com.github.dfauth.socketio.utils

import java.time.Duration
import java.time.temporal.{ChronoUnit, TemporalAmount}
import java.util.concurrent.TimeUnit

import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Flow, Sink}
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

