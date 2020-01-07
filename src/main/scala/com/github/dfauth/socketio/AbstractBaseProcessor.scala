package com.github.dfauth.socketio

import com.github.dfauth.utils.TryCatchUtils
import com.github.dfauth.utils.TryCatchUtils._
import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.{Processor, Subscriber, Subscription}

import scala.util.{Failure, Success, Try}

trait AbstractBaseProcessor[I, O] extends Processor[I, O] with LazyLogging {

  var subscriber:Option[Subscriber[_ >: O]] = None
  var subscription:Option[Subscription] = None

  override def onSubscribe(s: Subscription): Unit = {
    subscription = Some(s)
    init()
  }

  override def onError(t: Throwable): Unit = subscriber.map(_.onError(t))

  override def onComplete(): Unit = subscriber.map(_.onComplete())

  override def subscribe(s: Subscriber[_ >: O]): Unit = {
    subscriber = Some(s)
    init()
  }

  private def init(): Unit = {
    subscriber.flatMap(s => {
      logger.info("subscribed")
      subscription.map(q => s.onSubscribe(q))
    })
  }
}

class FunctionProcessor[I, O](val f:I => O) extends AbstractBaseProcessor[I, O] {

  override def onNext(i: I): Unit = {
    subscriber.foreach(s => {
      tryCatch {
        val o = f(i)
        logger.info(s"onNext: ${i} => ${o}")
        s.onNext(o)
      }()
    })
  }
}

class FilteringProcessor[I](val f:I => Boolean) extends AbstractBaseProcessor[I, I] {

  override def onNext(i: I): Unit = {
    subscriber.foreach(s => {
      tryCatch {
        if(f(i)) {
          s.onNext(i)
        }
      }()
    })
  }
}

class TryFunctionProcessor[I, O](val f:I => Try[O]) extends AbstractBaseProcessor[I, O] {

  override def onNext(i: I): Unit = {
    subscriber.foreach(s => {
      tryCatch[Unit] {
        f(i) match {
          case Success(o) => {
            logger.info(s"onNext: ${i} => ${o}")
            s.onNext(o)
          }
          case Failure(t) => {
            logger.error(t.getMessage, t)
            s.onError(t)
          }
        }
      } ()
    })
  }
}

class PartialFunctionProcessor[I, O](val f:PartialFunction[I, O]) extends AbstractBaseProcessor[I, O] {

  override def onNext(i: I): Unit = {
    subscriber.foreach(s => {
      tryCatch {
        if(f.isDefinedAt(i)) {
          val o = f(i)
          logger.info(s"onNext: ${i} => ${o}")
          s.onNext(o)
        }
      }()
    })
  }

}
