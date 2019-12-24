package com.github.dfauth.engineio

object EngineIOTransport {
  def valueOf(name:String):EngineIOTransport = name match {
    case "polling" => Polling
    case "websocket" => Websocket
  }
}

sealed trait EngineIOTransport {
  def name:String
}

case object Polling extends EngineIOTransport {
  override def name: String = this.productPrefix.toLowerCase()
}
case object Websocket extends EngineIOTransport {
  override def name: String = this.productPrefix.toLowerCase()
}
