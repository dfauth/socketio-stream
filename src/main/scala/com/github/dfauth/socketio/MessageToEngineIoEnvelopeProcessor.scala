package com.github.dfauth.socketio

import akka.http.scaladsl.model.ws.Message
import com.github.dfauth.engineio.EngineIOEnvelope

case class EngineIoEnvelopeToMessageProcessor(f:Message => EngineIOEnvelope) extends AbstractBaseProcessor[Message, EngineIOEnvelope] {

  override def onNext(t: Message): Unit = {
    subscriber.map(i => {
      val o = f(t)
      logger.info(s"onNext: ${i} => ${o}")
      i.onNext(o)
    })
  }
}
