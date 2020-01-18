package com.github.dfauth.socketio

import akka.NotUsed
import akka.actor.Cancellable
import akka.stream.scaladsl.{Flow, Sink, Source}

trait SourceFactory {
  val namespace:String
  def create[T >: Ackable with Eventable]:Source[T, Cancellable]
}

trait FlowFactory {
  val namespace:String
  def create[T >: Ackable with Eventable, U](ctx: UserContext[U]):Tuple2[Sink[T, Any], Source[T, Any]]
}

trait Ackable {
  val ackId:Long
}
trait Eventable {
  val eventId:String
}
