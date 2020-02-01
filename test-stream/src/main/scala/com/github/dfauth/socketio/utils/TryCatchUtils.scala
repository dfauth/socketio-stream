package com.github.dfauth.socketio.utils

import com.typesafe.scalalogging.LazyLogging

object TryCatchUtils extends LazyLogging {

  def tryCatch[U](codeBlock: => U): U = tryCatch(codeBlock, (t:Throwable) => throw t)

  def tryCatch[U](codeBlock: => U, exceptionHandler:Throwable => U): U = {
    try {
      codeBlock
    } catch {
      case t:Throwable => {
        logger.error(t.getMessage, t)
        exceptionHandler(t)
      }
    }
  }

}
