package com.github.dfauth.socketio.utils

import java.util
import java.util.concurrent.atomic.AtomicBoolean

import com.typesafe.scalalogging.LazyLogging

object Ackker extends LazyLogging {

  def process[T, U](supplier:() => Iterable[Ackker[T]], matcher: (T,U) => Boolean = (t:T,u:U) => t.equals(u)):U => Unit = u => {
    val found = new AtomicBoolean(true)
    supplier().filter(a =>
      found.get &&
      matcher(a.t,u))
    .foreach(a => { a.ack; found.set(false)})
    if (found.get) {
      logger.error(s"failed to find record ${u}")
    }
  }

  def enqueue[T](q:util.Queue[Ackker[T]]):T => T = (t:T) => {q.offer(Ackker[T](t));t}
}

case class Ackker[+T](t:T, acked:AtomicBoolean = new AtomicBoolean(false)) {
  def ack:Unit = acked.set(true)
  def isAcked:Boolean = acked.get()
  def payload:T = t
}

class FilteringQueue[T](capacity:Int, f:T=>Boolean) extends util.ArrayDeque[T](capacity) with LazyLogging {
  override def poll(): T = Option(super.peek()).filter(e => f(e)).map(_ => super.poll()).getOrElse(null).asInstanceOf[T]
}