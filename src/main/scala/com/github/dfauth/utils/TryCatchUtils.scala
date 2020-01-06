package com.github.dfauth.utils

import com.typesafe.scalalogging.LazyLogging

object TryCatchUtils extends LazyLogging {

  def tryCatch[U](codeBlock: => U)(exceptionHandler:Throwable => U = (t:Throwable) => throw t): U = {
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
