package com.github.dfauth.socketio

case class EventWrapper[T](name:String, payload:T) {
  override def toString: String = {
    val r = payload match {
      case c:Character => {
        s""""${payload}""""
      }
      case s:String => {
        s""""${payload}""""
      }
      case _ => payload.toString
    }
    s"""["${name}", ${r}]"""
  }
}
