package com.github.dfauth.socketio

import akka.stream.scaladsl.Flow

trait FlowFactory[U] {
  val namespace:String
  def create(ctx: AuthenticationContext[U]):Flow[StreamMessage, Event, Any]
}