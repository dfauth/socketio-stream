package com.github.dfauth.socketio

import com.github.dfauth.utils.TryCatchUtils
import com.github.dfauth.utils.TryCatchUtils._
import com.typesafe.scalalogging.LazyLogging

class HandshakeProcessor[I, O](handshake: I => Option[O]) extends AbstractBaseProcessor[I, O] with LazyLogging {

  override def onNext(t: I): Unit = {
    // first message is the handshake
    subscriber.map(i => handshake(t).map { o =>
      tryCatch {
        logger.info(s"onNext: ${i} => ${o}")
        i.onNext(o)
      }()
    })
  }
}
