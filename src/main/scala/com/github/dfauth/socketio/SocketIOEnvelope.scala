package com.github.dfauth.socketio

import com.github.dfauth.actor._
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
    case Some(SocketIOPacket(namespace, _, _)) => AddNamespace(ctx.token, namespace)
  }
}
case object Disconnect extends MessageType(1)
case object Event extends MessageType(2) {
  override def toActorMessage[U](ctx:UserContext[U], data: Option[SocketIOPacket]): Command = {
    data.map { e => {
      e.payload.map { f => EventCommand(ctx.token, e.namespace, Some(f)) } getOrElse { EventCommand(ctx.token, e.namespace) }

    } }.getOrElse { ErrorMessage(ctx.token, new RuntimeException("Oops"))}
  }
}
case object Ack extends MessageType(3) {
  override def toActorMessage[U](ctx:UserContext[U], data: Option[SocketIOPacket]): Command = {
    data.map {
      e => e.ackId.map ( y => AckCommand(ctx.token, e.namespace, y)).getOrElse{
        ErrorMessage(ctx.token, new RuntimeException("Oops, no ackId found"))
      }
    }.getOrElse{
      ErrorMessage(ctx.token, new RuntimeException("Oops no SocketIOPacket found"))
    }

//    data.map { e => {
//      e.payload.map { f => AckCommand(ctx.token, e.namespace, Some(f)) } getOrElse { AckCommand(ctx.token, e.namespace) }
//
//    } }.getOrElse { ErrorMessage(ctx.token, new RuntimeException("Oops"))}
  }
}
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
            Success(SocketIOEnvelope(MessageType.fromByte(msgType), s))
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
      case Array(msgType, _*) => Success(SocketIOEnvelope(MessageType.fromChar(msgType), SocketIOPacket(str.substring(1))))
      case x => {
        val t = new IllegalArgumentException(s"Oops unknown argument ${x}")
        logger.error(t.getMessage, t)
        Failure(t)
      }
    }
  }

  def apply(messageType:MessageType, data:SocketIOPacket) = new SocketIOEnvelope(messageType, Some(data))
  def apply(messageType:MessageType) = new SocketIOEnvelope(messageType)

  def connect(namespace:String) = {
    SocketIOEnvelope(Connect, SocketIOPacket(namespace))
  }

  def connect() = {
    SocketIOEnvelope(Connect, None)
  }

  def event(namespace:String, payload:String, optAckId:Option[Int] = None) = {
    optAckId.map { ackId =>
      SocketIOEnvelope(Event, SocketIOPacket(namespace, ackId, Some(payload)))
    }.getOrElse {
      SocketIOEnvelope(Event, SocketIOPacket(namespace, Some(payload)))
    }
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
    messageType.toString+payload.toString
  }
}

case class SocketIOPacket(namespace:String, ackId:Option[Int] = None, payload:Option[String] = None) extends Bytable {
  def toBytes: Array[Byte] = namespace.getBytes(EngineIOEnvelope.UTF8)
  override def toString:String = payload.map(p =>
    namespace+","+ackId.map(_.toString).getOrElse(new String)+p
  ).getOrElse(namespace)
}

object SocketIOPacket extends LazyLogging {

  def apply(namespace:String) = new SocketIOPacket(namespace)
  def apply(namespace:String, ackId:Int) = new SocketIOPacket(namespace, Some(ackId))
  def apply(namespace:String, payload:Option[String]) = new SocketIOPacket(namespace, None, payload)
  def apply(namespace:String, ackId:Int, payload:Option[String]) = new SocketIOPacket(namespace, Some(ackId), payload)

  def fromBytes(bytes: Array[Byte]): Try[SocketIOPacket] = {
    val str = (bytes.map(_.toChar)).mkString
    str.split(",") match {
      case Array() => {
        val t = new IllegalArgumentException("Oops empty string")
        logger.error(t.getMessage, t)
        Failure(t)
      }
      case Array(namespace) => {
        Success(SocketIOPacket(namespace))
      }
      case Array(namespace, _*) => {
        val tmp = str.substring(namespace.length+1)
        tmp.split("\\[") match {
          case Array("", x) => Success(SocketIOPacket(namespace, Some('['+x)))
          case Array(ackId, x) => Success(SocketIOPacket(namespace, ackId.toInt, Some('['+x)))
          case y => Failure(new RuntimeException(s"Unable to parse message: ${tmp}"))
        }
      }
      case x => {
        val t = new IllegalArgumentException("Oops empty string")
        logger.error(t.getMessage, t)
        Failure(t)
      }
    }
  }
}
