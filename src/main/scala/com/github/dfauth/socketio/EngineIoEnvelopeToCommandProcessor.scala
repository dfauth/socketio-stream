package com.github.dfauth.socketio

import com.github.dfauth.actor.Command
import com.github.dfauth.engineio.EngineIOEnvelope

case class CommandToEngineIoEnvelopeProcessor(f:EngineIOEnvelope => Command) extends AbstractBaseProcessor[EngineIOEnvelope, Command] {

  override def onNext(t: EngineIOEnvelope): Unit = {
    subscriber.map(i => {
      val o = f(t)
      logger.info(s"onNext: ${i} => ${o}")
      i.onNext(o)
    })
  }
}
