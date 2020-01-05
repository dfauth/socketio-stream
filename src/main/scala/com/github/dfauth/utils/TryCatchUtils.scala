package com.github.dfauth.utils

import com.typesafe.scalalogging.LazyLogging

object TryCatchUtils extends LazyLogging {

  def tryCatch[U](codeBlock: => U): U = {
    try {
      codeBlock
    } catch {
      case t:Throwable => {
        logger.error(t.getMessage, t)
        throw t
      }

    }
  }

}
