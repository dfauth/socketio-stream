package com.github.dfauth.socketio

import com.github.dfauth.actor.Command
import com.github.dfauth.engineio.EngineIOEnvelope
import com.github.dfauth.utils.TryCatchUtils
import com.github.dfauth.utils.TryCatchUtils._

case class EngineIoEnvelopeToCommandProcessor(f:EngineIOEnvelope => Command) extends AbstractBaseProcessor[EngineIOEnvelope, Command] {

  override def onNext(t: EngineIOEnvelope): Unit = {
    subscriber.map(i => {
      tryCatch {
        val o = f(t)
        logger.info(s"onNext: ${i} => ${o}")
        i.onNext(o)
      }()
    })
  }
}
