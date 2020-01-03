package com.github.dfauth.socketio

import com.github.dfauth.engineio._
import com.github.dfauth.protocol.{Bytable, ProtocolMessageType}

import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

object MessageType {
  def fromChar(c:Char) = fromByte(c.toByte)

  def fromByte(b: Byte) = (b.toInt - 48 )  match {
    case 0 => Connect
    case 1 => Disconnect
    case 2 => Event
    case 3 => Ack
    case 4 => Error
    case 5 => BinaryEvent
    case 6 => BinaryAck
  }
}

sealed class MessageType(override val value:Int) extends ProtocolMessageType

case object Connect extends MessageType(0)
case object Disconnect extends MessageType(1)
case object Event extends MessageType(2)
case object Ack extends MessageType(3)
case object Error extends MessageType(4)
case object BinaryEvent extends MessageType(5)
case object BinaryAck extends MessageType(6)

object SocketIOEnvelope {
  def fromString(str: String): Try[SocketIOEnvelope] = {
    str.toCharArray match {
      case Array() => Failure(new IllegalArgumentException("Oops empty string"))
      case Array(msgType) => Success(SocketIOEnvelope(MessageType.fromChar(msgType)))
      case Array(msgType, _*) => Success(SocketIOEnvelope(MessageType.fromChar(msgType), Some(SocketIOPacket(str.substring(1)))))
      case x => Failure(new IllegalArgumentException(s"Oops unknown argument ${x}"))
    }
  }

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
