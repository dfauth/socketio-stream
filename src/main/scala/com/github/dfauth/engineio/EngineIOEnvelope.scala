package com.github.dfauth.engineio

import java.nio.charset.Charset
import java.time.temporal.ChronoUnit

import akka.util.ByteString
import com.github.dfauth.engineio.EngineIOEnvelope.MessageType
import com.github.dfauth.socketio
import com.github.dfauth.socketio.SocketIOConfig
import com.typesafe.scalalogging.LazyLogging
import spray.json.DefaultJsonProtocol

import scala.collection.mutable.ArrayBuffer

object EngineIOEnvelope {

  val UTF8 = Charset.forName("UTF-8")

  sealed class MessageType(value:Int) {
    def getValue = value

    def toByte: Byte = (value + 48).toByte
  }

  case object Open extends MessageType(0)
  case object Close extends MessageType(1)
  case object Ping extends MessageType(2)
  case object Pong extends MessageType(3)
  case object Message extends MessageType(4)
  case object Upgrade extends MessageType(5)
  case object Noop extends MessageType(6)
  case object Error extends MessageType(7)

  def open(sid:String, config:SocketIOConfig, activeTransport:EngineIOTransport):EngineIOEnvelope = EngineIOEnvelope(Open, Some(EngineIOSessionInitPacket(sid, config.transportsFiltering(activeTransport), config.pingInterval.get(ChronoUnit.SECONDS)*1000, config.pingTimeout.get(ChronoUnit.SECONDS)*1000)))
  def message(envelope: socketio.SocketIOEnvelope):EngineIOEnvelope = EngineIOEnvelope(Message, socketioPacket = Some(envelope))
  def heartbeat(optMessage:Option[String] = None):EngineIOEnvelope = optMessage.map(m => EngineIOEnvelope(Pong, Some(EngineIOStringPacket(m)))).getOrElse(EngineIOEnvelope(Pong))
}


case class EngineIOEnvelope(messageType:MessageType, data:Option[EngineIOPacket] = None, socketioPacket:Option[socketio.SocketIOEnvelope] = None) extends LazyLogging {

  def convert(i:Int, size:Int): Array[Byte] = {
    size match {
      case 1 => {
        Array((i % 10).toByte)
      }
      case x => {
        val pow1 = Math.pow(10, size).toInt
        val v = i % pow1
        val pow = Math.pow(10, size-1).toInt
        val u = v / pow
        val result = convert(i, size - 1)
        Array(u.toByte) ++ result
      }
    }
  }

  def intToBytes(i:Int): Array[Byte] = {
    val size = Math.log10(i).toInt
    Array(0.toByte) ++ convert(i, size+1)
  }

  def toByteString: ByteString = {
    val payload:Array[Byte] = data.map(_.toBytes).getOrElse(Array.emptyByteArray)
    val bytes = ArrayBuffer[Byte]()
    bytes.appendAll(intToBytes(payload.length+1))
    bytes.append(0xff.toByte)
    bytes.append(messageType.toByte)
    bytes.appendAll(payload)
    ByteString(bytes.toArray)
  }

  override def toString: String = {
    val payload:String = data.map(_.toString).getOrElse(new String())
    val buffer = new StringBuffer()
    buffer.append(messageType.getValue)
    buffer.append(payload)
    buffer.toString
  }

}

trait EngineIOPacket {
  def toBytes():Array[Byte]
}

case class EngineIOSessionInitPacket(sid:String, upgrades:Array[String], pingInterval:Long, pingTimeout:Long) extends EngineIOPacket {
  def toBytes: Array[Byte] = EngineIO.packetFormat.write(this).toString().getBytes(EngineIOEnvelope.UTF8)
}

case class EngineIOStringPacket(message:String) extends EngineIOPacket {
  def toBytes: Array[Byte] = message.getBytes(EngineIOEnvelope.UTF8)
  override def toString:String = message
}

case class EngineIOPackets(packets:EngineIOEnvelope*) {
  def toByteString: ByteString = {
    packets.foldLeft[ByteString](ByteString())((acc, n) => acc ++ n.toByteString)
  }
  override def toString:String = {
    packets.foldLeft[String](new String())((acc, n) => acc ++ n.toString)
  }
}

object EngineIO extends DefaultJsonProtocol {
  implicit val packetFormat = jsonFormat4(EngineIOSessionInitPacket)
}