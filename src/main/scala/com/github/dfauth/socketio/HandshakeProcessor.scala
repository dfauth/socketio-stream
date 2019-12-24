package com.github.dfauth.socketio

import akka.stream.scaladsl.Flow
import org.reactivestreams.{Processor, Subscriber, Subscription}

class HandshakeProcessor[I, O](handshake: I => O, nested: Flow[I, O, _]) extends Processor[I, O]{

  var subscriber:Option[Subscriber[_ >: O]] = None
  var subscription:Option[Subscription] = None

  override def onSubscribe(s: Subscription): Unit = {
    subscription = Some(s)
    subscriber.map(_.onSubscribe(s))
  }

  override def onNext(t: I): Unit = {
    subscriber.map(_.onNext(handshake(t)))
  }

  override def onError(t: Throwable): Unit = subscriber.map(_.onError(t))

  override def onComplete(): Unit = subscriber.map(_.onComplete())

  override def subscribe(s: Subscriber[_ >: O]): Unit = {
    subscriber = Some(s)
    subscription.map(s.onSubscribe(_))
  }
}
