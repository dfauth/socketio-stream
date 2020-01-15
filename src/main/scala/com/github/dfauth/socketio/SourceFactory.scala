package com.github.dfauth.socketio

import akka.actor.Cancellable
import akka.stream.scaladsl.Source

trait SourceFactory {
  val namespace:String
  def create[T >: Ackable with Eventable]:Source[T, Cancellable]
}

trait Ackable {
  val ackId:Int
}
trait Eventable {
  val eventId:String
}
