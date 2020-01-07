package com.github.dfauth.socketio

import com.github.dfauth.utils.TryCatchUtils
import com.github.dfauth.utils.TryCatchUtils._
import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.{Processor, Subscriber, Subscription}

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
      subscription.map(s.onSubscribe(_))
    })
  }
}

class FunctionProcessor[I, O](val f:I => O) extends AbstractBaseProcessor[I, O] {

  override def onNext(t: I): Unit = {
    subscriber.map(i => {
      tryCatch {
        val o = f(t)
        logger.info(s"onNext: ${i} => ${o}")
        i.onNext(o)
      }()
    })
  }
}
