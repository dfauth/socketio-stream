package com.github.dfauth.socketio

import com.github.dfauth.engineio.{EngineIOEnvelope, EngineIOPacket}
import com.github.dfauth.protocol.{Bytable, ProtocolMessageType, ProtocolOps}

import scala.collection.mutable.ArrayBuffer

sealed class MessageType(override val value:Int) extends ProtocolMessageType

case object Connect extends MessageType(0)
case object Disconnect extends MessageType(1)
case object Event extends MessageType(2)
case object Ack extends MessageType(3)
case object Error extends MessageType(4)
case object BinaryEvent extends MessageType(5)
case object BinaryAck extends MessageType(6)

object SocketIOEnvelope {
  def connect(namespace:String) = {
    SocketIOEnvelope(Connect, Some(SocketIOPacket(namespace)))
  }
}

case class SocketIOEnvelope(messageType:MessageType, data:Option[SocketIOPacket] = None) extends Bytable {
  override def toBytes: Array[Byte] = {
    val payload:Array[Byte] = data.map(_.toBytes).getOrElse(Array.emptyByteArray)
    val bytes = ArrayBuffer[Byte]()
    bytes.append(messageType.toByte)
    bytes.appendAll(payload)
    bytes.toArray
  }
}

case class SocketIOPacket(namespace:String) extends Bytable {
  def toBytes: Array[Byte] = namespace.getBytes(EngineIOEnvelope.UTF8)
  override def toString:String = namespace
}
