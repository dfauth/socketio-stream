package com.github.dfauth.socketio

import akka.actor.Cancellable
import akka.stream.scaladsl.Source

trait SourceFactory {
  val namespaces:Iterable[String]
  def create[T >: Ackable with Eventable](namespace:String):Source[T, Cancellable]
}

trait Ackable {
  val ackId:Int
}
trait Eventable {
  val eventId:String
}
