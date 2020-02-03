package com.github.dfauth.socketio

import akka.stream.scaladsl.Flow
import com.github.dfauth.auth.AuthenticationContext

trait FlowFactory[U] {
  val namespace:String
  def create(ctx: AuthenticationContext[U]):Flow[StreamMessage, Event, Any]
}