package com.github.dfauth.utils

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.typesafe.scalalogging.LazyLogging

object StreamUtils extends LazyLogging {

  def log[T](msg:String):Flow[T, T, NotUsed] = Flow.fromFunction(i => {
    logger.info(s"${msg} payload: ${i}")
    i
  })


}
