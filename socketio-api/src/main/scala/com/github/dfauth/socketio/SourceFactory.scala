package com.github.dfauth.socketio

import akka.actor.Cancellable
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.Config

trait SourceFactory {
  val namespace:String
  def create[T >: Eventable]:Source[T, Cancellable]
}

trait FlowFactory {
  val namespace:String
  def create[U](ctx: UserContext[U]):Tuple2[Sink[Ackable, Any], Source[Eventable, Any]]
}

trait UserContext[U] {
  val token:String
  def userId:String
  val payload:U
  val config:Config
}
