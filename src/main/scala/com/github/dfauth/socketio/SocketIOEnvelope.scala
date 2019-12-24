package com.github.dfauth.socketio

sealed class MessageType(value:Int)

case object Connect extends MessageType(0)
case object Disconnect extends MessageType(1)
case object Event extends MessageType(2)
case object Ack extends MessageType(3)
case object Error extends MessageType(4)
case object BinaryEvent extends MessageType(5)
case object BinaryAck extends MessageType(6)

case class SocketIOEnvelope(messageType:MessageType, data:Option[SocketIOPacket] = None) {

}

case class SocketIOPacket()
