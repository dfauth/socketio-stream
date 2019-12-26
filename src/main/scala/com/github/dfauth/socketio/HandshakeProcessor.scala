package com.github.dfauth.socketio

import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.{Processor, Subscriber, Subscription}

class HandshakeProcessor[I, O](handshake: I => O, completionLogic:(I,O) => Boolean) extends Processor[I, O] with LazyLogging {

  var subscriber:Option[Subscriber[_ >: O]] = None
  var subscription:Option[Subscription] = None

  override def onSubscribe(s: Subscription): Unit = {
    subscription = Some(s)
    //subscription.map(_.request(1)) // as soon as our subscription is accepted, ask for the handshake
    init()
  }

  override def onNext(t: I): Unit = {
    // first message is the handshake
    subscriber.map(i => {
      val o = handshake(t)
      logger.info(s"onNext: ${i} => ${o}")
      i.onNext(o)
//      if(completionLogic((i,o))) {
//        onComplete()
//      }
    })
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
