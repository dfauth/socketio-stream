package com.github.dfauth.utils

import java.util.concurrent.TimeUnit

import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Flow, Sink}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object StreamUtils extends LazyLogging {

  def log[T](msg:String):Flow[T, T, NotUsed] = Flow.fromFunction(loggingFn(msg))

  def loggingSink[T](msg:String):Sink[T, Future[Done]] = Sink.foreach((t:T) => loggingFn(msg)(t))

  def loggingFn[T](msg:String):T => T = t => {
    logger.info(s"${msg} payload: ${t}")
    t
  }

  val ONE_SECOND = FiniteDuration(1, TimeUnit.SECONDS)
}
