package com.github.dfauth.socketio.utils

import java.util
import java.util.stream.Stream
import java.util.concurrent.atomic.AtomicBoolean

import com.typesafe.scalalogging.LazyLogging

object Ackker extends LazyLogging {

  def process[T](s:() => Stream[Ackker[T]]):T => Unit = r => {
    val found = new AtomicBoolean(true)
    s().filter(a => found.get && a.matches(r))
    .forEach(a => { a.ack; found.set(false)})
    if (found.get) {
      logger.error(s"failed to find record ${r}")
    }
  }

  def enqueue[T](q:util.Queue[Ackker[T]]):T => T = (t:T) => {q.offer(Ackker[T](t));t}
}

case class Ackker[T](t:T, acked:AtomicBoolean = new AtomicBoolean(false)) {
  def ack:Unit = acked.set(true)
  def isAcked:Boolean = acked.get()
  def payload:T = t
  def matches(t1:T): Boolean = t.equals(t1)
}

class FilteringQueue[T](capacity:Int, f:T=>Boolean) extends util.ArrayDeque[T](capacity) with LazyLogging {
  override def poll(): T = Option(super.peek()).filter(e => f(e)).map(_ => super.poll()).getOrElse(null).asInstanceOf[T]
}