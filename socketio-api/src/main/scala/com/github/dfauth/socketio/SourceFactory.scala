package com.github.dfauth.socketio

import akka.stream.scaladsl.{Sink, Source}

trait FlowFactory[U] {
  val namespace:String
  def create(ctx: AuthenticationContext[U]):Tuple2[Sink[StreamMessage, Any], Source[Event, Any]]
}