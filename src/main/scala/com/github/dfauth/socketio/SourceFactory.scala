package com.github.dfauth.socketio

import akka.NotUsed
import akka.actor.Cancellable
import akka.stream.scaladsl.{Flow, Sink, Source}

trait SourceFactory {
  val namespace:String
  def create[T >: Eventable]:Source[T, Cancellable]
}

trait FlowFactory {
  val namespace:String
  def create[U](ctx: UserContext[U]):Tuple2[Sink[Ackable, Any], Source[Eventable, Any]]
}

trait Ackable {
  val ackId:Long
}
trait Eventable {
  val eventId:String
}
