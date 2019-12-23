package com.github.dfauth.engineio

object Transport {
  def valueOf(name:String):Transport = name match {
    case "polling" => Polling
    case "websocket" => Websocket
  }
}

sealed trait Transport {
  def name:String
}

case object Polling extends Transport {
  override def name: String = this.productPrefix.toLowerCase()
}
case object Websocket extends Transport {
  override def name: String = this.productPrefix.toLowerCase()
}
