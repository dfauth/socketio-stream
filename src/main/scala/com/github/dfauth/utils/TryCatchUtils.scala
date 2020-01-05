package com.github.dfauth.utils

import com.typesafe.scalalogging.LazyLogging

object TryCatchUtils extends LazyLogging {

  def tryCatch[U](codeBlock: => U): U = {
    try {
      codeBlock
    } catch {
      case e:RuntimeException => {
        logger.error(e.getMessage, e)
        throw e
      }
      case t:Throwable => {
        logger.error(t.getMessage, t)
        //throw new RuntimeException(t)
        throw t
      }

    }
  }

}
