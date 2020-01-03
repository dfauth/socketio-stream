package com.github.dfauth.engineio

import java.nio.charset.Charset
import java.time.temporal.ChronoUnit

import com.github.dfauth.protocol.{Bytable, ProtocolMessageType, ProtocolOps}
import com.github.dfauth.socketio.{SocketIOConfig, SocketIOEnvelope}
import com.typesafe.scalalogging.LazyLogging
import spray.json.DefaultJsonProtocol

import scala.util.{Failure, Success, Try}

object EngineIOEnvelope extends LazyLogging {

  def fromBytes(b: Array[Byte]):Option[EngineIOEnvelope] = {
    b match {
      case Array(x) => {
        val msgType = MessageType.fromByte(x)
        Some(EngineIOEnvelope(msgType))
      }
      case Array(x, _*) => {
        val msgType = MessageType.fromByte(x)
        val payload = msgType.payload(b.slice(1, b.length))
        Some(EngineIOEnvelope(msgType, payload))
      }
      case _ => {
        logger.error("Unexpected empty byte array")
        None
      }
    }
  }

  def fromString(str: String):Try[EngineIOEnvelope] = {
    str.toCharArray match {
      case Array() => Failure(new IllegalArgumentException("Unexpected empty string"))
      case Array(len, ':', msgType, _*) => Success(EngineIOEnvelope(MessageType.fromChar(msgType),
        SocketIOEnvelope.fromString(str.substring(str.length-len.toString.toInt+1, str.length)).toOption
//          new Bytable() {
//            override def toBytes: Array[Byte] = str.substring(str.length-len, str.length).getBytes
//          }
      ))
      case _ => Failure(new IllegalArgumentException("Unexpected string: ${str}"))
    }
  }

  val UTF8 = Charset.forName("UTF-8")

  def open(sid:String, config:SocketIOConfig, activeTransport:EngineIOTransport):EngineIOEnvelope = EngineIOEnvelope(Open, Some(EngineIOSessionInitPacket(sid, config.transportsFiltering(activeTransport), config.pingInterval.get(ChronoUnit.SECONDS)*1000, config.pingTimeout.get(ChronoUnit.SECONDS)*1000)))
  def connect(message:String):EngineIOEnvelope = EngineIOEnvelope(Message, Some(SocketIOEnvelope.connect(message)))
  def heartbeat(optMessage:Option[String] = None):EngineIOEnvelope = optMessage.map(m => EngineIOEnvelope(Pong, Some(EngineIOStringPacket(m)))).getOrElse(EngineIOEnvelope(Pong))
  def error(optMessage:Option[String] = None):EngineIOEnvelope = optMessage.map(m => EngineIOEnvelope(Error, Some(EngineIOStringPacket(m)))).getOrElse(EngineIOEnvelope(Error))
}

object MessageType {

  def fromChar(c:Char) = fromByte(c.toByte)

  def fromByte(b: Byte) = (b.toInt - 48 )  match {
    case 0 => Open
    case 1 => Close
    case 2 => Ping
    case 3 => Pong
    case 4 => Message
    case 5 => Upgrade
    case 6 => Noop
    case 7 => Error
  }

}

sealed class MessageType(override val value:Int) extends ProtocolMessageType

case object Open extends MessageType(0)
case object Close extends MessageType(1)
case object Ping extends MessageType(2) {
  override def payload(b:Array[Byte]):Option[EngineIOPacket] = Some(EngineIOStringPacket((b.map(_.toChar)).mkString))
}
case object Pong extends MessageType(3)
case object Message extends MessageType(4)
case object Upgrade extends MessageType(5)
case object Noop extends MessageType(6)
case object Error extends MessageType(7)



case class EngineIOEnvelope(messageType:MessageType, data:Option[Bytable] = None) extends ProtocolOps

trait EngineIOPacket extends Bytable {
  def toBytes():Array[Byte]
}

case class EngineIOSessionInitPacket(sid:String, upgrades:Array[String], pingInterval:Long, pingTimeout:Long) extends EngineIOPacket {
  def toBytes: Array[Byte] = EngineIO.packetFormat.write(this).toString().getBytes(EngineIOEnvelope.UTF8)
}

case class EngineIOEmptyPacket() extends EngineIOPacket {
  def toBytes: Array[Byte] = Array.emptyByteArray
  override def toString:String = new String
}

case class EngineIOStringPacket(message:String) extends EngineIOPacket {
  def toBytes: Array[Byte] = message.getBytes(EngineIOEnvelope.UTF8)
  override def toString:String = message
}

case class EngineIOPackets(packets:EngineIOEnvelope*) {
  def toBytes: Array[Byte] = {
    packets.foldLeft[Array[Byte]](Array.emptyByteArray)((acc, n) => acc ++ n.toBytes)
  }
  override def toString:String = {
    packets.foldLeft[String](new String())((acc, n) => acc ++ n.toString)
  }
}

object EngineIO extends DefaultJsonProtocol {
  implicit val packetFormat = jsonFormat4(EngineIOSessionInitPacket)
}