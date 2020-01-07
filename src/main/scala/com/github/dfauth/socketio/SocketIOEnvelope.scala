package com.github.dfauth.socketio

import com.github.dfauth.actor.{AddNamespace, Command}
import com.github.dfauth.engineio._
import com.github.dfauth.protocol.{Bytable, ProtocolMessageType}
import com.typesafe.scalalogging.LazyLogging

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

sealed class MessageType(override val value:Int) extends ProtocolMessageType {
  def toActorMessage[U](ctx:UserContext[U], data: Option[SocketIOPacket]): Command = ???
}

case object Connect extends MessageType(0) {
  override def toActorMessage[U](ctx:UserContext[U], data: Option[SocketIOPacket]): Command = data match {
    case Some(SocketIOPacket(namespace, _)) => AddNamespace(ctx.token, namespace)
  }
}
case object Disconnect extends MessageType(1)
case object Event extends MessageType(2)
case object Ack extends MessageType(3)
case object Error extends MessageType(4)
case object BinaryEvent extends MessageType(5)
case object BinaryAck extends MessageType(6)

object SocketIOEnvelope extends LazyLogging {
  def fromBytes(b:Array[Byte]): Try[SocketIOEnvelope] = {
    b match {
      case Array() => {
        val t = new IllegalArgumentException("Oops empty string")
        logger.error(t.getMessage, t)
        Failure(t)
      }
      case Array(msgType) => Success(SocketIOEnvelope(MessageType.fromByte(msgType)))
      case Array(msgType, _*) => {
        SocketIOPacket.fromBytes(b.takeRight(b.length-1)) match {
          case Success(s) => {
            Success(SocketIOEnvelope(MessageType.fromByte(msgType), Some(s)))
          }
          case Failure(t) => Failure(t)
        }
      }
      case x => {
        val t = new IllegalArgumentException(s"Oops unknown argument ${x}")
        logger.error(t.getMessage, t)
        Failure(t)
      }
    }
  }

  def fromString(str: String): Try[SocketIOEnvelope] = {
    str.toCharArray match {
      case Array() => {
        val t = new IllegalArgumentException("Oops empty string")
        logger.error(t.getMessage, t)
        Failure(t)
      }
      case Array(msgType) => Success(SocketIOEnvelope(MessageType.fromChar(msgType)))
      case Array(msgType, _*) => Success(SocketIOEnvelope(MessageType.fromChar(msgType), Some(SocketIOPacket(str.substring(1)))))
      case x => {
        val t = new IllegalArgumentException(s"Oops unknown argument ${x}")
        logger.error(t.getMessage, t)
        Failure(t)
      }
    }
  }

  def connect(namespace:String) = {
    SocketIOEnvelope(Connect, Some(SocketIOPacket(namespace)))
  }

  def connect() = {
    SocketIOEnvelope(Connect, None)
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
  override def toString: String = {
    val payload:String = data.map(_.toString).getOrElse(new String)
    payload.length.toString+":"+messageType.toString+payload.toString
  }
}

case class SocketIOPacket(namespace:String, payload:Option[String] = None) extends Bytable {
  def toBytes: Array[Byte] = namespace.getBytes(EngineIOEnvelope.UTF8)
  override def toString:String = payload.map(namespace+","+_).getOrElse(namespace)
}

object SocketIOPacket extends LazyLogging {
  def fromBytes(bytes: Array[Byte]): Try[SocketIOPacket] = {
    (bytes.map(_.toChar)).mkString.split(",") match {
      case Array() => {
        val t = new IllegalArgumentException("Oops empty string")
        logger.error(t.getMessage, t)
        Failure(t)
      }
      case Array(namespace, _*) => {
        Success(SocketIOPacket(namespace, Some(bytes.map(_.toChar).mkString.substring(namespace.length+1))))
      }
      case x => {
        val t = new IllegalArgumentException("Oops empty string")
        logger.error(t.getMessage, t)
        Failure(t)
      }
    }
  }
}
