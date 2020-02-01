package com.github.dfauth.socketio.utils

import java.util
import java.util.concurrent.atomic.AtomicBoolean

import com.typesafe.scalalogging.LazyLogging

object Ackker extends LazyLogging {

  def process[T, U](it:Iterable[Ackker[T]], matcher: (T,U) => Boolean = (t:T,u:U) => t.equals(u)):U => Unit = u => it
      .filter(a => matcher(a.t,u))
      .collectFirst {
        case a => a.ack
      }.getOrElse{
        logger.error(s"failed to find record ${u}")
      }

  def enqueue[T](q:util.Queue[Ackker[T]]):T => Unit = (t:T) => q.offer(Ackker[T](t))

  def enqueueFn[T](q:util.Queue[Ackker[T]]):T => T = (t:T) => {enqueue(q)(t);t}
}

case class Ackker[+T](t:T, acked:AtomicBoolean = new AtomicBoolean(false)) {
  def ack:Boolean = {acked.set(true); true}
  def isAcked:Boolean = acked.get()
  def payload:T = t
}

class FilteringQueue[T](capacity:Int, f:T=>Boolean) extends util.ArrayDeque[T](capacity) with LazyLogging {
  override def poll(): T = {
    Option(peek())
      .filter(e => f(e))
      .map(_ => {
      val t:T = super.poll()
      logger.debug(s"dequeued; ${t}")
      t
    }).getOrElse(null).asInstanceOf[T]
  }
}