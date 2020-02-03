package com.github.dfauth.socketio

import akka.stream.scaladsl.Flow
import com.github.dfauth.auth.AuthenticationContext

object ProcessorFactoryAdapter {

  def adapter[U](processorFactory:ProcessorFactory[U]):FlowFactory[U] = {
    new FlowFactory[U] {
      override val namespace: String = processorFactory.namespace()

      override def create(ctx: AuthenticationContext[U]): Flow[StreamMessage, Event, Any] =     Flow.fromProcessor(() => {
        processorFactory.create(ctx)
      })
    }
  }
}
