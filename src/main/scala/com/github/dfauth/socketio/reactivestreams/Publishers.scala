package com.github.dfauth.socketio.reactivestreams

import java.util
import java.util.concurrent.{BlockingQueue, Executors, ScheduledExecutorService}

import com.github.dfauth.reactivestreams.QueuePublisher

object QueuePublisher {
  def apply[T](q:util.Queue[T])(implicit ec:ScheduledExecutorService) = new QueuePublisher[T](q, ec)
}
